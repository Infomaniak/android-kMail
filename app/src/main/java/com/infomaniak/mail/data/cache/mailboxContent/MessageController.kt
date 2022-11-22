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
import com.infomaniak.mail.data.models.thread.Thread
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

    fun getMessagesQuery(realm: TypedRealm? = null): RealmQuery<Message> {
        return (realm ?: RealmDatabase.mailboxContent()).query()
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

    fun getMessages(folderId: String, realm: TypedRealm? = null): RealmQuery<Message> {
        return getMessagesQuery(folderId, realm)
    }

    fun getMessage(uid: String, realm: TypedRealm? = null): Message? {
        return getMessageQuery(uid, realm).find()
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
    suspend fun fetchCurrentFolderMessages(mailboxUuid: String, folderId: String, threadMode: ThreadMode) {

        val folder = FolderController.getFolder(folderId) ?: return

        fetchFolderMessages(mailboxUuid, folder, threadMode)

        val roles = when (folder.role) {
            FolderRole.INBOX -> listOf(FolderRole.SENT, FolderRole.DRAFT)
            FolderRole.SENT -> listOf(FolderRole.INBOX, FolderRole.DRAFT)
            FolderRole.DRAFT -> listOf(FolderRole.INBOX, FolderRole.SENT)
            else -> return
        }

        roles.forEach { role ->
            FolderController.getFolder(role)?.let { folder ->
                fetchFolderMessages(mailboxUuid, folder, threadMode)
            }
        }
    }

    private suspend fun fetchFolderMessages(mailboxUuid: String, folder: Folder, threadMode: ThreadMode) {
        val previousCursor = folder.cursor

        val messagesUids = if (previousCursor == null) {
            getMessagesUids(mailboxUuid, folder.id)
        } else {
            getMessagesUidsDelta(mailboxUuid, folder.id, previousCursor)
        } ?: return

        handleMessagesUids(messagesUids, folder, mailboxUuid, previousCursor, threadMode)

        // TODO: Do we still need this with the new API routes?
        // RealmDatabase.mailboxContent().writeBlocking {
        //     val isDraftFolder = FolderController.getFolder(folderId, realm = this)?.role == FolderRole.DRAFT
        //     if (isDraftFolder) {
        //         val messages = getMessages(folderId, realm = this).find()
        //         DraftController.cleanOrphans(messages, realm = this)
        //     }
        // }
    }

    private suspend fun handleMessagesUids(
        messagesUids: MessagesUids,
        folder: Folder,
        mailboxUuid: String,
        previousCursor: String?,
        threadMode: ThreadMode,
    ) = with(messagesUids) {

        Log.e(
            "API",
            "As threads | A: ${addedShortUids.count()} | D: ${deletedUids.count()} | U: ${updatedMessages.count()} | ${folder.name}",
        )

        add(addedShortUids, folder, mailboxUuid, threadMode)

        RealmDatabase.mailboxContent().writeBlocking {

            delete(deletedUids, threadMode)
            update(updatedMessages, folder.id)

            FolderController.getFolder(folder.id, realm = this)?.let {
                if (previousCursor != null) it.unreadCount = unreadCount
                it.lastUpdatedAt = Date().toRealmInstant()
                it.cursor = cursor
            }
        }
    }

    private suspend fun add(
        shortUids: List<String>,
        folder: Folder,
        mailboxUuid: String,
        threadMode: ThreadMode,
    ) {
        if (shortUids.isNotEmpty()) {

            var pageStart = 0
            val pageSize = ApiRepository.PER_PAGE
            val uids = getUniquesUidsInReverse(folder, shortUids)

            while (pageStart < uids.count()) {

                val pageEnd = min(pageStart + pageSize, uids.count())
                val page = uids.subList(pageStart, pageEnd)

                ApiRepository.getMessagesByUids(mailboxUuid, folder.id, page).data?.messages?.let { messages ->
                    RealmDatabase.mailboxContent().writeBlocking {
                        when (threadMode) {
                            ThreadMode.THREADS -> createMultiMessagesThreads(messages)
                            ThreadMode.MESSAGES -> createSingleMessageThreads(messages)
                        }
                    }
                }

                pageStart += pageSize
            }
        }
    }

    fun MutableRealm.createMultiMessagesThreads(messages: List<Message>) {
        val allThreads = ThreadController.getThreads(realm = this).toMutableList()
        val threadsToUpsert = mutableListOf<Thread>()

        messages.forEach { message ->

            val messageIds = getMessageIds(message)
            message.messageIds = messageIds.toRealmSet()

            val thread = allThreads.find { it.messagesIds.intersect(messageIds).isNotEmpty() }
                ?: run { message.toThread().also(allThreads::add) }

            thread.addMessage(message)
            threadsToUpsert.add(thread)
        }

        threadsToUpsert.forEach { thread ->
            thread.recomputeThread()
            ThreadController.upsertThread(thread, realm = this)
        }
    }

    fun MutableRealm.createSingleMessageThreads(messages: List<Message>) {
        val threads = mutableListOf<Thread>()
        messages.forEach { message ->
            val thread = message.toThread()
            thread.addMessage(message)
            threads.add(thread)
        }
        threads.forEach {
            it.recomputeThread()
            ThreadController.upsertThread(it, realm = this)
        }
    }

    private fun MutableRealm.delete(uids: List<String>, threadMode: ThreadMode) {
        if (uids.isNotEmpty()) {

            val deletedMessages = getMessages(uids, realm = this)

            when (threadMode) {
                ThreadMode.THREADS -> deletedMessages.forEach { message ->
                    val thread = ThreadController.getThread(message.threadUid!!, realm = this)!!
                    if (thread.uniqueMessagesCount == 1) {
                        delete(thread)
                    } else {
                        thread.removeMessage(message)
                    }
                }
                ThreadMode.MESSAGES -> delete(getThreadsQuery(uids))
            }

            deleteMessages(deletedMessages)
        }
    }

    private fun MutableRealm.update(messageFlags: List<MessageFlags>, folderId: String) {
        messageFlags.forEach { flags ->

            val uid = flags.shortUid.toLongUid(folderId)
            getMessage(uid, realm = this)?.let { message ->

                message.apply {
                    seen = flags.seen
                    isFavorite = flags.isFavorite
                    answered = flags.answered
                    forwarded = flags.forwarded
                    scheduled = flags.scheduled
                }

                message.threadUid?.let { ThreadController.getThread(it, realm = this)?.recomputeThread() }
            }
        }
    }

    private fun getMessageIds(message: Message): Set<String> {

        fun parseMessagesIds(messageId: String): List<String> {
            return messageId.removePrefix("<").removeSuffix(">").split("><")
        }

        return mutableSetOf<String>().apply {
            addAll(parseMessagesIds(message.msgId))
            message.references?.let { addAll(parseMessagesIds(it)) }
            message.inReplyTo?.let { addAll(parseMessagesIds(it)) }
        }
    }

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
                unreadCount = it.unreadCount,
                cursor = it.cursor,
            )
        }
    }

    private fun getUniquesUidsInReverse(folder: Folder, remoteUids: List<String>): List<String> {
        val localUids = folder.id.let(ThreadController::getThreads).find().map { it.uid.toShortUid() }
        val uniqueUids = remoteUids - localUids.intersect(remoteUids.toSet())
        return uniqueUids.reversed()
    }

    private data class MessagesUids(
        var addedShortUids: List<String> = emptyList(),
        var deletedUids: List<String> = emptyList(),
        var updatedMessages: List<MessageFlags> = emptyList(),
        var unreadCount: Int = 0,
        var cursor: String,
    )
    //endregion
}
