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
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.getMessage
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.data.models.thread.ThreadsResult
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.types.RealmList

object ThreadController {

    //region Get data
    fun getThread(uid: String, realm: MutableRealm? = null): Thread? {
        return realm.getThreadQuery(uid).find()
    }

    private fun MutableRealm?.getThreadQuery(uid: String): RealmSingleQuery<Thread> {
        return (this ?: RealmDatabase.mailboxContent).query<Thread>("${Thread::uid.name} = '$uid'").first()
    }

    private fun MutableRealm.getMergedThread(apiThread: Thread, realmThread: Thread?): Thread {
        return apiThread.apply {
            if (realmThread != null) {
                messages.forEach { apiMessage ->
                    realmThread.messages.find { realmMessage -> realmMessage.uid == apiMessage.uid }
                        ?.let { realmMessage -> getMessage(realmMessage.uid, this@getMergedThread) }
                        ?.let { realmMessage -> saveMessageWithBackedUpData(apiMessage, realmMessage) }
                }
            }
        }
    }
    //endregion

    //region Edit data
    fun refreshThreads(
        threadsResult: ThreadsResult,
        mailboxUuid: String,
        folderId: String,
        filter: ThreadFilter,
    ): Boolean = RealmDatabase.mailboxContent.writeBlocking {

        // Get current data
        Log.d(RealmDatabase.TAG, "Threads: Get current data")
        val realmThreads = getRealmThreads(folderId, filter)
        val apiThreads = getApiThreads(threadsResult, mailboxUuid)

        // Get outdated data
        Log.d(RealmDatabase.TAG, "Threads: Get outdated data")
        val outdatedMessages = getOutdatedMessages(realmThreads, folderId)

        // Delete outdated data
        Log.d(RealmDatabase.TAG, "Threads: Delete outdated data")
        deleteMessages(outdatedMessages)
        deleteThreads(realmThreads)

        // Save new data
        Log.d(RealmDatabase.TAG, "Threads: Save new data")
        updateFolderThreads(folderId, apiThreads, threadsResult.folderUnseenMessage)

        return@writeBlocking canPaginate(threadsResult.messagesCount)
    }

    private fun MutableRealm.getRealmThreads(folderId: String, filter: ThreadFilter): List<Thread> {
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

    private fun getApiThreads(threadsResult: ThreadsResult, mailboxUuid: String): List<Thread> {
        return threadsResult.threads.map { it.initLocalValues(mailboxUuid) }
    }

    private fun getOutdatedMessages(realmThreads: List<Thread>, folderId: String): List<Message> {
        // val deletableThreads = MailboxContentController.getDeletableThreads(threadsFromApi)
        return realmThreads.flatMap { thread -> thread.messages.filter { it.folderId == folderId } }
    }

    private fun MutableRealm.updateFolderThreads(folderId: String, apiThreads: List<Thread>, folderUnseenMessage: Int) {
        FolderController.updateFolder(folderId, this) { folder ->
            folder.apply {
                threads = apiThreads.map { if (it.isManaged()) findLatest(it) ?: it else it }.toRealmList()
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
    ): Boolean = RealmDatabase.mailboxContent.writeBlocking {

        // Get current data
        Log.d(RealmDatabase.TAG, "Threads: Get current data")
        val realmThreads = getRealmThreads(folderId, filter)
        val apiThreads = getPaginatedThreads(threadsResult, realmThreads, mailboxUuid)

        // Save new data
        Log.d(RealmDatabase.TAG, "Threads: Save new data")
        insertNewData(realmThreads, apiThreads, folderId, offset, threadsResult.folderUnseenMessage)

        return@writeBlocking canPaginate(threadsResult.messagesCount)
    }

    private fun getPaginatedThreads(
        threadsResult: ThreadsResult,
        realmThreads: List<Thread>,
        mailboxUuid: String,
    ): List<Thread> {
        val apiThreadsSinceOffset = getApiThreads(threadsResult, mailboxUuid)
        return realmThreads.plus(apiThreadsSinceOffset).distinctBy { it.uid }
    }

    private fun MutableRealm.insertNewData(
        realmThreads: List<Thread>,
        apiThreads: List<Thread>,
        folderId: String,
        offset: Int,
        folderUnseenMessage: Int,
    ) {
        val newPageSize = apiThreads.size - offset
        if (newPageSize > 0) {
            apiThreads.takeLast(newPageSize).forEach { apiThread ->
                val realmThread = realmThreads.find { it.uid == apiThread.uid }
                val mergedThread = getMergedThread(apiThread, realmThread)
                copyToRealm(mergedThread, UpdatePolicy.ALL)
            }
            updateFolderThreads(folderId, apiThreads, folderUnseenMessage)
        }
    }

    fun toggleSeenStatus(thread: Thread, folderId: String) {
        if (thread.unseenMessagesCount == 0) {
            markAsUnseen(thread, folderId)
        } else {
            markAsSeen(thread, folderId)
        }
    }

    private fun markAsUnseen(thread: Thread, folderId: String) {
        RealmDatabase.mailboxContent.writeBlocking {

            val latestThread = findLatest(thread) ?: return@writeBlocking
            val lastMessage = latestThread.messages.last()

            val uids = mutableListOf<String>().apply {
                add(lastMessage.uid)
                addAll(lastMessage.duplicates.map { duplicate -> duplicate.uid })
            }

            val apiResponse = ApiRepository.markMessagesAsUnseen(latestThread.mailboxUuid, uids)

            if (apiResponse.isSuccess()) {
                latestThread.apply {
                    lastMessage.seen = false
                    unseenMessagesCount++
                }
                incrementFolderUnreadCount(folderId, latestThread.unseenMessagesCount, this)
            }
        }
    }

    fun markAsSeen(thread: Thread, folderId: String) {
        if (thread.unseenMessagesCount != 0) {

            RealmDatabase.mailboxContent.writeBlocking {

                val latestThread = findLatest(thread) ?: return@writeBlocking

                val uids = mutableListOf<String>().apply {
                    latestThread.messages.forEach {
                        if (!it.seen) {
                            add(it.uid)
                            addAll(it.duplicates.map { duplicate -> duplicate.uid })
                        }
                    }
                }

                val apiResponse = ApiRepository.markMessagesAsSeen(latestThread.mailboxUuid, uids)

                if (apiResponse.isSuccess()) {
                    incrementFolderUnreadCount(folderId, -latestThread.unseenMessagesCount, this)
                    latestThread.apply {
                        messages.forEach { it.seen = true }
                        unseenMessagesCount = 0
                    }
                }
            }
        }
    }

    private fun incrementFolderUnreadCount(folderId: String, unseenMessagesCount: Int, realm: MutableRealm? = null) {
        FolderController.updateFolder(folderId, realm) {
            it.unreadCount += unseenMessagesCount
        }
    }

    private fun MutableRealm.saveMessageWithBackedUpData(apiMessage: Message, realmMessage: Message) {
        apiMessage.apply {
            fullyDownloaded = realmMessage.fullyDownloaded
            body = realmMessage.body
            attachmentsResource = realmMessage.attachmentsResource
            attachments.setRealmListValues(realmMessage.attachments)
        }
        copyToRealm(apiMessage, UpdatePolicy.ALL)
    }

    private fun <T> RealmList<T>.setRealmListValues(values: RealmList<T>) {
        if (isNotEmpty()) clear()
        addAll(values)
    }

    fun MutableRealm.deleteThreads(threads: List<Thread>) {
        threads.forEach(::delete)
    }
    //endregion

    // TODO: RealmKotlin doesn't fully support `IN` for now.
    // TODO: Workaround: https://github.com/realm/realm-js/issues/2781#issuecomment-607213640
    // fun getDeletableThreads(folder: Folder, threadsToKeep: List<Thread>): RealmResults<Thread> {
    //     val threadsIds = threadsToKeep.map { it.uid }
    //     val query = threadsIds.joinToString(
    //         prefix = "NOT (${Thread::uid.name} = '",
    //         separator = "' OR ${Thread::uid.name} = '",
    //         postfix = "')"
    //     )
    //     return MailRealm.mailboxContent.query<Thread>(query).find()
    // }
}
