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
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController.incrementFolderUnreadCount
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.deleteMessages
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.data.models.thread.ThreadsResult
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.getLastMessageToExecuteAction
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmSingleQuery

object ThreadController {

    //region Queries
    private fun MutableRealm?.getThreadsQuery(uids: List<String>): RealmQuery<Thread> {
        val threads = "${Thread::uid.name} IN {${uids.joinToString { "\"$it\"" }}}"
        return (this ?: RealmDatabase.mailboxContent()).query(threads)
    }

    private fun MutableRealm?.getThreadQuery(uid: String): RealmSingleQuery<Thread> {
        return (this ?: RealmDatabase.mailboxContent()).query<Thread>("${Thread::uid.name} = '$uid'").first()
    }
    //endregion

    //region Get data
    fun getThreads(uids: List<String>, realm: MutableRealm? = null): RealmQuery<Thread> {
        return realm.getThreadsQuery(uids)
    }

    fun getThread(uid: String, realm: MutableRealm? = null): Thread? {
        return realm.getThreadQuery(uid).find()
    }
    //endregion

    //region Edit data
    fun updateThread(uid: String, realm: MutableRealm? = null, onUpdate: (message: Thread) -> Unit) {
        val block: (MutableRealm) -> Unit = { getThread(uid, it)?.let(onUpdate) }
        realm?.let(block) ?: RealmDatabase.mailboxContent().writeBlocking(block)
    }

    fun upsertThread(thread: Thread, realm: MutableRealm? = null) {
        val block: (MutableRealm) -> Unit = { it.copyToRealm(thread, UpdatePolicy.ALL) }
        realm?.let(block) ?: RealmDatabase.mailboxContent().writeBlocking(block)
    }

    fun refreshThreads(
        threadsResult: ThreadsResult,
        mailboxUuid: String,
        folderId: String,
        filter: ThreadFilter,
        realm: MutableRealm,
    ): Boolean = with(realm) {

        Log.d(RealmDatabase.TAG, "Threads: Get current data")
        val localThreads = getLocalThreads(folderId, filter)
        val apiThreads = initApiThreads(threadsResult, mailboxUuid)

        Log.d(RealmDatabase.TAG, "Threads: Get outdated data")
        val outdatedMessages = getOutdatedMessages(localThreads)

        Log.d(RealmDatabase.TAG, "Threads: Delete outdated data")
        deleteMessages(outdatedMessages)
        deleteThreads(localThreads)

        Log.d(RealmDatabase.TAG, "Threads: Save new data")
        updateFolderThreads(folderId, apiThreads, threadsResult.folderUnseenMessage)

        return canPaginate(threadsResult.messagesCount)
    }

    private fun MutableRealm.getLocalThreads(folderId: String, filter: ThreadFilter): List<Thread> {
        return FolderController.getFolder(folderId, this)?.threads?.filter {
            when (filter) {
                ThreadFilter.SEEN -> it.unseenMessagesCount == 0
                ThreadFilter.UNSEEN -> it.unseenMessagesCount > 0
                ThreadFilter.STARRED -> it.isFavorite
                ThreadFilter.ATTACHMENTS -> it.hasAttachments
                else -> true
            }
        } ?: emptyList()
    }

    private fun initApiThreads(threadsResult: ThreadsResult, mailboxUuid: String): List<Thread> {
        return threadsResult.threads.map { it.initLocalValues(mailboxUuid) }
    }

    private fun getOutdatedMessages(localThreads: List<Thread>): List<Message> = localThreads.flatMap { it.messages }

    private fun MutableRealm.updateFolderThreads(folderId: String, apiThreads: List<Thread>, folderUnseenMessage: Int) {
        FolderController.updateFolder(folderId, this) { folder ->
            folder.apply {
                threads = apiThreads.toRealmList()
                unreadCount = folderUnseenMessage
            }
        }
    }

    private fun canPaginate(messagesCount: Int): Boolean = messagesCount >= ApiRepository.PER_PAGE

    fun loadMoreThreads(
        threadsResult: ThreadsResult,
        mailboxUuid: String,
        folderId: String,
        offset: Int,
        filter: ThreadFilter,
    ): Boolean = RealmDatabase.mailboxContent().writeBlocking {

        Log.d(RealmDatabase.TAG, "Threads: Get new data")
        val apiThreads = initPaginatedThreads(folderId, filter, threadsResult, mailboxUuid)

        Log.d(RealmDatabase.TAG, "Threads: Save new data")
        insertNewData(apiThreads, folderId, offset, threadsResult.folderUnseenMessage)

        return@writeBlocking canPaginate(threadsResult.messagesCount)
    }

    private fun MutableRealm.initPaginatedThreads(
        folderId: String,
        filter: ThreadFilter,
        threadsResult: ThreadsResult,
        mailboxUuid: String,
    ): List<Thread> {
        val localThreads = getLocalThreads(folderId, filter)
        val apiThreadsSinceOffset = initApiThreads(threadsResult, mailboxUuid)
        return localThreads.plus(apiThreadsSinceOffset).distinctBy { it.uid }
    }

    private fun MutableRealm.insertNewData(apiThreads: List<Thread>, folderId: String, offset: Int, folderUnseenMessage: Int) {
        val newPageSize = apiThreads.size - offset
        if (newPageSize > 0) updateFolderThreads(folderId, apiThreads, folderUnseenMessage)
    }

    private fun MutableRealm.markThreadAsUnseen(thread: Thread, folderId: String) {
        thread.apply {
            messages.getLastMessageToExecuteAction().seen = false
            unseenMessagesCount++
        }

        incrementFolderUnreadCount(folderId, thread.unseenMessagesCount)
    }

    private fun MutableRealm.markThreadAsSeen(thread: Thread, folderId: String) {
        incrementFolderUnreadCount(folderId, -thread.unseenMessagesCount)

        thread.apply {
            messages.forEach { it.seen = true }
            unseenMessagesCount = 0
        }
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

    private fun MutableRealm.deleteThreads(threads: List<Thread>) {
        threads.forEach(::delete)
    }

    fun deleteThread(uid: String) {
        RealmDatabase.mailboxContent().writeBlocking { getThread(uid, this)?.let(::delete) }
    }
    //endregion

    //region Mark as seen/unseen
    fun toggleSeenStatus(thread: Thread) {
        val folderId = MainViewModel.currentFolderId.value!!
        RealmDatabase.mailboxContent().writeBlocking {
            if (thread.unseenMessagesCount == 0) {
                markAsUnseen(thread, folderId, realm = this)
            } else {
                markAsSeen(thread, folderId, realm = this)
            }
        }
    }

    private fun markAsUnseen(thread: Thread, folderId: String, realm: MutableRealm) {
        val latestThread = realm.findLatest(thread) ?: return
        val uid = getThreadLastMessageUid(latestThread)
        with(ApiRepository.markMessagesAsUnseen(latestThread.mailboxUuid, uid)) {
            if (isSuccess()) realm.markThreadAsUnseen(latestThread, folderId)
        }
    }

    fun markAsSeen(thread: Thread, folderId: String, realm: MutableRealm) {
        if (thread.unseenMessagesCount == 0) return

        val latestThread = realm.findLatest(thread) ?: return
        val uids = getThreadUnseenMessagesUids(latestThread)
        with(ApiRepository.markMessagesAsSeen(latestThread.mailboxUuid, uids)) {
            if (isSuccess()) realm.markThreadAsSeen(latestThread, folderId)
        }
    }
    //endregion
}
