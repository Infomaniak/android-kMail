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
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController.getThreadsQuery
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.getMessages.GetMessagesUidsDeltaResult.MessageFlags
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.copyListToRealm
import com.infomaniak.mail.utils.toRealmInstant
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.toRealmSet
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

object MessageController {

    //region Queries
    private fun getMessagesQuery(uids: List<String>, realm: TypedRealm? = null): RealmQuery<Message> {
        val byUids = "${Message::uid.name} IN {${uids.joinToString { "\"$it\"" }}}"
        return (realm ?: RealmDatabase.mailboxContent()).query(byUids)
    }

    private fun getMessageQuery(uid: String, realm: TypedRealm? = null): RealmSingleQuery<Message> {
        val byUid = "${Message::uid.name} == '$uid'"
        return (realm ?: RealmDatabase.mailboxContent()).query<Message>(byUid).first()
    }
    //endregion

    //region Get data
    private fun getMessages(uids: List<String>, realm: TypedRealm? = null): RealmResults<Message> {
        return getMessagesQuery(uids, realm).find()
    }

    fun getMessage(uid: String, realm: TypedRealm? = null): Message? {
        return getMessageQuery(uid, realm).find()
    }
    //endregion

    //region Edit data
    private fun updateMessage(uid: String, realm: MutableRealm? = null, onUpdate: (message: Message) -> Unit) {
        val block: (MutableRealm) -> Unit = { getMessage(uid, realm = it)?.let(onUpdate) }
        realm?.let(block) ?: RealmDatabase.mailboxContent().writeBlocking(block)
    }

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
    suspend fun fetchCurrentFolderMessages(mailboxUuid: String, folderId: String, threadMode: ThreadMode) {
        val folder = FolderController.getFolder(folderId) ?: return
        if (folder.role == FolderRole.INBOX || folder.role == FolderRole.SENT || folder.role == FolderRole.DRAFT) {
            FolderController.getFolder(FolderRole.INBOX)?.let { fetchFolderMessages(mailboxUuid, it, threadMode) }
            FolderController.getFolder(FolderRole.SENT)?.let { fetchFolderMessages(mailboxUuid, it, threadMode) }
            FolderController.getFolder(FolderRole.DRAFT)?.let { fetchFolderMessages(mailboxUuid, it, threadMode) }
        } else {
            fetchFolderMessages(mailboxUuid, folder, threadMode)
        }
    }

    private suspend fun fetchFolderMessages(mailboxUuid: String, folder: Folder, threadMode: ThreadMode) {
        val previousCursor = folder.cursor

        val messagesUids = if (previousCursor == null) {
            getMessagesUids(mailboxUuid, folder.id)
        } else {
            getMessagesUidsDelta(mailboxUuid, folder.id, previousCursor)
        } ?: return

        when (threadMode) {
            ThreadMode.THREADS -> updateMailboxContentAsThreads(messagesUids, folder, mailboxUuid, previousCursor)
            ThreadMode.MESSAGES -> updateMailboxContentAsMessages(messagesUids, folder, mailboxUuid)
        }

        // TODO: Do we still need this with the new API routes?
        // RealmDatabase.mailboxContent().writeBlocking {
        //     val isDraftFolder = FolderController.getFolder(folderId, realm = this)?.role == FolderRole.DRAFT
        //     if (isDraftFolder) {
        //         val messages = getMessages(folderId, realm = this).find()
        //         DraftController.cleanOrphans(messages, realm = this)
        //     }
        // }
    }

    //region MultiMessages Threads
    private suspend fun updateMailboxContentAsThreads(
        messagesUids: MessagesUids,
        folder: Folder,
        mailboxUuid: String,
        previousCursor: String?,
    ) = with(messagesUids) {

        Log.e(
            "API",
            "As threads | A: ${addedShortUids.count()} | D: ${deletedUids.count()} | U: ${updatedMessages.count()} | ${folder.name}",
        )

        var unreadCount = folder.unreadCount

        unreadCount = addMessagesAsThreads(addedShortUids, folder, mailboxUuid, unreadCount)

        RealmDatabase.mailboxContent().writeBlocking {

            unreadCount = deleteMessagesAsThreads(deletedUids, unreadCount, realm = this)
            unreadCount = updateMessagesAsThreads(updatedMessages, folder.id, unreadCount, realm = this)

            FolderController.updateFolder(folder.id, realm = this) {
                if (previousCursor != null) it.unreadCount = max(unreadCount, 0)
                it.lastUpdatedAt = Date().toRealmInstant()
                it.cursor = cursor
            }
        }
    }

    private suspend fun addMessagesAsThreads(
        shortUids: List<String>,
        folder: Folder,
        mailboxUuid: String,
        unreadCount: Int,
    ): Int {
        var newUnreadCount = unreadCount
        if (shortUids.isNotEmpty()) {
            val uids = getUniquesUidsInReverse(folder, shortUids)
            val pageSize = ApiRepository.PER_PAGE
            var pageStart = 0
            while (pageStart < uids.count()) {
                val pageEnd = min(pageStart + pageSize, uids.count())
                val page = uids.subList(pageStart, pageEnd)
                ApiRepository.getMessagesByUids(mailboxUuid, folder.id, page).data?.messages?.let { messages ->
                    RealmDatabase.mailboxContent().writeBlocking {
                        newUnreadCount = createMultiMessagesThreads(messages, unreadCount)
                    }
                }

                pageStart += pageSize
            }
        }
        return newUnreadCount
    }

    fun MutableRealm.createMultiMessagesThreads(messages: List<Message>, unreadCount: Int = 0): Int {
        var newUnreadCount = unreadCount
        val threads = ThreadController.getThreads(realm = this).toMutableList()

        messages.forEach { message ->

            fun parseMessagesIds(messageId: String): List<String> {
                return messageId.removePrefix("<").removeSuffix(">").split("><")
            }

            val messageIds = mutableSetOf<String>()
            messageIds.addAll(parseMessagesIds(message.msgId))
            message.references?.let { messageIds.addAll(parseMessagesIds(it)) }
            message.inReplyTo?.let { messageIds.addAll(parseMessagesIds(it)) }
            message.messageIds = messageIds.toRealmSet()

            val thread = threads.find { it.messagesIds.intersect(messageIds).isNotEmpty() }
                ?: run { message.toThread().also(threads::add) }
            thread.addMessage(message)
            ThreadController.upsertThread(thread, realm = this)

            if (!message.seen) newUnreadCount++
        }

        return newUnreadCount
    }

    private fun deleteMessagesAsThreads(uids: List<String>, unreadCount: Int, realm: MutableRealm): Int {
        var newUnreadCount = unreadCount
        if (uids.isNotEmpty()) {
            val deletedMessages = getMessages(uids, realm)

            deletedMessages.forEach { message ->

                if (!message.seen) newUnreadCount--

                val thread = ThreadController.getThread(message.threadUid!!, realm)!!
                if (thread.uniqueMessagesCount == 1) {
                    realm.delete(thread)
                } else {
                    thread.removeMessage(message)
                    ThreadController.upsertThread(thread, realm)
                }
            }

            realm.deleteMessages(deletedMessages)
        }
        return newUnreadCount
    }

    private fun updateMessagesAsThreads(
        messageFlags: List<MessageFlags>,
        folderId: String,
        unreadCount: Int,
        realm: MutableRealm,
    ): Int {
        var newUnreadCount = unreadCount
        messageFlags.forEach { flags ->
            val uid = flags.shortUid.toLongUid(folderId)
            updateMessage(uid, realm) { message ->

                if (message.seen && !flags.seen) newUnreadCount++
                if (!message.seen && flags.seen) newUnreadCount--

                message.seen = flags.seen
                message.isFavorite = flags.isFavorite
                message.answered = flags.answered
                message.forwarded = flags.forwarded
                message.scheduled = flags.scheduled

                message.threadUid?.let {
                    ThreadController.updateThread(it, realm) { thread ->
                        thread.recomputeData()
                        // ThreadController.upsertThread(thread, realm)
                    }
                }
            }

        }
        return newUnreadCount
    }
    //endregion

    //region SingleMessage Threads
    private suspend fun updateMailboxContentAsMessages(
        messagesUids: MessagesUids,
        folder: Folder,
        mailboxUuid: String,
    ) = with(messagesUids) {

        Log.e(
            "API",
            "As messages | A: ${addedShortUids.count()} | D: ${deletedUids.count()} | U: ${updatedMessages.count()} | ${folder.name}",
        )

        addMessagesAsMessages(addedShortUids, folder, mailboxUuid)

        RealmDatabase.mailboxContent().writeBlocking {

            deleteMessagesAsMessages(deletedUids, realm = this)
            updateMessagesAsMessages(updatedMessages, folder.id, realm = this)

            if (cursor != null) FolderController.updateFolder(folder.id, realm = this) {
                it.lastUpdatedAt = Date().toRealmInstant()
                it.cursor = cursor
            }
        }
    }

    private suspend fun addMessagesAsMessages(shortUids: List<String>, folder: Folder, mailboxUuid: String) {
        if (shortUids.isNotEmpty()) {
            val uids = getUniquesUidsInReverse(folder, shortUids)
            val pageSize = ApiRepository.PER_PAGE
            var pageStart = 0
            while (pageStart < uids.count()) {
                val pageEnd = min(pageStart + pageSize, uids.count())
                val page = uids.subList(pageStart, pageEnd)
                ApiRepository.getMessagesByUids(mailboxUuid, folder.id, page).data?.messages?.let { messages ->
                    RealmDatabase.mailboxContent().writeBlocking {
                        createSingleMessageThreads(messages)
                    }
                }

                pageStart += pageSize
            }
        }
    }

    fun MutableRealm.createSingleMessageThreads(messages: List<Message>) {
        messages.forEach { message ->
            val thread = message.toThread()
            thread.addMessage(message)
            ThreadController.upsertThread(thread, realm = this)
        }
    }

    private fun deleteMessagesAsMessages(uids: List<String>, realm: MutableRealm) {
        if (uids.isNotEmpty()) {
            realm.deleteMessages(getMessages(uids, realm))
            realm.delete(getThreadsQuery(uids, realm))
        }
    }

    private fun updateMessagesAsMessages(messageFlags: List<MessageFlags>, folderId: String, realm: MutableRealm) {
        messageFlags.forEach { flags ->
            val uid = flags.shortUid.toLongUid(folderId)
            updateMessage(uid, realm) { message ->
                message.seen = flags.seen
                message.isFavorite = flags.isFavorite
                message.answered = flags.answered
                message.forwarded = flags.forwarded
                message.scheduled = flags.scheduled

                message.threadUid?.let {
                    ThreadController.updateThread(it, realm) { thread ->
                        thread.recomputeData()
                        // ThreadController.upsertThread(thread, realm)
                    }
                }
            }
        }
    }
    //endregion

    private fun threeMonthsAgo(): String = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date().monthsAgo(3))

    private fun String.toLongUid(folderId: String) = "${this}@${folderId}"

    private fun String.toShortUid(): String = substringBefore('@')

    private fun getMessagesUids(mailboxUuid: String, folderId: String): MessagesUids? {
        return ApiRepository.getMessagesUids(mailboxUuid, folderId, threeMonthsAgo()).data?.let {
            MessagesUids(
                addedShortUids = it.addedShortUids,
                cursor = it.cursor,
            )
        }
    }

    private fun getMessagesUidsDelta(mailboxUuid: String, folderId: String, previousCursor: String): MessagesUids? {
        return ApiRepository.getMessagesUidsDelta(mailboxUuid, folderId, previousCursor).data?.let {
            MessagesUids(
                addedShortUids = it.addedShortUids,
                deletedUids = it.deletedShortUids.map { shortUid -> shortUid.toLongUid(folderId) },
                updatedMessages = it.updatedMessages,
                cursor = it.cursor,
            )
        }
    }

    private fun getUniquesUidsInReverse(folder: Folder, remoteUids: List<String>): List<String> {
        val localUids = folder.threads.map { it.uid.toShortUid() }
        val uniqueUids = remoteUids - localUids.intersect(remoteUids.toSet())
        return uniqueUids.reversed()
    }

    private data class MessagesUids(
        var addedShortUids: List<String> = emptyList(),
        var deletedUids: List<String> = emptyList(),
        var updatedMessages: List<MessageFlags> = emptyList(),
        var cursor: String,
    )
    //endregion
}
