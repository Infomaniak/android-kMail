/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import com.infomaniak.lib.core.utils.monthsAgo
import com.infomaniak.mail.data.LocalSettings.ThreadMode
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController.incrementFolderUnreadCount
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.getMessages.GetMessagesUidsDeltaResult.MessageFlags
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.copyListToRealm
import com.infomaniak.mail.utils.throwErrorAsException
import com.infomaniak.mail.utils.toRealmInstant
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.RealmSet
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

object MessageController {

    //region Queries
    private fun getMessagesQuery(uids: List<String>, realm: TypedRealm? = null): RealmQuery<Message> {
        val byUids = "${Message::uid.name} IN {${uids.joinToString { "\"$it\"" }}}"
        return (realm ?: RealmDatabase.mailboxContent()).query(byUids)
    }

    private fun getMessagesQuery(folderId: String, realm: TypedRealm? = null): RealmQuery<Message> {
        val byFolderId = "${Message::folderId.name} == '$folderId'"
        return (realm ?: RealmDatabase.mailboxContent()).query(byFolderId)
    }

    private fun getMessagesQuery(realm: TypedRealm? = null): RealmQuery<Message> {
        return (realm ?: RealmDatabase.mailboxContent()).query()
    }

    private fun getMessageQuery(uid: String, realm: TypedRealm? = null): RealmSingleQuery<Message> {
        val byUid = "${Message::uid.name} == '$uid'"
        return (realm ?: RealmDatabase.mailboxContent()).query<Message>(byUid).first()
    }
    //endregion

    //region Get data
    fun getMessages(realm: TypedRealm? = null): RealmResults<Message> {
        return getMessagesQuery(realm).find()
    }

    private fun getMessages(uids: List<String>, realm: TypedRealm? = null): RealmResults<Message> {
        return getMessagesQuery(uids, realm).find()
    }

    fun getMessages(folderId: String, realm: TypedRealm? = null): RealmResults<Message> {
        return getMessagesQuery(folderId, realm).find()
    }

    fun getMessage(uid: String, realm: TypedRealm? = null): Message? {
        return getMessageQuery(uid, realm).find()
    }

    fun getLastMessage(threadUid: String, folderId: String, realm: TypedRealm): Message? {
        val parentThreadUidProperty = "${Message::parentThread.name}.${Thread::uid.name}"
        val folderIdProperty = Message::folderId.name
        val dateProperty = Message::date.name
        val query = "$parentThreadUidProperty == '$threadUid' AND $folderIdProperty == '$folderId'"
        return realm.query<Message>(query).sort(dateProperty, Sort.DESCENDING).first().find()
    }
    //endregion

    //region Edit data
    fun MutableRealm.update(localMessages: List<Message>, remoteMessages: List<Message>) {

        Log.d(RealmDatabase.TAG, "Messages: Delete outdated data")
        deleteMessages(getOutdatedMessages(localMessages, remoteMessages))

        Log.d(RealmDatabase.TAG, "Messages: Save new data")
        copyListToRealm(remoteMessages, alsoCopyManagedItems = false)
    }

    // TODO: Replace this with a Realm query (blocked by https://github.com/realm/realm-kotlin/issues/591)
    private fun getOutdatedMessages(localMessages: List<Message>, remoteMessages: List<Message>): List<Message> {
        return localMessages.filter { localMessage ->
            remoteMessages.none { remoteMessage -> remoteMessage.uid == localMessage.uid }
        }
    }

    fun MutableRealm.deleteMessages(messages: List<Message>) {
        messages.reversed().forEach { deleteMessage(it.uid, realm = this) }
    }

    fun deleteMessage(uid: String, realm: MutableRealm? = null) {
        val block: (MutableRealm) -> Unit = {
            getMessage(uid, realm = it)
                ?.let { message ->
                    DraftController.getDraftByMessageUid(message.uid, realm = it)?.let(it::delete)
                    it.delete(message)
                }
        }
        realm?.let(block) ?: RealmDatabase.mailboxContent().writeBlocking(block)
    }
    //endregion

    //region New API routes
    fun fetchCurrentFolderMessages(
        mailboxUuid: String,
        folderId: String,
        threadMode: ThreadMode,
        okHttpClient: OkHttpClient? = null,
        realm: Realm? = null,
    ): List<Thread> {

        val folder = FolderController.getFolder(folderId, realm) ?: return emptyList()

        val newMessagesThreads = fetchFolderMessages(mailboxUuid, folder, threadMode, okHttpClient, realm)

        val roles = when (folder.role) {
            FolderRole.INBOX -> listOf(FolderRole.SENT, FolderRole.DRAFT)
            FolderRole.SENT -> listOf(FolderRole.INBOX, FolderRole.DRAFT)
            FolderRole.DRAFT -> listOf(FolderRole.INBOX, FolderRole.SENT)
            else -> return emptyList()
        }

        roles.forEach { role ->
            FolderController.getFolder(role)?.let { folder ->
                fetchFolderMessages(mailboxUuid, folder, threadMode, okHttpClient, realm)
            }
        }

        return newMessagesThreads
    }

    private fun fetchFolderMessages(
        mailboxUuid: String,
        folder: Folder,
        threadMode: ThreadMode,
        okHttpClient: OkHttpClient?,
        realm: Realm?,
    ): List<Thread> {
        val previousCursor = folder.cursor

        val messagesUids = if (previousCursor == null) {
            getMessagesUids(mailboxUuid, folder.id, okHttpClient)
        } else {
            getMessagesUidsDelta(mailboxUuid, folder.id, previousCursor, okHttpClient)
        } ?: return emptyList()

        return handleMessagesUids(messagesUids, folder, mailboxUuid, threadMode, okHttpClient, realm)
    }

    private fun handleMessagesUids(
        messagesUids: MessagesUids,
        folder: Folder,
        mailboxUuid: String,
        threadMode: ThreadMode,
        okHttpClient: OkHttpClient?,
        realm: Realm?,
    ) = with(messagesUids) {

        Log.i(
            "API",
            "Added: ${addedShortUids.count()} | Deleted: ${deletedUids.count()} | Updated: ${updatedMessages.count()} | ${folder.name}",
        )

        val newMessagesThreads = handleAddedUids(addedShortUids, folder, mailboxUuid, threadMode, okHttpClient, realm)

        (realm ?: RealmDatabase.mailboxContent()).writeBlocking {

            handleDeletedUids(deletedUids, folder.id, threadMode)
            handleUpdatedUids(updatedMessages, folder.id)

            FolderController.getFolder(folder.id, realm = this)?.let {
                it.lastUpdatedAt = Date().toRealmInstant()
                it.cursor = cursor
            }
        }

        newMessagesThreads
    }

    private fun handleAddedUids(
        shortUids: List<String>,
        folder: Folder,
        mailboxUuid: String,
        threadMode: ThreadMode,
        okHttpClient: OkHttpClient?,
        realm: Realm?,
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
                    (realm ?: RealmDatabase.mailboxContent()).writeBlocking {
                        val threads = when (threadMode) {
                            ThreadMode.THREADS -> createMultiMessagesThreads(messages)
                            ThreadMode.MESSAGES -> createSingleMessageThreads(messages)
                        }
                        newMessagesThreads.addAll(threads)
                    }
                }

                pageStart += pageSize
            }
        }

        return newMessagesThreads.toList()
    }

    fun MutableRealm.createMultiMessagesThreads(messages: List<Message>): List<Thread> {
        val allThreads = ThreadController.getThreads(realm = this).toMutableList()

        // Here, we use a Set instead of a List, so we can't add multiple times the same Thread to it.
        val threadsToUpsert = mutableSetOf<Thread>()

        messages.forEach { message ->

            if (!message.seen) incrementFolderUnreadCount(message.folderId, 1)

            val messageIds = getMessageIds(message)
            message.messageIds = messageIds

            val thread = allThreads.find { it.messagesIds.any { id -> messageIds.contains(id) } }
                ?: run { message.toThread().also(allThreads::add) }

            thread.addMessage(message)
            threadsToUpsert.add(thread)
        }

        return threadsToUpsert.map { thread ->
            thread.recomputeThread()
            ThreadController.upsertThread(thread, realm = this)
            if (thread.isManaged()) copyFromRealm(thread, 0u) else thread
        }
    }

    fun MutableRealm.createSingleMessageThreads(messages: List<Message>): Set<Thread> {
        val threads = mutableSetOf<Thread>()
        messages.forEach { message ->
            if (!message.seen) incrementFolderUnreadCount(message.folderId, 1)
            val thread = message.toThread()
            thread.addMessage(message)
            thread.recomputeThread()
            ThreadController.upsertThread(thread, realm = this)
            threads.add(thread)
        }
        return threads
    }

    private fun MutableRealm.handleDeletedUids(uids: List<String>, folderId: String, threadMode: ThreadMode) {
        if (uids.isNotEmpty()) {

            val deletedMessages = getMessages(uids, realm = this)

            var unreadCount = 0

            when (threadMode) {
                ThreadMode.THREADS -> deletedMessages.forEach { message ->

                    if (!message.seen) unreadCount--

                    message.parentThread.firstOrNull()?.let { thread ->
                        if (thread.uniqueMessagesCount == 1) {
                            delete(thread)
                        } else {
                            thread.removeMessage(message)
                        }
                    }
                }
                ThreadMode.MESSAGES -> {
                    deletedMessages.forEach { if (!it.seen) unreadCount-- }
                    delete(ThreadController.getThreads(uids, realm = this))
                }
            }

            incrementFolderUnreadCount(folderId, unreadCount)
            deleteMessages(deletedMessages)
        }
    }

    private fun MutableRealm.handleUpdatedUids(messageFlags: List<MessageFlags>, folderId: String) {

        var unreadCount = 0

        messageFlags.forEach { flags ->

            val uid = flags.shortUid.toLongUid(folderId)
            getMessage(uid, realm = this)?.let { message ->

                val wasRead = message.seen
                message.updateFlags(flags)
                val isRead = message.seen

                if (!wasRead && isRead) unreadCount--
                if (wasRead && !isRead) unreadCount++

                message.parentThread.firstOrNull()?.recomputeThread()
            }
        }

        incrementFolderUnreadCount(folderId, unreadCount)
    }

    private fun getMessageIds(message: Message): RealmSet<String> {

        fun parseMessagesIds(messageId: String): List<String> {
            return messageId.removePrefix("<").removeSuffix(">").split("><", "> <")
        }

        return realmSetOf<String>().apply {
            addAll(parseMessagesIds(message.msgId))
            message.references?.let { addAll(parseMessagesIds(it)) }
            message.inReplyTo?.let { addAll(parseMessagesIds(it)) }
        }
    }

    private fun threeMonthsAgo(): String = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date().monthsAgo(3))

    private fun String.toLongUid(folderId: String) = "${this}@${folderId}"

    private fun String.toShortUid(): String = substringBefore('@')

    private fun getMessagesUids(mailboxUuid: String, folderId: String, okHttpClient: OkHttpClient? = null): MessagesUids? {
        val apiResponse = ApiRepository.getMessagesUids(mailboxUuid, folderId, threeMonthsAgo(), okHttpClient)
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
        val localUids = getMessages(folder.id).map { it.uid.toShortUid() }.toSet()
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
