/*
 * Infomaniak Mail - Android
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
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.SearchUtils.Companion.convertToSearchThreads
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
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
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val refreshController: RefreshController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    //region Get data
    fun getThreadsAsync(folder: Folder, filter: ThreadFilter = ThreadFilter.ALL): Flow<ResultsChange<Thread>> {
        return getThreadsQuery(folder, filter).asFlow()
    }

    fun getSearchThreadsAsync(): Flow<ResultsChange<Thread>> {
        return getSearchThreadsQuery(mailboxContentRealm()).asFlow()
    }

    fun getSearchThreadsCount(): Long {
        return getSearchThreadsQuery(mailboxContentRealm()).count().find()
    }

    fun getThread(uid: String): Thread? {
        return getThread(uid, mailboxContentRealm())
    }

    fun getThreadAsync(uid: String): Flow<SingleQueryChange<Thread>> {
        return getThreadQuery(uid, mailboxContentRealm()).asFlow()
    }

    /**
     * Initialize and retrieve the search threads obtained from the API.
     * - Format the remote threads to make them compatible with the existing logic.
     * - Preserve old message data if it already exists locally.
     * - Handle duplicates using the existing logic.
     * @param remoteThreads The list of API threads that need to be processed.
     * @param folderRole The role of the selected folder. This is only useful when selecting the spam or trash folder.
     * @return A list of search threads. The search only returns messages from spam or trash if we explicitly selected those folders
     */
    suspend fun initAndGetSearchFolderThreads(
        remoteThreads: List<Thread>,
        folderRole: FolderRole?,
    ): List<Thread> = withContext(ioDispatcher) {

        fun MutableRealm.keepOldMessagesAndAddToSearchFolder(remoteThread: Thread, searchFolder: Folder) {

            remoteThread.messages.forEach { remoteMessage: Message ->
                ensureActive()

                val localMessage = MessageController.getMessage(remoteMessage.uid, realm = this)

                // The Search only returns Messages from SPAM or TRASH if we explicitly selected those folders,
                // which is the reason why we can compute `isSpam` and `isTrashed` values so loosely.
                remoteMessage.initLocalValues(
                    date = localMessage?.date ?: remoteMessage.date,
                    isFullyDownloaded = localMessage?.isFullyDownloaded() ?: false,
                    isTrashed = folderRole == FolderRole.TRASH,
                    isFromSearch = localMessage == null,
                    draftLocalUuid = localMessage?.draftLocalUuid,
                )

                localMessage?.let(remoteMessage::keepHeavyData)

                remoteThread.messagesIds += remoteMessage.messageIds
                searchFolder.messages.add(remoteMessage)
            }
        }

        return@withContext mailboxContentRealm().writeBlocking {
            val searchFolder = FolderController.getOrCreateSearchFolder(realm = this)
            remoteThreads.map { remoteThread ->
                ensureActive()
                remoteThread.isFromSearch = true
                remoteThread.folderId = remoteThread.messages.first().folderId

                keepOldMessagesAndAddToSearchFolder(remoteThread, searchFolder)

                return@map remoteThread
            }.also(searchFolder.threads::addAll)
        }
    }
    //endregion

    //region Edit data
    fun deleteSearchThreads(realm: MutableRealm) = with(realm) {
        delete(query<Thread>("${Thread::isFromSearch.name} == true").find())
    }

    /**
     * Asynchronously fetches heavy data for a list of messages within a given mailbox and realm.
     *
     * This function fetches heavy data associated with the provided list of messages, such as attachments
     * or other resource-intensive content. It operates within the given realm and mailbox context.
     *
     * This function is deliberately present here as it relies on a method accessible solely through injection.
     *
     * @param messages List of messages for which heavy data needs to be fetched.
     * @param mailbox The mailbox context within which the heavy data should be fetched.
     * @param realm The realm context in which the heavy data fetching and updates should occur.
     * @param okHttpClient An optional OkHttpClient instance to use for making network requests. If not provided, a default client will be used.
     */
    suspend fun fetchMessagesHeavyData(
        messages: List<Message>,
        mailbox: Mailbox,
        realm: Realm,
        okHttpClient: OkHttpClient? = null,
    ) {
        val failedFoldersIds = realm.writeBlocking { fetchMessagesHeavyData(messages, realm = this, okHttpClient) }
        updateFailedFolders(failedFoldersIds, mailbox, realm, okHttpClient)
    }

    private suspend fun updateFailedFolders(
        failedFoldersIds: Set<String>,
        mailbox: Mailbox,
        realm: Realm,
        okHttpClient: OkHttpClient?,
    ) {
        failedFoldersIds.forEach { folderId ->
            FolderController.getFolder(folderId, realm)?.let { folder ->
                refreshController.refreshThreads(
                    refreshMode = RefreshMode.REFRESH_FOLDER,
                    mailbox = mailbox,
                    folder = folder,
                    okHttpClient = okHttpClient,
                    realm = realm,
                )
            }
        }
    }

    fun saveThreads(searchMessages: List<Message>) {
        mailboxContentRealm().writeBlocking {
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
            return realm.query("ANY ${Thread::messagesIds.name} IN $0", messageIds)
        }

        private fun getOrphanThreadsQuery(realm: TypedRealm): RealmQuery<Thread> {
            return realm.query("${Thread::folderId.name} == ''")
        }

        private fun getSearchThreadsQuery(realm: TypedRealm): RealmQuery<Thread> {
            return realm.query("${Thread::isFromSearch.name} == true")
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
            return realm.query<Thread>("${Thread::uid.name} == $0", uid).first()
        }
        //endregion

        //region Get data
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
        //endregion

        //region Edit data
        fun upsertThread(thread: Thread, realm: MutableRealm): Thread = realm.copyToRealm(thread, UpdatePolicy.ALL)

        fun fetchMessagesHeavyData(
            messages: List<Message>,
            realm: MutableRealm,
            okHttpClient: OkHttpClient? = null,
        ): Set<String> {

            val failedFoldersIds = mutableSetOf<String>()

            messages.forEach { localMessage ->
                if (localMessage.isFullyDownloaded()) return@forEach

                val apiResponse = ApiRepository.getMessage(localMessage.resource, okHttpClient)

                if (apiResponse.isSuccess()) {
                    apiResponse.data?.also { remoteMessage ->
                        remoteMessage.initLocalValues(
                            date = localMessage.date,
                            isFullyDownloaded = true,
                            isTrashed = localMessage.isTrashed,
                            isFromSearch = localMessage.isFromSearch,
                            draftLocalUuid = getDraftLocalUuid(remoteMessage, realm),
                            messageIds = localMessage.messageIds,
                        )

                        MessageController.upsertMessage(remoteMessage, realm)
                    }
                } else {
                    failedFoldersIds.add(localMessage.folderId)
                }
            }

            return failedFoldersIds
        }

        // If we've already got this Message's Draft beforehand, we need to save
        // its `draftLocalUuid`, otherwise we'll lose the link between them.
        private fun getDraftLocalUuid(message: Message, realm: MutableRealm): String? {
            return if (message.isDraft) {
                DraftController.getDraftByMessageUid(message.uid, realm)?.localUuid
            } else {
                null
            }
        }
        //endregion
    }
}
