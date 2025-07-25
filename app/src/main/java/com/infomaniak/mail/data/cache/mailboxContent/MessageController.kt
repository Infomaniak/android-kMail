/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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

import android.content.Context
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Body
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.LocalStorageUtils.deleteDraftUploadDir
import com.infomaniak.mail.utils.extensions.getStartAndEndOfPlusEmail
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmScalarQuery
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MessageController @Inject constructor(
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val localSettings: LocalSettings,
) {

    //region Queries
    private fun getSortedAndNotDeletedMessagesQuery(
        threadUid: String,
        featureFlags: Mailbox.FeatureFlagSet?,
    ): RealmQuery<Message>? {
        return ThreadController.getThread(threadUid, mailboxContentRealm())
            ?.getDisplayedMessages(featureFlags, localSettings)
            ?.query("${Message::isDeletedOnApi.name} == false")
            ?.sort(Message::internalDate.name, Sort.ASCENDING)
    }
    //endregion

    //region Get data
    fun getMessage(uid: String): Message? {
        return getMessage(uid, mailboxContentRealm())
    }

    fun getLastMessageToExecuteAction(thread: Thread, featureFlags: Mailbox.FeatureFlagSet?): Message {
        fun RealmQuery<Message>.last(): Message? = sort(Message::internalDate.name, Sort.DESCENDING).first().find()

        val isNotScheduledDraft = "${Message::isScheduledDraft.name} == false"

        val isNotFromRealMe = "SUBQUERY(${Message::from.name}, \$recipient, " +
                "\$recipient.${Recipient::email.name} != '${AccountUtils.currentMailboxEmail}').@count > 0"

        val (start, end) = AccountUtils.currentMailboxEmail.getStartAndEndOfPlusEmail()
        val isNotFromPlusMe = "SUBQUERY(${Message::from.name}, \$recipient," +
                " \$recipient.${Recipient::email.name} BEGINSWITH '${start}'" +
                " AND \$recipient.${Recipient::email.name} ENDSWITH '${end}'" +
                ").@count < 1"

        val messages = thread.getDisplayedMessages(featureFlags, localSettings)
        return messages.query("$isNotDraft AND $isNotScheduledDraft AND $isNotFromRealMe AND $isNotFromPlusMe").last()
            ?: messages.query("$isNotDraft AND $isNotScheduledDraft").last()
            ?: messages.query(isNotScheduledDraft).last()
            ?: messages.last()
    }

    fun getLastMessageAndItsDuplicatesToExecuteAction(thread: Thread, featureFlags: Mailbox.FeatureFlagSet?): List<Message> {
        return getMessageAndDuplicates(
            thread = thread,
            message = getLastMessageToExecuteAction(thread, featureFlags),
        )
    }

    fun getUnseenMessages(thread: Thread): List<Message> {
        return getMessagesAndDuplicates(thread, "${Message::isSeen.name} == false")
    }

    fun getFavoriteMessages(thread: Thread): List<Message> {
        return getMessagesAndTheirDuplicates(thread, "${Message::isFavorite.name} == true")
    }

    fun getMovableMessages(thread: Thread): List<Message> {
        val byFolderId = "${Message::folderId.name} == '${thread.folderId}'"
        return getMessagesAndTheirDuplicates(thread, "$byFolderId AND $isNotScheduledMessage")
    }

    fun getUnscheduledMessages(thread: Thread): List<Message> {
        return getMessagesAndTheirDuplicates(thread, isNotScheduledMessage)
    }

    private fun getMessagesAndTheirDuplicates(thread: Thread, query: String): List<Message> {
        val messages = thread.messages.query(query).find()
        val duplicates = thread.duplicates.query("${Message::messageId.name} IN $0", messages.map { it.messageId }).find()
        return messages + duplicates
    }

    private fun getMessagesAndDuplicates(thread: Thread, query: String): List<Message> {
        val messages = thread.messages.query(query).find()
        val duplicates = thread.duplicates.query(query).find()
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
                    ThreadFilter.ATTACHMENTS -> add("${Message::hasAttachable.name} == true")
                    else -> Unit
                }
            }
        }

        val filtersQuery = queriesList.joinToString(separator = " AND ")

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

    fun getSortedAndNotDeletedMessagesAsync(
        threadUid: String,
        featureFlags: Mailbox.FeatureFlagSet?,
    ): Flow<ResultsChange<Message>>? {
        return getSortedAndNotDeletedMessagesQuery(threadUid, featureFlags)?.asFlow()
    }

    fun getMessageAsync(messageUid: String): Flow<SingleQueryChange<Message>> {
        return getMessagesQuery(messageUid, mailboxContentRealm()).first().asFlow()
    }

    fun getMessagesCountInThread(threadUid: String, featureFlags: Mailbox.FeatureFlagSet?, realm: Realm): Int? {
        return ThreadController.getThread(threadUid, realm)?.getDisplayedMessages(featureFlags, localSettings)?.count()
    }
    //endregion

    companion object {
        private val isNotDraft = "${Message::isDraft.name} == false"
        private val isNotScheduledMessage = "${Message::isScheduledMessage.name} == false"

        //region Queries
        private fun getMessagesQuery(messageUid: String, realm: TypedRealm): RealmQuery<Message> {
            return realm.query<Message>("${Message::uid.name} == $0", messageUid)
        }

        private fun getMessageQuery(uid: String, realm: TypedRealm): RealmSingleQuery<Message> {
            return getMessagesQuery(uid, realm).first()
        }

        private fun getMessagesByFolderIdQuery(folderId: String, realm: TypedRealm): RealmQuery<Message> {
            return realm.query<Message>("${Message::folderId.name} == '$folderId'")
        }

        private fun doesMessageExistQuery(uid: String, realm: TypedRealm): RealmScalarQuery<Long> {
            return realm.query<Message>("${Message::uid.name} == $0", uid).count()
        }
        //endregion

        //region Get data
        fun getMessage(uid: String, realm: TypedRealm): Message? {
            return getMessageQuery(uid, realm).find()
        }

        fun getMessagesByUids(messagesUids: List<String>, realm: MutableRealm): List<Message> {
            return realm.query<Message>("${Message::uid.name} IN $0", messagesUids).find()
        }

        fun getMessagesByFolderId(folderId: String, realm: TypedRealm): List<Message> {
            return getMessagesByFolderIdQuery(folderId, realm).find()
        }

        fun getThreadLastMessageInFolder(threadUid: String, realm: TypedRealm): Message? {
            val thread = ThreadController.getThread(threadUid, realm)
            return thread?.messages?.query("${Message::folderId.name} == $0", thread.folderId)?.find()?.lastOrNull()
        }

        fun doesMessageExist(uid: String, realm: TypedRealm): Boolean {
            return doesMessageExistQuery(uid, realm).find() > 0
        }
        //endregion

        //region Edit data
        fun upsertMessage(message: Message, realm: MutableRealm): Message = realm.copyToRealm(message, UpdatePolicy.ALL)

        fun updateMessage(messageUid: String, realm: MutableRealm, onUpdate: (Message?) -> Unit) {
            onUpdate(getMessage(messageUid, realm))
        }

        fun deleteMessage(context: Context, mailbox: Mailbox, message: Message, realm: MutableRealm) {

            DraftController.getDraftByMessageUid(message.uid, realm)?.let { draft ->
                if (draft.action == null) {
                    deleteDraftUploadDir(
                        context = context,
                        draftLocalUuid = draft.localUuid,
                        userId = mailbox.userId,
                        mailboxId = mailbox.mailboxId,
                        mustForceDelete = true,
                    )
                    realm.delete(draft)
                } else {
                    draft.remoteUuid = null
                }
            }

            realm.delete(message)
        }

        fun deleteMessageByUid(uid: String, realm: MutableRealm) {
            val message = getMessage(uid, realm) ?: return
            realm.delete(message)
        }

        fun deleteMessages(context: Context, mailbox: Mailbox, messages: List<Message>, realm: MutableRealm) {
            /**
             * This list is reversed because we'll delete items while looping over it.
             * Doing so for managed Realm objects will lively update the list we're iterating through, making us skip the next item.
             * Looping in reverse enables us to not skip any item.
             */
            messages.asReversed().forEach { message ->
                deleteMessage(context, mailbox, message, realm)
            }
        }

        fun deleteSearchMessages(realm: MutableRealm) = with(realm) {
            delete(query<Message>("${Message::isFromSearch.name} == true").find())
        }

        fun updateFavoriteStatus(messageUids: List<String>, isFavorite: Boolean, realm: MutableRealm) {
            getMessagesByUids(messageUids, realm).forEach {
                it.isFavorite = isFavorite
            }
        }

        fun updateSeenStatus(messageUids: List<String>, isSeen: Boolean, realm: MutableRealm) {
            getMessagesByUids(messageUids, realm).forEach {
                it.isSeen = isSeen
            }
        }
        //endregion
    }
}
