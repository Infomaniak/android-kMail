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
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.getMessages.GetMessagesUidsDeltaResult.MessageFlags
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.copyListToRealm
import com.infomaniak.mail.utils.toRealmInstant
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.toRealmList
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
    suspend fun fetchMessages(mailboxUuid: String, folderId: String) {

        val folder = FolderController.getFolder(folderId) ?: return
        val previousCursor = folder.cursor

        val messagesUids = if (previousCursor == null) {
            getMessagesUids(mailboxUuid, folderId)
        } else {
            getMessagesUidsDelta(mailboxUuid, folderId, previousCursor)
        } ?: return

        with(messagesUids) {

            Log.e("API", "Threads - Added   : ${addedShortUids.count()}")
            Log.e("API", "Threads - Deleted : ${deletedUids.count()}")
            Log.e("API", "Threads - Updated : ${updatedMessages.count()}")

            addMessages(addedShortUids, folder, mailboxUuid)

            RealmDatabase.mailboxContent().writeBlocking {

                deleteMessages(deletedUids, realm = this)
                updateMessages(updatedMessages, mailboxUuid, folderId, realm = this)

                FolderController.updateFolder(folderId, realm = this) {
                    it.lastUpdatedAt = Date().toRealmInstant()
                    it.cursor = cursor
                }
            }
        }

        // TODO: Do we still need this with the new API routes?
        // val isDraftFolder = FolderController.getFolder(folderId, realm = this)?.role == FolderRole.DRAFT
        // if (isDraftFolder) DraftController.cleanOrphans(threadsResult.threads, realm = this)
    }

    private suspend fun addMessages(shortUids: List<String>, folder: Folder, mailboxUuid: String) {
        if (shortUids.isNotEmpty()) {
            val uids = getUniquesUidsInReverse(folder, shortUids)
            val pageSize = ApiRepository.PER_PAGE
            // val pageSize = 200 // TODO: Magic number
            var pageStart = 0
            while (pageStart < uids.count()) {
                val pageEnd = min(pageStart + pageSize, uids.count())
                val page = uids.subList(pageStart, pageEnd)
                ApiRepository.getMessagesByUids(mailboxUuid, folder.id, page).data?.messages?.let { messages ->
                    FolderController.updateFolder(folder.id) { folder ->
                        folder.threads += messages.map { it.toThread(mailboxUuid, folder.id) }.toRealmList()
                    }
                }

                pageStart += pageSize
            }
        }
    }

    private fun deleteMessages(uids: List<String>, realm: MutableRealm) {
        if (uids.isNotEmpty()) {
            realm.deleteMessages(getMessages(uids, realm))
            realm.delete(ThreadController.getThreads(uids, realm))
        }
    }

    private fun updateMessages(messageFlags: List<MessageFlags>, mailboxUuid: String, folderId: String, realm: MutableRealm) {
        messageFlags.forEach { flags ->

            val uid = flags.shortUid.toLongUid(folderId)
            getMessage(uid, realm)?.let { message ->

                message.apply {
                    answered = flags.answered
                    isFavorite = flags.isFavorite
                    forwarded = flags.forwarded
                    scheduled = flags.scheduled
                    seen = flags.seen
                }

                ThreadController.upsertThread(message.toThread(mailboxUuid, folderId), realm)
            }
        }
    }

    private fun threeMonthsAgo(): String = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date().monthsAgo(3))

    private fun String.toLongUid(folderId: String) = "${this}@${folderId}"

    private fun String.toShortUid(): String = substringBefore('@')

    private fun getMessagesUids(mailboxUuid: String, folderId: String): MessagesUids? {
        return with(ApiRepository.getMessagesUids(mailboxUuid, folderId, threeMonthsAgo())) {
            if (isSuccess()) with(data!!) {
                MessagesUids(
                    addedShortUids = addedShortUids,
                    cursor = cursor,
                )
            } else {
                null
            }
        }
    }

    private fun getMessagesUidsDelta(mailboxUuid: String, folderId: String, previousCursor: String): MessagesUids? {
        return with(ApiRepository.getMessagesUidsDelta(mailboxUuid, folderId, previousCursor)) {
            if (isSuccess()) with(data!!) {
                MessagesUids(
                    addedShortUids = addedShortUids,
                    deletedUids = deletedShortUids.map { it.toLongUid(folderId) },
                    updatedMessages = updatedMessages,
                    cursor = cursor,
                )
            } else {
                null
            }
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
