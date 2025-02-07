/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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

import android.content.Context
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.ThreadMode
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode.*
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.getMessages.ActivitiesResult
import com.infomaniak.mail.data.models.getMessages.ActivitiesResult.MessageFlags
import com.infomaniak.mail.data.models.getMessages.NewMessagesResult
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.message.Message.MessageInitialState
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.ApiErrorException
import com.infomaniak.mail.utils.ErrorCode
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.SentryDebug.displayForSentry
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.extensions.throwErrorAsException
import com.infomaniak.mail.utils.extensions.toLongUid
import com.infomaniak.mail.utils.extensions.toRealmInstant
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmList
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
    private val appContext: Context,
    private val localSettings: LocalSettings,
    private val mailboxController: MailboxController,
    private val delayApiCallManager: DelayApiCallManager,
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

    private fun clearCallbacks() {
        onStart = null
        onStop = null
    }

    suspend fun refreshThreads(
        refreshMode: RefreshMode,
        mailbox: Mailbox,
        folderId: String,
        okHttpClient: OkHttpClient? = null,
        realm: Realm,
        callbacks: RefreshCallbacks? = null,
    ): Pair<Set<Thread>?, Throwable?> {

        val folder = FolderController.getFolder(folderId, realm)!!

        // If the Mailbox is not available (i.e. the mailbox is locked or its password is wrong),
        // we'll be denied permission to fetch it by the API, so we don't even try to do it.
        // We can leave safely.
        if (!mailbox.isAvailable) {
            SentryLog.w("API", "Refresh threads: We left early because of a predictable denied access.")
            return emptySet<Thread>() to null
        }

        SentryLog.i("API", "Refresh threads with mode: $refreshMode | (${folder.displayForSentry()})")

        refreshThreadsJob?.cancel()
        refreshThreadsJob = Job()

        setupConfiguration(refreshMode, mailbox, folder, realm, okHttpClient, callbacks)

        return refreshWithRunCatching(refreshThreadsJob!!).also { (threads, _) ->

            ThreadController.deleteEmptyThreadsInFolder(folder.id, realm)

            if (threads != null) {
                onStop?.invoke()
                SentryLog.d("API", "End of refreshing threads with mode: $refreshMode | (${folder.displayForSentry()})")
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
            this.onStart = it.onStart
            this.onStop = it.onStop
        }
        this.endOfMessagesReached = false
    }

    private suspend fun refreshWithRunCatching(job: Job): Pair<Set<Thread>?, Throwable?> = runCatching {
        withContext(Dispatchers.IO + job) {
            onStart?.invoke()
            realm.handleRefreshMode(scope = this) to null
        }
    }.getOrElse {
        handleRefreshFailure(throwable = it)
    }

    private fun handleRefreshFailure(throwable: Throwable): Pair<Set<Thread>?, Throwable?> {

        val (returnThreads, exception) = if (throwable is ReturnThreadsException) {
            throwable.threads to throwable.exception
        } else {
            null to throwable
        }

        handleAllExceptions(exception)

        return returnThreads to exception
    }

    private suspend fun Realm.handleRefreshMode(scope: CoroutineScope): Set<Thread> {

        SentryLog.d(
            "API",
            "Start of refreshing threads with mode: $refreshMode | (${initialFolder.displayForSentry()})",
        )

        return when (refreshMode) {
            REFRESH_FOLDER_WITH_ROLE -> refreshWithRoleConsideration(scope)
            REFRESH_FOLDER -> {
                refresh(scope, initialFolder).also {
                    onStop?.invoke()
                    clearCallbacks()
                }
            }
            ONE_PAGE_OF_OLD_MESSAGES -> {
                fetchOnePageOfOldMessages(scope, initialFolder.id)
                emptySet()
            }
        }
    }

    private suspend fun Realm.refreshWithRoleConsideration(scope: CoroutineScope): Set<Thread> {

        val impactedThreads = refresh(scope, initialFolder)
        onStop?.invoke()
        clearCallbacks()

        when (initialFolder.role) {
            FolderRole.INBOX -> listOf(FolderRole.SENT, FolderRole.DRAFT, FolderRole.SCHEDULED_DRAFTS)
            FolderRole.SENT -> listOf(FolderRole.INBOX, FolderRole.DRAFT, FolderRole.SCHEDULED_DRAFTS)
            FolderRole.DRAFT -> listOf(FolderRole.INBOX, FolderRole.SENT, FolderRole.SCHEDULED_DRAFTS)
            FolderRole.SCHEDULED_DRAFTS -> listOf(FolderRole.INBOX, FolderRole.SENT, FolderRole.DRAFT)
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

        val previousCursor = folder.cursor
        val impactedThreads = mutableSetOf<Thread>()

        if (previousCursor == null) {
            fetchOldMessagesUids(scope, folder)
        } else {
            fetchActivities(scope, folder, previousCursor)
        }

        impactedThreads += fetchAllNewPages(scope, folder.id)
        fetchAllOldPages(scope, folder.id)

        return impactedThreads
    }

    private suspend fun Realm.fetchOnePageOfOldMessages(scope: CoroutineScope, folderId: String) {

        var totalNewThreads = 0
        var upToDateFolder = getUpToDateFolder(folderId)
        var maxPagesToFetch = Utils.MAX_OLD_PAGES_TO_FETCH_TO_GET_ENOUGH_NEW_THREADS

        while (
            totalNewThreads < Utils.PAGE_SIZE / 2 &&
            upToDateFolder.oldMessagesUidsToFetch.isNotEmpty() &&
            maxPagesToFetch > 0
        ) {
            totalNewThreads += fetchOnePage(scope, upToDateFolder, Direction.IN_THE_PAST).count()
            upToDateFolder = getUpToDateFolder(folderId)
            maxPagesToFetch--
        }
    }

    private suspend fun Realm.fetchOldMessagesUids(scope: CoroutineScope, folder: Folder) {

        val result = getDateOrderedMessagesUids(folder.id)!!
        scope.ensureActive()

        FolderController.updateFolder(folder.id, realm = this) {
            it.oldMessagesUidsToFetch.replaceContent(result.addedShortUids)
            it.lastUpdatedAt = Date().toRealmInstant()
            it.cursor = result.cursor
        }
    }

    private suspend fun Realm.fetchActivities(scope: CoroutineScope, folder: Folder, previousCursor: String) {

        val activities = getMessagesUidsDelta(folder.id, previousCursor) ?: return
        scope.ensureActive()

        val logMessage = "Deleted: ${activities.deletedShortUids.count()} | " +
                "Updated: ${activities.updatedMessages.count()} | " +
                "Added: ${activities.addedShortUids.count()}"
        SentryLog.d("API", "$logMessage | ${folder.displayForSentry()}")

        addSentryBreadcrumbForActivities(logMessage, mailbox.email, folder, activities)

        var inboxUnreadCount: Int? = null
        write {
            val impactedFoldersIds = mutableSetOf<String>().apply {
                addAll(handleDeletedUids(scope, activities.deletedShortUids, folder.id))
                addAll(handleUpdatedUids(scope, activities.updatedMessages, folder.id))
            }

            inboxUnreadCount = updateFoldersUnreadCount(impactedFoldersIds, realm = this)

            getUpToDateFolder(folder.id).let {
                it.newMessagesUidsToFetch.addAll(activities.addedShortUids)
                it.unreadCountRemote = activities.unreadCountRemote
                it.lastUpdatedAt = Date().toRealmInstant()
                if (it.role == FolderRole.SCHEDULED_DRAFTS) it.isDisplayed = it.threads.isNotEmpty()
                it.cursor = activities.cursor
                SentryDebug.addCursorBreadcrumb("fetchActivities", it, activities.cursor)
            }
        }

        updateMailboxUnreadCount(inboxUnreadCount)

        sendOrphanMessages(folder, previousCursor)
    }

    private suspend fun Realm.fetchAllNewPages(scope: CoroutineScope, folderId: String): Set<Thread> {

        val impactedThreads = mutableSetOf<Thread>()
        var folder = getUpToDateFolder(folderId)

        while (folder.newMessagesUidsToFetch.isNotEmpty()) {
            scope.ensureActive()

            impactedThreads += fetchOnePage(scope, folder, Direction.TO_THE_FUTURE)
            folder = getUpToDateFolder(folderId)
        }

        return impactedThreads
    }

    private suspend fun Realm.fetchAllOldPages(scope: CoroutineScope, folderId: String) {

        var folder = getUpToDateFolder(folderId)

        while (folder.remainingOldMessagesToFetch > 0) {
            scope.ensureActive()

            fetchOnePage(scope, folder, Direction.IN_THE_PAST)
            folder = getUpToDateFolder(folderId)
        }
    }

    private suspend fun Realm.fetchOnePage(scope: CoroutineScope, folder: Folder, direction: Direction): Set<Thread> {

        val allUids = if (direction == Direction.TO_THE_FUTURE) folder.newMessagesUidsToFetch else folder.oldMessagesUidsToFetch
        val (uidsToFetch, uidsRemaining) = if (allUids.count() > Utils.PAGE_SIZE) {
            allUids.subList(0, Utils.PAGE_SIZE) to allUids.subList(Utils.PAGE_SIZE, allUids.count())
        } else {
            allUids to emptyList<Int>().toRealmList()
        }

        val impactedThreads = handleAddedUids(scope, folder, uidsToFetch)

        FolderController.updateFolder(folder.id, realm = this) {
            if (direction == Direction.TO_THE_FUTURE) {
                it.newMessagesUidsToFetch.replaceContent(uidsRemaining)
                it.lastUpdatedAt = Date().toRealmInstant()
            } else {
                it.oldMessagesUidsToFetch.replaceContent(uidsRemaining)
                it.remainingOldMessagesToFetch = if (it.oldMessagesUidsToFetch.isEmpty()) {
                    0
                } else {
                    max(it.remainingOldMessagesToFetch - uidsToFetch.count(), 0)
                }
            }

            if (it.role == FolderRole.SCHEDULED_DRAFTS) it.isDisplayed = it.threads.isNotEmpty()
        }

        sendOrphanMessages(folder)

        return impactedThreads
    }

    private fun updateFoldersUnreadCount(foldersIds: Set<String>, realm: MutableRealm): Int? {
        return foldersIds.firstNotNullOfOrNull {
            val folder = realm.getUpToDateFolder(it)

            val unreadCount = ThreadController.getUnreadThreadsCount(folder)
            folder.unreadCountLocal = unreadCount

            return@firstNotNullOfOrNull if (folder.role == FolderRole.INBOX) unreadCount else null
        }
    }

    private suspend fun updateMailboxUnreadCount(unreadCount: Int?) {
        if (unreadCount == null) return

        mailboxController.updateMailbox(mailbox.objectId) {
            it.unreadCountLocal = unreadCount
        }
    }

    private fun TypedRealm.getUpToDateFolder(id: String) = FolderController.getFolder(id, realm = this)!!

    private inline fun <reified T> RealmList<T>.replaceContent(list: List<T>) {
        clear()
        addAll(list.toRealmList())
    }
    //endregion

    //region Added Messages
    private suspend fun Realm.handleAddedUids(
        scope: CoroutineScope,
        folder: Folder,
        uids: List<Int>,
    ): Set<Thread> {

        val logMessage = "Added: ${uids.count()}"
        SentryLog.d("API", "$logMessage | ${folder.displayForSentry()}")

        addSentryBreadcrumbForAddedUids(logMessage = logMessage, email = mailbox.email, folder = folder, uids = uids)

        if (uids.isEmpty()) return emptySet()

        val impactedThreads = mutableSetOf<Thread>()

        val apiResponse = delayApiCallManager.getMessagesByUids(scope, mailbox.uuid, folder.id, uids, okHttpClient)
        if (!apiResponse.isSuccess()) apiResponse.throwErrorAsException()
        scope.ensureActive()

        apiResponse.data?.messages?.let { messages ->

            var inboxUnreadCount: Int? = null
            write {
                val upToDateFolder = getUpToDateFolder(folder.id)
                val isConversationMode = localSettings.threadMode == ThreadMode.CONVERSATION
                val allImpactedThreads = createThreads(scope, upToDateFolder, messages, isConversationMode).also { threads ->
                    inboxUnreadCount = updateFoldersUnreadCount(
                        foldersIds = (if (isConversationMode) threads.mapTo(mutableSetOf()) { it.folderId } else emptySet()) + folder.id,
                        realm = this,
                    )
                }

                val messagesCount = MessageController.getMessagesCountByFolderId(upToDateFolder.id, realm = this)
                SentryLog.d(
                    "Realm",
                    "Saved Messages: ${upToDateFolder.displayForSentry()} | ($messagesCount)",
                )

                impactedThreads += allImpactedThreads.filter { it.folderId == upToDateFolder.id }
            }

            updateMailboxUnreadCount(inboxUnreadCount)
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

            for (thread in message.threads.asReversed()) {
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

            MessageController.deleteMessage(appContext, mailbox, message, realm = this)
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

        val impactedThreadsManaged = mutableSetOf<Thread>()
        val folderMessages = folder.messages(realm = this).associateByTo(mutableMapOf()) { it.uid }
        val addedMessagesUids = mutableListOf<Int>()

        remoteMessages.forEach { remoteMessage ->
            scope.ensureActive()

            if (remoteMessage.uid.substringAfter('@') != folder.id) {
                SentryDebug.sendMessageInWrongFolder(remoteMessage, folder, realm = this)
            }

            initMessageLocalValues(remoteMessage, folder)

            val shouldSkipThisMessage = isThisMessageAlreadyInRealm(remoteMessage, folder, folderMessages)
            if (shouldSkipThisMessage) return@forEach

            addedMessagesUids.add(remoteMessage.shortUid)

            val newThread = if (isConversationMode) {
                createNewThread(scope, remoteMessage, impactedThreadsManaged)
            } else {
                remoteMessage.toThread()
            }

            newThread?.let { addNewThreadToFolder(it, folder, impactedThreadsManaged) }

            folderMessages[remoteMessage.uid] = remoteMessage
        }

        addSentryBreadcrumbForAddedUidsInFolder(addedMessagesUids)

        val impactedThreadsUnmanaged = mutableSetOf<Thread>()
        impactedThreadsManaged.forEach {
            scope.ensureActive()

            it.recomputeThread(realm = this)
            impactedThreadsUnmanaged.add(it.copyFromRealm(depth = 0u))
        }

        return impactedThreadsUnmanaged
    }

    private fun MutableRealm.createNewThread(
        scope: CoroutineScope,
        remoteMessage: Message,
        impactedThreadsManaged: MutableSet<Thread>,
    ): Thread? {
        // Other pre-existing Threads that will also require this Message and will provide the prior Messages for this new Thread.
        val existingThreads = ThreadController.getThreads(remoteMessage.messageIds, realm = this)
        val existingMessages = getExistingMessages(existingThreads)

        // Some Messages don't have references to all previous Messages of the Thread (ex: these from the iOS Mail app).
        // Because we are missing the links between Messages, it will create multiple Threads for the same Folder.
        // Hence, we need to find these duplicates.
        val isThereDuplicatedThreads = isThereDuplicatedThreads(remoteMessage.messageIds, existingThreads.count())

        // Create Thread in this Folder
        val thread = createNewThreadIfRequired(scope, remoteMessage, existingThreads, existingMessages)
        // Update Threads in other Folders
        updateOtherExistingThreads(scope, remoteMessage, existingThreads, existingMessages, impactedThreadsManaged)

        // Now that all other existing Threads are updated, we need to remove the duplicated Threads.
        if (isThereDuplicatedThreads) removeDuplicatedThreads(remoteMessage.messageIds, impactedThreadsManaged)

        return thread
    }

    private fun initMessageLocalValues(remoteMessage: Message, folder: Folder) {
        remoteMessage.initLocalValues(
            MessageInitialState(
                date = remoteMessage.date,
                isFullyDownloaded = false,
                isTrashed = folder.role == FolderRole.TRASH,
                isFromSearch = false,
                draftLocalUuid = null,
            ),
            latestCalendarEventResponse = null,
        )
    }

    private fun MutableRealm.isThisMessageAlreadyInRealm(
        remoteMessage: Message,
        folder: Folder,
        folderMessages: MutableMap<String, Message>,
    ): Boolean {

        // Get managed version of existing Message
        val existingMessage = folderMessages[remoteMessage.uid]?.let {
            if (it.isManaged()) it else MessageController.getMessage(it.uid, realm = this)
        }

        // Add Sentry log and leave if the Message already exists
        if (existingMessage != null && !existingMessage.isOrphan()) {
            SentryLog.i(
                "Realm",
                "Already existing message in folder ${folder.displayForSentry()} | threadMode = ${localSettings.threadMode}",
            )
            return true
        }

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
        newMessage: Message,
        existingThreads: List<Thread>,
        existingMessages: Set<Message>,
    ): Thread? {
        var newThread: Thread? = null

        if (existingThreads.none { it.folderId == newMessage.folderId }) {

            newThread = newMessage.toThread()

            addPreviousMessagesToThread(scope, newThread, existingMessages)
        }

        return newThread
    }

    private fun MutableRealm.updateOtherExistingThreads(
        scope: CoroutineScope,
        remoteMessage: Message,
        existingThreads: RealmResults<Thread>,
        existingMessages: Set<Message>,
        impactedThreadsManaged: MutableSet<Thread>,
    ) {
        if (existingThreads.isEmpty()) return

        val allExistingMessages = mutableSetOf<Message>().apply {
            addAll(existingMessages)
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

    private fun getExistingMessages(existingThreads: List<Thread>): Set<Message> {
        return existingThreads.flatMapTo(mutableSetOf()) { it.messages }
    }

    private fun TypedRealm.addPreviousMessagesToThread(
        scope: CoroutineScope,
        newThread: Thread,
        referenceMessages: Set<Message>,
    ) {
        referenceMessages.forEach { message ->
            scope.ensureActive()

            newThread.apply {
                messagesIds += message.computeMessageIds()
                addMessageWithConditions(message, realm = this@addPreviousMessagesToThread)
            }
        }
    }
    //endregion

    //region API calls
    private fun getDateOrderedMessagesUids(folderId: String): NewMessagesResult? {
        return with(ApiRepository.getDateOrderedMessagesUids(mailbox.uuid, folderId, okHttpClient)) {
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

        if (throwable is ApiErrorException) throwable.handleOtherApiErrors()

        // This is the end. The `onStop` callback should be called before we are gone.
        onStop?.invoke()
        clearCallbacks()
    }

    private fun ApiErrorException.handleOtherApiErrors() {
        when (errorCode) {
            ErrorCode.FOLDER_DOES_NOT_EXIST -> Unit // Here, we want to fail silently. We are just outdated, it will be ok.
            ErrorCode.MESSAGE_NOT_FOUND -> Unit // This Sentry is already sent via `sendMessageNotFoundSentry()`
            else -> Sentry.captureException(this)
        }
    }

    private suspend fun Realm.sendOrphanMessages(folder: Folder, previousCursor: String? = null) {
        write {
            val upToDateFolder = getUpToDateFolder(folder.id)
            SentryDebug.sendOrphanMessages(previousCursor, folder = upToDateFolder, realm = this).also { orphans ->
                MessageController.deleteMessages(appContext, mailbox, orphans, realm = this)
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
                "7_added" to activities.addedShortUids.map { it },
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

    private fun addSentryBreadcrumbForAddedUidsInFolder(uids: List<Int>) {
        SentryDebug.addThreadsAlgoBreadcrumb(
            message = "Added in Folder",
            data = mapOf(
                "uids" to uids,
            ),
        )
    }
    //endregion

    enum class RefreshMode {
        REFRESH_FOLDER, /* Fetch activities, and also get old Messages until `NUMBER_OF_OLD_MESSAGES_TO_FETCH` is reached */
        REFRESH_FOLDER_WITH_ROLE, /* Same, but also check if other Folders need to be updated (Inbox, Sent, Draft, etc…) */
        ONE_PAGE_OF_OLD_MESSAGES, /* Get 1 page of old Messages */
    }

    private enum class Direction {
        IN_THE_PAST, /* To get more pages of old Messages */
        TO_THE_FUTURE, /* To get more pages of new Messages */
    }

    private class ReturnThreadsException(val threads: Set<Thread>, val exception: Throwable) : Exception()

    private class ForcedCancellationException : CancellationException()

    data class RefreshCallbacks(
        val onStart: (() -> Unit),
        val onStop: (() -> Unit),
    )
}
