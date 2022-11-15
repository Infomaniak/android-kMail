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
import com.infomaniak.mail.data.cache.mailboxContent.FolderController.incrementFolderUnreadCount
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.utils.getLastMessageToExecuteAction
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery

object ThreadController {

    //region Queries
    fun getThreadsQuery(uids: List<String>, realm: TypedRealm? = null): RealmQuery<Thread> {
        val byUids = "${Thread::uid.name} IN {${uids.joinToString { "\"$it\"" }}}"
        return (realm ?: RealmDatabase.mailboxContent()).query(byUids)
    }

    private fun getThreadsQuery(folderId: String, filter: ThreadFilter, realm: TypedRealm? = null): RealmQuery<Thread> {
        val byFolderId = "ANY ${Thread::foldersIds.name} = '$folderId'"
        val query = (realm ?: RealmDatabase.mailboxContent()).query<Thread>(byFolderId)
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
    fun getThreads(uids: List<String>, realm: TypedRealm? = null): RealmResults<Thread> {
        return getThreadsQuery(uids, realm).find()
    }

    fun getThreads(folderId: String, filter: ThreadFilter, realm: TypedRealm? = null): RealmQuery<Thread> {
        return getThreadsQuery(folderId, filter, realm)
    }

    fun getThread(uid: String, realm: TypedRealm? = null): Thread? {
        return getThreadQuery(uid, realm).find()
    }
    //endregion

    //region Edit data
    fun upsertThread(thread: Thread, realm: MutableRealm? = null) {
        val block: (MutableRealm) -> Unit = { it.copyToRealm(thread, UpdatePolicy.ALL) }
        realm?.let(block) ?: RealmDatabase.mailboxContent().writeBlocking(block)
    }

    // TODO: Replace this with a Realm query (blocked by https://github.com/realm/realm-kotlin/issues/591)
    private fun getThreadLastMessageUid(thread: Thread): List<String> {
        return listOf(thread.messages.getLastMessageToExecuteAction().uid)
    }

    // TODO: Replace this with a Realm query (blocked by https://github.com/realm/realm-kotlin/issues/591)
    private fun getThreadUnseenMessagesUids(thread: Thread): List<String> {
        return mutableListOf<String>().apply {
            thread.messages.forEach { if (!it.seen) add(it.uid) }
        }
    }

    fun deleteThread(uid: String) {
        RealmDatabase.mailboxContent().writeBlocking { getThread(uid, realm = this)?.let(::delete) }
    }
    //endregion

    //region Mark as seen/unseen
    fun toggleSeenStatus(thread: Thread) {
        if (thread.unseenMessagesCount == 0) markAsUnseen(thread) else markAsSeen(thread)
    }

    private fun markAsUnseen(thread: Thread) {

        val mailboxUuid = MailboxController.getCurrentMailboxUuid() ?: return
        val uid = getThreadLastMessageUid(thread)

        if (ApiRepository.markMessagesAsUnseen(mailboxUuid, uid).isSuccess()) markThreadAsUnseen(thread.uid)
    }

    fun markAsSeen(thread: Thread) {
        if (thread.unseenMessagesCount == 0) return

        val mailboxUuid = MailboxController.getCurrentMailboxUuid() ?: return
        val uids = getThreadUnseenMessagesUids(thread)

        if (ApiRepository.markMessagesAsSeen(mailboxUuid, uids).isSuccess()) markThreadAsSeen(thread.uid)
    }

    private fun markThreadAsUnseen(threadUid: String) {
        RealmDatabase.mailboxContent().writeBlocking {
            val thread = getThread(threadUid, realm = this) ?: return@writeBlocking
            val message = thread.messages.getLastMessageToExecuteAction()
            message.seen = false
            thread.unseenMessagesCount++

            incrementFolderUnreadCount(message.folderId, 1)
        }
    }

    private fun markThreadAsSeen(threadUid: String) {
        RealmDatabase.mailboxContent().writeBlocking {
            val thread = getThread(threadUid, realm = this) ?: return@writeBlocking
            thread.messages.forEach {
                if (!it.seen) {
                    incrementFolderUnreadCount(it.folderId, -1)
                    it.seen = true
                }
            }

            thread.unseenMessagesCount = 0
        }
    }
    //endregion
}
