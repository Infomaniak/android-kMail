/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.utils.SearchUtils.convertToSearchThreads
import com.infomaniak.mail.utils.SharedViewModelUtils.fetchFolderMessagesJob
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

object ThreadController {

    private inline val defaultRealm get() = RealmDatabase.mailboxContent()

    //region Queries
    private fun getThreadsQuery(realm: TypedRealm): RealmQuery<Thread> {
        return realm.query()
    }

    private fun getOrphanThreadsQuery(realm: TypedRealm): RealmQuery<Thread> {
        return realm.query("${Thread::folderId.name} == ''")
    }

    private fun getUnreadThreadsCountQuery(folder: Folder): RealmScalarQuery<Long> {
        val unseen = "${Thread::unseenMessagesCount.name} > 0"
        return folder.threads.query(unseen).count()
    }

    private fun getThreadsQuery(folder: Folder, filter: ThreadFilter = ThreadFilter.ALL): RealmQuery<Thread> {

        val notFromSearch = "${Thread::isFromSearch.name} == false"
        val realmQuery = folder.threads.query(notFromSearch).sort(Thread::date.name, Sort.DESCENDING)

        return if (filter == ThreadFilter.ALL) {
            realmQuery
        } else {
            val withFilter = when (filter) {
                ThreadFilter.SEEN -> "${Thread::unseenMessagesCount.name} == 0"
                ThreadFilter.UNSEEN -> "${Thread::unseenMessagesCount.name} > 0"
                ThreadFilter.STARRED -> "${Thread::isFavorite.name} == true"
                ThreadFilter.ATTACHMENTS -> "${Thread::hasAttachments.name} == true"
                ThreadFilter.FOLDER -> TODO()
                else -> throw IllegalStateException("`${ThreadFilter::class.simpleName}` cannot be `${ThreadFilter.ALL.name}` here.")
            }
            realmQuery.query(withFilter)
        }
    }

    private fun getThreadQuery(uid: String, realm: TypedRealm): RealmSingleQuery<Thread> {
        return realm.query<Thread>("${Thread::uid.name} == '$uid'").first()
    }
    //endregion

    //region Get data
    fun getThreads(realm: TypedRealm): RealmResults<Thread> {
        return getThreadsQuery(realm).find()
    }

    fun getOrphanThreads(realm: TypedRealm): RealmResults<Thread> {
        return getOrphanThreadsQuery(realm).find()
    }

    fun getUnreadThreadsCount(folder: Folder): Int {
        return getUnreadThreadsCountQuery(folder).find().toInt()
    }

    fun getThreadsAsync(folder: Folder, filter: ThreadFilter = ThreadFilter.ALL): Flow<ResultsChange<Thread>> {
        return getThreadsQuery(folder, filter).asFlow()
    }

    fun getSearchThreadsAsync(): Flow<ResultsChange<Thread>> {
        return defaultRealm.query<Thread>("${Thread::isFromSearch.name} == true").asFlow()
    }

    fun getThread(uid: String, realm: TypedRealm = defaultRealm): Thread? {
        return getThreadQuery(uid, realm).find()
    }

    fun getThreadAsync(uid: String): Flow<SingleQueryChange<Thread>> {
        return getThreadQuery(uid, defaultRealm).asFlow()
    }

    /**
     * Init the search Threads that we have recovered from the API.
     * - Format remote Threads to make them work with the existing logic
     * - Keep old Messages data if it's already existing in local
     * - Handle Duplicates with the existing logic
     * @param remoteThreads The list of API Threads that need to be treated
     * @return a list of search Threads
     */
    suspend fun initAndGetSearchFolderThreads(remoteThreads: List<Thread>): List<Thread> = withContext(Dispatchers.IO) {

        fun MutableRealm.keepOldMessagesAndAddToSearchFolder(remoteThread: Thread, searchFolder: Folder) {
            remoteThread.messages.forEach { remoteMessage: Message ->
                MessageController.getMessage(remoteMessage.uid, this)?.let { localMessage ->
                    val position = remoteThread.messages.indexOf(localMessage)
                    remoteThread.messages[position] = localMessage.copyFromRealm()
                } ?: run {
                    remoteMessage.isFromSearch = true
                }
                searchFolder.messages.add(remoteMessage)
            }
        }

        fun Thread.handleDuplicatesMessages() {
            if (messages.count() > 1) {
                val firstMessage = messages.removeAt(0)
                duplicates = messages
                messages = realmListOf(firstMessage)
            }
        }

        return@withContext defaultRealm.writeBlocking {
            val searchFolder = FolderController.getOrCreateSearchFolder(this)
            remoteThreads.map { remoteThread ->
                ensureActive()
                val remoteMessage = remoteThread.messages.first()
                remoteThread.isFromSearch = true
                remoteThread.folderId = remoteMessage.folderId

                keepOldMessagesAndAddToSearchFolder(remoteThread, searchFolder)
                remoteThread.handleDuplicatesMessages()
                remoteThread.recomputeThread()

                return@map remoteThread
            }.also(searchFolder.threads::addAll)
        }
    }
    //endregion

    //region Edit data
    fun MutableRealm.upsertThread(thread: Thread): Thread = copyToRealm(thread, UpdatePolicy.ALL)

    fun deleteSearchThreads(realm: MutableRealm) = with(realm) {
        delete(query<Thread>("${Thread::isFromSearch.name} == true").find())
    }

    suspend fun fetchIncompleteMessages(
        messages: List<Message>,
        mailbox: Mailbox,
        okHttpClient: OkHttpClient? = null,
        realm: Realm = defaultRealm,
    ) = withContext(Dispatchers.IO) {

        val impactedFoldersIds = mutableSetOf<String>()

        realm.writeBlocking {
            messages.forEach { localMessage ->
                if (!localMessage.isFullyDownloaded) {
                    with(ApiRepository.getMessage(localMessage.resource, okHttpClient)) {
                        if (isSuccess()) {
                            data?.also { remoteMessage ->

                                // If we've already got this Message's Draft beforehand, we need to save
                                // its `draftLocalUuid`, otherwise we'll lose the link between them.
                                val draftLocalUuid = if (remoteMessage.isDraft) {
                                    DraftController.getDraftByMessageUid(remoteMessage.uid, realm = this@writeBlocking)?.localUuid
                                } else {
                                    null
                                }

                                remoteMessage.initLocalValues(
                                    isFullyDownloaded = true,
                                    messageIds = localMessage.messageIds,
                                    isSpam = localMessage.isSpam,
                                    date = localMessage.date,
                                    draftLocalUuid,
                                )

                                MessageController.upsertMessage(remoteMessage, realm = this@writeBlocking)
                            }
                        } else {
                            impactedFoldersIds.add(localMessage.folderId)
                        }
                    }
                }
            }
        }

        impactedFoldersIds.forEach { folderId ->
            FolderController.getFolder(folderId, realm)?.let { folder ->
                fetchFolderMessagesJob?.cancel()
                fetchFolderMessagesJob = launch {
                    MessageController.fetchFolderMessages(this, mailbox, folder, okHttpClient, realm)
                }
                fetchFolderMessagesJob?.join()
            }
        }
    }

    fun saveThreads(searchMessages: List<Message>) {
        defaultRealm.writeBlocking {
            FolderController.getOrCreateSearchFolder(this).apply {
                messages = searchMessages.toRealmList()
                threads = searchMessages.convertToSearchThreads().toRealmList()
            }
        }
    }
    //endregion
}
