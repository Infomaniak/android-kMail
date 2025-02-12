/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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

import android.content.Context
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.SwissTransferContainer
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.message.Message.MessageInitialState
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.ErrorCode
import com.infomaniak.mail.utils.SearchUtils.Companion.convertToSearchThreads
import com.infomaniak.mail.utils.SentryDebug
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.isManaged
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
    private val context: Context,
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    //region Get data
    fun getThreadsAsync(folder: Folder, filter: ThreadFilter = ThreadFilter.ALL, sortOrder: Sort): Flow<ResultsChange<Thread>> {
        return getThreadsQuery(folder, filter, sortOrder).asFlow()
    }

    fun getSearchThreadsAsync(): Flow<ResultsChange<Thread>> {
        return getSearchThreadsQuery(mailboxContentRealm()).asFlow()
    }

    fun getSearchThreadsCount(): Long {
        return getSearchThreadsQuery(mailboxContentRealm()).count().find()
    }

    fun getThreads(threadsUids: List<String>): RealmResults<Thread> {
        return getThreadsByUids(threadsUids, mailboxContentRealm())
    }

    fun getThread(uid: String): Thread? {
        return getThread(uid, mailboxContentRealm())
    }

    fun getThreadAsync(uid: String): Flow<SingleQueryChange<Thread>> {
        return getThreadQuery(uid, mailboxContentRealm()).asFlow()
    }

    /**
     * Initialize and retrieve the search Threads obtained from the API.
     * - Format the remote Threads to make them compatible with the existing logic.
     * - Preserve old Messages data if it already exists locally.
     * - Handle duplicates using the existing logic.
     * @param remoteThreads The list of API Threads that need to be processed.
     * @param filterFolder The selected Folder on which we filter the Search.
     * @return List of search Threads. The search only returns Messages from SPAM or TRASH if we explicitly selected those folders
     */
    suspend fun initAndGetSearchFolderThreads(
        remoteThreads: List<Thread>,
        filterFolder: Folder?,
    ): List<Thread> = withContext(ioDispatcher) {

        fun MutableRealm.keepOldMessagesAndAddToSearchFolder(remoteThread: Thread, searchFolder: Folder) {

            remoteThread.messages.forEach { remoteMessage: Message ->
                ensureActive()

                val localMessage = MessageController.getMessage(remoteMessage.uid, realm = this)

                // The Search only returns Messages from TRASH if we explicitly selected this folder,
                // which is the reason why we can compute the `isTrashed` value so loosely.
                remoteMessage.initLocalValues(
                    MessageInitialState(
                        date = localMessage?.date ?: remoteMessage.date,
                        isFullyDownloaded = localMessage?.isFullyDownloaded() ?: false,
                        isTrashed = filterFolder?.role == FolderRole.TRASH,
                        isFromSearch = localMessage == null,
                        draftLocalUuid = localMessage?.draftLocalUuid,
                    ),
                    latestCalendarEventResponse = null,
                )

                localMessage?.let(remoteMessage::keepHeavyData)

                remoteThread.messagesIds += remoteMessage.messageIds

                // TODO: Remove this when the API returns the good value for `has_attachments`.
                if (remoteMessage.hasAttachable) remoteThread.hasAttachable = true
            }
        }

        return@withContext mailboxContentRealm().write {
            val searchFolder = FolderController.getOrCreateSearchFolder(realm = this)
            val cachedFolderIds = mutableMapOf<String, String>()

            remoteThreads.map { remoteThread ->
                ensureActive()

                remoteThread.isFromSearch = true

                // If we only have 1 Message, we want to display its Folder name.
                val folderId = if (remoteThread.messages.count() == 1) {
                    val firstMessageFolderId = remoteThread.messages.single().folderId
                    setFolderName(firstMessageFolderId, remoteThread, cachedFolderIds)
                    firstMessageFolderId
                } else {
                    filterFolder!!.id
                }
                remoteThread.folderId = folderId

                keepOldMessagesAndAddToSearchFolder(remoteThread, searchFolder)

                return@map remoteThread
            }.also(searchFolder.threads::addAll)
        }
    }

    private fun MutableRealm.setFolderName(
        firstMessageFolderId: String,
        remoteThread: Thread,
        cachedFolderIds: MutableMap<String, String>,
    ) {
        val folderName = cachedFolderIds[firstMessageFolderId]
            ?: FolderController.getFolder(firstMessageFolderId, this)
                ?.getLocalizedName(context)
                ?.also { cachedFolderIds[firstMessageFolderId] = it }

        folderName?.let { remoteThread.folderName = it }
    }
    //endregion

    //region Edit data
    suspend fun saveThreads(searchMessages: List<Message>) {
        mailboxContentRealm().write {
            FolderController.getOrCreateSearchFolder(realm = this).apply {
                threads = searchMessages.convertToSearchThreads().toRealmList()
            }
        }
    }

    suspend fun deleteThread(threadUid: String) {
        mailboxContentRealm().write {
            delete(getThreadQuery(threadUid, realm = this))
        }
    }

    suspend fun updateIsLocallyMovedOutStatus(threadUids: List<String>, hasBeenMovedOut: Boolean) {
        mailboxContentRealm().write {
            threadUids.forEach {
                getThread(it, realm = this)?.isLocallyMovedOut = hasBeenMovedOut
            }
        }
    }
    //endregion

    companion object {

        //region Queries
        private fun getThreadsByUidsQuery(threadsUids: List<String>, realm: TypedRealm): RealmQuery<Thread> {
            return realm.query("${Thread::uid.name} IN $0", threadsUids)
        }

        private fun getThreadsQuery(messageIds: Set<String>, realm: TypedRealm): RealmQuery<Thread> {
            return realm.query("ANY ${Thread::messagesIds.name} IN $0", messageIds)
        }

        private fun getExistingThreadsFoldersCountQuery(messageIds: Set<String>, realm: TypedRealm): RealmScalarQuery<Long> {
            return getThreadsQuery(messageIds, realm).distinct(Thread::folderId.name).count()
        }

        private fun getSearchThreadsQuery(realm: TypedRealm): RealmQuery<Thread> {
            return realm.query<Thread>("${Thread::isFromSearch.name} == true").sort(Thread::date.name, Sort.DESCENDING)
        }

        private fun getUnreadThreadsCountQuery(folder: Folder): RealmScalarQuery<Long> {
            val unseen = "${Thread::unseenMessagesCount.name} > 0"
            return folder.threads.query(unseen).count()
        }

        private fun getThreadsQuery(
            folder: Folder,
            filter: ThreadFilter = ThreadFilter.ALL,
            sortOrder: Sort,
        ): RealmQuery<Thread> {

            val notFromSearch = "${Thread::isFromSearch.name} == false"
            val notLocallyMovedOut = " AND ${Thread::isLocallyMovedOut.name} == false"
            val realmQuery = folder.threads.query(notFromSearch + notLocallyMovedOut).sort(Thread::date.name, sortOrder)

            return if (filter == ThreadFilter.ALL) {
                realmQuery
            } else {
                val withFilter = when (filter) {
                    ThreadFilter.SEEN -> "${Thread::unseenMessagesCount.name} == 0"
                    ThreadFilter.UNSEEN -> "${Thread::unseenMessagesCount.name} > 0"
                    ThreadFilter.STARRED -> "${Thread::isFavorite.name} == true"
                    ThreadFilter.ATTACHMENTS -> "${Thread::hasAttachable.name} == true"
                    ThreadFilter.FOLDER -> TODO()
                    else -> error("`${ThreadFilter::class.simpleName}` cannot be `${ThreadFilter.ALL.name}` here.")
                }
                realmQuery.query(withFilter)
            }
        }

        private fun getThreadQuery(uid: String, realm: TypedRealm): RealmSingleQuery<Thread> {
            return realm.query<Thread>("${Thread::uid.name} == $0", uid).first()
        }

        private fun getEmptyThreadsInFolderQuery(folderId: String, realm: TypedRealm): RealmQuery<Thread> {
            val noMessages = "${Thread::messages.name}.@size == $0"
            return FolderController.getFolder(folderId, realm)?.threads?.query(noMessages, 0)
                ?: realm.query<Thread>("$noMessages AND ${Thread::folderId.name} == $1", 0, folderId)
        }
        //endregion

        //region Get data
        fun getThread(uid: String, realm: TypedRealm): Thread? {
            return getThreadQuery(uid, realm).find()
        }

        private fun getThreadsByUids(threadsUids: List<String>, realm: TypedRealm): RealmResults<Thread> {
            return getThreadsByUidsQuery(threadsUids, realm).find()
        }

        fun getThreads(messageIds: Set<String>, realm: TypedRealm): RealmResults<Thread> {
            return getThreadsQuery(messageIds, realm).find()
        }

        fun getExistingThreadsFoldersCount(messageIds: Set<String>, realm: TypedRealm): Long {
            return getExistingThreadsFoldersCountQuery(messageIds, realm).find()
        }

        fun getUnreadThreadsCount(folder: Folder): Int {
            return getUnreadThreadsCountQuery(folder).find().toInt()
        }
        //endregion

        //region Edit data
        fun upsertThread(thread: Thread, realm: MutableRealm): Thread = realm.copyToRealm(thread, UpdatePolicy.ALL)

        private fun updateThread(threadUid: String, realm: MutableRealm, onUpdate: (Thread?) -> Unit) {
            onUpdate(getThread(threadUid, realm))
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
         * @param realm The realm context in which the heavy data fetching and updates should occur.
         * @param okHttpClient An optional OkHttpClient instance to use for making network requests.
         *                     If not provided, a default client will be used.
         */
        suspend fun fetchMessagesHeavyData(
            messages: List<Message>,
            realm: Realm,
            okHttpClient: OkHttpClient? = null,
        ): Pair<List<String>, List<String>> {

            val deletedMessagesUids = mutableListOf<String>()
            val failedMessagesUids = mutableListOf<String>()

            val apiCallsResults = getApiCallsResults(messages, okHttpClient, failedMessagesUids)

            realm.write {
                var hasAttachableInThread = false

                apiCallsResults.forEach { (localMessage, apiResponse, swissTransferContainer) ->

                    if (localMessage.isFullyDownloaded()) return@forEach

                    if (apiResponse.isSuccess()) {
                        apiResponse.data?.also { remoteMessage ->
                            val swissTransferFiles = swissTransferContainer?.let { container ->
                                SwissTransferContainerController.upsertSwissTransferContainer(container, realm = this)
                                container.swissTransferFiles
                            } ?: realmListOf()

                            remoteMessage.initLocalValues(
                                MessageInitialState(
                                    date = localMessage.date,
                                    isFullyDownloaded = true,
                                    isTrashed = localMessage.isTrashed,
                                    isFromSearch = localMessage.isFromSearch,
                                    draftLocalUuid = remoteMessage.getDraftLocalUuid(realm),
                                ),
                                latestCalendarEventResponse = localMessage.latestCalendarEventResponse,
                                messageIds = localMessage.messageIds,
                                swissTransferFiles = swissTransferFiles,
                            )

                            if (remoteMessage.hasAttachable) hasAttachableInThread = true

                            MessageController.upsertMessage(remoteMessage, realm = this)
                        }
                    } else {
                        if (apiResponse.error?.code == ErrorCode.MESSAGE_NOT_FOUND) {
                            MessageController.getMessage(localMessage.uid, realm = this)?.isDeletedOnApi = true
                            deletedMessagesUids.add(localMessage.uid)
                        } else {
                            failedMessagesUids.add(localMessage.uid)
                        }
                    }

                    // TODO: Remove this when the API returns the good value for `has_attachments`.
                    verifyAttachmentsValues(hasAttachableInThread, messages, this@write)
                }
            }

            return deletedMessagesUids to failedMessagesUids
        }

        private fun getApiCallsResults(
            messages: List<Message>,
            okHttpClient: OkHttpClient?,
            failedMessagesUids: MutableList<String>,
        ): List<ApiCallsResults> {
            return messages.mapNotNull { localMessage ->
                return@mapNotNull runCatching {
                    val apiResponse = ApiRepository.getMessage(localMessage.resource, okHttpClient)
                    val swissTransferUuid = apiResponse.data?.swissTransferUuid
                    var swissTransferContainer: SwissTransferContainer? = null
                    if (apiResponse.isSuccess() && swissTransferUuid != null) {
                        swissTransferContainer = fetchSwissTransferContainer(swissTransferUuid)
                    }
                    return@runCatching ApiCallsResults(localMessage, apiResponse, swissTransferContainer)
                }.getOrElse {
                    // This `getOrElse` is here only to catch `OutOfMemoryError` when trying to deserialize very big Body.
                    failedMessagesUids.add(localMessage.uid)
                    return@getOrElse null
                }
            }
        }

        private fun fetchSwissTransferContainer(uuid: String): SwissTransferContainer? = runCatching {
            val apiResponse = ApiRepository.getSwissTransferContainer(uuid)
            return@runCatching if (apiResponse.isSuccess()) {
                apiResponse.data
            } else {
                SentryLog.i(TAG, "Could not fetch SwissTransfer container")
                null
            }
        }.getOrNull()

        // If we've already got this Message's Draft beforehand, we need to save
        // its `draftLocalUuid`, otherwise we'll lose the link between them.
        private fun Message.getDraftLocalUuid(realm: TypedRealm): String? {
            return if (isDraft) DraftController.getDraftByMessageUid(uid, realm)?.localUuid else null
        }

        fun deleteSearchThreads(realm: MutableRealm) = with(realm) {
            delete(query<Thread>("${Thread::isFromSearch.name} == true"))
        }

        suspend fun deleteEmptyThreadsInFolder(folderId: String, realm: Realm) {
            realm.write {
                val emptyThreadsQuery = getEmptyThreadsInFolderQuery(folderId, realm = this)
                val emptyThreads = emptyThreadsQuery.find()
                // TODO: Find why we are sometimes displaying empty Threads, and fix it instead of just deleting them.
                //  It's possibly because we are out of sync, and the situation will resolve by itself shortly?
                if (emptyThreads.isNotEmpty()) {
                    emptyThreads.forEach {
                        SentryDebug.sendEmptyThread(it, "No Message in a Thread when refreshing a Folder", realm = this)
                    }
                    delete(emptyThreadsQuery)
                }
            }
        }

        private fun verifyAttachmentsValues(hasAttachableInThread: Boolean, messages: List<Message>, realm: MutableRealm) {
            // When this is called for the notifications, the `messages` aren't managed.
            // In this case we need to query them to use the backlinks to get their parent's threads
            // Otherwise, we got an IllegalStateException and the notifications aren't shown
            val localMessages = if (messages.firstOrNull()?.isManaged() == true) {
                messages
            } else {
                MessageController.getMessagesByUids(messages.map(Message::uid), realm)
            }

            localMessages.flatMapTo(mutableSetOf(), Message::threads).forEach { thread ->
                if (thread.hasAttachable != hasAttachableInThread) {
                    updateThread(thread.uid, realm) {
                        it?.hasAttachable = hasAttachableInThread
                    }
                }
            }
        }

        fun updateFavoriteStatus(threadUids: List<String>, isFavorite: Boolean, realm: MutableRealm) {
            threadUids.forEach {
                getThread(it, realm)?.isFavorite = isFavorite
            }
        }

        fun updateSeenStatus(threadUids: List<String>, isSeen: Boolean, realm: MutableRealm) {
            threadUids.forEach {
                getThread(it, realm)?.unseenMessagesCount = if (isSeen) 0 else 1
            }
        }
        //endregion

        private data class ApiCallsResults(
            val localMessage: Message,
            val apiResponse: ApiResponse<Message>,
            val swissTransferContainer: SwissTransferContainer?,
        )

        private const val TAG = "ThreadController"
    }
}
