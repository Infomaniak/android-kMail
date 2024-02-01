/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.query.Sort
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MessageController @Inject constructor(private val mailboxContentRealm: RealmDatabase.MailboxContent) {

    //region Queries
    private fun getSortedAndNotDeletedMessagesQuery(threadUid: String): RealmQuery<Message>? {
        return ThreadController.getThread(threadUid, mailboxContentRealm())
            ?.messages?.query("${Message::isDeletedOnApi.name} == false")
            ?.sort(Message::date.name, Sort.ASCENDING)
    }

    //endregion

    //region Get data
    fun getMessage(uid: String): Message? {
        return getMessage(uid, mailboxContentRealm())
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

        val duplicates = thread.duplicates.query("${Message::messageId.name} IN $0", messages.map { it.messageId }).find()

        return messages + duplicates
    }

    fun getMessageAndDuplicates(thread: Thread, message: Message): List<Message> {
        return listOf(message) + thread.duplicates.query("${Message::messageId.name} == $0", message.messageId).find()
    }

    fun searchMessages(
        searchQuery: String?,
        filters: Set<ThreadFilter>,
        folderId: String?,
    ): List<Message> = with(mailboxContentRealm()) {
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

        val filtersQuery = queriesList.joinToString(" AND ") { it }

        return if (searchQuery?.isNotBlank() == true) {
            val containsSubject = "${Message::subject.name} CONTAINS[c] $0"
            val containsPreview = "${Message::preview.name} CONTAINS[c] $0"
            val containsBody = "${Message::body.name}.${Body::value.name} CONTAINS[c] $0"
            val beginQuery = if (filtersQuery.isNotBlank()) "$filtersQuery AND " else ""

            query<Message>("$beginQuery ($containsSubject OR $containsPreview OR $containsBody)", searchQuery)
        } else {
            query<Message>(filtersQuery)
        }.find().copyFromRealm()
    }

    fun getSortedAndNotDeletedMessagesAsync(threadUid: String): Flow<ResultsChange<Message>>? {
        return getSortedAndNotDeletedMessagesQuery(threadUid)?.asFlow()
    }
    //endregion

    //region Edit data
    fun deleteSearchMessages(realm: MutableRealm) = with(realm) {
        delete(query<Message>("${Message::isFromSearch.name} == true").find())
    }
    //endregion

    companion object {
        private val isNotDraft = "${Message::isDraft.name} == false"
        private val isNotScheduled = "${Message::isScheduled.name} == false"

        //region Queries
        private fun getOldestOrNewestMessagesQuery(
            folderId: String,
            sort: Sort,
            realm: TypedRealm,
            fibonacci: Int = 1,
        ): RealmQuery<Message> {

            val byFolderId = "${Message::folderId.name} == $0"
            val isNotFromSearch = "${Message::isFromSearch.name} == false"

            return realm.query<Message>("$byFolderId AND $isNotFromSearch", folderId)
                .sort(Message::shortUid.name, sort)
                .limit(fibonacci)
        }

        private fun getMessageQuery(uid: String, realm: TypedRealm): RealmSingleQuery<Message> {
            return realm.query<Message>("${Message::uid.name} == $0", uid).first()
        }
        //endregion

        //region Get data
        fun getMessage(uid: String, realm: TypedRealm): Message? {
            return getMessageQuery(uid, realm).find()
        }

        fun getThreadLastMessageInFolder(threadUid: String, realm: TypedRealm): Message? {
            val thread = ThreadController.getThread(threadUid, realm)
            return thread?.messages?.query("${Message::folderId.name} == $0", thread.folderId)?.find()?.lastOrNull()
        }

        fun getOldestMessage(folderId: String, realm: TypedRealm): Message? {
            return getOldestOrNewestMessagesQuery(folderId, Sort.ASCENDING, realm).first().find()
        }

        fun getNewestMessage(folderId: String, fibonacci: Int, realm: TypedRealm, endOfMessagesReached: () -> Unit): Message? {
            return getOldestOrNewestMessagesQuery(folderId, Sort.DESCENDING, realm, fibonacci)
                .find()
                .also { if (it.count() < fibonacci) endOfMessagesReached() }
                .lastOrNull()
        }
        //endregion

        //region Edit data
        fun upsertMessage(message: Message, realm: MutableRealm) {
            realm.copyToRealm(message, UpdatePolicy.ALL)
        }

        fun updateMessage(messageUid: String, realm: MutableRealm, onUpdate: (Message?) -> Unit) {
            onUpdate(getMessage(messageUid, realm))
        }

        fun deleteMessage(message: Message, realm: MutableRealm) {

            DraftController.getDraftByMessageUid(message.uid, realm)?.let { draft ->
                if (draft.action == null) {
                    realm.delete(draft)
                } else {
                    draft.remoteUuid = null
                }
            }

            realm.delete(message)
        }

        fun deleteMessages(messages: List<Message>, realm: MutableRealm) {
            messages.reversed().forEach { message ->
                deleteMessage(message, realm)
            }
        }
        //endregion
    }
}
