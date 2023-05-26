/*
 * Infomaniak kMail - Android
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
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.deleteMessage
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode.*
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController.upsertThread
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
import kotlin.math.max

object RefreshController {

    private inline val defaultRealm get() = RealmDatabase.mailboxContent()

    private var refreshThreadsJob: Job? = null

    enum class RefreshMode {
        REFRESH_FOLDER, /* Fetch activities, and also get old Messages until `NUMBER_OF_OLD_MESSAGES_TO_FETCH` is reached */
        REFRESH_FOLDER_WITH_ROLE, /* Same as `NEW_MESSAGES`, but also check if other Folders need to be updated (Inbox, Sent, Draft, etc…) */
        ONE_PAGE_OF_OLD_MESSAGES, /* Get 1 page of old Messages */
    }

    private enum class Direction(val apiCallValue: String) {
        IN_THE_PAST("previous"), /* To get more old Messages */
        TO_THE_FUTURE("following"), /* To get more new Messages */
    }

    data class PaginationInfo(
        val offsetUid: Int,
        val direction: String,
    )

    //region Fetch Messages
    suspend fun refreshThreads(
        refreshMode: RefreshMode,
        mailbox: Mailbox,
        folder: Folder,
        okHttpClient: OkHttpClient? = null,
        realm: Realm = defaultRealm,
        started: (() -> Unit)? = null,
        stopped: (() -> Unit)? = null,
    ): Set<Thread>? = withContext(Dispatchers.IO) {

        refreshThreadsJob?.cancel()

        val job = async {

            return@async runCatching {
                started?.invoke()
                return@runCatching realm.handleRefreshMode(refreshMode, scope = this, mailbox, folder, okHttpClient)
            }.getOrElse {
                // It failed, but not because we cancelled it. Something bad happened, so we call the `stopped` callback.
                if (it !is CancellationException) stopped?.invoke()
                if (it is ApiErrorException) it.handleApiErrors()
                return@getOrElse null
            }
        }

        refreshThreadsJob = job

        return@withContext job.await().also {
            if (it != null) stopped?.invoke()
        }
    }

    private fun ApiErrorException.handleApiErrors() {
        when (errorCode) {
            ErrorCode.FOLDER_DOES_NOT_EXIST -> Unit
            else -> Sentry.captureException(this)
        }
    }

    private suspend fun Realm.handleRefreshMode(
        refreshMode: RefreshMode,
        scope: CoroutineScope,
        mailbox: Mailbox,
        folder: Folder,
        okHttpClient: OkHttpClient?,
    ): Set<Thread> {
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

            FolderController.getFolder(role)?.let {
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
        var isTheFutureCaughtUp = false

        while (!isTheFutureCaughtUp) {
            scope.ensureActive()

            val (uidsCount, threads) = fetchOneNewPage(scope, mailbox, folder, okHttpClient)
            isTheFutureCaughtUp = uidsCount < Utils.PAGE_SIZE
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

        val impactedThreads = mutableSetOf<Thread>()

        val info = when (direction) {
            Direction.IN_THE_PAST -> MessageController.getOldestMessage(folder.id, realm = this)
            Direction.TO_THE_FUTURE -> MessageController.getNewestMessage(folder.id, realm = this)
        }?.shortUid?.let { offsetUid ->
            PaginationInfo(offsetUid, direction.apiCallValue)
        }

        val newMessages = getMessagesUids(mailbox.uuid, folder.id, okHttpClient, info)
        scope.ensureActive()

        val uids = newMessages?.addedShortUids?.let { getOnlyNewUids(folder, it) }
        val uidsCount = uids?.count() ?: -1

        // `count >= 0` and not `count > 0`, because if we get an empty page, we need to update the Folder to end the algorithm.
        if (uids != null && uidsCount >= 0) {

            val logMessage = "Added: $uidsCount"
            impactedThreads += handleAddedUids(scope, mailbox, folder, okHttpClient, uids, newMessages.cursor, logMessage)

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

                        // If it's the 1st opening, and we didn't even get 1 full page, it means we already reached the end.
                        if (folder.cursor == null && newMessages.addedShortUids.count() < Utils.PAGE_SIZE) it.theEndIsReached()
                    }
                }

                if (shouldUpdateCursor) it.cursor = newMessages.cursor
            }
        }

        writeBlocking {
            findLatest(folder)?.let {
                SentryDebug.sendOrphanMessages(null, folder = it)
                SentryDebug.sendOrphanThreads(null, folder = it, realm = this)
            }
        }

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
                it.cursor = activities.cursor
            }

            findLatest(folder)?.let {
                SentryDebug.sendOrphanMessages(previousCursor, folder = it)
                SentryDebug.sendOrphanThreads(previousCursor, folder = it, realm = this)
            }
        }

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
        logMessage: String,
    ): Set<Thread> {

        if (uids.isEmpty()) return emptySet()

        val impactedThreads = mutableSetOf<Thread>()

        val before = System.currentTimeMillis()
        val apiResponse = ApiRepository.getMessagesByUids(mailbox.uuid, folder.id, uids, okHttpClient)
        val after = System.currentTimeMillis()
        if (!apiResponse.isSuccess()) apiResponse.throwErrorAsException()
        scope.ensureActive()

        apiResponse.data?.messages?.let { messages ->

            writeBlocking {
                findLatest(folder)?.let { latestFolder ->
                    val threads = createMultiMessagesThreads(scope, latestFolder, messages)
                    Log.d("Realm", "Saved Messages: ${latestFolder.name} | ${latestFolder.messages.count()}")

                    val impactedFoldersIds = (threads.map { it.folderId }.toSet()) + folder.id
                    impactedFoldersIds.forEach { folderId ->
                        FolderController.refreshUnreadCount(folderId, mailbox.objectId, realm = this)
                    }

                    impactedThreads.addAll(threads)
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

            SentryDebug.addThreadsAlgoBreadcrumb(
                message = logMessage,
                data = mapOf(
                    "1_folderName" to folder.name,
                    "2_folderId" to folder.id,
                    "3_added" to uids,
                    // "4_deleted" to newMessages.deletedUids.map { "${it.toShortUid()}" },
                    // "5_updated" to newMessages.updatedMessages.map { it.shortUid },
                ),
            )

            SentryDebug.sendMissingMessages(uids, messages, folder, cursor)
        }

        return impactedThreads.filter { it.folderId == folder.id }.toSet()
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

            deleteMessage(message)
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
    private fun MutableRealm.createMultiMessagesThreads(
        scope: CoroutineScope,
        folder: Folder,
        messages: List<Message>,
    ): List<Thread> {

        val idsOfFoldersWithIncompleteThreads = FolderController.getIdsOfFoldersWithIncompleteThreads(realm = this)
        val threadsToUpsert = mutableMapOf<String, Thread>()

        messages.forEach { message ->
            scope.ensureActive()

            message.apply {
                initMessageIds()
                isSpam = folder.role == FolderRole.SPAM
                shortUid = uid.toShortUid()
            }

            val existingMessage = folder.messages.firstOrNull { it == message }
            if (existingMessage == null) {
                folder.messages.add(message)
            } else if (!existingMessage.isOrphan()) {
                SentryDebug.sendAlreadyExistingMessage(folder, existingMessage, message)
                return@forEach
            }

            val existingThreads = ThreadController.getThreads(message.messageIds, realm = this).toList()

            createNewThreadIfRequired(scope, existingThreads, message, idsOfFoldersWithIncompleteThreads)?.let { newThread ->
                upsertThread(newThread).also {
                    folder.threads.add(it)
                    threadsToUpsert[it.uid] = it
                }
            }

            val allExistingMessages = mutableSetOf<Message>().apply {
                existingThreads.forEach { addAll(it.messages) }
                add(message)
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

                threadsToUpsert[thread.uid] = upsertThread(thread)
            }
        }

        val impactedThreads = mutableListOf<Thread>()
        threadsToUpsert.forEach { (_, thread) ->
            scope.ensureActive()

            thread.recomputeThread(realm = this)
            upsertThread(thread)
            impactedThreads.add(if (thread.isManaged()) thread.copyFromRealm(1u) else thread)
        }

        return impactedThreads
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
            newThread.addFirstMessage(newMessage)

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
    //endregion

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

    private fun getOnlyNewUids(folder: Folder, remoteUids: List<Int>): List<Int> {
        val localUids = folder.messages.map { it.shortUid }.toSet()
        return remoteUids.subtract(localUids).toList()
    }
    //endregion
}
