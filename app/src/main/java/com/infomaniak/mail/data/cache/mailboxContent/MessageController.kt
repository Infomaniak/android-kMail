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
import com.infomaniak.mail.utils.FeatureAvailability
import com.infomaniak.mail.utils.LocalStorageUtils.deleteDraftUploadDir
import com.infomaniak.mail.utils.extensions.findSuspend
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
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.RealmList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.invoke
import javax.inject.Inject

class MessageController @Inject constructor(
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val localSettings: LocalSettings,
) {

    //region Queries
    private suspend fun getSortedAndNotDeletedMessagesQuery(
        threadUid: String,
        featureFlags: Mailbox.FeatureFlagSet?,
    ): RealmQuery<Message>? {
        return ThreadController.getThread(threadUid, mailboxContentRealm())
            ?.getDisplayedMessages(featureFlags, localSettings)
            ?.query("${Message::isDeletedOnApi.name} == false")
            ?.sort(Message::internalDate.name, Sort.ASCENDING)
    }
    //endregion

    suspend fun getMessage(uid: String): Message? {
        return getMessage(uid, mailboxContentRealm())
    }

    suspend fun getMessages(uids: List<String>): List<Message> {
        return getMessagesByUids(uids, mailboxContentRealm())
    }

    suspend fun getLastMessageToExecuteAction(thread: Thread, featureFlags: Mailbox.FeatureFlagSet?): Message {
        val messages = thread.getDisplayedMessages(featureFlags, this@MessageController.localSettings)
        return getLastMessageToExecuteActionWithExtraQuery(messages = messages) ?: messages.last()
    }

    suspend fun getLastMessageToExecuteReaction(thread: Thread, featureFlags: Mailbox.FeatureFlagSet?): Message? {
        val canBeReactedTo = "${Message::_emojiReactionNotAllowedReason.name} == null"

        val messages = thread.getDisplayedMessages(featureFlags, this@MessageController.localSettings)
        return getLastMessageToExecuteActionWithExtraQuery(messages, extraQuery = canBeReactedTo)
    }

    suspend fun getLastMessageAndItsDuplicatesToExecuteAction(
        thread: Thread,
        featureFlags: Mailbox.FeatureFlagSet?
    ): List<Message> {
        return getMessageAndDuplicates(
            thread = thread,
            message = getLastMessageToExecuteAction(thread, featureFlags),
        )
    }

    suspend fun getUnseenMessages(thread: Thread): List<Message> {
        return getMessagesAndDuplicates(thread, "${Message::isSeen.name} == false")
    }

    suspend fun getFavoriteMessages(thread: Thread): List<Message> {
        return getMessagesFromThread(thread, "${Message::isFavorite.name} == true", includeDuplicates = true)
    }

    suspend fun getMovableMessages(thread: Thread): List<Message> {
        val byFolderId = "${Message::folderId.name} == '${thread.folderId}'"
        return getMessagesFromThread(thread, "$byFolderId AND $isNotScheduledMessage", includeDuplicates = false)
    }

    suspend fun getUnscheduledMessagesFromThread(thread: Thread, includeDuplicates: Boolean): List<Message> {
        return getMessagesFromThread(thread, isNotScheduledMessage, includeDuplicates)
    }

    fun getUnscheduledMessages(messages: List<Message>): List<Message> {
        return messages.filter { message -> !message.isScheduledMessage }
    }

    private suspend fun getMessagesFromThread(thread: Thread, query: String, includeDuplicates: Boolean): List<Message> {
        val messages = thread.messages.query(query).findSuspend()
        if (includeDuplicates) {
            val duplicates = thread.duplicates.query(
                "${Message::messageId.name} IN $0",
                messages.map { it.messageId },
            ).findSuspend()
            return messages + duplicates
        }
        return messages
    }

    private suspend fun getMessagesAndDuplicates(thread: Thread, query: String): List<Message> = coroutineScope {
        val messagesAsync = async { thread.messages.query(query).findSuspend() }
        val duplicatesAsync = async { thread.duplicates.query(query).findSuspend() }
        messagesAsync.await() + duplicatesAsync.await()
    }

    suspend fun getMessagesAndDuplicates(messages: List<Message>): List<Message> {
        return messages.flatMap { message ->
            getMessageAndDuplicates(message.threads.first(), message)
        }
    }

    suspend fun getMessageAndDuplicates(thread: Thread, message: Message): List<Message> {
        return listOf(message) + thread.duplicates.query("${Message::messageId.name} == $0", message.messageId).findSuspend()
    }

    suspend fun searchMessages(
        searchQuery: String?,
        filters: Set<ThreadFilter>,
        folderId: String?,
        featureFlags: Mailbox.FeatureFlagSet?,
        localSettings: LocalSettings
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
            val featureFlag = if (FeatureAvailability.isReactionsAvailable(featureFlags, localSettings)) {
                ""
            } else {
                " AND ${Message::emojiReaction.name} == nil"
            }

            query<Message>(
                query = "$beginQuery ($containsSubject OR $containsPreview OR $containsBody) ${featureFlag}",
                searchQuery
            )
        } else {
            query<Message>(filtersQuery)
        }.findSuspend().let { Dispatchers.IO { it.copyFromRealm() } }
    }

    fun getSortedAndNotDeletedMessagesAsync(
        threadUid: String,
        featureFlags: Mailbox.FeatureFlagSet?,
    ): Flow<ResultsChange<Message>> = flow {
        val query = getSortedAndNotDeletedMessagesQuery(threadUid, featureFlags)
        emitAll(query?.asFlow() ?: emptyFlow())
    }

    fun getMessageAsync(messageUid: String): Flow<SingleQueryChange<Message>> {
        return getMessagesQuery(messageUid, mailboxContentRealm()).first().asFlow()
    }

    suspend fun getMessagesCountInThread(threadUid: String, featureFlags: Mailbox.FeatureFlagSet?, realm: Realm): Int? {
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

        private fun getMessagesByFolderIdQuery(folderId: String, realm: TypedRealm, sort: Sort?): RealmQuery<Message> {

            val query = realm.query<Message>("${Message::folderId.name} == '$folderId'")

            return if (sort == null) query else query.sort(Message::shortUid.name, sort)
        }

        private suspend fun getLastMessageToExecuteActionWithExtraQuery(
            messages: RealmList<Message>,
            extraQuery: String? = null
        ): Message? {
            suspend fun RealmQuery<Message>.last(): Message? {
                return sort(Message::internalDate.name, Sort.DESCENDING).first().findSuspend()
            }

            val appendableExtraQuery = extraQuery
                ?.takeIf { it.isNotEmpty() }
                ?.let { " AND $it" }
                ?: ""

            val isNotScheduledDraft = "${Message::isScheduledDraft.name} == false"

            val isNotFromRealMe = "SUBQUERY(${Message::from.name}, \$recipient, " +
                    "\$recipient.${Recipient::email.name} != '${AccountUtils.currentMailboxEmail}').@count > 0"

            val (start, end) = AccountUtils.currentMailboxEmail.getStartAndEndOfPlusEmail()
            val isNotFromPlusMe = "SUBQUERY(${Message::from.name}, \$recipient," +
                    " \$recipient.${Recipient::email.name} BEGINSWITH '${start}'" +
                    " AND \$recipient.${Recipient::email.name} ENDSWITH '${end}'" +
                    ").@count < 1"

            return messages.query("$isNotDraft AND $isNotScheduledDraft AND $isNotFromRealMe AND $isNotFromPlusMe $appendableExtraQuery")
                .last()
                ?: messages.query("$isNotDraft AND $isNotScheduledDraft $appendableExtraQuery").last()
                ?: messages.query(isNotScheduledDraft + appendableExtraQuery).last()
                ?: extraQuery
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { messages.query(it).last() }
        }
        //endregion

        //region Get data
        fun getMessageBlocking(uid: String, realm: TypedRealm): Message? {
            return getMessageQuery(uid, realm).find()
        }

        suspend fun getMessage(uid: String, realm: TypedRealm): Message? {
            return getMessageQuery(uid, realm).findSuspend()
        }

        suspend fun getMessagesByUids(messagesUids: List<String>, realm: Realm): List<Message> {
            return realm.query<Message>("${Message::uid.name} IN $0", messagesUids).findSuspend()
        }

        fun getMessagesByUidsBlocking(messagesUids: List<String>, realm: MutableRealm): List<Message> {
            return realm.query<Message>("${Message::uid.name} IN $0", messagesUids).find()
        }

        fun getMessagesByFolderIdBlocking(folderId: String, realm: TypedRealm, sort: Sort? = null): List<Message> {
            return getMessagesByFolderIdQuery(folderId, realm, sort).find()
        }

        suspend fun getThreadLastMessageInFolder(threadUid: String, realm: TypedRealm): Message? {
            val thread = ThreadController.getThread(threadUid, realm)
            return thread?.messages?.query("${Message::folderId.name} == $0", thread.folderId)?.findSuspend()?.lastOrNull()
        }

        suspend fun doesMessageExist(uid: String, realm: TypedRealm): Boolean {
            return getMessagesQuery(uid, realm).count().findSuspend() > 0
        }
        //endregion

        //region Edit data
        fun upsertMessageBlocking(message: Message, realm: MutableRealm): Message = realm.copyToRealm(message, UpdatePolicy.ALL)

        fun updateMessageBlocking(messageUid: String, realm: MutableRealm, onUpdate: (Message?) -> Unit) {
            onUpdate(getMessageBlocking(messageUid, realm))
        }

        fun deleteMessageBlocking(context: Context, mailbox: Mailbox, message: Message, realm: MutableRealm) {

            DraftController.getDraftByMessageUidBlocking(message.uid, realm)?.let { draft ->
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

        fun deleteMessageByUidBlocking(uid: String, realm: MutableRealm) {
            val message = getMessageBlocking(uid, realm) ?: return
            realm.delete(message)
        }

        fun deleteMessages(context: Context, mailbox: Mailbox, messages: List<Message>, realm: MutableRealm) {
            /**
             * This list is reversed because we'll delete items while looping over it.
             * Doing so for managed Realm objects will lively update the list we're iterating through, making us skip the next item.
             * Looping in reverse enables us to not skip any item.
             */
            messages.asReversed().forEach { message ->
                deleteMessageBlocking(context, mailbox, message, realm)
            }
        }

        fun deleteSearchMessages(realm: MutableRealm) = with(realm) {
            delete(query<Message>("${Message::isFromSearch.name} == true").find())
        }

        fun updateFavoriteStatus(messageUids: List<String>, isFavorite: Boolean, realm: MutableRealm) {
            getMessagesByUidsBlocking(messageUids, realm).forEach {
                it.isFavorite = isFavorite
            }
        }

        fun updateSeenStatus(messageUids: List<String>, isSeen: Boolean, realm: MutableRealm) {
            getMessagesByUidsBlocking(messageUids, realm).forEach {
                it.isSeen = isSeen
            }
        }
        //endregion
    }
}
