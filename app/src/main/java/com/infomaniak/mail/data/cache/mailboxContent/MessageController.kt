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
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.getMessages.GetMessagesUidsDeltaResult.MessageFlags
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.throwErrorAsException
import com.infomaniak.mail.utils.toRealmInstant
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.query.Sort
import okhttp3.OkHttpClient
import java.util.Date
import kotlin.math.min

object MessageController {

    private inline val defaultRealm get() = RealmDatabase.mailboxContent()

    private fun byFolderId(folderId: String) = "${Message::folderId.name} == '$folderId'"
    private val isNotDraft = "${Message::isDraft.name} == false"
    private val isNotScheduled = "${Message::isScheduled.name} == false"
    private val isNotFromMe = "SUBQUERY(${Message::from.name}, \$recipient, " +
            "\$recipient.${Recipient::email.name} != '${AccountUtils.currentMailboxEmail}').@count > 0"

    //region Queries
    private fun getMessagesQuery(uids: List<String>, realm: TypedRealm): RealmQuery<Message> {
        val byUids = "${Message::uid.name} IN {${uids.joinToString { "'$it'" }}}"
        return realm.query(byUids)
    }

    private fun getMessagesQuery(folderId: String, realm: TypedRealm): RealmQuery<Message> {
        return realm.query(byFolderId(folderId))
    }

    private fun getMessageQuery(uid: String, realm: TypedRealm): RealmSingleQuery<Message> {
        val byUid = "${Message::uid.name} == '$uid'"
        return realm.query<Message>(byUid).first()
    }
    //endregion

    //region Get data
    private fun getMessages(uids: List<String>, realm: TypedRealm): RealmResults<Message> {
        return getMessagesQuery(uids, realm).find()
    }

    fun getMessages(folderId: String, realm: TypedRealm = defaultRealm): RealmResults<Message> {
        return getMessagesQuery(folderId, realm).find()
    }

    fun getSortedMessages(threadUid: String): RealmQuery<Message>? {
        return ThreadController.getThread(threadUid)?.messages?.query()?.sort(Message::date.name, Sort.ASCENDING)
    }

    fun getMessage(uid: String, realm: TypedRealm = defaultRealm): Message? {
        return getMessageQuery(uid, realm).find()
    }

    fun getMessageToReplyTo(thread: Thread): Message = with(thread) {
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

    fun getArchivableMessages(thread: Thread, folderId: String): List<Message> {
        return getMessagesAndDuplicates(thread, "${byFolderId(folderId)} AND $isNotScheduled")
    }

    fun getUnscheduledMessages(thread: Thread) = getMessagesAndDuplicates(thread, isNotScheduled)

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
    //endregion

    //region New API routes
    fun fetchCurrentFolderMessages(
        mailbox: Mailbox,
        folderId: String,
        okHttpClient: OkHttpClient? = null,
        realm: Realm = defaultRealm,
    ): List<Thread> {

        val folder = FolderController.getFolder(folderId, realm) ?: return emptyList()

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

        return handleMessagesUids(messagesUids, folder, mailbox, okHttpClient, realm)
    }

    private fun handleMessagesUids(
        messagesUids: MessagesUids,
        folder: Folder,
        mailbox: Mailbox,
        okHttpClient: OkHttpClient?,
        realm: Realm,
    ) = with(messagesUids) {

        Log.i(
            "API",
            "Added: ${addedShortUids.count()} | Deleted: ${deletedUids.count()} | Updated: ${updatedMessages.count()} | ${folder.name}",
        )

        val newMessagesThreads = handleAddedUids(addedShortUids, folder, mailbox.uuid, okHttpClient, realm)

        realm.writeBlocking {

            val impactedFolders = newMessagesThreads.map { it.folderId }.toMutableSet()

            impactedFolders += handleDeletedUids(deletedUids)
            impactedFolders += handleUpdatedUids(updatedMessages, folder.id)

            impactedFolders.forEach { folderId ->
                FolderController.refreshUnreadCount(folderId, mailbox.objectId, realm = this)
            }

            FolderController.getFolder(folder.id, realm = this)?.let {
                it.lastUpdatedAt = Date().toRealmInstant()
                it.cursor = cursor
            }
        }

        return@with newMessagesThreads
    }

    private fun handleAddedUids(
        shortUids: List<String>,
        folder: Folder,
        mailboxUuid: String,
        okHttpClient: OkHttpClient?,
        realm: Realm,
    ): List<Thread> {
        val newMessagesThreads = mutableSetOf<Thread>()
        if (shortUids.isNotEmpty()) {

            var pageStart = 0
            val pageSize = ApiRepository.PER_PAGE
            val uids = getUniquesUidsWithNewestFirst(folder.id, shortUids)

            while (pageStart < uids.count()) {

                val pageEnd = min(pageStart + pageSize, uids.count())
                val page = uids.subList(pageStart, pageEnd)

                val apiResponse = ApiRepository.getMessagesByUids(mailboxUuid, folder.id, page, okHttpClient)
                if (!apiResponse.isSuccess() && okHttpClient != null) apiResponse.throwErrorAsException()
                apiResponse.data?.messages?.let { messages ->
                    realm.writeBlocking {
                        val threads = createMultiMessagesThreads(messages, folder)
                        newMessagesThreads.addAll(threads)
                    }
                }

                pageStart += pageSize
            }
        }

        return newMessagesThreads.toList()
    }

    private fun MutableRealm.createMultiMessagesThreads(messages: List<Message>, folder: Folder): List<Thread> {

        // TODO: Temporary Realm crash fix (`getThreadsQuery(messageIds: Set<String>)` is broken), remove this when it's fixed.
        val allThreads = ThreadController.getThreads(realm = this).toMutableList()

        val idsOfFoldersWithSpecificBehavior = FolderController.getIdsOfFoldersWithSpecificBehavior(realm = this)
        val threadsToUpsert = mutableMapOf<String, Thread>()

        messages.forEach { message ->

            message.initMessageIds()

            message.isSpam = folder.role == FolderRole.SPAM

            // TODO: Temporary Realm crash fix (`getThreadsQuery(messageIds: Set<String>)` is broken), put this back when it's fixed.
            // val existingThreads = ThreadController.getThreads(message.messageIds, realm = this).toList()
            // TODO: Temporary Realm crash fix (`getThreadsQuery(messageIds: Set<String>)` is broken), remove this when it's fixed.
            val existingThreads = allThreads.filter { it.messagesIds.any { id -> message.messageIds.contains(id) } }

            createNewThreadIfRequired(existingThreads, message, idsOfFoldersWithSpecificBehavior)?.let { newThread ->
                upsertThread(newThread)
                threadsToUpsert[newThread.uid] = newThread
                // TODO: Temporary Realm crash fix (`getThreadsQuery(messageIds: Set<String>)` is broken), remove this when it's fixed.
                allThreads.add(newThread)
            }

            existingThreads.forEach {
                it.addMessageWithConditions(message, realm = this)
                upsertThread(it)
                threadsToUpsert[it.uid] = it
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
        idsOfFoldersWithSpecificBehavior: List<String>,
    ): Thread? {
        var newThread: Thread? = null

        if (existingThreads.none { it.folderId == newMessage.folderId }) {

            newThread = newMessage.toThread()
            newThread.addFirstMessage(newMessage)

            val referenceThread = existingThreads.firstOrNull { !idsOfFoldersWithSpecificBehavior.contains(it.folderId) }

            if (referenceThread == null) {
                existingThreads.forEach { thread -> addPreviousMessagesToThread(newThread, thread) }
            } else {
                addPreviousMessagesToThread(newThread, referenceThread)
            }
        }

        return newThread
    }

    private fun TypedRealm.addPreviousMessagesToThread(newThread: Thread, existingThread: Thread) {

        newThread.messagesIds += existingThread.messagesIds

        existingThread.messages.forEach { message ->
            newThread.addMessageWithConditions(message, realm = this)
        }
    }

    // Unused for now
    private fun MutableRealm.createSingleMessageThreads(messages: List<Message>): List<Thread> {
        return messages.map { message ->
            message.toThread().also { thread ->
                thread.addFirstMessage(message)
                thread.recomputeThread(realm = this)
                upsertThread(thread)
            }
        }
    }

    private fun MutableRealm.handleDeletedUids(uids: List<String>): Set<String> {

        val impactedFolders = mutableSetOf<String>()

        if (uids.isNotEmpty()) {

            val deletedMessages = getMessages(uids, realm = this)

            val threads = mutableSetOf<Thread>()
            deletedMessages.forEach { message ->
                for (thread in message.parentThreads.reversed()) {

                    val isSuccess = thread.messages.removeIf { it.uid == message.uid }
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
            }
            threads.forEach { it.recomputeThread(realm = this) }

            deleteMessages(deletedMessages)
        }

        return impactedFolders
    }

    private fun MutableRealm.handleUpdatedUids(messageFlags: List<MessageFlags>, folderId: String): Set<String> {

        val impactedFolders = mutableSetOf<String>()
        val threads = mutableSetOf<Thread>()

        messageFlags.forEach { flags ->

            val uid = flags.shortUid.toLongUid(folderId)
            getMessage(uid, realm = this)?.let { message ->
                message.updateFlags(flags)
                threads += message.parentThreads
            }
        }

        threads.forEach { thread ->
            impactedFolders.add(thread.folderId)
            thread.recomputeThread(realm = this)
        }

        return impactedFolders
    }

    private fun String.toLongUid(folderId: String) = "${this}@${folderId}"

    private fun String.toShortUid(): String = substringBefore('@')

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

    private fun getUniquesUidsWithNewestFirst(folderId: String, remoteUids: List<String>): List<String> {
        val localUids = getMessages(folderId).map { it.uid.toShortUid() }.toSet()
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
