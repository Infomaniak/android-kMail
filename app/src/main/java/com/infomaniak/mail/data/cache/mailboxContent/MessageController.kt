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
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController.upsertThread
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.Thread
import com.infomaniak.mail.data.models.Thread.ThreadFilter
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.getMessages.GetMessagesUidsDeltaResult.MessageFlags
import com.infomaniak.mail.data.models.message.Body
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.*
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
import io.sentry.Sentry
import io.sentry.SentryLevel
import okhttp3.OkHttpClient
import java.util.Date
import kotlin.math.min

object MessageController {

    private inline val defaultRealm get() = RealmDatabase.mailboxContent()

    private val isNotDraft = "${Message::isDraft.name} == false"

    //region Queries
    private fun getMessageQuery(uid: String, realm: TypedRealm): RealmSingleQuery<Message> {
        val byUid = "${Message::uid.name} == '$uid'"
        return realm.query<Message>(byUid).first()
    }
    //endregion

    //region Get data
    fun getSortedMessages(threadUid: String): RealmQuery<Message>? {
        return ThreadController.getThread(threadUid)?.messages?.query()?.sort(Message::date.name, Sort.ASCENDING)
    }

    fun getMessage(uid: String, realm: TypedRealm = defaultRealm): Message? {
        return getMessageQuery(uid, realm).find()
    }

    fun getMessageToReplyTo(thread: Thread): Message = with(thread) {

        val isNotFromMe = "SUBQUERY(${Message::from.name}, \$recipient, " +
                "\$recipient.${Recipient::email.name} != '${AccountUtils.currentMailboxEmail}').@count > 0"

        return messages.query("$isNotDraft AND $isNotFromMe").find().lastOrNull()
            ?: messages.query(isNotDraft).find().lastOrNull()
            ?: messages.last()
    }

    fun getUnseenMessages(thread: Thread): List<Message> {
        return getMessagesAndDuplicates(thread, "${Message::isSeen.name} == false")
    }

    fun getFavoriteMessages(thread: Thread): List<Message> {
        val isFavorite = "${Message::isFavorite.name} == true"
        return getMessagesAndDuplicates(thread, "$isFavorite AND $isNotDraft")
    }

    fun getMovableMessages(thread: Thread, folderId: String): List<Message> {
        val byFolderId = "${Message::folderId.name} == '$folderId'"
        return getMessagesAndDuplicates(thread, byFolderId)
    }

    fun getUnscheduledMessages(thread: Thread): List<Message> {
        val isNotScheduled = "${Message::isScheduled.name} == false"
        return getMessagesAndDuplicates(thread, isNotScheduled)
    }

    fun getLastMessageToExecuteAction(thread: Thread): List<Message> {
        val message = thread.messages.query(isNotDraft).find().lastOrNull() ?: thread.messages.last()
        return getMessageAndDuplicates(thread, message)
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

        if (!searchQuery.isNullOrBlank()) {
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
        messages.reversed().forEach { deleteMessage(it.uid, realm = this) }
    }

    private fun deleteMessage(uid: String, realm: MutableRealm) {
        getMessage(uid, realm)?.let { message ->
            DraftController.getDraftByMessageUid(message.uid, realm)?.let(realm::delete)
            realm.delete(message)
        }
    }

    fun deleteSearchMessages(realm: MutableRealm) = with(realm) {
        delete(query<Message>("${Message::isFromSearch.name} == true").find())
    }
    //endregion

    //region New API routes
    fun fetchCurrentFolderMessages(
        mailbox: Mailbox,
        folder: Folder,
        okHttpClient: OkHttpClient? = null,
        realm: Realm = defaultRealm,
    ): List<Thread> {

        val newMessagesThreads = fetchFolderMessages(mailbox, folder, okHttpClient, realm)

        val roles = when (folder.role) {
            FolderRole.INBOX -> listOf(FolderRole.SENT, FolderRole.DRAFT)
            FolderRole.SENT -> listOf(FolderRole.INBOX, FolderRole.DRAFT)
            FolderRole.DRAFT -> listOf(FolderRole.INBOX, FolderRole.SENT)
            else -> return emptyList()
        }

        roles.forEach { role ->
            FolderController.getFolder(role)?.let { folder ->
                fetchFolderMessages(mailbox, folder, okHttpClient, realm)
            }
        }

        return newMessagesThreads
    }

    fun fetchFolderMessages(
        mailbox: Mailbox,
        folder: Folder,
        okHttpClient: OkHttpClient?,
        realm: Realm = defaultRealm,
    ): List<Thread> {
        val previousCursor = folder.cursor

        val messagesUids = if (previousCursor == null) {
            getMessagesUids(mailbox.uuid, folder.id, okHttpClient)
        } else {
            getMessagesUidsDelta(mailbox.uuid, folder.id, previousCursor, okHttpClient)
        } ?: return emptyList()

        val updatedThreads = handleMessagesUids(messagesUids, folder, mailbox, okHttpClient, realm)

        SentryDebug.sendOrphanMessagesSentry(previousCursor, folder, realm)
        SentryDebug.sendOrphanThreadsSentry(previousCursor, folder, realm)

        return updatedThreads
    }

    private fun handleMessagesUids(
        messagesUids: MessagesUids,
        folder: Folder,
        mailbox: Mailbox,
        okHttpClient: OkHttpClient?,
        realm: Realm,
    ): List<Thread> = with(messagesUids) {

        Log.i(
            "API",
            "Added: ${addedShortUids.count()} | Deleted: ${deletedUids.count()} | Updated: ${updatedMessages.count()} | ${folder.name}",
        )

        val newMessagesThreads = handleAddedUids(addedShortUids, folder, mailbox.uuid, cursor, okHttpClient, realm)

        return@with realm.writeBlocking {

            val impactedFoldersIds = newMessagesThreads.map { it.folderId }.toMutableSet()

            impactedFoldersIds += handleDeletedUids(deletedUids)
            impactedFoldersIds += handleUpdatedUids(updatedMessages, folder.id)

            impactedFoldersIds.forEach { folderId ->
                FolderController.refreshUnreadCount(folderId, mailbox.objectId, realm = this)
            }

            FolderController.getFolder(folder.id, realm = this)?.let {
                it.lastUpdatedAt = Date().toRealmInstant()
                it.cursor = cursor
            }

            return@writeBlocking newMessagesThreads
        }
    }

    private fun handleAddedUids(
        shortUids: List<String>,
        folder: Folder,
        mailboxUuid: String,
        newCursor: String,
        okHttpClient: OkHttpClient?,
        realm: Realm,
    ): List<Thread> {
        val newMessagesThreads = mutableSetOf<Thread>()
        if (shortUids.isNotEmpty()) {

            var pageStart = 0
            val pageSize = ApiRepository.PER_PAGE
            val uids = getUniquesUidsWithNewestFirst(folder, shortUids)

            while (pageStart < uids.count()) {

                val pageEnd = min(pageStart + pageSize, uids.count())
                val page = uids.subList(pageStart, pageEnd)

                val apiResponse = ApiRepository.getMessagesByUids(mailboxUuid, folder.id, page, okHttpClient)
                if (!apiResponse.isSuccess() && okHttpClient != null) apiResponse.throwErrorAsException()
                apiResponse.data?.messages?.let { messages ->
                    realm.writeBlocking {
                        val threads = createMultiMessagesThreads(messages, findLatest(folder)!!)
                        newMessagesThreads.addAll(threads)
                    }
                    SentryDebug.sendMissingMessagesSentry(page, messages, folder, newCursor)
                }

                pageStart += pageSize
            }
        }

        return newMessagesThreads.toList()
    }

    private fun MutableRealm.createMultiMessagesThreads(messages: List<Message>, folder: Folder): List<Thread> {

        // TODO: Temporary Realm crash fix (`getThreadsQuery(messageIds: Set<String>)` is broken), remove this when it's fixed.
        val allThreads = ThreadController.getThreads(realm = this).toMutableList()

        val idsOfFoldersWithIncompleteThreads = FolderController.getIdsOfFoldersWithIncompleteThreads(realm = this)
        val threadsToUpsert = mutableMapOf<String, Thread>()

        messages.forEach { message ->

            val existingMessage = folder.messages.firstOrNull { it.uid == message.uid }
            if (existingMessage != null) {
                Sentry.withScope { scope ->
                    scope.level = SentryLevel.ERROR
                    scope.setExtra("folder.id", folder.id)
                    scope.setExtra("existingMessage.folderId", existingMessage.folderId)
                    scope.setExtra("message.folderId", message.folderId)
                    scope.setExtra("uid", existingMessage.uid)
                    scope.setExtra("Existing messageId", "${existingMessage.messageId}")
                    scope.setExtra("New messageId", "${message.messageId}")
                    if (existingMessage.messageId == message.messageId) {
                        Sentry.captureMessage("Same message id")
                    } else {
                        Sentry.captureMessage("Same message uid")
                    }
                }
                Log.d("FolderSingle", "Message's Folder list has more than one element.")
                Log.d("FolderSingle", "existingMessage.uid: ${existingMessage.uid}")
                Log.d("FolderSingle", "folder.id: ${folder.id} | folderName: ${folder.name}")
                Log.d("FolderSingle", "existingMessage.folderId: ${existingMessage.folderId}")
                Log.d("FolderSingle", "message.folderId: ${message.folderId}")
                Log.d("FolderSingle", "existingMessage.messageId: ${existingMessage.messageId}")
                Log.d("FolderSingle", "message.messageId: ${message.messageId}")
                return@forEach
            }

            folder.messages.add(message)

            message.initMessageIds()
            message.isSpam = folder.role == FolderRole.SPAM

            // TODO: Temporary Realm crash fix (`getThreadsQuery(messageIds: Set<String>)` is broken), put this back when it's fixed.
            // val existingThreads = ThreadController.getThreads(message.messageIds, realm = this).toList()
            // TODO: Temporary Realm crash fix (`getThreadsQuery(messageIds: Set<String>)` is broken), remove this when it's fixed.
            val existingThreads = allThreads.filter { it.messagesIds.any { id -> message.messageIds.contains(id) } }

            createNewThreadIfRequired(existingThreads, message, idsOfFoldersWithIncompleteThreads)?.let { newThread ->
                upsertThread(newThread).also {
                    folder.threads.add(it)
                    threadsToUpsert[it.uid] = it
                    // TODO: Temporary Realm crash fix (`getThreadsQuery(messageIds: Set<String>)` is broken), remove this when it's fixed.
                    allThreads.add(it)
                }
            }

            existingThreads.forEach { thread ->
                thread.messagesIds += message.messageIds
                thread.addMessageWithConditions(message, realm = this)
                threadsToUpsert[thread.uid] = upsertThread(thread)
            }
        }

        val folderThreads = mutableListOf<Thread>()
        threadsToUpsert.forEach { (_, thread) ->
            thread.recomputeThread(realm = this)
            upsertThread(thread)
            if (thread.folderId == folder.id) {
                folderThreads.add(if (thread.isManaged()) thread.copyFromRealm(1u) else thread)
            }
        }

        return folderThreads
    }

    private fun TypedRealm.createNewThreadIfRequired(
        existingThreads: List<Thread>,
        newMessage: Message,
        idsOfFoldersWithIncompleteThreads: List<String>,
    ): Thread? {
        var newThread: Thread? = null

        if (existingThreads.none { it.folderId == newMessage.folderId }) {

            newThread = newMessage.toThread()
            newThread.addFirstMessage(newMessage)

            val referenceThread = getReferenceThread(existingThreads, idsOfFoldersWithIncompleteThreads)
            if (referenceThread != null) addPreviousMessagesToThread(newThread, referenceThread)
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

    private fun TypedRealm.addPreviousMessagesToThread(newThread: Thread, existingThread: Thread) {

        newThread.messagesIds += existingThread.messagesIds

        existingThread.messages.forEach { message ->
            newThread.addMessageWithConditions(message, realm = this)
        }
    }

    private fun MutableRealm.handleDeletedUids(uids: List<String>): Set<String> {

        val impactedFolders = mutableSetOf<String>()
        val threads = mutableSetOf<Thread>()

        uids.forEach { messageUid ->
            val message = getMessage(messageUid, this) ?: return@forEach

            for (thread in message.threads.reversed()) {

                val isSuccess = thread.messages.removeIf { it.uid == messageUid }
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

            deleteMessage(messageUid, this)
        }

        threads.forEach { it.recomputeThread(realm = this) }

        return impactedFolders
    }

    private fun MutableRealm.handleUpdatedUids(messageFlags: List<MessageFlags>, folderId: String): Set<String> {

        val impactedFolders = mutableSetOf<String>()
        val threads = mutableSetOf<Thread>()

        messageFlags.forEach { flags ->

            val uid = flags.shortUid.toLongUid(folderId)
            getMessage(uid, realm = this)?.let { message ->
                message.updateFlags(flags)
                threads += message.threads
            }
        }

        threads.forEach { thread ->
            impactedFolders.add(thread.folderId)
            thread.recomputeThread(realm = this)
        }

        return impactedFolders
    }

    private fun getMessagesUids(mailboxUuid: String, folderId: String, okHttpClient: OkHttpClient? = null): MessagesUids? {
        val apiResponse = ApiRepository.getMessagesUids(mailboxUuid, folderId, okHttpClient)
        if (!apiResponse.isSuccess() && okHttpClient != null) apiResponse.throwErrorAsException()
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
        okHttpClient: OkHttpClient? = null,
    ): MessagesUids? {
        val apiResponse = ApiRepository.getMessagesUidsDelta(mailboxUuid, folderId, previousCursor, okHttpClient)
        if (!apiResponse.isSuccess() && okHttpClient != null) apiResponse.throwErrorAsException()
        return apiResponse.data?.let {
            MessagesUids(
                addedShortUids = it.addedShortUids,
                deletedUids = it.deletedShortUids.map { shortUid -> shortUid.toLongUid(folderId) },
                updatedMessages = it.updatedMessages,
                cursor = it.cursor,
            )
        }
    }

    private fun getUniquesUidsWithNewestFirst(folder: Folder, remoteUids: List<String>): List<String> {
        val localUids = folder.messages.map { it.uid.toShortUid() }.toSet()
        val uniqueUids = remoteUids.subtract(localUids)
        return uniqueUids.reversed()
    }

    data class MessagesUids(
        var addedShortUids: List<String> = emptyList(),
        var deletedUids: List<String> = emptyList(),
        var updatedMessages: List<MessageFlags> = emptyList(),
        var cursor: String,
    )
    //endregion
}
