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
import com.infomaniak.mail.data.cache.RealmController
import com.infomaniak.mail.data.cache.mailboxContent.FolderController.getLatestFolderSync
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.deleteMessages
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.getLatestMessageSync
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

    /**
     * Get data
     */
    fun getThreadSync(uid: String): Thread? {
        return getThread(uid).find()
    }

    fun getThreadAsync(uid: String): SharedFlow<SingleQueryChange<Thread>> {
        return getThread(uid).asFlow().toSharedFlow()
    }

    fun MutableRealm.getLatestThreadSync(uid: String): Thread? {
        return getThreadSync(uid)?.let(::findLatest)
    }

    /**
     * Edit data
     */
    fun upsertApiData(
        mailboxUuid: String,
        folderId: String,
        offset: Int,
        filter: ThreadFilter,
        canContinueToPaginate: (Boolean) -> Unit,
    ): List<Thread> {

        // Get current data
        Log.d(RealmController.TAG, "Threads: Get current data")
        val realmThreads = FolderController.getFolderSync(folderId)?.threads?.filter {
            when (filter) {
                ThreadFilter.SEEN -> it.unseenMessagesCount == 0
                ThreadFilter.UNSEEN -> it.unseenMessagesCount > 0
                ThreadFilter.STARRED -> it.isFavorite
                ThreadFilter.ATTACHMENTS -> it.hasAttachments
                else -> true
            }
        } ?: emptyList()
        val apiThreadsSinceOffset = ApiRepository.getThreads(mailboxUuid, folderId, offset, filter).data
            ?.also { threadsResult ->
                FolderController.updateFolderCounts(folderId, threadsResult)
                canContinueToPaginate(threadsResult.messagesCount >= ApiRepository.PER_PAGE)
            }
            ?.threads?.map { it.initLocalValues() }
            ?: emptyList()
        val apiThreads = if (offset == ApiRepository.OFFSET_FIRST_PAGE) {
            apiThreadsSinceOffset
        } else {
            realmThreads.plus(apiThreadsSinceOffset).distinctBy { it.uid }
        }

        // Get outdated data
        Log.d(RealmController.TAG, "Threads: Get outdated data")
        // val deletableThreads = MailboxContentController.getDeletableThreads(threadsFromApi)
        val deletableThreads = if (offset == ApiRepository.OFFSET_FIRST_PAGE) {
            realmThreads.filter { realmThread ->
                apiThreads.none { apiThread -> apiThread.uid == realmThread.uid }
            }
        } else {
            emptyList()
        }
        val deletableMessages = deletableThreads.flatMap { thread -> thread.messages.filter { it.folderId == folderId } }

        RealmController.mailboxContent.writeBlocking {
            // Save new data
            Log.d(RealmController.TAG, "Threads: Save new data")
            val newPageSize = apiThreads.size - offset
            if (newPageSize > 0) {
                apiThreads.takeLast(newPageSize).forEach { apiThread ->
                    val realmThread = realmThreads.find { it.uid == apiThread.uid }
                    val mergedThread = getMergedThread(apiThread, realmThread)
                    copyToRealm(mergedThread, UpdatePolicy.ALL)
                }
                updateFolder(folderId, apiThreads)
            }

            // Delete outdated data
            Log.d(RealmController.TAG, "Threads: Delete outdated data")
            deleteMessages(deletableMessages)
            deleteThreads(deletableThreads)
        }

        return apiThreads
    }

    fun MutableRealm.deleteThreads(threads: List<Thread>) {
        threads.forEach { deleteLatestThread(it.uid) }
    }

    /**
     * Utils
     */
    private fun getThread(uid: String): RealmSingleQuery<Thread> {
        return RealmController.mailboxContent.query<Thread>("${Thread::uid.name} == '$uid'").first()
    }

    private fun MutableRealm.deleteLatestThread(uid: String) {
        getLatestThreadSync(uid)?.let(::delete)
    }

    private fun MutableRealm.getMergedThread(apiThread: Thread, realmThread: Thread?): Thread {
        return apiThread.apply {
            if (realmThread != null) {
                messages.forEach { apiMessage ->
                    realmThread.messages.find { realmMessage -> realmMessage.uid == apiMessage.uid }
                        ?.let { realmMessage -> getLatestMessageSync(realmMessage.uid) }
                        ?.let { realmMessage -> saveMessageWithBackedUpData(apiMessage, realmMessage) }
                }
            }
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

    private fun MutableRealm.updateFolder(folderId: String, apiThreads: List<Thread>) {
        getLatestFolderSync(folderId)?.let { latestFolder ->
            latestFolder.threads = apiThreads.map { if (it.isManaged()) findLatest(it) ?: it else it }.toRealmList()
            copyToRealm(latestFolder, UpdatePolicy.ALL)
        }
    }

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
