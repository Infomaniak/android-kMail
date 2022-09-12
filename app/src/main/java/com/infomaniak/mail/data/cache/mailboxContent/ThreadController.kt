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
import com.infomaniak.mail.utils.toSharedFlow
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.types.RealmList
import kotlinx.coroutines.flow.SharedFlow

object ThreadController {

    //region Get data
    fun getThread(uid: String, realm: MutableRealm? = null): Thread? {
        return realm.getThreadQuery(uid).find()
    }

    private fun getThreadAsync(uid: String, realm: MutableRealm? = null): SharedFlow<SingleQueryChange<Thread>> {
        return realm.getThreadQuery(uid).asFlow().toSharedFlow()
    }

    private fun MutableRealm?.getThreadQuery(uid: String): RealmSingleQuery<Thread> {
        return (this ?: RealmDatabase.mailboxContent).query<Thread>("${Thread::uid.name} == '$uid'").first()
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
    fun update(mailboxUuid: String, folderId: String, offset: Int, filter: ThreadFilter): Boolean {

        // Get current data
        Log.d(RealmDatabase.TAG, "Threads: Get current data")
        val realmThreads = FolderController.getFolder(folderId)?.threads?.filter {
            when (filter) {
                ThreadFilter.SEEN -> it.unseenMessagesCount == 0
                ThreadFilter.UNSEEN -> it.unseenMessagesCount > 0
                ThreadFilter.STARRED -> it.isFavorite
                ThreadFilter.ATTACHMENTS -> it.hasAttachments
                else -> true
            }
        } ?: emptyList()
        DuplicateController.removeDuplicates()
        val threadsResult = ApiRepository.getThreads(mailboxUuid, folderId, offset, filter).data
        val apiThreadsSinceOffset = threadsResult?.threads?.map { it.initLocalValues(mailboxUuid) }
            ?: emptyList()
        val apiThreads = if (offset == ApiRepository.OFFSET_FIRST_PAGE) {
            apiThreadsSinceOffset
        } else {
            realmThreads.plus(apiThreadsSinceOffset).distinctBy { it.uid }
        }

        // Get outdated data
        Log.d(RealmDatabase.TAG, "Threads: Get outdated data")
        // val deletableThreads = MailboxContentController.getDeletableThreads(threadsFromApi)
        val deletableThreads = if (offset == ApiRepository.OFFSET_FIRST_PAGE) {
            realmThreads.filter { realmThread ->
                apiThreads.none { apiThread -> apiThread.uid == realmThread.uid }
            }
        } else {
            emptyList()
        }
        val deletableMessages = deletableThreads.flatMap { thread -> thread.messages.filter { it.folderId == folderId } }

        RealmDatabase.mailboxContent.writeBlocking {
            // Save new data
            Log.d(RealmDatabase.TAG, "Threads: Save new data")
            val newPageSize = apiThreads.size - offset
            if (newPageSize > 0) {
                apiThreads.takeLast(newPageSize).forEach { apiThread ->
                    val realmThread = realmThreads.find { it.uid == apiThread.uid }
                    val mergedThread = getMergedThread(apiThread, realmThread)
                    copyToRealm(mergedThread, UpdatePolicy.ALL)
                }
                updateFolderThreads(folderId, apiThreads)
            }

            // Delete outdated data
            Log.d(RealmDatabase.TAG, "Threads: Delete outdated data")
            deleteMessages(deletableMessages)
            deleteThreads(deletableThreads)
        }

        return threadsResult?.let {
            val canContinueToPaginate = it.messagesCount >= ApiRepository.PER_PAGE
            FolderController.updateFolderUnreadCount(folderId, it.folderUnseenMessage)
            canContinueToPaginate
        } ?: false
    }

    fun markAsSeen(thread: Thread, folderId: String?) {
        if (thread.unseenMessagesCount != 0) {

            RealmDatabase.mailboxContent.writeBlocking {

                val liveThread = getThread(thread.uid, this) ?: return@writeBlocking

                val uids = mutableListOf<String>().apply {
                    liveThread.messages.forEach {
                        if (!it.seen) {
                            add(it.uid)
                            addAll(it.duplicates.map { duplicate -> duplicate.uid })
                        }
                    }
                }

                val apiResponse = ApiRepository.markMessagesAsSeen(liveThread.mailboxUuid, uids)

                if (apiResponse.isSuccess()) {
                    updateFolderUnreadCount(folderId, liveThread)
                    liveThread.apply {
                        messages.forEach { it.seen = true }
                        unseenMessagesCount = 0
                    }
                }
            }
        }
    }

    private fun MutableRealm.updateFolderUnreadCount(folderId: String?, latestThread: Thread) {
        val folder = folderId?.let { FolderController.getFolder(folderId, this) } ?: return
        val newUnreadCount = folder.unreadCount - latestThread.unseenMessagesCount
        FolderController.updateFolderUnreadCount(folderId, newUnreadCount, this)
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

    private fun MutableRealm.updateFolderThreads(folderId: String, apiThreads: List<Thread>) {
        FolderController.updateFolder(folderId, this) { folder ->
            folder.threads = apiThreads.map { if (it.isManaged()) findLatest(it) ?: it else it }.toRealmList()
        }
    }

    fun MutableRealm.deleteThreads(threads: List<Thread>) {
        threads.forEach { getThread(it.uid, this)?.let(::delete) }
    }
    //endregion

    /**
     * TODO?
     */
    // fun deleteThreads(threads: List<Thread>) {
    //     MailRealm.mailboxContent.writeBlocking { threads.forEach { getLatestThread(it.uid)?.let(::delete) } }
    // }

    // fun upsertThread(thread: Thread) {
    //     MailRealm.mailboxContent.writeBlocking { copyToRealm(thread, UpdatePolicy.ALL) }
    // }

    // fun upsertLatestThread(uid: String) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestThread(uid)?.let { copyToRealm(it, UpdatePolicy.ALL) } }
    // }

    // fun updateThread(uid: String, onUpdate: (thread: Thread) -> Unit) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestThread(uid)?.let(onUpdate) }
    // }

    // fun getLatestThread(uid: String): Thread? = RealmController.mailboxContent.writeBlocking { getLatestThread(uid) }

    // TODO: RealmKotlin doesn't fully support `IN` for now.
    // TODO: Workaround: https://github.com/realm/realm-js/issues/2781#issuecomment-607213640
    // fun getDeletableThreads(folder: Folder, threadsToKeep: List<Thread>): RealmResults<Thread> {
    //     val threadsIds = threadsToKeep.map { it.uid }
    //     val query = threadsIds.joinToString(
    //         prefix = "NOT (${Thread::uid.name} == '",
    //         separator = "' OR ${Thread::uid.name} == '",
    //         postfix = "')"
    //     )
    //     return MailRealm.mailboxContent.query<Thread>(query).find()
    // }
}
