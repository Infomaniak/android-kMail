/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.mail.data.cache.mailboxContent

import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.ThreadMode
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode.*
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RetryStrategy.Iteration
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.getMessages.ActivitiesResult
import com.infomaniak.mail.data.models.getMessages.ActivitiesResult.MessageFlags
import com.infomaniak.mail.data.models.getMessages.NewMessagesResult
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.*
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmSet
import io.sentry.Sentry
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class RefreshController @Inject constructor(
    private val localSettings: LocalSettings,
    private val mailboxController: MailboxController,
) {

    private var refreshThreadsJob: Job? = null

    private lateinit var refreshMode: RefreshMode
    private lateinit var mailbox: Mailbox
    private lateinit var initialFolder: Folder
    private lateinit var realm: Realm
    private var okHttpClient: OkHttpClient? = null
    private var onStart: (() -> Unit)? = null
    private var onStop: (() -> Unit)? = null
    private var endOfMessagesReached: Boolean = false

    //region Fetch Messages
    fun cancelRefresh() {
        refreshThreadsJob?.cancel(ForcedCancellationException())
    }

    fun clearCallbacks() {
        onStart = null
        onStop = null
    }

    suspend fun refreshThreads(
        refreshMode: RefreshMode,
        mailbox: Mailbox,
        folder: Folder,
        okHttpClient: OkHttpClient? = null,
        realm: Realm,
        callbacks: RefreshCallbacks? = null,
    ): Pair<Set<Thread>?, Throwable?> {

        SentryLog.i("API", "Refresh threads with mode: $refreshMode | (${folder.name})")

        refreshThreadsJob?.cancel()
        refreshThreadsJob = Job()

        setupConfiguration(refreshMode, mailbox, folder, realm, okHttpClient, callbacks)

        return refreshWithRunCatching(refreshThreadsJob!!).also { (threads, _) ->
            if (threads != null) {
                onStop?.invoke()
                SentryLog.d("API", "End of refreshing threads with mode: $refreshMode | (${folder.name})")
            }
        }
    }

    private fun setupConfiguration(
        refreshMode: RefreshMode,
        mailbox: Mailbox,
        initialFolder: Folder,
        realm: Realm,
        okHttpClient: OkHttpClient?,
        callbacks: RefreshCallbacks? = null,
    ) {
        this.refreshMode = refreshMode
        this.mailbox = mailbox
        this.initialFolder = initialFolder
        this.realm = realm
        this.okHttpClient = okHttpClient
        callbacks?.let {
            onStart = it.onStart
            onStop = it.onStop
        }
        endOfMessagesReached = false
    }

    private suspend fun refreshWithRunCatching(job: Job): Pair<Set<Thread>?, Throwable?> = runCatching {
        withContext(Dispatchers.IO + job) {
            onStart?.invoke()
            realm.handleRefreshMode(scope = this) to null
        }
    }.getOrElse {
        handleRefreshFailure(job, throwable = it)
    }

    private suspend fun retryWithRunCatching(
        job: Job,
        failedFolder: Folder,
        retryStrategy: RetryStrategy,
        returnThreads: Set<Thread>?,
    ): Pair<Set<Thread>?, Throwable?> = runCatching {
        withContext(Dispatchers.IO + job) {

            // If fetching the Activities failed because of a not found Message, we should pause briefly
            // before trying again to retrieve new Messages, to ensure that the API is up-to-date.
            delay(Utils.DELAY_BEFORE_FETCHING_ACTIVITIES_AGAIN)
            ensureActive()

            val (_, threads) = if (retryStrategy.iteration == Iteration.ABORT_MISSION) {
                realm.fetchOneNewPage(scope = this, failedFolder, shouldUpdateCursor = true)
            } else {
                realm.fetchOneNewPage(scope = this, failedFolder, retryStrategy.fibonacci)
            }

            val isSameFolder = failedFolder.id == initialFolder.id
            val impactedThreads = if (isSameFolder) threads else returnThreads

            impactedThreads to null
        }
    }.getOrElse {
        handleRefreshFailure(job, throwable = it, retryStrategy, returnThreads)
    }

    private suspend fun handleRefreshFailure(
        job: Job,
        throwable: Throwable,
        retryStrategy: RetryStrategy = RetryStrategy(),
        returnThreadsFromParameters: Set<Thread>? = null,
    ): Pair<Set<Thread>?, Throwable?> {

        val (returnThreadsFromException, exception) = if (throwable is ReturnThreadsException) {
            throwable.threads to throwable.exception
        } else {
            null to throwable
        }

        val returnThreads = returnThreadsFromParameters ?: returnThreadsFromException

        return if (exception is MessageNotFoundException) {
            handleMessageNotFound(job, exception, retryStrategy, returnThreads)
        } else {
            handleAllExceptions(exception)
            returnThreads to exception
        }
    }

    private suspend fun handleMessageNotFound(
        job: Job,
        exception: MessageNotFoundException,
        strategy: RetryStrategy,
        returnThreads: Set<Thread>?,
    ): Pair<Set<Thread>?, Throwable?> {

        fun sendMessageNotFoundSentry(failedFolder: Folder) {
            Sentry.withScope { scope ->
                val fibonacci = if (strategy.iteration == Iteration.ABORT_MISSION) -1 else strategy.fibonacci
                scope.setTag("iteration", strategy.iteration.name)
                scope.setTag("fibonacci", "$fibonacci")
                scope.setTag("direction", exception.direction.name)
                scope.setExtra("iteration", strategy.iteration.name)
                scope.setExtra("fibonacci", "$fibonacci")
                scope.setExtra("folderCursor", "${failedFolder.cursor}")
                scope.setExtra("folderName", failedFolder.name)
                Sentry.captureException(exception)
            }
        }

        suspend fun retry(failedFolder: Folder, retryStrategy: RetryStrategy): Pair<Set<Thread>?, Throwable?> {
            return retryWithRunCatching(job, failedFolder, retryStrategy, returnThreads)
        }

        val failedFolder = exception.folder

        sendMessageNotFoundSentry(failedFolder)

        when (strategy.iteration) {
            Iteration.FIRST_TIME -> {
                strategy.iteration = Iteration.SECOND_TIME
            }
            Iteration.SECOND_TIME -> {
                strategy.apply {
                    iteration = Iteration.FIBONACCI_TIME
                    fibonacci = FIBONACCI_SEQUENCE.first()
                }
            }
            Iteration.FIBONACCI_TIME -> {
                val nextFibonacci = FIBONACCI_SEQUENCE.firstOrNull { it > strategy.fibonacci }
                if (nextFibonacci == null || endOfMessagesReached) {
                    realm.writeBlocking {
                        FolderController.getFolder(id = failedFolder.id, realm = this)?.apply {
                            delete(messages)
                            delete(threads)
                            lastUpdatedAt = null
                            cursor = null
                            unreadCountLocal = 0
                            remainingOldMessagesToFetch = Folder.DEFAULT_REMAINING_OLD_MESSAGES_TO_FETCH
                            isHistoryComplete = Folder.DEFAULT_IS_HISTORY_COMPLETE
                        }
                    }
                    strategy.iteration = Iteration.ABORT_MISSION
                } else {
                    strategy.fibonacci = nextFibonacci
                }
            }
            Iteration.ABORT_MISSION -> {
                handleAllExceptions(exception)
                return returnThreads to exception
            }
        }

        return retry(failedFolder, strategy)
    }

    private suspend fun Realm.handleRefreshMode(scope: CoroutineScope): Set<Thread> {

        SentryLog.d("API", "Start of refreshing threads with mode: $refreshMode | (${initialFolder.name})")

        return when (refreshMode) {
            REFRESH_FOLDER_WITH_ROLE -> refreshWithRoleConsideration(scope)
            REFRESH_FOLDER -> refresh(scope, initialFolder)
            ONE_PAGE_OF_OLD_MESSAGES -> {
                fetchOneOldPage(scope, initialFolder)
                emptySet()
            }
        }
    }

    private suspend fun Realm.refreshWithRoleConsideration(scope: CoroutineScope): Set<Thread> {

        val impactedThreads = refresh(scope, initialFolder)

        when (initialFolder.role) {
            FolderRole.INBOX -> listOf(FolderRole.SENT, FolderRole.DRAFT)
            FolderRole.SENT -> listOf(FolderRole.INBOX, FolderRole.DRAFT)
            FolderRole.DRAFT -> listOf(FolderRole.INBOX, FolderRole.SENT)
            else -> emptyList()
        }.forEach { role ->
            scope.ensureActive()

            runCatching {
                FolderController.getFolder(role, realm = this)?.let {
                    refresh(scope, folder = it)
                }
            }.onFailure {
                throw ReturnThreadsException(impactedThreads, exception = it)
            }
        }

        return impactedThreads
    }

    private suspend fun Realm.refresh(scope: CoroutineScope, folder: Folder): Set<Thread> {

        val impactedThreads = mutableSetOf<Thread>()
        val previousCursor = folder.cursor

        impactedThreads += if (previousCursor == null) {
            fetchOneNewPage(scope, folder, shouldUpdateCursor = true).second
        } else {
            fetchActivities(scope, folder, previousCursor)
        }

        if (folder.remainingOldMessagesToFetch > 0) fetchAllOldPages(scope, folder)

        return impactedThreads
    }

    private suspend fun Realm.fetchOneOldPage(scope: CoroutineScope, folder: Folder) {
        fetchOnePage(scope, folder, Direction.IN_THE_PAST, shouldUpdateCursor = false)
    }

    private suspend fun Realm.fetchOneNewPage(
        scope: CoroutineScope,
        folder: Folder,
        fibonacci: Int = 1,
        shouldUpdateCursor: Boolean = false,
    ): Pair<Int, Set<Thread>> {
        return fetchOnePage(scope, folder, Direction.TO_THE_FUTURE, shouldUpdateCursor, fibonacci)
    }

    private suspend fun Realm.fetchAllOldPages(scope: CoroutineScope, folder: Folder) {

        fun remainingCount() = FolderController.getFolder(folder.id, realm = this)?.remainingOldMessagesToFetch ?: -1

        var remainingOldMessagesToFetch = remainingCount()

        while (remainingOldMessagesToFetch > 0) {
            scope.ensureActive()

            fetchOneOldPage(scope, folder)
            remainingOldMessagesToFetch = remainingCount()
        }
    }

    private suspend fun Realm.fetchAllNewPages(scope: CoroutineScope, folder: Folder): Set<Thread> {

        val impactedThreads = mutableSetOf<Thread>()
        var futureIsStillAhead = true

        while (futureIsStillAhead) {
            scope.ensureActive()

            val (uidsCount, threads) = fetchOneNewPage(scope, folder)
            futureIsStillAhead = uidsCount >= Utils.PAGE_SIZE
            impactedThreads += threads
        }

        return impactedThreads
    }

    private suspend fun Realm.fetchOnePage(
        scope: CoroutineScope,
        folder: Folder,
        direction: Direction,
        shouldUpdateCursor: Boolean,
        fibonacci: Int = 1,
    ): Pair<Int, Set<Thread>> {

        fun Realm.getPaginationInfo(): PaginationInfo? {
            return if (shouldUpdateCursor) {
                null
            } else {
                when (direction) {
                    Direction.IN_THE_PAST -> MessageController.getOldestMessage(folder.id, realm = this)
                    Direction.TO_THE_FUTURE -> MessageController.getNewestMessage(folder.id, fibonacci, realm = this) {
                        endOfMessagesReached = true
                    }
                }?.shortUid?.let { offsetUid ->
                    PaginationInfo(offsetUid, direction.apiCallValue)
                }
            }
        }

        fun getNewMessages(paginationInfo: PaginationInfo?): NewMessagesResult {
            return runCatching {
                getMessagesUids(folder.id, paginationInfo)!!
            }.getOrElse {
                throw if (it is ApiErrorException && it.errorCode == ErrorCode.MESSAGE_NOT_FOUND) {
                    MessageNotFoundException(it.message, folder, direction)
                } else {
                    it
                }
            }
        }

        val impactedThreads = mutableSetOf<Thread>()
        val paginationInfo = getPaginationInfo()
        val newMessages = getNewMessages(paginationInfo)
        val uidsCount = newMessages.addedShortUids.count()
        scope.ensureActive()

        impactedThreads += handleAddedUids(scope, folder, newMessages.addedShortUids, newMessages.cursor)
        updateFolder(folder.id, direction, uidsCount, paginationInfo, shouldUpdateCursor, newMessages.cursor)
        sendSentryOrphans(folder)

        return uidsCount to impactedThreads
    }

    private fun Realm.updateFolder(
        folderId: String,
        direction: Direction,
        uidsCount: Int,
        paginationInfo: PaginationInfo?,
        shouldUpdateCursor: Boolean,
        cursor: String,
    ) {

        fun Folder.theEndIsReached() {
            remainingOldMessagesToFetch = 0
            isHistoryComplete = true
        }

        fun Folder.resetHistoryInfo() {
            remainingOldMessagesToFetch = Folder.DEFAULT_REMAINING_OLD_MESSAGES_TO_FETCH
            isHistoryComplete = Folder.DEFAULT_IS_HISTORY_COMPLETE
        }

        FolderController.updateFolder(folderId, realm = this) {

            when (direction) {
                Direction.IN_THE_PAST -> {
                    if (uidsCount < Utils.PAGE_SIZE) {
                        it.theEndIsReached()
                    } else {
                        it.remainingOldMessagesToFetch = max(it.remainingOldMessagesToFetch - uidsCount, 0)
                    }
                }

                Direction.TO_THE_FUTURE -> {
                    it.lastUpdatedAt = Date().toRealmInstant()

                    // If we try to get new Messages, but `paginationInfo` is null, it's either because :
                    // - it's the 1st opening of this Folder,
                    // - or that the Folder has been emptied.
                    if (paginationInfo == null) {
                        if (uidsCount < Utils.PAGE_SIZE) {
                            it.theEndIsReached() // If we didn't even get 1 full page, it means we already reached the end.
                        } else {
                            it.resetHistoryInfo() // If the Folder has been emptied, and the end isn't reached yet, we need to reset history info, so we'll be able to get all Messages again.
                        }
                    }
                }
            }

            if (shouldUpdateCursor) {
                SentryDebug.addCursorBreadcrumb("fetchOnePage", it, cursor)
                it.cursor = cursor
            }
        }
    }

    private suspend fun Realm.fetchActivities(scope: CoroutineScope, folder: Folder, previousCursor: String): Set<Thread> {

        val activities = getMessagesUidsDelta(folder.id, previousCursor) ?: return emptySet()
        scope.ensureActive()

        val logMessage = "Deleted: ${activities.deletedShortUids.count()} | Updated: ${activities.updatedMessages.count()}"
        SentryLog.d("API", "$logMessage | ${folder.name}")

        addSentryBreadcrumbForActivities(logMessage, mailbox.email, folder, activities)

        writeBlocking {
            val impactedFoldersIds = mutableSetOf<String>().apply {
                addAll(handleDeletedUids(scope, activities.deletedShortUids, folder.id))
                addAll(handleUpdatedUids(scope, activities.updatedMessages, folder.id))
            }

            impactedFoldersIds.forEach { folderId ->
                refreshUnreadCount(folderId, realm = this)
            }

            FolderController.getFolder(folder.id, realm = this)?.let {
                it.lastUpdatedAt = Date().toRealmInstant()
                it.unreadCountRemote = activities.unreadCountRemote
                SentryDebug.addCursorBreadcrumb("fetchActivities", it, activities.cursor)
                it.cursor = activities.cursor
            }
        }

        sendSentryOrphans(folder, previousCursor)

        return fetchAllNewPages(scope, folder)
    }

    private fun refreshUnreadCount(id: String, realm: MutableRealm) {

        val folder = FolderController.getFolder(id, realm) ?: return

        val unreadCount = ThreadController.getUnreadThreadsCount(folder)
        folder.unreadCountLocal = unreadCount

        if (folder.role == FolderRole.INBOX) {
            mailboxController.updateMailbox(mailbox.objectId) { mailbox ->
                mailbox.unreadCountLocal = unreadCount
            }
        }
    }
    //endregion

    //region Added Messages
    private suspend fun Realm.handleAddedUids(
        scope: CoroutineScope,
        folder: Folder,
        uids: List<Int>,
        cursor: String,
    ): Set<Thread> {

        val logMessage = "Added: ${uids.count()}"
        SentryLog.d("API", "$logMessage | ${folder.name}")

        addSentryBreadcrumbForAddedUids(logMessage = logMessage, email = mailbox.email, folder = folder, uids = uids)

        if (uids.isEmpty()) return emptySet()

        val impactedThreads = mutableSetOf<Thread>()

        val before = System.currentTimeMillis()
        val apiResponse = ApiRepository.getMessagesByUids(mailbox.uuid, folder.id, uids, okHttpClient)
        val after = System.currentTimeMillis()
        if (!apiResponse.isSuccess()) apiResponse.throwErrorAsException()
        scope.ensureActive()

        SentryDebug.sendMissingMessages(
            sentUids = uids,
            receivedMessages = apiResponse.data?.messages ?: emptyList(),
            folder = folder,
            newCursor = cursor,
        )

        apiResponse.data?.messages?.let { messages ->

            writeBlocking {
                findLatest(folder)?.let { latestFolder ->
                    val isConversationMode = localSettings.threadMode == ThreadMode.CONVERSATION
                    val allImpactedThreads = createThreads(scope, latestFolder, messages, isConversationMode).also { threads ->
                        val foldersIds = (if (isConversationMode) threads.map { it.folderId }.toSet() else emptySet()) + folder.id
                        foldersIds.forEach { refreshUnreadCount(id = it, realm = this) }
                    }
                    SentryLog.d("Realm", "Saved Messages: ${latestFolder.name} | ${latestFolder.messages.count()}")

                    impactedThreads += allImpactedThreads.filter { it.folderId == folder.id }
                }
            }

            /**
             * Realm really doesn't like to be written on too frequently.
             * So we want to be sure that we don't write twice in less than 500 ms.
             * Appreciable side effect: it will also reduce the stress on the API.
             */
            val delay = Utils.MAX_DELAY_BETWEEN_API_CALLS - (after - before)
            if (delay > 0L) {
                delay(delay)
                scope.ensureActive()
            }

        }

        return impactedThreads
    }
    //endregion

    //region Deleted Messages
    private fun MutableRealm.handleDeletedUids(scope: CoroutineScope, shortUids: List<String>, folderId: String): Set<String> {

        val impactedFolders = mutableSetOf<String>()
        val threads = mutableSetOf<Thread>()

        shortUids.forEach { shortUid ->
            scope.ensureActive()

            val message = MessageController.getMessage(uid = shortUid.toLongUid(folderId), realm = this) ?: return@forEach

            for (thread in message.threads.reversed()) {
                scope.ensureActive()

                val isSuccess = thread.messages.remove(message)
                val numberOfMessagesInFolder = thread.messages.count { it.folderId == thread.folderId }

                // We need to save this value because the Thread could be deleted before we use this `folderId`.
                val threadFolderId = thread.folderId

                if (numberOfMessagesInFolder == 0) {
                    threads.removeIf { it.uid == thread.uid }
                    delete(thread)
                } else if (isSuccess) {
                    threads += thread
                } else {
                    continue
                }

                impactedFolders.add(threadFolderId)
            }

            MessageController.deleteMessage(message, realm = this)
        }

        threads.forEach {
            scope.ensureActive()

            it.recomputeThread(realm = this)
        }

        return impactedFolders
    }
    //endregion

    //region Updated Messages
    private fun MutableRealm.handleUpdatedUids(
        scope: CoroutineScope,
        messageFlags: List<MessageFlags>,
        folderId: String,
    ): Set<String> {
        val impactedFolders = mutableSetOf<String>()
        val threads = mutableSetOf<Thread>()

        messageFlags.forEach { flags ->
            scope.ensureActive()

            val uid = flags.shortUid.toLongUid(folderId)
            MessageController.getMessage(uid, realm = this)?.let { message ->
                message.updateFlags(flags)
                threads += message.threads
            }
        }

        threads.forEach { thread ->
            scope.ensureActive()

            impactedFolders.add(thread.folderId)
            thread.recomputeThread(realm = this)
        }

        return impactedFolders
    }
    //endregion

    //region Create Threads
    private fun MutableRealm.createThreads(
        scope: CoroutineScope,
        folder: Folder,
        remoteMessages: List<Message>,
        isConversationMode: Boolean,
    ): Set<Thread> {

        val idsOfFoldersWithIncompleteThreads = if (isConversationMode) {
            FolderController.getIdsOfFoldersWithIncompleteThreads(realm = this)
        } else {
            emptyList()
        }
        val impactedThreadsManaged = mutableSetOf<Thread>()
        val existingMessages = folder.messages.associateByTo(mutableMapOf()) { it.uid }

        remoteMessages.forEach { remoteMessage ->
            scope.ensureActive()

            val shouldSkipThisMessage = addRemoteMessageToFolder(remoteMessage, folder, existingMessages)
            if (shouldSkipThisMessage) return@forEach

            val newThread = if (isConversationMode) {

                // Other pre-existing Threads that will also require this Message and will provide the prior Messages for this new Thread.
                val existingThreads = ThreadController.getThreads(remoteMessage.messageIds, realm = this)

                // Some Messages don't have references to all previous Messages of the Thread (ex: these from the iOS Mail app).
                // Because we are missing the links between Messages, it will create multiple Threads for the same Folder.
                // Hence, we need to find these duplicates.
                val isThereDuplicatedThreads = isThereDuplicatedThreads(remoteMessage.messageIds, existingThreads.count())

                val thread = createNewThreadIfRequired(
                    scope,
                    existingThreads,
                    remoteMessage,
                    idsOfFoldersWithIncompleteThreads,
                )

                updateOtherExistingThreads(existingThreads, remoteMessage, scope, impactedThreadsManaged)

                // Now that all other existing Threads are updated, we need to remove the duplicated Threads.
                if (isThereDuplicatedThreads) removeDuplicatedThreads(remoteMessage.messageIds, impactedThreadsManaged)

                thread
            } else {
                remoteMessage.toThread()
            }

            newThread?.let { addNewThreadToFolder(it, folder, impactedThreadsManaged) }

            existingMessages[remoteMessage.uid] = remoteMessage
        }

        val impactedThreadsUnmanaged = mutableSetOf<Thread>()
        impactedThreadsManaged.forEach {
            scope.ensureActive()

            it.recomputeThread(realm = this)
            impactedThreadsUnmanaged.add(it.copyFromRealm(1u))
        }

        return impactedThreadsUnmanaged
    }

    private fun MutableRealm.addRemoteMessageToFolder(
        remoteMessage: Message,
        folder: Folder,
        existingMessages: MutableMap<String, Message>,
    ): Boolean {

        // Get managed version of existing Message
        val existingMessage = existingMessages[remoteMessage.uid]?.let {
            if (it.isManaged()) it else MessageController.getMessage(it.uid, realm = this)
        }
        // Send Sentry and leave if needed
        if (existingMessage != null && !existingMessage.isOrphan()) {
            SentryDebug.sendAlreadyExistingMessage(folder, remoteMessage, localSettings.threadMode)
            return true
        }

        remoteMessage.initLocalValues(
            date = remoteMessage.date,
            isFullyDownloaded = false,
            isTrashed = folder.role == FolderRole.TRASH,
            isFromSearch = false,
            draftLocalUuid = null,
        )

        if (existingMessage == null) folder.messages.add(remoteMessage)

        return false
    }

    private fun MutableRealm.isThereDuplicatedThreads(messageIds: RealmSet<String>, threadsCount: Int): Boolean {
        val foldersCount = ThreadController.getExistingThreadsFoldersCount(messageIds, realm = this)
        return foldersCount != threadsCount.toLong()
    }

    private fun MutableRealm.removeDuplicatedThreads(messageIds: RealmSet<String>, impactedThreadsManaged: MutableSet<Thread>) {

        // Create a map with all duplicated Threads of the same Thread in a list.
        val map = mutableMapOf<String, MutableList<Thread>>()
        ThreadController.getThreads(messageIds, realm = this).forEach {
            map.getOrPut(it.folderId) { mutableListOf() }.add(it)
        }

        map.values.forEach { threads ->
            threads.forEachIndexed { index, thread ->
                if (index > 0) { // We want to keep only 1 duplicated Thread, so we skip the 1st one. (He's the chosen one!)
                    impactedThreadsManaged.remove(thread)
                    delete(thread) // Delete the other Threads. Sorry bro, you won't be missed.
                }
            }
        }
    }

    private fun TypedRealm.createNewThreadIfRequired(
        scope: CoroutineScope,
        existingThreads: List<Thread>,
        newMessage: Message,
        idsOfFoldersWithIncompleteThreads: List<String>,
    ): Thread? {
        var newThread: Thread? = null

        if (existingThreads.none { it.folderId == newMessage.folderId }) {

            newThread = newMessage.toThread()

            val referenceThread = getReferenceThread(existingThreads, idsOfFoldersWithIncompleteThreads)
            if (referenceThread != null) addPreviousMessagesToThread(scope, newThread, referenceThread)
        }

        return newThread
    }

    private fun MutableRealm.updateOtherExistingThreads(
        existingThreads: RealmResults<Thread>,
        remoteMessage: Message,
        scope: CoroutineScope,
        impactedThreadsManaged: MutableSet<Thread>,
    ) {
        if (existingThreads.isEmpty()) return

        val allExistingMessages = mutableSetOf<Message>().apply {
            existingThreads.forEach { addAll(it.messages) }
            add(remoteMessage)
        }

        existingThreads.forEach { thread ->
            scope.ensureActive()

            allExistingMessages.forEach { existingMessage ->
                scope.ensureActive()

                if (!thread.messages.contains(existingMessage)) {
                    thread.messagesIds += existingMessage.messageIds
                    thread.addMessageWithConditions(existingMessage, realm = this)
                }
            }

            impactedThreadsManaged += thread
        }
    }

    private fun MutableRealm.addNewThreadToFolder(newThread: Thread, folder: Folder, impactedThreadsManaged: MutableSet<Thread>) {
        ThreadController.upsertThread(newThread, realm = this).also {
            folder.threads.add(it)
            impactedThreadsManaged += it
        }
    }

    /**
     * We need to add 2 things to a new Thread:
     * - the previous Messages `messagesIds`
     * - the previous Messages, depending on conditions (for example, we don't want deleted Messages outside of the Trash)
     * If there is no `existingThread` with all the Messages, we fallback on an `incompleteThread` to get its `messagesIds`.
     */
    private fun getReferenceThread(existingThreads: List<Thread>, idsOfFoldersWithIncompleteThreads: List<String>): Thread? {
        return existingThreads.firstOrNull { !idsOfFoldersWithIncompleteThreads.contains(it.folderId) }
            ?: existingThreads.firstOrNull()
    }

    private fun TypedRealm.addPreviousMessagesToThread(scope: CoroutineScope, newThread: Thread, existingThread: Thread) {

        newThread.messagesIds += existingThread.messagesIds

        existingThread.messages.forEach { message ->
            scope.ensureActive()

            newThread.addMessageWithConditions(message, realm = this)
        }
    }

    private fun Thread.addMessageWithConditions(message: Message, realm: TypedRealm) {
        val folderRole = FolderController.getFolder(folderId, realm)?.role
        addMessageWithConditions(message, folderRole)
    }
    //endregion

    //region API calls
    private fun getMessagesUids(folderId: String, info: PaginationInfo?): NewMessagesResult? {
        return with(ApiRepository.getMessagesUids(mailbox.uuid, folderId, okHttpClient, info)) {
            if (!isSuccess()) throwErrorAsException()
            return@with data
        }
    }

    private fun getMessagesUidsDelta(folderId: String, previousCursor: String): ActivitiesResult? {
        return with(ApiRepository.getMessagesUidsDelta(mailbox.uuid, folderId, previousCursor, okHttpClient)) {
            if (!isSuccess()) throwErrorAsException()
            return@with data
        }
    }
    //endregion

    //region Handle errors
    private fun handleAllExceptions(throwable: Throwable) {

        // We force-cancelled, so we need to call the `stopped` callback.
        if (throwable is ForcedCancellationException) onStop?.invoke()

        // It failed, but not because we cancelled it. Something bad happened, so we call the `stopped` callback.
        if (throwable !is CancellationException) onStop?.invoke()

        if (throwable is ApiErrorException) throwable.handleOtherApiErrors()
    }

    private fun ApiErrorException.handleOtherApiErrors() {
        when (errorCode) {
            ErrorCode.FOLDER_DOES_NOT_EXIST -> Unit // Here, we want to fail silently. We are just outdated, it will be ok.
            ErrorCode.MESSAGE_NOT_FOUND -> Unit // This Sentry is already sent via `sendMessageNotFoundSentry()`
            else -> Sentry.captureException(this)
        }
    }

    private fun Realm.sendSentryOrphans(folder: Folder, previousCursor: String? = null) {
        writeBlocking {
            findLatest(folder)?.let {
                SentryDebug.sendOrphanMessages(previousCursor, folder = it).also { orphans ->
                    MessageController.deleteMessages(orphans, realm = this)
                }
                SentryDebug.sendOrphanThreads(previousCursor, folder = it, realm = this).also { orphans ->
                    orphans.forEach { thread -> MessageController.deleteMessages(thread.messages, realm = this) }
                    delete(orphans)
                }
            }
        }
    }

    private fun addSentryBreadcrumbForActivities(
        logMessage: String,
        email: String,
        folder: Folder,
        activities: ActivitiesResult,
    ) {
        SentryDebug.addThreadsAlgoBreadcrumb(
            message = logMessage,
            data = mapOf(
                "1_mailbox" to email,
                "2_folderName" to folder.name,
                "3_folderId" to folder.id,
                "5_deleted" to activities.deletedShortUids.map { it },
                "6_updated" to activities.updatedMessages.map { it.shortUid },
            ),
        )
    }

    private fun addSentryBreadcrumbForAddedUids(
        logMessage: String,
        email: String,
        folder: Folder,
        uids: List<Int>,
    ) {
        SentryDebug.addThreadsAlgoBreadcrumb(
            message = logMessage,
            data = mapOf(
                "1_mailbox" to email,
                "2_folderName" to folder.name,
                "3_folderId" to folder.id,
                "4_added" to uids,
            ),
        )
    }
    //endregion

    enum class RefreshMode {
        REFRESH_FOLDER, /* Fetch activities, and also get old Messages until `NUMBER_OF_OLD_MESSAGES_TO_FETCH` is reached */
        REFRESH_FOLDER_WITH_ROLE, /* Same as `NEW_MESSAGES`, but also check if other Folders need to be updated (Inbox, Sent, Draft, etc…) */
        ONE_PAGE_OF_OLD_MESSAGES, /* Get 1 page of old Messages */
    }

    private enum class Direction(val apiCallValue: String) {
        IN_THE_PAST("previous"), /* To get more pages of old Messages */
        TO_THE_FUTURE("following"), /* To get more pages of new Messages */
    }

    data class PaginationInfo(
        val offsetUid: Int,
        val direction: String,
    )

    private class ReturnThreadsException(val threads: Set<Thread>, val exception: Throwable) : Exception()

    private class ForcedCancellationException : CancellationException()

    private class MessageNotFoundException(
        override val message: String?,
        val folder: Folder,
        val direction: Direction,
    ) : ApiErrorException(message)

    private data class RetryStrategy(
        var iteration: Iteration = Iteration.FIRST_TIME,
        var fibonacci: Int = 1,
    ) {
        enum class Iteration {
            FIRST_TIME,
            SECOND_TIME,
            FIBONACCI_TIME,
            ABORT_MISSION,
        }
    }

    data class RefreshCallbacks(
        val onStart: (() -> Unit),
        val onStop: (() -> Unit),
    )

    private companion object {
        val FIBONACCI_SEQUENCE = arrayOf(2, 8, 34, 144)
    }
}
