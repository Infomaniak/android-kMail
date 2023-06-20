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
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MailboxContentRealm
import com.infomaniak.mail.utils.SearchUtils.Companion.convertToSearchThreads
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject

class ThreadController @Inject constructor(
    @MailboxContentRealm private val mailboxContentRealm: Realm,
    private val refreshController: RefreshController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    //region Get data
    fun getThreadsAsync(folder: Folder, filter: ThreadFilter = ThreadFilter.ALL): Flow<ResultsChange<Thread>> {
        return getThreadsQuery(folder, filter).asFlow()
    }

    fun getSearchThreadsAsync(): Flow<ResultsChange<Thread>> {
        return mailboxContentRealm.query<Thread>("${Thread::isFromSearch.name} == true").asFlow()
    }

    fun getThread(uid: String): Thread? {
        return Companion.getThread(uid, mailboxContentRealm)
    }

    fun getThreadAsync(uid: String): Flow<SingleQueryChange<Thread>> {
        return getThreadQuery(uid, mailboxContentRealm).asFlow()
    }

    /**
     * Init the search Threads that we have recovered from the API.
     * - Format remote Threads to make them work with the existing logic
     * - Keep old Messages data if it's already existing in local
     * - Handle Duplicates with the existing logic
     * @param remoteThreads The list of API Threads that need to be treated
     * @return a list of search Threads
     */
    suspend fun initAndGetSearchFolderThreads(remoteThreads: List<Thread>): List<Thread> = withContext(ioDispatcher) {

        fun MutableRealm.keepOldMessagesAndAddToSearchFolder(remoteThread: Thread, searchFolder: Folder) {

            remoteThread.messages.forEach { remoteMessage: Message ->

                MessageController.getMessage(remoteMessage.uid, realm = this)?.let { localMessage ->
                    remoteMessage.initLocalValues(
                        date = localMessage.date,
                        isFullyDownloaded = localMessage.isFullyDownloaded,
                        isSpam = localMessage.isSpam,
                        messageIds = localMessage.messageIds,
                        draftLocalUuid = localMessage.draftLocalUuid,
                        isFromSearch = localMessage.isFromSearch,
                    )
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

        return@withContext mailboxContentRealm.writeBlocking {
            val searchFolder = FolderController.getOrCreateSearchFolder(realm = this)
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
    fun deleteSearchThreads(realm: MutableRealm) = with(realm) {
        delete(query<Thread>("${Thread::isFromSearch.name} == true").find())
    }

    suspend fun fetchIncompleteMessages(
        messages: List<Message>,
        mailbox: Mailbox,
        okHttpClient: OkHttpClient? = null,
        realm: Realm = mailboxContentRealm,
    ) {
        val failedFoldersIds = realm.writeBlocking { fetchIncompleteMessages(realm = this, messages, okHttpClient) }
        updateFailedFolders(failedFoldersIds, mailbox, okHttpClient, realm)
    }

    private suspend fun updateFailedFolders(
        failedFoldersIds: Set<String>,
        mailbox: Mailbox,
        okHttpClient: OkHttpClient?,
        realm: Realm,
    ) = withContext(ioDispatcher) {
        failedFoldersIds.forEach { folderId ->
            FolderController.getFolder(folderId, realm)?.let { folder ->
                refreshController.refreshThreads(RefreshMode.REFRESH_FOLDER, mailbox, folder, okHttpClient, realm)
            }
        }
    }

    fun saveThreads(searchMessages: List<Message>) {
        mailboxContentRealm.writeBlocking {
            FolderController.getOrCreateSearchFolder(realm = this).apply {
                messages = searchMessages.toRealmList()
                threads = searchMessages.convertToSearchThreads().toRealmList()
            }
        }
    }
    //endregion

    companion object {

        //region Queries
        private fun getThreadsQuery(messageIds: Set<String>, realm: TypedRealm): RealmQuery<Thread> {
            val byMessagesIds = "ANY ${Thread::messagesIds.name} IN {${messageIds.joinToString { "'$it'" }}}"
            return realm.query(byMessagesIds)
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

        fun getThread(uid: String, realm: TypedRealm): Thread? {
            return getThreadQuery(uid, realm).find()
        }

        fun getThreads(messageIds: Set<String>, realm: TypedRealm): RealmResults<Thread> {
            return getThreadsQuery(messageIds, realm).find()
        }

        fun getUnreadThreadsCount(folder: Folder): Int {
            return getUnreadThreadsCountQuery(folder).find().toInt()
        }

        fun getOrphanThreads(realm: TypedRealm): RealmResults<Thread> {
            return getOrphanThreadsQuery(realm).find()
        }

        fun upsertThread(realm: MutableRealm, thread: Thread): Thread = realm.copyToRealm(thread, UpdatePolicy.ALL)

        fun fetchIncompleteMessages(
            realm: MutableRealm,
            messages: List<Message>,
            okHttpClient: OkHttpClient? = null,
        ): Set<String> {

            val failedFoldersIds = mutableSetOf<String>()

            messages.forEach { localMessage ->
                if (!localMessage.isFullyDownloaded) {
                    with(ApiRepository.getMessage(localMessage.resource, okHttpClient)) {
                        if (isSuccess()) {
                            data?.also { remoteMessage ->

                                // If we've already got this Message's Draft beforehand, we need to save
                                // its `draftLocalUuid`, otherwise we'll lose the link between them.
                                val draftLocalUuid = if (remoteMessage.isDraft) {
                                    DraftController.getDraftByMessageUid(remoteMessage.uid, realm)?.localUuid
                                } else {
                                    null
                                }

                                remoteMessage.initLocalValues(
                                    date = localMessage.date,
                                    isFullyDownloaded = true,
                                    isSpam = localMessage.isSpam,
                                    messageIds = localMessage.messageIds,
                                    draftLocalUuid = draftLocalUuid,
                                    isFromSearch = localMessage.isFromSearch,
                                )

                                MessageController.upsertMessage(remoteMessage, realm)
                            }
                        } else {
                            failedFoldersIds.add(localMessage.folderId)
                        }
                    }
                }
            }

            return failedFoldersIds
        }
    }
}
