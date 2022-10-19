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
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.deleteMessages
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.data.models.thread.ThreadsResult
import com.infomaniak.mail.utils.toSharedFlow
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmSingleQuery
import kotlinx.coroutines.flow.SharedFlow

object ThreadController {

    //region Queries
    private fun MutableRealm?.getThreadQuery(uid: String): RealmSingleQuery<Thread> {
        return (this ?: RealmDatabase.mailboxContent()).query<Thread>("${Thread::uid.name} = '$uid'").first()
    }
    //endregion

    //region Get data
    fun getThread(uid: String, realm: MutableRealm? = null): Thread? {
        return realm.getThreadQuery(uid).find()
    }

    fun getThreadAsync(uid: String, realm: MutableRealm? = null): SharedFlow<SingleQueryChange<Thread>> {
        return realm.getThreadQuery(uid).asFlow().toSharedFlow()
    }
    //endregion

    //region Edit data
    fun refreshThreads(
        threadsResult: ThreadsResult,
        mailboxUuid: String,
        folderId: String,
        filter: ThreadFilter,
    ): Boolean = RealmDatabase.mailboxContent().writeBlocking {

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

        return@writeBlocking canPaginate(threadsResult.messagesCount)
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

    fun MutableRealm.markThreadAsUnseen(thread: Thread, folderId: String) {
        thread.apply {
            messages.last().seen = false
            unseenMessagesCount++
        }

        incrementFolderUnreadCount(folderId, thread.unseenMessagesCount)
    }

    fun MutableRealm.markThreadAsSeen(thread: Thread, folderId: String) {
        incrementFolderUnreadCount(folderId, -thread.unseenMessagesCount)

        thread.apply {
            messages.forEach { it.seen = true }
            unseenMessagesCount = 0
        }
    }

    // TODO: Replace this with a Realm query (blocked by https://github.com/realm/realm-kotlin/issues/591)
    fun getThreadLastMessageUid(thread: Thread): List<String> = listOf(thread.messages.last().uid)

    // TODO: Replace this with a Realm query (blocked by https://github.com/realm/realm-kotlin/issues/591)
    fun getThreadUnseenMessagesUids(thread: Thread): List<String> {
        return mutableListOf<String>().apply {
            thread.messages.forEach { if (!it.seen) add(it.uid) }
        }
    }

    private fun MutableRealm.incrementFolderUnreadCount(folderId: String, unseenMessagesCount: Int) {
        FolderController.updateFolder(folderId, this) {
            it.unreadCount += unseenMessagesCount
        }
    }

    private fun MutableRealm.deleteThreads(threads: List<Thread>) {
        threads.forEach(::delete)
    }

    fun deleteThread(uid: String) {
        RealmDatabase.mailboxContent().writeBlocking { getThread(uid, this)?.let(::delete) }
    }
    //endregion
}
