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

import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.message.Body
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.utils.AccountUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.query.Sort

object MessageController {

    private inline val defaultRealm get() = RealmDatabase.mailboxContent()

    private val isNotDraft = "${Message::isDraft.name} == false"
    private val isNotScheduled = "${Message::isScheduled.name} == false"

    //region Queries
    private fun getOldestMessageQuery(folderId: String, realm: TypedRealm): RealmSingleQuery<Message> {
        val byFolderId = "${Message::folderId.name} == '$folderId'"
        return realm.query<Message>(byFolderId).sort(Message::shortUid.name).first()
    }

    private fun getMessageQuery(uid: String, realm: TypedRealm): RealmSingleQuery<Message> {
        val byUid = "${Message::uid.name} == '$uid'"
        return realm.query<Message>(byUid).first()
    }
    //endregion

    //region Get data
    fun getOldestMessage(folderId: String, realm: TypedRealm = defaultRealm): Message? {
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

    fun MutableRealm.deleteMessage(message: Message) {

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
}
