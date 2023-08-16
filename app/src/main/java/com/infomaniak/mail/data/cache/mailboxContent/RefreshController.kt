/*
 * Infomaniak ikMail - Android
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

import android.util.Log
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.ThreadMode
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode.*
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
import io.sentry.Sentry
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class RefreshController @Inject constructor(private val localSettings: LocalSettings) {

    private var refreshThreadsJob: Job? = null

    //region Fetch Messages
    fun cancelRefresh() {
        refreshThreadsJob?.cancel(ForcedCancellationException())
    }

    suspend fun refreshThreads(
        refreshMode: RefreshMode,
        mailbox: Mailbox,
        folder: Folder,
        okHttpClient: OkHttpClient? = null,
        realm: Realm,
        started: (() -> Unit)? = null,
        stopped: (() -> Unit)? = null,
    ): List<Thread>? {

        suspend fun refreshWithRunCatching(job: Job, isFirstTime: Boolean = true): List<Thread>? = runCatching {
            withContext(Dispatchers.IO + job) {
                if (isFirstTime) {
                    started?.invoke()
                } else {
                    delay(Utils.DELAY_BEFORE_FETCHING_ACTIVITIES_AGAIN)
                    ensureActive()
                }
                realm.handleRefreshMode(refreshMode, scope = this, mailbox, folder, okHttpClient).toList()
            }
        }.getOrElse {
            // If fetching the activities failed because of a not found Message, we should pause briefly
            // before trying again to retrieve activities, to ensure that the API is up-to-date.
            if (it is ApiErrorException && it.errorCode == ErrorCode.MESSAGE_NOT_FOUND && isFirstTime) {
                Sentry.captureException(it)
                refreshWithRunCatching(job, isFirstTime = false)
            } else {
                handleAllExceptions(it, stopped)
            }
        }

        Log.i("API", "Refresh threads with mode: $refreshMode | (${folder.name})")

        refreshThreadsJob?.cancel()
        refreshThreadsJob = Job()

        return refreshWithRunCatching(refreshThreadsJob!!).also {
            if (it != null) {
                stopped?.invoke()
                Log.d("API", "End of refreshing threads with mode: $refreshMode | (${folder.name})")
            }
        }
    }

    private suspend fun Realm.handleRefreshMode(
        refreshMode: RefreshMode,
        scope: CoroutineScope,
        mailbox: Mailbox,
        folder: Folder,
        okHttpClient: OkHttpClient?,
    ): Set<Thread> {

        Log.d("API", "Start of refreshing threads with mode: $refreshMode | (${folder.name})")

        return when (refreshMode) {
            REFRESH_FOLDER_WITH_ROLE -> refreshWithRoleConsideration(scope, mailbox, folder, okHttpClient)
            REFRESH_FOLDER -> refresh(scope, mailbox, folder, okHttpClient)
            ONE_PAGE_OF_OLD_MESSAGES -> {
                fetchOneOldPage(scope, mailbox, folder, okHttpClient)
                emptySet()
            }
        }
    }

    private suspend fun Realm.refreshWithRoleConsideration(
        scope: CoroutineScope,
        mailbox: Mailbox,
        folder: Folder,
        okHttpClient: OkHttpClient?,
    ): Set<Thread> {

        val impactedThreads = refresh(scope, mailbox, folder, okHttpClient)

        when (folder.role) {
            FolderRole.INBOX -> listOf(FolderRole.SENT, FolderRole.DRAFT)
            FolderRole.SENT -> listOf(FolderRole.INBOX, FolderRole.DRAFT)
            FolderRole.DRAFT -> listOf(FolderRole.INBOX, FolderRole.SENT)
            else -> emptyList()
        }.forEach { role ->
            scope.ensureActive()

            FolderController.getFolder(role, realm = this)?.let {
                refresh(scope, mailbox, it, okHttpClient)
            }
        }

        return impactedThreads
    }

    private suspend fun Realm.refresh(
        scope: CoroutineScope,
        mailbox: Mailbox,
        folder: Folder,
        okHttpClient: OkHttpClient?,
    ): Set<Thread> {

        val impactedThreads = mutableSetOf<Thread>()
        val previousCursor = folder.cursor

        impactedThreads += if (previousCursor == null) {
            fetchOneNewPage(scope, mailbox, folder, okHttpClient, shouldUpdateCursor = true).second
        } else {
            fetchActivities(scope, mailbox, folder, okHttpClient, previousCursor)
        }

        if (folder.remainingOldMessagesToFetch > 0) fetchAllOldPages(scope, mailbox, folder, okHttpClient)

        return impactedThreads
    }

    private suspend fun Realm.fetchOneOldPage(
        scope: CoroutineScope,
        mailbox: Mailbox,
        folder: Folder,
        okHttpClient: OkHttpClient?,
    ) {
        fetchOnePage(scope, mailbox, folder, okHttpClient, Direction.IN_THE_PAST, shouldUpdateCursor = false)
    }

    private suspend fun Realm.fetchOneNewPage(
        scope: CoroutineScope,
        mailbox: Mailbox,
        folder: Folder,
        okHttpClient: OkHttpClient?,
        shouldUpdateCursor: Boolean = false,
    ): Pair<Int, Set<Thread>> {
        return fetchOnePage(scope, mailbox, folder, okHttpClient, Direction.TO_THE_FUTURE, shouldUpdateCursor)
    }

    private suspend fun Realm.fetchAllOldPages(
        scope: CoroutineScope,
        mailbox: Mailbox,
        folder: Folder,
        okHttpClient: OkHttpClient?,
    ) {

        fun remainingCount() = FolderController.getFolder(folder.id, realm = this)?.remainingOldMessagesToFetch ?: -1

        var remainingOldMessagesToFetch = remainingCount()

        while (remainingOldMessagesToFetch > 0) {
            scope.ensureActive()

            fetchOneOldPage(scope, mailbox, folder, okHttpClient)
            remainingOldMessagesToFetch = remainingCount()
        }
    }

    private suspend fun Realm.fetchAllNewPages(
        scope: CoroutineScope,
        mailbox: Mailbox,
        folder: Folder,
        okHttpClient: OkHttpClient?,
    ): Set<Thread> {

        val impactedThreads = mutableSetOf<Thread>()
        var futureIsStillAhead = true

        while (futureIsStillAhead) {
            scope.ensureActive()

            val (uidsCount, threads) = fetchOneNewPage(scope, mailbox, folder, okHttpClient)
            futureIsStillAhead = uidsCount >= Utils.PAGE_SIZE
            impactedThreads += threads
        }

        return impactedThreads
    }

    private suspend fun Realm.fetchOnePage(
        scope: CoroutineScope,
        mailbox: Mailbox,
        folder: Folder,
        okHttpClient: OkHttpClient?,
        direction: Direction,
        shouldUpdateCursor: Boolean,
    ): Pair<Int, Set<Thread>> {

        fun Folder.theEndIsReached() {
            remainingOldMessagesToFetch = 0
            isHistoryComplete = true
        }

        fun Folder.resetHistoryInfo() {
            remainingOldMessagesToFetch = Folder.DEFAULT_REMAINING_OLD_MESSAGES_TO_FETCH
            isHistoryComplete = Folder.DEFAULT_IS_HISTORY_COMPLETE
        }

        val impactedThreads = mutableSetOf<Thread>()

        val paginationInfo = when (direction) {
            Direction.IN_THE_PAST -> MessageController.getOldestMessage(folder.id, realm = this)
            Direction.TO_THE_FUTURE -> MessageController.getNewestMessage(folder.id, realm = this)
        }?.shortUid?.let { offsetUid ->
            PaginationInfo(offsetUid, direction.apiCallValue)
        }

        val newMessages = getMessagesUids(mailbox.uuid, folder.id, okHttpClient, paginationInfo)!!
        val uidsCount = newMessages.addedShortUids.count()
        scope.ensureActive()

        impactedThreads += handleAddedUids(scope, mailbox, folder, okHttpClient, newMessages.addedShortUids, newMessages.cursor)

        FolderController.updateFolder(folder.id, realm = this) {

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

            if (shouldUpdateCursor) it.cursor = newMessages.cursor
        }

        sendSentryOrphans(folder)

        return uidsCount to impactedThreads
    }

    private suspend fun Realm.fetchActivities(
        scope: CoroutineScope,
        mailbox: Mailbox,
        folder: Folder,
        okHttpClient: OkHttpClient?,
        previousCursor: String,
    ): Set<Thread> {

        val activities = getMessagesUidsDelta(mailbox.uuid, folder.id, okHttpClient, previousCursor) ?: return emptySet()
        scope.ensureActive()

        val logMessage = "Deleted: ${activities.deletedShortUids.count()} | Updated: ${activities.updatedMessages.count()}"
        Log.d("API", "$logMessage | ${folder.name}")

        addSentryBreadcrumbsForActivities(logMessage, mailbox.email, folder, activities)

        writeBlocking {
            val impactedFoldersIds = mutableSetOf<String>().apply {
                addAll(handleDeletedUids(scope, activities.deletedShortUids, folder.id))
                addAll(handleUpdatedUids(scope, activities.updatedMessages, folder.id))
            }

            impactedFoldersIds.forEach { folderId ->
                FolderController.refreshUnreadCount(folderId, mailbox.objectId, realm = this)
            }

            FolderController.getFolder(folder.id, realm = this)?.let {
                it.lastUpdatedAt = Date().toRealmInstant()
                it.unreadCountRemote = activities.unreadCountRemote
                it.cursor = activities.cursor
            }
        }

        sendSentryOrphans(folder, previousCursor)

        return fetchAllNewPages(scope, mailbox, folder, okHttpClient)
    }
    //endregion

    //region Added Messages
    private suspend fun Realm.handleAddedUids(
        scope: CoroutineScope,
        mailbox: Mailbox,
        folder: Folder,
        okHttpClient: OkHttpClient?,
        uids: List<Int>,
        cursor: String,
    ): Set<Thread> {

        val logMessage = "Added: ${uids.count()}"
        Log.d("API", "$logMessage | ${folder.name}")

        if (uids.isEmpty()) return emptySet()

        addSentryBreadcrumbsForAddedUids(logMessage = logMessage, email = mailbox.email, folder = folder, uids = uids)

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
                    val allImpactedThreads = if (localSettings.threadMode == ThreadMode.MESSAGE) {
                        createMessageModeThreads(scope, latestFolder, messages).also {
                            FolderController.refreshUnreadCount(folder.id, mailbox.objectId, realm = this)
                        }

                    } else {
                        createConversationModeThreads(scope, latestFolder, messages).also { threads ->
                            val impactedFoldersIds = (threads.map { it.folderId }.toSet()) + folder.id
                            impactedFoldersIds.forEach { folderId ->
                                FolderController.refreshUnreadCount(folderId, mailbox.objectId, realm = this)
                            }
                        }
                    }
                    Log.d("Realm", "Saved Messages: ${latestFolder.name} | ${latestFolder.messages.count()}")

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

            val message = MessageController.getMessage(shortUid.toLongUid(folderId), realm = this) ?: return@forEach

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
    private fun MutableRealm.createMessageModeThreads(
        scope: CoroutineScope,
        folder: Folder,
        remoteMessages: List<Message>,
    ): Set<Thread> {

        val threadsToUpsert = mutableSetOf<Thread>()

        remoteMessages.forEach { remoteMessage ->
            scope.ensureActive()

            val isMessageAlreadyAdded = folder.messages.firstOrNull { it == remoteMessage && !it.isOrphan() } != null
            if (isMessageAlreadyAdded) {
                SentryDebug.sendAlreadyExistingMessage(folder, remoteMessage, localSettings.threadMode)
                return@forEach
            }

            remoteMessage.initLocalValues(
                date = remoteMessage.date,
                isFullyDownloaded = false,
                isSpam = folder.role == FolderRole.SPAM,
                isTrashed = folder.role == FolderRole.TRASH,
                isFromSearch = false,
                draftLocalUuid = null,
            )

            folder.messages.add(remoteMessage)

            val newThread = remoteMessage.toThread()
            newThread.recomputeThread(realm = this)

            ThreadController.upsertThread(newThread, realm = this).also {
                folder.threads.add(it)
                threadsToUpsert += it.copyFromRealm(1u)
            }
        }

        return threadsToUpsert
    }

    private fun MutableRealm.createConversationModeThreads(
        scope: CoroutineScope,
        folder: Folder,
        remoteMessages: List<Message>,
    ): Set<Thread> {

        val idsOfFoldersWithIncompleteThreads = FolderController.getIdsOfFoldersWithIncompleteThreads(realm = this)
        val threadsToUpsert = mutableMapOf<String, Thread>()

        remoteMessages.forEach { remoteMessage ->
            scope.ensureActive()

            val isMessageAlreadyAdded = folder.messages.firstOrNull { it == remoteMessage && !it.isOrphan() } != null
            if (isMessageAlreadyAdded) {
                SentryDebug.sendAlreadyExistingMessage(folder, remoteMessage, localSettings.threadMode)
                return@forEach
            }

            remoteMessage.initLocalValues(
                date = remoteMessage.date,
                isFullyDownloaded = false,
                isSpam = folder.role == FolderRole.SPAM,
                isTrashed = folder.role == FolderRole.TRASH,
                isFromSearch = false,
                draftLocalUuid = null,
            )

            folder.messages.add(remoteMessage)

            val existingThreads = ThreadController.getThreads(remoteMessage.messageIds, realm = this).toList()

            createNewThreadIfRequired(
                scope,
                existingThreads,
                remoteMessage,
                idsOfFoldersWithIncompleteThreads,
            )?.let { newThread ->
                ThreadController.upsertThread(newThread, realm = this).also {
                    folder.threads.add(it)
                    threadsToUpsert[it.uid] = it
                }
            }

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

                threadsToUpsert[thread.uid] = ThreadController.upsertThread(thread, realm = this)
            }
        }

        val allImpactedThreads = mutableSetOf<Thread>()
        threadsToUpsert.forEach { (_, thread) ->
            scope.ensureActive()

            thread.recomputeThread(realm = this)
            ThreadController.upsertThread(thread, realm = this)
            allImpactedThreads.add(if (thread.isManaged()) thread.copyFromRealm(1u) else thread)
        }

        return allImpactedThreads
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
    private fun getMessagesUids(
        mailboxUuid: String,
        folderId: String,
        okHttpClient: OkHttpClient?,
        info: PaginationInfo?,
    ): NewMessagesResult? {
        return with(ApiRepository.getMessagesUids(mailboxUuid, folderId, okHttpClient, info)) {
            if (!isSuccess()) throwErrorAsException()
            return@with data
        }
    }

    private fun getMessagesUidsDelta(
        mailboxUuid: String,
        folderId: String,
        okHttpClient: OkHttpClient?,
        previousCursor: String,
    ): ActivitiesResult? {
        return with(ApiRepository.getMessagesUidsDelta(mailboxUuid, folderId, previousCursor, okHttpClient)) {
            if (!isSuccess()) throwErrorAsException()
            return@with data
        }
    }
    //endregion

    //region Handle errors
    private fun handleAllExceptions(throwable: Throwable, stopped: (() -> Unit)?): Nothing? {

        // We force-cancelled, so we need to call the `stopped` callback.
        if (throwable is ForcedCancellationException) stopped?.invoke()

        // It failed, but not because we cancelled it. Something bad happened, so we call the `stopped` callback.
        if (throwable !is CancellationException) stopped?.invoke()

        if (throwable is ApiErrorException) throwable.handleOtherApiErrors()

        return null
    }

    private fun ApiErrorException.handleOtherApiErrors() {
        when (errorCode) {
            ErrorCode.FOLDER_DOES_NOT_EXIST -> Unit
            else -> Sentry.captureException(this)
        }
    }

    private fun Realm.sendSentryOrphans(folder: Folder, previousCursor: String? = null) {
        writeBlocking {
            findLatest(folder)?.let {
                SentryDebug.sendOrphanMessages(previousCursor, folder = it)
                SentryDebug.sendOrphanThreads(previousCursor, folder = it, realm = this)
            }
        }
    }

    private fun addSentryBreadcrumbsForActivities(
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

    private fun addSentryBreadcrumbsForAddedUids(
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

    private class ForcedCancellationException : CancellationException()
}
