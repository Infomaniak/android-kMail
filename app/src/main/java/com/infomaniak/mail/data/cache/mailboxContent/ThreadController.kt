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

import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.utils.getLastMessageToExecuteAction
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.*
import kotlinx.coroutines.flow.Flow

object ThreadController {

    //region Queries
    private fun getThreadsQuery(realm: TypedRealm? = null): RealmQuery<Thread> {
        return (realm ?: RealmDatabase.mailboxContent()).query()
    }

    private fun getThreadsQuery(uids: List<String>, realm: TypedRealm? = null): RealmQuery<Thread> {
        val byUids = "${Thread::uid.name} IN {${uids.joinToString { "\"$it\"" }}}"
        return (realm ?: RealmDatabase.mailboxContent()).query(byUids)
    }

    private fun getThreadsQuery(messageIds: Set<String>, realm: TypedRealm? = null): RealmQuery<Thread> {
        val byMessagesIds = "ANY ${Thread::messagesIds.name} IN {${messageIds.joinToString { "\"$it\"" }}}"
        return (realm ?: RealmDatabase.mailboxContent()).query(byMessagesIds)
    }

    private fun getUnreadThreadsCountQuery(folderId: String, realm: TypedRealm? = null): RealmScalarQuery<Long> {
        val byFolderId = "${Thread::folderId.name} == '$folderId'"
        val unseen = "${Thread::unseenMessagesCount.name} > 0"
        val query = "$byFolderId AND $unseen"
        return (realm ?: RealmDatabase.mailboxContent()).query<Thread>(query).count()
    }

    private fun getThreadsQuery(
        folderId: String,
        filter: ThreadFilter = ThreadFilter.ALL,
        realm: TypedRealm? = null
    ): RealmQuery<Thread> {

        val byFolderId = "${Thread::folderId.name} == '$folderId'"
        val query = (realm ?: RealmDatabase.mailboxContent())
            .query<Thread>(byFolderId)
            .sort(Thread::date.name, Sort.DESCENDING)

        return if (filter == ThreadFilter.ALL) {
            query
        } else {
            val withFilter = when (filter) {
                ThreadFilter.SEEN -> "${Thread::unseenMessagesCount.name} == 0"
                ThreadFilter.UNSEEN -> "${Thread::unseenMessagesCount.name} > 0"
                ThreadFilter.STARRED -> "${Thread::isFavorite.name} == true"
                ThreadFilter.ATTACHMENTS -> "${Thread::hasAttachments.name} == true"
                ThreadFilter.FOLDER -> TODO()
                else -> throw IllegalStateException("`${ThreadFilter::class.simpleName}` cannot be `${ThreadFilter.ALL.name}` here.")
            }
            query.query(withFilter)
        }
    }

    private fun getThreadQuery(uid: String, realm: TypedRealm? = null): RealmSingleQuery<Thread> {
        return (realm ?: RealmDatabase.mailboxContent()).query<Thread>("${Thread::uid.name} == '$uid'").first()
    }
    //endregion

    //region Get data
    private fun getThreads(realm: TypedRealm? = null): RealmResults<Thread> {
        return getThreadsQuery(realm).find()
    }

    fun getThreads(uids: List<String>, realm: TypedRealm? = null): RealmResults<Thread> {
        return getThreadsQuery(uids, realm).find()
    }

    fun getThreads(messageIds: Set<String>, realm: TypedRealm? = null): RealmResults<Thread> {
        return getThreadsQuery(messageIds, realm).find()
    }

    fun getUnreadThreadsCount(folderId: String, realm: TypedRealm? = null): Int {
        return getUnreadThreadsCountQuery(folderId, realm).find().toInt()
    }

    fun getThreadsAsync(
        folderId: String,
        filter: ThreadFilter = ThreadFilter.ALL,
        realm: TypedRealm? = null,
    ): Flow<ResultsChange<Thread>> {
        return getThreadsQuery(folderId, filter, realm).asFlow()
    }

    fun getThread(uid: String, realm: TypedRealm? = null): Thread? {
        return getThreadQuery(uid, realm).find()
    }

    fun getThreadAsync(uid: String, realm: TypedRealm? = null): Flow<SingleQueryChange<Thread>> {
        return getThreadQuery(uid, realm).asFlow()
    }
    //endregion

    //region Edit data
    fun upsertThread(thread: Thread, realm: MutableRealm? = null) {
        val block: (MutableRealm) -> Unit = { it.copyToRealm(thread, UpdatePolicy.ALL) }
        realm?.let(block) ?: RealmDatabase.mailboxContent().writeBlocking(block)
    }

    // TODO: Replace this with a Realm query (blocked by https://github.com/realm/realm-kotlin/issues/591)
    private fun getThreadLastMessageUids(thread: Thread): List<String> {
        return mutableListOf<String>().apply {

            val lastMessage = thread.messages.getLastMessageToExecuteAction()
            add(lastMessage.uid)

            addAll(thread.getMessageDuplicates(lastMessage.messageId))
        }
    }

    // TODO: Replace this with a Realm query (blocked by https://github.com/realm/realm-kotlin/issues/591)
    private fun getThreadUnseenMessagesUids(thread: Thread): List<String> {
        return mutableListOf<String>().apply {
            thread.messages.forEach { message ->
                if (!message.seen) {
                    add(message.uid)
                    addAll(thread.getMessageDuplicates(message.messageId))
                }
            }
        }
    }

    fun deleteThreads(realm: MutableRealm) {
        realm.delete(getThreads(realm))
    }

    fun deleteThreads(folderId: String, realm: MutableRealm) {
        realm.delete(getThreadsQuery(folderId, realm = realm))
    }
    //endregion

    //region Mark as seen/unseen
    fun toggleSeenStatus(thread: Thread, mailbox: Mailbox) {
        if (thread.unseenMessagesCount == 0) markAsUnseen(thread, mailbox) else markAsSeen(thread, mailbox)
    }

    private fun markAsUnseen(thread: Thread, mailbox: Mailbox) {
        val uids = getThreadLastMessageUids(thread)

        ApiRepository.markMessagesAsUnseen(mailbox.uuid, uids)
    }

    fun markAsSeen(thread: Thread, mailbox: Mailbox) {
        val uids = getThreadUnseenMessagesUids(thread)

        ApiRepository.markMessagesAsSeen(mailbox.uuid, uids)
    }
    //endregion
}
