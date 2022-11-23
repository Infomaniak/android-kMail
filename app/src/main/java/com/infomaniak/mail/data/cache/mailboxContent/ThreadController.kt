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
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmSingleQuery

object ThreadController {

    //region Queries
    private fun MutableRealm?.getThreadsQuery(uids: List<String>): RealmQuery<Thread> {
        val byUids = "${Thread::uid.name} IN {${uids.joinToString { "\"$it\"" }}}"
        return (this ?: RealmDatabase.mailboxContent()).query(byUids)
    }

    private fun MutableRealm?.getThreadsQuery(folderId: String, filter: ThreadFilter): RealmQuery<Thread> {
        val byFolderId = "${Thread::folderId.name} = '$folderId'"
        val query = (this ?: RealmDatabase.mailboxContent()).query<Thread>(byFolderId)
        return if (filter == ThreadFilter.ALL) {
            query
        } else {
            val byFilter = when (filter) {
                ThreadFilter.SEEN -> "${Thread::unseenMessagesCount.name} = 0"
                ThreadFilter.UNSEEN -> "${Thread::unseenMessagesCount.name} > 0"
                ThreadFilter.STARRED -> "${Thread::isFavorite.name} = true"
                ThreadFilter.ATTACHMENTS -> "${Thread::hasAttachments.name} = true"
                ThreadFilter.FOLDER -> TODO()
                else -> throw IllegalStateException("`${ThreadFilter::class.simpleName}` cannot be `${ThreadFilter.ALL.name}` here.")
            }
            query.query(byFilter)
        }
    }

    private fun MutableRealm?.getThreadQuery(uid: String): RealmSingleQuery<Thread> {
        return (this ?: RealmDatabase.mailboxContent()).query<Thread>("${Thread::uid.name} = '$uid'").first()
    }
    //endregion

    //region Get data
    fun getThreads(uids: List<String>, realm: MutableRealm? = null): RealmQuery<Thread> {
        return realm.getThreadsQuery(uids)
    }

    fun getThreads(folderId: String, filter: ThreadFilter, realm: MutableRealm? = null): RealmQuery<Thread> {
        return realm.getThreadsQuery(folderId, filter)
    }

    fun getThread(uid: String, realm: MutableRealm? = null): Thread? {
        return realm.getThreadQuery(uid).find()
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
