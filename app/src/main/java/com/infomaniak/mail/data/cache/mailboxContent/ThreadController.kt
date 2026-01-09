/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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

import com.infomaniak.core.common.cancellable
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Snoozable
import com.infomaniak.mail.data.models.SnoozeState
import com.infomaniak.mail.data.models.SwissTransferContainer
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.utils.ErrorCode
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.extensions.findSuspend
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmScalarQuery
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import javax.inject.Inject

class ThreadController @Inject constructor(private val mailboxContentRealm: RealmDatabase.MailboxContent) {

    //region Get data
    fun getThreadsAsync(folder: Folder, filter: ThreadFilter = ThreadFilter.ALL): Flow<ResultsChange<Thread>> {
        return getThreadsByMessageIdsQuery(folder, filter).asFlow()
    }

    fun getSearchThreadsAsync(): Flow<ResultsChange<Thread>> {
        return getSearchThreadsQuery(mailboxContentRealm()).asFlow()
    }

    suspend fun getSearchThreadsCount(): Long {
        return getSearchThreadsQuery(mailboxContentRealm()).count().findSuspend()
    }

    suspend fun getThreads(threadsUids: List<String>): RealmResults<Thread> {
        return getThreadsByUids(threadsUids, mailboxContentRealm())
    }

    suspend fun getThread(uid: String): Thread? {
        return getThread(uid, mailboxContentRealm())
    }

    fun getThreadAsync(uid: String): Flow<SingleQueryChange<Thread>> {
        return getThreadQuery(uid, mailboxContentRealm()).asFlow()
    }
    //endregion

    //region Edit data
    suspend fun saveSearchThreads(searchThreads: List<Thread>) {
        mailboxContentRealm().write {
            FolderController.getOrCreateSearchFolder(realm = this).apply {
                threads.addAll(searchThreads)
            }
        }
    }

    suspend fun deleteThread(threadUid: String) {
        mailboxContentRealm().write {
            delete(getThreadQuery(threadUid, realm = this))
        }
    }

    suspend fun updateIsLocallyMovedOutStatus(threadsUids: List<String>, hasBeenMovedOut: Boolean) {
        mailboxContentRealm().write {
            threadsUids.forEach {
                getThreadBlocking(it, realm = this)?.isLocallyMovedOut = hasBeenMovedOut
            }
        }
    }

    // TODO: Remove this function when the Threads parental issues are fixed
    suspend fun removeThreadsWithParentalIssues() {
        val realm = mailboxContentRealm()
        val threads = realm.query<Thread>("${Thread::_folders.name}.@count > 1").findSuspend()

        threads.forEach { thread ->

            var isFirstTime = true
            val wrongFolders = thread._folders.toMutableList().apply {
                removeIf { folder ->
                    (folder.id == thread.folderId && isFirstTime).also { isFirstTime = false }
                }
            }

            wrongFolders.forEach { wrongFolder ->
                SentryDebug.addThreadParentsBreadcrumb(wrongFolder.id, thread.uid)
                FolderController.removeThreadFromFolder(wrongFolder.id, thread, realm)
            }
        }
    }
    //endregion

    companion object {

        private const val TAG = "ThreadController"

        /**
         * Keep the snooze state condition of [isSnoozed] the same as
         * the condition used in [ThreadController.Companion.isSnoozedState].
         *
         * Checking for [Snoozable.snoozeEndDate] and [Snoozable.snoozeUuid] on top of [Snoozable.snoozeState] mimics the
         * webmail's behavior and helps to avoid displaying threads that are in an incoherent state on the API
         */
        private val isSnoozedState =
            """_snoozeState == "${SnoozeState.Snoozed.apiValue}" AND snoozeEndDate != null AND snoozeUuid != null"""

        //region Queries
        private fun getThreadsByUidsQuery(threadsUids: List<String>, realm: TypedRealm): RealmQuery<Thread> {
            return realm.query("${Thread::uid.name} IN $0", threadsUids)
        }

        private fun getThreadsByMessageIdsQuery(messageIds: Set<String>, realm: TypedRealm): RealmQuery<Thread> {
            return realm.query("ANY ${Thread::messagesIds.name} IN $0", messageIds)
        }

        private fun getSearchThreadsQuery(realm: TypedRealm): RealmQuery<Thread> {
            return realm.query<Thread>("${Thread::isFromSearch.name} == true").sort(Thread::internalDate.name, Sort.DESCENDING)
        }

        private fun getUnreadThreadsCountQuery(folder: Folder): RealmScalarQuery<Long> {
            val unseen = "${Thread::unseenMessagesCount.name} > 0"
            return folder.threads.query(unseen).count()
        }

        private fun getThreadsByMessageIdsQuery(
            folder: Folder,
            filter: ThreadFilter = ThreadFilter.ALL,
        ): RealmQuery<Thread> {

            val notFromSearch = "${Thread::isFromSearch.name} == false"
            val notLocallyMovedOut = " AND ${Thread::isLocallyMovedOut.name} == false"
            val folderSort = folder.folderSort
            val realmQuery = folder.threads
                .query(notFromSearch + notLocallyMovedOut)
                .sort(folderSort.sortBy, folderSort.sortOrder)

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

        private fun getThreadsByFolderIdQuery(folderId: String, realm: TypedRealm): RealmQuery<Thread> {
            return realm.query<Thread>("${Thread::folderId.name} == $0", folderId)
        }

        private fun getThreadsWithSnoozeFilterQuery(
            folderId: String,
            withSnooze: Boolean,
            realm: TypedRealm,
        ): RealmQuery<Thread> {
            val snoozeQuery = if (withSnooze) isSnoozedState else "NOT($isSnoozedState)"
            return realm.query<Thread>("${Thread::folderId.name} == $0 AND $snoozeQuery", folderId)
        }

        private fun getThreadQuery(uid: String, realm: TypedRealm): RealmSingleQuery<Thread> {
            return realm.query<Thread>("${Thread::uid.name} == $0", uid).first()
        }

        private fun getEmptyThreadsInFolderQueryBlocking(folderId: String, realm: TypedRealm): RealmQuery<Thread> {
            val noMessages = "${Thread::messages.name}.@size == $0"
            return FolderController.getFolderBlocking(folderId, realm)?.threads?.query(noMessages, 0)
                ?: realm.query<Thread>("$noMessages AND ${Thread::folderId.name} == $1", 0, folderId)
        }
        //endregion

        //region Get data
        suspend fun getThread(uid: String, realm: TypedRealm): Thread? {
            return getThreadQuery(uid, realm).findSuspend()
        }

        fun getThreadBlocking(uid: String, realm: TypedRealm): Thread? {
            return getThreadQuery(uid, realm).find()
        }

        private suspend fun getThreadsByUids(threadsUids: List<String>, realm: TypedRealm): RealmResults<Thread> {
            return getThreadsByUidsQuery(threadsUids, realm).findSuspend()
        }

        fun getThreadsByMessageIdsBlocking(messageIds: Set<String>, realm: TypedRealm): RealmResults<Thread> {
            return getThreadsByMessageIdsQuery(messageIds, realm).find()
        }

        fun getUnreadThreadsCountBlocking(folder: Folder): Int {
            return getUnreadThreadsCountQuery(folder).find().toInt()
        }

        fun getThreadsByFolderIdBlocking(folderId: String, realm: TypedRealm): RealmResults<Thread> {
            return getThreadsByFolderIdQuery(folderId, realm).find()
        }

        fun getInboxThreadsWithSnoozeFilterBlocking(withSnooze: Boolean, realm: TypedRealm): List<Thread> {
            val inboxId = FolderController.getFolderBlocking(FolderRole.INBOX, realm)?.id ?: return emptyList()
            return getThreadsWithSnoozeFilterQuery(inboxId, withSnooze, realm).find()
        }

        suspend fun getSnoozedThreadsWithNewMessage(inboxFolderId: String, realm: Realm): List<Thread> {
            val isInFolder = "${Thread::folderId.name} == $0"
            val hasNewMessage = "${Thread::isLastInboxMessageSnoozed.name} == false"
            return realm.query<Thread>("$isInFolder AND $hasNewMessage AND $isSnoozedState", inboxFolderId).findSuspend()
        }
        //endregion

        //region Edit data
        fun upsertThread(thread: Thread, realm: MutableRealm): Thread = realm.copyToRealm(thread, UpdatePolicy.ALL)

        private fun updateThread(threadUid: String, realm: MutableRealm, onUpdate: (Thread?) -> Unit) {
            onUpdate(getThreadBlocking(threadUid, realm))
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
                                areHeavyDataFetched = true,
                                isTrashed = localMessage.isTrashed,
                                messageIds = localMessage.messageIds,
                                draftLocalUuid = remoteMessage.getDraftLocalUuidBlocking(realm),
                                isFromSearch = localMessage.isFromSearch,
                                isDeletedOnApi = false,
                                latestCalendarEventResponse = localMessage.latestCalendarEventResponse,
                                swissTransferFiles = swissTransferFiles,
                                emojiReactions = localMessage.emojiReactions,
                            )

                            if (remoteMessage.hasAttachable) hasAttachableInThread = true

                            MessageController.upsertMessageBlocking(remoteMessage, realm = this)
                        }
                    } else {
                        if (apiResponse.error?.code == ErrorCode.MESSAGE_NOT_FOUND) {
                            MessageController.getMessageBlocking(localMessage.uid, realm = this)?.isDeletedOnApi = true
                            deletedMessagesUids.add(localMessage.uid)
                        } else {
                            failedMessagesUids.add(localMessage.uid)
                        }
                    }

                    // TODO: Remove this when the API returns the good value for [Message.hasAttachments]
                    verifyAttachmentsValues(hasAttachableInThread, messages, this@write)
                }
            }

            return deletedMessagesUids to failedMessagesUids
        }

        private suspend fun getApiCallsResults(
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
                }.cancellable().getOrElse {
                    // This `getOrElse` is here only to catch `OutOfMemoryError` when trying to deserialize very big Body.
                    failedMessagesUids.add(localMessage.uid)
                    return@getOrElse null
                }
            }
        }

        private suspend fun fetchSwissTransferContainer(uuid: String): SwissTransferContainer? = runCatching {
            val apiResponse = ApiRepository.getSwissTransferContainer(uuid)
            return@runCatching if (apiResponse.isSuccess()) {
                apiResponse.data
            } else {
                SentryLog.i(TAG, "Could not fetch SwissTransfer container")
                null
            }
        }.cancellable().getOrNull()

        // If we've already got this Message's Draft beforehand, we need to save
        // its `draftLocalUuid`, otherwise we'll lose the link between them.
        private fun Message.getDraftLocalUuidBlocking(realm: TypedRealm): String? {
            return if (isDraft) DraftController.getDraftByMessageUidBlocking(uid, realm)?.localUuid else null
        }

        fun deleteSearchThreads(realm: MutableRealm) = with(realm) {
            delete(query<Thread>("${Thread::isFromSearch.name} == true"))
        }

        suspend fun deleteEmptyThreadsInFolder(folderId: String, realm: Realm) {
            realm.write {
                val emptyThreads = getEmptyThreadsInFolderQueryBlocking(folderId, realm = this).find()
                // TODO: Find why we are sometimes displaying empty Threads, and fix it instead of just deleting them.
                //  It's possibly because we are out of sync, and the situation will resolve by itself shortly?
                if (emptyThreads.isNotEmpty()) {
                    emptyThreads.forEach {
                        SentryDebug.sendEmptyThreadBlocking(it, "No Message in a Thread when refreshing a Folder", realm = this)
                    }
                    delete(emptyThreads)
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
                MessageController.getMessagesByUidsBlocking(messages.map(Message::uid), realm)
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
                getThreadBlocking(it, realm)?.isFavorite = isFavorite
            }
        }

        fun updateSeenStatus(threadUids: List<String>, isSeen: Boolean, realm: MutableRealm) {
            threadUids.forEach {
                getThreadBlocking(it, realm)?.unseenMessagesCount = if (isSeen) 0 else 1
            }
        }
        //endregion

        private data class ApiCallsResults(
            val localMessage: Message,
            val apiResponse: ApiResponse<Message>,
            val swissTransferContainer: SwissTransferContainer?,
        )
    }
}
