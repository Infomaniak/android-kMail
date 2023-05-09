/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController.upsertThread
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.getMessages.GetMessagesUidsDeltaResult.MessageFlags
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Body
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.SharedViewModelUtils.fetchFolderMessagesJob
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.util.Date

object MessageController {

    private inline val defaultRealm get() = RealmDatabase.mailboxContent()

    private const val NUMBER_OF_OLD_MESSAGES_TO_FETCH = 500

    private val isNotDraft = "${Message::isDraft.name} == false"
    private val isNotScheduled = "${Message::isScheduled.name} == false"

    //region Queries
    private fun getOldestMessageQuery(folderId: String, realm: TypedRealm): RealmSingleQuery<Message> {
        val byFolderId = "${Message::folderId.name} == '$folderId'"
        return realm.query<Message>(byFolderId).sort(Message::shortUidAsInt.name).first()
    }

    private fun getMessageQuery(uid: String, realm: TypedRealm): RealmSingleQuery<Message> {
        val byUid = "${Message::uid.name} == '$uid'"
        return realm.query<Message>(byUid).first()
    }
    //endregion

    //region Get data
    private fun getOldestMessage(folderId: String, realm: TypedRealm = defaultRealm): Message? {
        return getOldestMessageQuery(folderId, realm).find()
    }

    fun getSortedMessages(threadUid: String): RealmQuery<Message>? {
        return ThreadController.getThread(threadUid)?.messages?.query()?.sort(Message::date.name, Sort.ASCENDING)
    }

    fun getMessage(uid: String, realm: TypedRealm = defaultRealm): Message? {
        return getMessageQuery(uid, realm).find()
    }

    fun getLastMessageToExecuteAction(thread: Thread): Message = with(thread) {

        val isNotFromMe = "SUBQUERY(${Message::from.name}, \$recipient, " +
                "\$recipient.${Recipient::email.name} != '${AccountUtils.currentMailboxEmail}').@count > 0"

        return messages.query("$isNotDraft AND $isNotFromMe").find().lastOrNull()
            ?: messages.query(isNotDraft).find().lastOrNull()
            ?: messages.last()
    }

    fun getLastMessageAndItsDuplicatesToExecuteAction(thread: Thread): List<Message> {
        return getMessageAndDuplicates(
            thread = thread,
            message = getLastMessageToExecuteAction(thread),
        )
    }

    fun getUnseenMessages(thread: Thread): List<Message> {
        return getMessagesAndDuplicates(thread, "${Message::isSeen.name} == false")
    }

    fun getFavoriteMessages(thread: Thread): List<Message> {
        val isFavorite = "${Message::isFavorite.name} == true"
        return getMessagesAndDuplicates(thread, "$isFavorite AND $isNotDraft")
    }

    fun getMovableMessages(thread: Thread): List<Message> {
        val byFolderId = "${Message::folderId.name} == '${thread.folderId}'"
        return getMessagesAndDuplicates(thread, "$byFolderId AND $isNotScheduled")
    }

    fun getUnscheduledMessages(thread: Thread): List<Message> {
        return getMessagesAndDuplicates(thread, isNotScheduled)
    }

    private fun getMessagesAndDuplicates(thread: Thread, query: String): List<Message> {

        val messages = thread.messages.query(query).find()

        val byMessagesIds = "${Message::messageId.name} IN {${messages.joinToString { "'${it.messageId}'" }}}"
        val duplicates = thread.duplicates.query(byMessagesIds).find()

        return messages + duplicates
    }

    fun getMessageAndDuplicates(thread: Thread, message: Message): List<Message> {
        return listOf(message) + thread.duplicates.query("${Message::messageId.name} == '${message.messageId}'").find()
    }

    fun searchMessages(searchQuery: String?, filters: Set<ThreadFilter>, folderId: String?): List<Message> {
        val queriesList = mutableListOf<String>().apply {
            filters.forEach { filter ->
                when (filter) {
                    ThreadFilter.SEEN -> add("${Message::isSeen.name} == true")
                    ThreadFilter.UNSEEN -> add("${Message::isSeen.name} == false")
                    ThreadFilter.STARRED -> add("${Message::isFavorite.name} == true")
                    ThreadFilter.FOLDER -> add("${Message::folderId.name} == '$folderId'")
                    ThreadFilter.ATTACHMENTS -> add("${Message::hasAttachments.name} == true")
                    else -> Unit
                }
            }
        }

        if (searchQuery?.isNotBlank() == true) {
            val containsSubject = "${Message::subject.name} CONTAINS[c] '$searchQuery'"
            val containsPreview = "${Message::preview.name} CONTAINS[c] '$searchQuery'"
            val containsBody = "${Message::body.name}.${Body::value.name} CONTAINS[c] '$searchQuery'"
            queriesList.add("($containsSubject OR $containsPreview OR $containsBody)")
        }

        return defaultRealm.writeBlocking {
            query<Message>(queriesList.joinToString(" AND ") { it }).find().copyFromRealm()
        }
    }
    //endregion

    //region Edit data
    fun upsertMessage(message: Message, realm: MutableRealm) {
        realm.copyToRealm(message, UpdatePolicy.ALL)
    }

    fun MutableRealm.deleteMessages(messages: List<Message>) {
        messages.reversed().forEach { message ->
            deleteMessage(message)
        }
    }

    private fun MutableRealm.deleteMessage(message: Message) {

        DraftController.getDraftByMessageUid(message.uid, realm = this)?.let { draft ->
            if (draft.action == null) {
                delete(draft)
            } else {
                draft.remoteUuid = null
            }
        }

        delete(message)
    }

    fun deleteSearchMessages(realm: MutableRealm) = with(realm) {
        delete(query<Message>("${Message::isFromSearch.name} == true").find())
    }
    //endregion

    //region New API routes
    suspend fun fetchCurrentFolderMessages(
        mailbox: Mailbox,
        folder: Folder,
        okHttpClient: OkHttpClient? = null,
        realm: Realm = defaultRealm,
    ): List<Thread> = withContext(Dispatchers.IO) {

        fetchFolderMessagesJob?.cancel()
        val job = async {
            val impactedCurrentFolderThreads = fetchFolderMessages(scope = this, mailbox, folder, okHttpClient, realm)

            val roles = when (folder.role) {
                FolderRole.INBOX -> listOf(FolderRole.SENT, FolderRole.DRAFT)
                FolderRole.SENT -> listOf(FolderRole.INBOX, FolderRole.DRAFT)
                FolderRole.DRAFT -> listOf(FolderRole.INBOX, FolderRole.SENT)
                else -> emptyList()
            }

            roles.forEach { role ->
                FolderController.getFolder(role)?.let { folder ->
                    fetchFolderMessages(scope = this, mailbox, folder, okHttpClient, realm)
                }
            }

            return@async impactedCurrentFolderThreads
        }

        fetchFolderMessagesJob = job
        return@withContext job.await()
    }

    fun fetchFolderMessages(
        scope: CoroutineScope,
        mailbox: Mailbox,
        folder: Folder,
        okHttpClient: OkHttpClient?,
        realm: Realm = defaultRealm,
    ): List<Thread> {
        val previousCursor = folder.cursor
        val impactedCurrentFolderThreads = mutableSetOf<Thread>()

        val uids = if (previousCursor == null) {
            getMessagesUids(mailbox.uuid, folder.id, okHttpClient)
        } else {
            getMessagesUidsDelta(mailbox.uuid, folder.id, previousCursor, okHttpClient)
        } ?: return emptyList()
        scope.ensureActive()

        impactedCurrentFolderThreads += realm.handleMessagesUids(scope, uids, folder, mailbox, okHttpClient, previousCursor)

        if (folder.shouldGetHistory) {
            impactedCurrentFolderThreads += fetchPreviousMessages(folder, realm, mailbox, okHttpClient, scope)
        }

        return impactedCurrentFolderThreads.toList()
    }

    private fun fetchPreviousMessages(
        folder: Folder,
        realm: Realm,
        mailbox: Mailbox,
        okHttpClient: OkHttpClient?,
        scope: CoroutineScope,
    ): List<Thread> {

        val impactedCurrentFolderThreads = mutableListOf<Thread>()
        var previousOffsetUid: String? = null

        run repeatBlock@{
            repeat((NUMBER_OF_OLD_MESSAGES_TO_FETCH - folder.messages.count()) / ApiRoutes.PAGE_SIZE) {

                val (newOffsetUid, shouldStop, threads) = getOneBatchOfOldMessages(
                    folder,
                    mailbox,
                    scope,
                    okHttpClient,
                    previousOffsetUid,
                    realm,
                )

                if (shouldStop) return@repeatBlock
                previousOffsetUid = newOffsetUid
                impactedCurrentFolderThreads += threads
            }
        }

        FolderController.updateFolder(folder.id, realm) {
            it.shouldGetHistory = false
        }

        return impactedCurrentFolderThreads
    }

    fun getOneBatchOfOldMessages(
        folder: Folder,
        mailbox: Mailbox,
        scope: CoroutineScope,
        okHttpClient: OkHttpClient? = null,
        previousOffsetUid: String? = null,
        realm: Realm = defaultRealm,
    ): Triple<String?, Boolean, List<Thread>> {

        val shouldStop = Triple(null, true, emptyList<Thread>())

        val offsetUid = getOldestMessage(folder.id, realm)?.shortUid.also {
            if (it == null || it == previousOffsetUid || it.toInt() <= 1) return shouldStop
        }

        val olderUids = getMessagesUids(
            mailbox.uuid,
            folder.id,
            okHttpClient,
            offsetUid,
        ).also { uids ->
            if (uids == null || uids.addedShortUids.isEmpty()) return shouldStop
        }
        scope.ensureActive()

        val impactedCurrentFolderThreads = realm.handleMessagesUids(
            scope,
            olderUids!!,
            folder,
            mailbox,
            okHttpClient,
            shouldUpdateCursor = false,
        )
        scope.ensureActive()

        return Triple(offsetUid, false, impactedCurrentFolderThreads)
    }

    private fun Realm.handleMessagesUids(
        scope: CoroutineScope,
        uids: MessagesUids,
        folder: Folder,
        mailbox: Mailbox,
        okHttpClient: OkHttpClient?,
        previousCursor: String? = null,
        shouldUpdateCursor: Boolean = true,
    ): List<Thread> {
        return writeBlocking {
            handleMessagesUids(scope, uids, folder, mailbox, okHttpClient, shouldUpdateCursor).also {
                findLatest(folder)?.let {
                    SentryDebug.sendOrphanMessages(previousCursor, folder = it)
                    SentryDebug.sendOrphanThreads(previousCursor, folder = it, realm = this)
                }
            }
        }
    }

    private fun MutableRealm.handleMessagesUids(
        scope: CoroutineScope,
        uids: MessagesUids,
        folder: Folder,
        mailbox: Mailbox,
        okHttpClient: OkHttpClient?,
        shouldUpdateCursor: Boolean,
    ): List<Thread> {

        val logMessage =
            "Added: ${uids.addedShortUids.count()} | Deleted: ${uids.deletedUids.count()} | Updated: ${uids.updatedMessages.count()}"
        Log.i("API", "$logMessage | ${folder.name}")

        val impactedThreads = if (uids.addedShortUids.isEmpty()) {
            emptyList()
        } else {
            handleAddedUids(
                scope = scope,
                messagesUids = uids,
                folder = folder,
                mailboxUuid = mailbox.uuid,
                okHttpClient = okHttpClient,
                logMessage = logMessage,
            )
        }

        val impactedFoldersIds = mutableSetOf<String>().apply {
            addAll(handleDeletedUids(scope, uids.deletedUids))
            addAll(handleUpdatedUids(scope, uids.updatedMessages, folder.id))
        }

        val impactedCurrentFolderThreads = impactedThreads.filter { it.folderId == folder.id }
        impactedFoldersIds += impactedThreads.map { it.folderId } + folder.id

        impactedFoldersIds.forEach { folderId ->
            FolderController.refreshUnreadCount(folderId, mailbox.objectId, realm = this)
        }
        scope.ensureActive()

        FolderController.getFolder(folder.id, realm = this)?.let {
            it.lastUpdatedAt = Date().toRealmInstant()
            if (shouldUpdateCursor) it.cursor = uids.cursor
        }

        return impactedCurrentFolderThreads
    }

    private fun MutableRealm.handleAddedUids(
        scope: CoroutineScope,
        messagesUids: MessagesUids,
        folder: Folder,
        mailboxUuid: String,
        okHttpClient: OkHttpClient?,
        logMessage: String,
    ): List<Thread> {

        val impactedThreads = mutableSetOf<Thread>()
        val shortUids = messagesUids.addedShortUids
        val uids = getOnlyNewUids(folder, shortUids)

        val apiResponse = ApiRepository.getMessagesByUids(mailboxUuid, folder.id, uids, okHttpClient)
        if (!apiResponse.isSuccess()) apiResponse.throwErrorAsException()
        scope.ensureActive()

        apiResponse.data?.messages?.let { messages ->

            findLatest(folder)?.let { latestFolder ->
                val threads = createMultiMessagesThreads(scope, messages, latestFolder)
                Log.d("Realm", "Saved Messages: ${latestFolder.name} | ${latestFolder.messages.count()}")
                impactedThreads.addAll(threads)
            }

            SentryDebug.addThreadsAlgoBreadcrumb(
                message = logMessage,
                data = mapOf(
                    "1_folderName" to folder.name,
                    "2_folderId" to folder.id,
                    "3_added" to shortUids,
                    "4_deleted" to messagesUids.deletedUids.map { it.toShortUid() },
                    "5_updated" to messagesUids.updatedMessages.map { it.shortUid },
                ),
            )

            SentryDebug.sendMissingMessages(uids, messages, folder, messagesUids.cursor)
        }

        return impactedThreads.toList()
    }

    private fun MutableRealm.createMultiMessagesThreads(
        scope: CoroutineScope,
        messages: List<Message>,
        folder: Folder,
    ): List<Thread> {

        val idsOfFoldersWithIncompleteThreads = FolderController.getIdsOfFoldersWithIncompleteThreads(realm = this)
        val threadsToUpsert = mutableMapOf<String, Thread>()

        messages.forEach { message ->
            scope.ensureActive()

            message.apply {
                initMessageIds()
                isSpam = folder.role == FolderRole.SPAM
                shortUidAsInt = shortUid.toInt()
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

    private fun MutableRealm.handleDeletedUids(scope: CoroutineScope, uids: List<String>): Set<String> {

        val impactedFolders = mutableSetOf<String>()
        val threads = mutableSetOf<Thread>()

        uids.forEach { messageUid ->
            scope.ensureActive()

            val message = getMessage(messageUid, this) ?: return@forEach

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
            getMessage(uid, realm = this)?.let { message ->
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

    private fun getMessagesUids(
        mailboxUuid: String,
        folderId: String,
        okHttpClient: OkHttpClient?,
        offsetUid: String? = null,
    ): MessagesUids? {
        val apiResponse = ApiRepository.getMessagesUids(mailboxUuid, folderId, offsetUid, okHttpClient)
        if (!apiResponse.isSuccess()) apiResponse.throwErrorAsException()
        return apiResponse.data?.let {
            MessagesUids(
                addedShortUids = it.addedShortUids,
                cursor = it.cursor,
            )
        }
    }

    private fun getMessagesUidsDelta(
        mailboxUuid: String,
        folderId: String,
        previousCursor: String,
        okHttpClient: OkHttpClient?,
    ): MessagesUids? {
        val apiResponse = ApiRepository.getMessagesUidsDelta(mailboxUuid, folderId, previousCursor, okHttpClient)
        if (!apiResponse.isSuccess()) apiResponse.throwErrorAsException()
        return apiResponse.data?.let {
            MessagesUids(
                addedShortUids = it.addedShortUids,
                deletedUids = it.deletedShortUids.map { shortUid -> shortUid.toLongUid(folderId) },
                updatedMessages = it.updatedMessages,
                cursor = it.cursor,
            )
        }
    }

    private fun getOnlyNewUids(folder: Folder, remoteUids: List<String>): List<String> {
        val localUids = folder.messages.map { it.shortUid }.toSet()
        return remoteUids.subtract(localUids).toList()
    }

    data class MessagesUids(
        var addedShortUids: List<String> = emptyList(),
        var deletedUids: List<String> = emptyList(),
        var updatedMessages: List<MessageFlags> = emptyList(),
        var cursor: String,
    )
    //endregion
}
