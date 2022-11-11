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
import com.infomaniak.mail.data.models.MessageFlags
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.copyListToRealm
import com.infomaniak.mail.utils.toRealmInstant
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmSingleQuery
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

object MessageController {

    //region Queries
    private fun MutableRealm?.getMessagesQuery(uids: List<String>): RealmQuery<Message> {
        val messages = "${Message::uid.name} IN {${uids.joinToString { "\"$it\"" }}}"
        return (this ?: RealmDatabase.mailboxContent()).query(messages)
    }

    private fun getMessageQuery(uid: String, realm: TypedRealm? = null): RealmSingleQuery<Message> {
        return (realm ?: RealmDatabase.mailboxContent()).query<Message>("${Message::uid.name} = '$uid'").first()
    }
    //endregion

    //region Get data
    fun getMessages(uids: List<String>, realm: MutableRealm? = null): RealmQuery<Message> {
        return realm.getMessagesQuery(uids)
    }

    fun getMessage(uid: String, realm: TypedRealm? = null): Message? {
        return getMessageQuery(uid, realm).find()
    }
    //endregion

    //region Edit data
    fun updateMessage(uid: String, realm: MutableRealm? = null, onUpdate: (message: Message) -> Unit) {
        val block: (MutableRealm) -> Unit = { getMessage(uid, it)?.let(onUpdate) }
        realm?.let(block) ?: RealmDatabase.mailboxContent().writeBlocking(block)
    }

    fun MutableRealm.update(localMessages: List<Message>, apiMessages: List<Message>) {

        Log.d(RealmDatabase.TAG, "Messages: Delete outdated data")
        deleteMessages(getOutdatedMessages(localMessages, apiMessages))

        Log.d(RealmDatabase.TAG, "Messages: Save new data")
        copyListToRealm(apiMessages, alsoCopyManagedItems = false)
    }

    // TODO: Replace this with a Realm query (blocked by https://github.com/realm/realm-kotlin/issues/591)
    private fun getOutdatedMessages(localMessages: List<Message>, apiMessages: List<Message>): List<Message> {
        return localMessages.filter { localMessage ->
            apiMessages.none { apiMessage -> apiMessage.uid == localMessage.uid }
        }
    }

    fun MutableRealm.deleteMessages(messages: List<Message>) {
        messages.reversed().forEach { deleteMessage(it.uid, this) }
    }

    fun deleteMessage(uid: String, realm: MutableRealm? = null) {
        val block: (MutableRealm) -> Unit = {
            getMessage(uid, it)
                ?.let { message ->
                    DraftController.getDraftByMessageUid(message.uid, it)?.let(it::delete)
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
        }

        with(messagesUids) {

            addMessages(addedShortUids, folder, mailboxUuid)

            RealmDatabase.mailboxContent().writeBlocking {

                deleteMessages(deletedUids, realm = this)
                updateMessages(updatedMessages, mailboxUuid, folderId, realm = this)

                if (cursor != null) FolderController.updateFolder(folderId, this) {
                    it.lastUpdatedAt = Date().toRealmInstant()
                    it.cursor = cursor
                }
            }
        }

        // TODO: Do we still need this with the new API routes?
        // val isDraftFolder = FolderController.getFolder(folderId, this)?.role == FolderRole.DRAFT
        // if (isDraftFolder) DraftController.cleanOrphans(threadsResult.threads, this)
    }

    private suspend fun addMessages(addedShortUids: List<String>, folder: Folder, mailboxUuid: String) {
        if (addedShortUids.isNotEmpty()) {
            val reversedUids = getUniquesUidsInReverse(folder, addedShortUids)
            val pageSize = ApiRepository.PER_PAGE
            // val pageSize = 200 // TODO: Magic number
            var offset = ApiRepository.OFFSET_FIRST_PAGE
            while (offset < reversedUids.count()) {
                val end = min(offset + pageSize, reversedUids.count())
                val newList = reversedUids.subList(offset, end)
                ApiRepository.getMessagesByUids(mailboxUuid, folder.id, newList).data?.messages?.let { messages ->
                    FolderController.updateFolder(folder.id) { folder ->
                        folder.threads += messages.map { it.toThread(mailboxUuid) }.toRealmList()
                        Log.e("TOTO", "Threads: ${folder.threads.count()}")
                    }
                }
                // TODO: Do we want a delay between each call, to not get blocked by the API?
                delay(500L)
                offset += pageSize
            }
        }
    }

    private fun deleteMessages(deletedUids: List<String>, realm: MutableRealm) {
        if (deletedUids.isNotEmpty()) {
            realm.delete(getMessages(deletedUids, realm))
            realm.delete(ThreadController.getThreads(deletedUids, realm))
        }
    }

    private fun updateMessages(updatedMessages: List<MessageFlags>, mailboxUuid: String, folderId: String, realm: MutableRealm) {
        updatedMessages.forEach {
            val uid = it.shortUid.toLongUid(folderId)
            updateMessage(uid, realm) { message ->
                message.answered = it.answered
                message.isFavorite = it.isFavorite
                message.forwarded = it.forwarded
                message.scheduled = it.scheduled
                message.seen = it.seen

                ThreadController.upsertThread(message.toThread(mailboxUuid), realm)
            }
        }
    }

    private fun threeMonthsAgo(): String = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date().monthsAgo(3))

    private fun String.toLongUid(folderId: String) = "${this}@${folderId}"

    private fun String.toShortUid(): String = substringBefore('@')

    private fun getMessagesUids(mailboxUuid: String, folderId: String): MessagesUids {
        return MessagesUids().apply {
            with(ApiRepository.getMessagesUids(mailboxUuid, folderId, threeMonthsAgo())) {
                if (isSuccess()) with(data!!) {
                    this@apply.addedShortUids = addedShortUids
                    this@apply.cursor = cursor
                }
            }
        }
    }

    private fun getMessagesUidsDelta(mailboxUuid: String, folderId: String, previousCursor: String): MessagesUids {
        return MessagesUids().apply {
            with(ApiRepository.getMessagesUidsDelta(mailboxUuid, folderId, previousCursor)) {
                if (isSuccess()) with(data!!) {
                    this@apply.addedShortUids = addedShortUids
                    this@apply.deletedUids = deletedShortUids.map { it.toLongUid(folderId) }
                    this@apply.updatedMessages = updatedMessages
                    this@apply.cursor = cursor
                }
            }
        }
    }

    private fun getUniquesUidsInReverse(folder: Folder?, remoteUids: List<String>): List<String> {
        val localUids = folder?.threads?.map { it.uid.toShortUid() }
        val uniqueUids = if (localUids == null) {
            remoteUids
        } else {
            remoteUids - localUids.intersect(remoteUids.toSet())
        }
        return uniqueUids.reversed()
    }

    private data class MessagesUids(
        var addedShortUids: List<String> = emptyList(),
        var deletedUids: List<String> = emptyList(),
        var updatedMessages: List<MessageFlags> = emptyList(),
        var cursor: String? = null,
    )
    //endregion
}
