/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
import com.infomaniak.core.cancellable
import com.infomaniak.core.utils.FORMAT_DATE_WITH_TIMEZONE
import com.infomaniak.core.utils.format
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.ThreadMode
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode.ONE_PAGE_OF_OLD_MESSAGES
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode.REFRESH_FOLDER
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode.REFRESH_FOLDER_WITH_ROLE
import com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies.RefreshStrategy
import com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies.ThreadRecomputations.recomputeThread
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.getMessages.ActivitiesResult
import com.infomaniak.mail.data.models.getMessages.DefaultMessageFlags
import com.infomaniak.mail.data.models.getMessages.MessageFlags
import com.infomaniak.mail.data.models.getMessages.NewMessagesResult
import com.infomaniak.mail.data.models.getMessages.SnoozeMessageFlags
import com.infomaniak.mail.data.models.isSnoozeMalformed
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.ApiErrorException
import com.infomaniak.mail.utils.ErrorCode
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.SentryDebug.displayForSentry
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.SharedUtils.Companion.AutomaticUnsnoozeResult
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.extensions.replaceContent
import com.infomaniak.mail.utils.extensions.throwErrorAsException
import com.infomaniak.mail.utils.extensions.toDate
import com.infomaniak.mail.utils.extensions.toRealmInstant
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RefreshController @Inject constructor(
    private val appContext: Context,
    private val localSettings: LocalSettings,
    private val mailboxController: MailboxController,
    private val delayApiCallManager: DelayApiCallManager,
) {

    private var refreshThreadsJob: Job? = null

    private lateinit var refreshMode: RefreshMode
    private lateinit var mailbox: Mailbox
    private lateinit var initialFolder: Folder
    private lateinit var realm: Realm
    private var okHttpClient: OkHttpClient? = null
    private var onStart: (() -> Unit)? = null
    private var onStop: (() -> Unit)? = null
    private var endOfMessagesReached: Boolean = false

    //region Fetch Messages
    fun cancelRefresh() {
        refreshThreadsJob?.cancel(ForcedCancellationException())
    }

    private fun clearCallbacks() {
        onStart = null
        onStop = null
    }

    suspend fun refreshThreads(
        refreshMode: RefreshMode,
        mailbox: Mailbox,
        folderId: String,
        okHttpClient: OkHttpClient? = null,
        realm: Realm,
        callbacks: RefreshCallbacks? = null,
    ): Pair<Set<Thread>?, Throwable?> {

        val folder = FolderController.getFolder(folderId, realm)!!

        // If the Mailbox is not available (i.e. the mailbox is locked or its password is wrong),
        // we'll be denied permission to fetch it by the API, so we don't even try to do it.
        // We can leave safely.
        if (!mailbox.isAvailable) {
            SentryLog.w("API", "Refresh threads: We left early because of a predictable denied access.")
            return emptySet<Thread>() to null
        }

        SentryLog.i("API", "Refresh threads with mode: $refreshMode | (${folder.displayForSentry()})")

        refreshThreadsJob?.cancel()
        refreshThreadsJob = Job()

        setupConfiguration(refreshMode, mailbox, folder, realm, okHttpClient, callbacks)

        return refreshWithRunCatching(refreshThreadsJob!!).also { (threads, _) ->

            ThreadController.deleteEmptyThreadsInFolder(folder.id, realm)

            if (threads != null) {
                onStop?.invoke()
                SentryLog.d("API", "End of refreshing threads with mode: $refreshMode | (${folder.displayForSentry()})")
            }
        }
    }

    private fun setupConfiguration(
        refreshMode: RefreshMode,
        mailbox: Mailbox,
        initialFolder: Folder,
        realm: Realm,
        okHttpClient: OkHttpClient?,
        callbacks: RefreshCallbacks? = null,
    ) {
        this.refreshMode = refreshMode
        this.mailbox = mailbox
        this.initialFolder = initialFolder
        this.realm = realm
        this.okHttpClient = okHttpClient
        callbacks?.let {
            this.onStart = it.onStart
            this.onStop = it.onStop
        }
        this.endOfMessagesReached = false
    }

    private suspend fun refreshWithRunCatching(job: Job): Pair<Set<Thread>?, Throwable?> = runCatching {
        withContext(Dispatchers.IO + job) {
            onStart?.invoke()
            realm.handleRefreshMode(scope = this) to null
        }
    }.getOrElse {
        handleRefreshFailure(throwable = it)
    }

    private fun handleRefreshFailure(throwable: Throwable): Pair<Set<Thread>?, Throwable?> {

        val (returnThreads, exception) = if (throwable is ReturnThreadsException) {
            throwable.threads to throwable.exception
        } else {
            null to throwable
        }

        handleAllExceptions(exception)

        return returnThreads to exception
    }

    private suspend fun Realm.handleRefreshMode(scope: CoroutineScope): Set<Thread> {

        SentryLog.d(
            "API",
            "Start of refreshing threads with mode: $refreshMode | (${initialFolder.displayForSentry()})",
        )

        return when (refreshMode) {
            REFRESH_FOLDER_WITH_ROLE -> refreshWithRoleConsideration(scope)
            REFRESH_FOLDER -> {
                refresh(scope, initialFolder).also {
                    onStop?.invoke()
                    clearCallbacks()
                }
            }
            ONE_PAGE_OF_OLD_MESSAGES -> {
                fetchOnePageOfOldMessages(scope, initialFolder.id)
                emptySet()
            }
        }
    }

    private suspend fun Realm.refreshWithRoleConsideration(scope: CoroutineScope): Set<Thread> {

        val impactedThreads = refresh(scope, initialFolder)
        onStop?.invoke()
        clearCallbacks()

        if (initialFolder.role in FOLDER_ROLES_TO_REFRESH_TOGETHER) {
            for (role in FOLDER_ROLES_TO_REFRESH_TOGETHER) {
                scope.ensureActive()

                if (initialFolder.role == role) continue

                runCatching {
                    FolderController.getFolder(role, realm = this)?.let {
                        refresh(scope, folder = it)
                    }
                }.cancellable().onFailure {
                    throw ReturnThreadsException(impactedThreads, exception = it)
                }
            }
        }

        return impactedThreads
    }

    private suspend fun Realm.refresh(scope: CoroutineScope, folder: Folder): Set<Thread> {
        val impactedThreads = mainRefresh(scope, folder)
        extraRefresh(scope, folder)

        return impactedThreads
    }

    private suspend fun Realm.mainRefresh(scope: CoroutineScope, folder: Folder): MutableSet<Thread> {
        val previousCursor = folder.cursor
        val impactedThreads = mutableSetOf<Thread>()

        if (previousCursor == null) {
            fetchOldMessagesUids(scope, folder)
        } else {
            fetchActivities(scope, folder, previousCursor).also { hasTooManyActivities ->
                if (hasTooManyActivities) {
                    resetFolder(folder.id)
                    fetchOldMessagesUids(scope, folder)
                }
            }
        }

        impactedThreads += fetchAllNewPages(scope, folder.id)
        fetchAllOldPages(scope, folder.id)

        return impactedThreads
    }

    private suspend fun Realm.resetFolder(folderId: String) {
        write {
            val folder = getUpToDateFolder(folderId)

            MessageController.deleteMessages(appContext, mailbox, folder.messages(realm = this), realm = this)
            if (folder.threads.isNotEmpty()) delete(folder.threads)

            folder.lastUpdatedAt = null
            folder.cursor = null
            folder.unreadCountLocal = 0
            folder.oldMessagesUidsToFetch.clear()
            folder.newMessagesUidsToFetch.clear()
            folder.remainingOldMessagesToFetch = Utils.NUMBER_OF_OLD_MESSAGES_TO_FETCH
        }
    }

    private suspend fun Realm.extraRefresh(scope: CoroutineScope, folder: Folder) {
        // No need to check realm, there can't be any snoozed thread with a new message when there's a single message per thread
        if (localSettings.threadMode == ThreadMode.MESSAGE) return

        val impactedFolders = removeSnoozeStateOfThreadsWithNewMessages(scope, folder)
        impactedFolders.getFolderIds(realm = this).forEach { folderId ->
            scope.ensureActive()

            runCatching {
                FolderController.getFolder(folderId, realm = this)?.let {
                    mainRefresh(scope, folder = it)
                }
            }.cancellable().onFailure {
                SentryLog.e(TAG, "Throwable during extraRefresh", it)
            }
        }
    }

    private suspend fun Realm.removeSnoozeStateOfThreadsWithNewMessages(scope: CoroutineScope, folder: Folder): ImpactedFolders {
        if (folder.role != FolderRole.INBOX) return ImpactedFolders()

        val impactedFolders = ImpactedFolders()
        val cannotBeUnsnoozedThreadUids = mutableListOf<String>()

        ThreadController.getSnoozedThreadsWithNewMessage(folder.id, realm).forEach { snoozedThreadWithNewMessage ->
            scope.ensureActive()

            val result = SharedUtils.unsnoozeThreadWithoutRefresh(mailbox, snoozedThreadWithNewMessage)
            when (result) {
                is AutomaticUnsnoozeResult.Success -> impactedFolders += result.impactedFolders
                AutomaticUnsnoozeResult.CannotBeUnsnoozedError -> cannotBeUnsnoozedThreadUids += snoozedThreadWithNewMessage.uid
                AutomaticUnsnoozeResult.OtherError -> Unit
            }
        }

        write {
            cannotBeUnsnoozedThreadUids.forEach { manuallyUnsnoozeOutOfSyncThread(it) }

            if (cannotBeUnsnoozedThreadUids.isNotEmpty()) {
                FolderController.getFolder(FolderRole.SNOOZED, realm = this)?.id?.let { snoozeFolderId ->
                    recomputeTwinFoldersThreadsDependantProperties(snoozeFolderId)
                }
            }
        }

        return impactedFolders
    }

    private fun MutableRealm.manuallyUnsnoozeOutOfSyncThread(threadUid: String) {
        ThreadController.getThread(threadUid, realm = this)?.manuallyUnsnooze()
    }

    private suspend fun Realm.fetchOnePageOfOldMessages(scope: CoroutineScope, folderId: String) {

        var totalNewThreads = 0
        var upToDateFolder = getUpToDateFolder(folderId)
        var maxPagesToFetch = Utils.MAX_OLD_PAGES_TO_FETCH_TO_GET_ENOUGH_THREADS

        while (
            totalNewThreads < Utils.MIN_THREADS_TO_GET_ENOUGH_THREADS &&
            upToDateFolder.oldMessagesUidsToFetch.isNotEmpty() &&
            maxPagesToFetch > 0
        ) {
            val impactedThreads = fetchOnePage(scope, upToDateFolder, Direction.IN_THE_PAST)
            totalNewThreads += impactedThreads.count { it.folderId == upToDateFolder.id }
            upToDateFolder = getUpToDateFolder(folderId)
            maxPagesToFetch--
        }
    }

    private suspend fun Realm.fetchOldMessagesUids(scope: CoroutineScope, folder: Folder) {

        val result = getDateOrderedMessagesUids(folder.id)!!
        scope.ensureActive()

        FolderController.updateFolder(folder.id, realm = this) { _, it ->
            it.oldMessagesUidsToFetch.replaceContent(result.addedShortUids)
            it.lastUpdatedAt = Date().toRealmInstant()
            it.cursor = result.cursor
        }
    }

    private suspend fun Realm.fetchActivities(scope: CoroutineScope, folder: Folder, previousCursor: String): Boolean {

        val activities = when (folder.role) {
            FolderRole.SNOOZED -> getMessagesUidsDelta<SnoozeMessageFlags>(folder.id, previousCursor)
            else -> getMessagesUidsDelta<DefaultMessageFlags>(folder.id, previousCursor)
        } ?: return false
        scope.ensureActive()

        val logMessage = "Deleted: ${activities.deletedShortUids.count()} | " +
                "Updated: ${activities.updatedMessages.count()} | " +
                "Added: ${activities.addedShortUids.count()}"
        SentryLog.d("API", "$logMessage | ${folder.displayForSentry()}")

        if (hasTooManyActivities(activities)) return true

        addSentryBreadcrumbForActivities(logMessage, mailbox.email, folder, activities)

        var inboxUnreadCount: Int? = null
        write {
            val refreshStrategy = folder.refreshStrategy
            val impactedFolders = ImpactedFolders()
            impactedFolders += handleDeletedUids(scope, activities.deletedShortUids, folder.id, refreshStrategy)
            impactedFolders += handleUpdatedUids(scope, activities.updatedMessages, folder.id, refreshStrategy)

            inboxUnreadCount = updateFoldersUnreadCount(impactedFolders, realm = this)

            getUpToDateFolder(folder.id).let {
                it.newMessagesUidsToFetch.addAll(activities.addedShortUids)
                it.unreadCountRemote = activities.unreadCountRemote
                it.lastUpdatedAt = Date().toRealmInstant()
                if (it.role == FolderRole.SCHEDULED_DRAFTS) it.isDisplayed = it.threads.isNotEmpty()
                it.cursor = activities.cursor
                SentryDebug.addCursorBreadcrumb("fetchActivities", it, activities.cursor)
            }
        }

        updateMailboxUnreadCount(inboxUnreadCount)

        sendOrphanMessages(folder, previousCursor)

        return false
    }

    private fun hasTooManyActivities(activities: ActivitiesResult<out MessageFlags>): Boolean = with(activities) {

        val deletedCount = deletedShortUids.count()
        val updatedCount = updatedMessages.count()
        val addedCount = addedShortUids.count()
        val counts = listOf(deletedCount, updatedCount, addedCount)

        return if (counts.any { it > Utils.MAX_DELTA_PER_ACTIVITIES_CALL }) {
            SentryDebug.sendTooManyActivities(cursor, deletedCount, updatedCount, addedCount)
            true
        } else {
            false
        }
    }

    private suspend fun Realm.fetchAllNewPages(scope: CoroutineScope, folderId: String): Set<Thread> {

        val impactedThreads = mutableSetOf<Thread>()
        var folder = getUpToDateFolder(folderId)

        while (folder.newMessagesUidsToFetch.isNotEmpty()) {
            scope.ensureActive()

            impactedThreads += fetchOnePage(scope, folder, Direction.TO_THE_FUTURE)
            folder = getUpToDateFolder(folderId)
        }

        return impactedThreads
    }

    private suspend fun Realm.fetchAllOldPages(scope: CoroutineScope, folderId: String) {

        var folder = getUpToDateFolder(folderId)

        while (folder.remainingOldMessagesToFetch > 0) {
            scope.ensureActive()

            fetchOnePage(scope, folder, Direction.IN_THE_PAST)
            folder = getUpToDateFolder(folderId)
        }
    }

    private suspend fun Realm.fetchOnePage(scope: CoroutineScope, folder: Folder, direction: Direction): Set<Thread> {
        val (addedUids, remainingUids) = computeUids(folder, direction)

        val impactedThreads = handleAddedUids(scope, folder, addedUids)

        var inboxUnreadCount: Int? = null
        write {
            recomputeTwinFoldersThreadsDependantProperties(
                folderId = folder.id,
                extraFolderUpdates = { updateDirectionDependentData(direction, remainingUids, addedUids) },
            )

            inboxUnreadCount = updateFoldersUnreadCount(
                // `impactedThreads` may not contain `folder.id` in special folders cases (i.e. snooze)
                folders = ImpactedFolders(folderIds = impactedThreads.mapTo(mutableSetOf(folder.id)) { it.folderId }),
                realm = this,
            )
        }

        updateMailboxUnreadCount(inboxUnreadCount)

        sendOrphanMessages(folder)

        return impactedThreads
    }

    private fun List<Int>.getAtStart(quantity: Int) = subList(0, quantity) to subList(quantity, count())

    private fun List<Int>.getAtEnd(quantity: Int) = subList(count() - quantity, count()) to subList(0, count() - quantity)

    private fun computeUids(folder: Folder, direction: Direction): Pair<List<Int>, List<Int>> {
        val allUids = if (direction == Direction.TO_THE_FUTURE) folder.newMessagesUidsToFetch else folder.oldMessagesUidsToFetch
        return if (allUids.count() > Utils.PAGE_SIZE) {
            if (direction == Direction.TO_THE_FUTURE) allUids.getAtEnd(Utils.PAGE_SIZE) else allUids.getAtStart(Utils.PAGE_SIZE)
        } else {
            allUids to emptyList<Int>().toRealmList()
        }
    }

    private fun Folder.updateDirectionDependentData(direction: Direction, remainingUids: List<Int>, addedUids: List<Int>) {
        if (direction == Direction.TO_THE_FUTURE) {
            newMessagesUidsToFetch.replaceContent(remainingUids)
            lastUpdatedAt = Date().toRealmInstant()
        } else {
            oldMessagesUidsToFetch.replaceContent(remainingUids)
            remainingOldMessagesToFetch = if (oldMessagesUidsToFetch.isEmpty()) {
                0
            } else {
                (remainingOldMessagesToFetch - addedUids.count()).coerceAtLeast(0)
            }
        }
    }

    private fun updateFoldersUnreadCount(folders: ImpactedFolders, realm: MutableRealm): Int? {

        var inboxUnreadCount: Int? = null

        folders.getFolderIds(realm).forEach {
            val folder = realm.getUpToDateFolder(it)

            val unreadCount = ThreadController.getUnreadThreadsCount(folder)
            folder.unreadCountLocal = unreadCount

            if (folder.role == FolderRole.INBOX) inboxUnreadCount = unreadCount
        }

        return inboxUnreadCount
    }

    private suspend fun updateMailboxUnreadCount(unreadCount: Int?) {
        if (unreadCount == null) return

        mailboxController.updateMailbox(mailbox.objectId) {
            it.unreadCountLocal = unreadCount
        }
    }

    private fun TypedRealm.getUpToDateFolder(id: String) = FolderController.getFolder(id, realm = this)!!

    private fun TypedRealm.getUpToDateFolder(folderRole: FolderRole) = FolderController.getFolder(folderRole, realm = this)
    //endregion

    //region Added Messages
    private suspend fun Realm.handleAddedUids(
        scope: CoroutineScope,
        folder: Folder,
        uids: List<Int>,
    ): Set<Thread> {

        val logMessage = "Added: ${uids.count()}"
        SentryLog.d("API", "$logMessage | ${folder.displayForSentry()}")

        addSentryBreadcrumbForAddedUids(logMessage = logMessage, email = mailbox.email, folder = folder, uids = uids)

        if (uids.isEmpty()) return emptySet()

        val apiResponse = delayApiCallManager.getMessagesByUids(scope, mailbox.uuid, folder.id, uids, okHttpClient)
        if (!apiResponse.isSuccess()) apiResponse.throwErrorAsException()
        scope.ensureActive()

        return apiResponse.data?.messages?.let { messages ->
            reportMalformedSnoozeMessages(messages)

            return@let write {

                val upToDateFolder = getUpToDateFolder(folder.id)
                val isConversationMode = localSettings.threadMode == ThreadMode.CONVERSATION

                return@write handleAddedMessages(scope, upToDateFolder, messages, isConversationMode)
            }
        } ?: emptySet()
    }

    private fun reportMalformedSnoozeMessages(messages: List<Message>) {
        messages.forEach { message ->
            if (message.isSnoozeMalformed()) {
                Sentry.captureMessage("Message contains malformed snoozed or unsnoozed information") { scope ->
                    val date = message.snoozeEndDate?.toDate()?.format(FORMAT_DATE_WITH_TIMEZONE)

                    scope.level = SentryLevel.WARNING
                    scope.setExtra("messageUid", message.uid)
                    scope.setExtra("snoozeUuid", message.snoozeUuid)
                    scope.setExtra("snoozeEndDate", date)
                    scope.setTag("snoozeState", message.snoozeState?.apiValue)
                }
            }
        }
    }
    //endregion

    //region Deleted Messages
    private fun MutableRealm.handleDeletedUids(
        scope: CoroutineScope,
        shortUids: List<String>,
        folderId: String,
        currentFolderRefreshStrategy: RefreshStrategy,
    ): ImpactedFolders {
        if (shortUids.isEmpty()) return ImpactedFolders()

        val threads = mutableSetOf<Thread>()
        shortUids.forEach { shortUid ->
            scope.ensureActive()

            val message = currentFolderRefreshStrategy.getMessageFromShortUid(shortUid, folderId, realm = this) ?: return@forEach
            threads += message.threads
            threads += message.threadsDuplicatedIn

            currentFolderRefreshStrategy.processDeletedMessage(scope, message, appContext, mailbox, realm = this)
        }

        val impactedFolders = ImpactedFolders()
        threads.forEach { thread ->
            scope.ensureActive()

            currentFolderRefreshStrategy.addFolderToImpactedFolders(thread.folderId, impactedFolders)
            currentFolderRefreshStrategy.processDeletedThread(thread, realm = this)
        }

        if (currentFolderRefreshStrategy.shouldQueryFolderThreadsOnDeletedUid()) {
            recomputeTwinFoldersThreadsDependantProperties(folderId)
        }

        return impactedFolders
    }
    //endregion

    //region Updated Messages
    private fun MutableRealm.handleUpdatedUids(
        scope: CoroutineScope,
        messageFlags: List<MessageFlags>,
        folderId: String,
        refreshStrategy: RefreshStrategy,
    ): ImpactedFolders {
        val threads = mutableSetOf<Thread>()
        messageFlags.forEach { flags ->
            scope.ensureActive()

            refreshStrategy.getMessageFromShortUid(flags.shortUid, folderId, realm = this)?.let { message ->
                when (flags) {
                    is DefaultMessageFlags -> message.updateFlags(flags)
                    is SnoozeMessageFlags -> message.updateSnoozeFlags(flags)
                }
                threads += message.threads
                threads += message.threadsDuplicatedIn
            }
        }

        val impactedFolders = ImpactedFolders()
        threads.forEach { thread ->
            scope.ensureActive()

            refreshStrategy.addFolderToImpactedFolders(thread.folderId, impactedFolders)
            thread.recomputeThread(realm = this)
        }

        return impactedFolders
    }
    //endregion

    //region Create Threads
    private fun MutableRealm.handleAddedMessages(
        scope: CoroutineScope,
        folder: Folder,
        remoteMessages: List<Message>,
        isConversationMode: Boolean,
    ): Set<Thread> {

        val impactedThreadsManaged = mutableSetOf<Thread>()
        val addedMessagesUids = mutableListOf<Int>()
        val refreshStrategy = folder.refreshStrategy

        remoteMessages.forEach { remoteMessage ->
            scope.ensureActive()

            initMessageLocalValues(remoteMessage, folder)
            addedMessagesUids.add(remoteMessage.shortUid)
            refreshStrategy.handleAddedMessage(scope, remoteMessage, isConversationMode, impactedThreadsManaged, realm = this)

            MessageController.getMessage(remoteMessage.uid, realm = this)?.let { localMessage ->
                impactedThreadsManaged.addAll(localMessage.threadsDuplicatedIn)
            }
        }

        addSentryBreadcrumbForAddedUidsInFolder(addedMessagesUids)

        val impactedThreadsUnmanaged = mutableSetOf<Thread>()
        impactedThreadsManaged.forEach {
            scope.ensureActive()

            it.recomputeThread(realm = this)
            impactedThreadsUnmanaged.add(it.copyFromRealm(depth = 0u))
        }

        return impactedThreadsUnmanaged
    }

    private fun initMessageLocalValues(remoteMessage: Message, folder: Folder) {
        remoteMessage.initLocalValues(
            areHeavyDataFetched = false,
            isTrashed = folder.role == FolderRole.TRASH,
            messageIds = remoteMessage.computeMessageIds(),
            draftLocalUuid = null,
            isFromSearch = false,
            isDeletedOnApi = false,
            latestCalendarEventResponse = null,
            swissTransferFiles = realmListOf(),
            emojiReactions = realmListOf(),
        )
    }
    //endregion

    //region API calls
    private suspend fun getDateOrderedMessagesUids(folderId: String): NewMessagesResult? {
        return with(ApiRepository.getDateOrderedMessagesUids(mailbox.uuid, folderId, okHttpClient)) {
            if (!isSuccess()) throwErrorAsException()
            return@with data
        }
    }

    private suspend inline fun <reified T : MessageFlags> getMessagesUidsDelta(
        folderId: String,
        previousCursor: String,
    ): ActivitiesResult<T>? {
        return with(ApiRepository.getMessagesUidsDelta<T>(mailbox.uuid, folderId, previousCursor, okHttpClient)) {
            if (!isSuccess()) throwErrorAsException()
            return@with data
        }
    }
    //endregion

    //region Handle errors
    private fun handleAllExceptions(throwable: Throwable) {
        SentryLog.w(TAG, "Throwable during thread algorithm", throwable)

        if (throwable is ApiErrorException) throwable.handleOtherApiErrors()

        // This is the end. The `onStop` callback should be called before we are gone.
        onStop?.invoke()
        clearCallbacks()
    }

    private fun ApiErrorException.handleOtherApiErrors() {
        when (errorCode) {
            ErrorCode.FOLDER_DOES_NOT_EXIST -> Unit // Here, we want to fail silently. We are just outdated, it will be ok.
            ErrorCode.MESSAGE_NOT_FOUND -> Unit // This Sentry is already sent via `sendMessageNotFoundSentry()`
            else -> Sentry.captureException(this)
        }
    }

    private suspend fun Realm.sendOrphanMessages(folder: Folder, previousCursor: String? = null) {
        write {
            val upToDateFolder = getUpToDateFolder(folder.id)
            SentryDebug.sendOrphanMessages(previousCursor, folder = upToDateFolder, realm = this).also { orphans ->
                MessageController.deleteMessages(appContext, mailbox, orphans, realm = this)
            }
        }
    }

    private fun addSentryBreadcrumbForActivities(
        logMessage: String,
        email: String,
        folder: Folder,
        activities: ActivitiesResult<out MessageFlags>,
    ) {
        SentryDebug.addThreadsAlgoBreadcrumb(
            message = logMessage,
            data = mapOf(
                "1_mailbox" to email,
                "2_folderName" to folder.name,
                "3_folderId" to folder.id,
                "5_deleted" to activities.deletedShortUids.map { it },
                "6_updated" to activities.updatedMessages.map { it.shortUid },
                "7_added" to activities.addedShortUids.map { it },
            ),
        )
    }

    private fun addSentryBreadcrumbForAddedUids(
        logMessage: String,
        email: String,
        folder: Folder,
        uids: List<Int>,
    ) {
        SentryDebug.addThreadsAlgoBreadcrumb(
            message = logMessage,
            data = mapOf(
                "1_mailbox" to email,
                "2_folderName" to folder.name,
                "3_folderId" to folder.id,
                "4_added" to uids,
            ),
        )
    }

    private fun addSentryBreadcrumbForAddedUidsInFolder(uids: List<Int>) {
        SentryDebug.addThreadsAlgoBreadcrumb(
            message = "Added in Folder",
            data = mapOf(
                "uids" to uids,
            ),
        )
    }
    //endregion

    private fun MutableRealm.recomputeTwinFoldersThreadsDependantProperties(
        folderId: String,
        extraFolderUpdates: (Folder.() -> Unit)? = null,
    ) {
        val currentFolderRefreshStrategy: RefreshStrategy

        getUpToDateFolder(folderId).let { currentFolder ->
            recomputeThreadsDependantProperties(currentFolder, folderId)
            extraFolderUpdates?.invoke(currentFolder)
            currentFolderRefreshStrategy = currentFolder.refreshStrategy
        }

        currentFolderRefreshStrategy.twinFolderRoles().forEach { otherFolderRole ->
            getUpToDateFolder(otherFolderRole)?.let { otherFolder ->
                recomputeThreadsDependantProperties(otherFolder, folderId)
            }
        }
    }

    private fun MutableRealm.recomputeThreadsDependantProperties(folder: Folder, folderIdToQueryOn: String) {
        val refreshStrategy = folder.refreshStrategy
        val allThreads = refreshStrategy.queryFolderThreads(folderIdToQueryOn, realm = this)
        folder.threads.replaceContent(list = allThreads)

        if (refreshStrategy.shouldHideEmptyFolder()) {
            folder.isDisplayed = allThreads.isNotEmpty()
        }
    }

    // SCHEDULED_DRAFTS and SNOOZED need to be refreshed often because these folders
    // only appear in the MenuDrawer when there is at least 1 email in it.
    private val FOLDER_ROLES_TO_REFRESH_TOGETHER = setOf(
        FolderRole.INBOX,
        FolderRole.SENT,
        FolderRole.DRAFT,
        FolderRole.SCHEDULED_DRAFTS,
        FolderRole.SNOOZED,
    )

    enum class RefreshMode {
        REFRESH_FOLDER, /* Fetch activities, and also get old Messages until `NUMBER_OF_OLD_MESSAGES_TO_FETCH` is reached */
        REFRESH_FOLDER_WITH_ROLE, /* Same, but also check if other Folders need to be updated (Inbox, Sent, Draft, etc…) */
        ONE_PAGE_OF_OLD_MESSAGES, /* Get 1 page of old Messages */
    }

    private enum class Direction {
        IN_THE_PAST, /* To get more pages of old Messages */
        TO_THE_FUTURE, /* To get more pages of new Messages */
    }

    private class ReturnThreadsException(val threads: Set<Thread>, val exception: Throwable) : Exception()

    private class ForcedCancellationException : CancellationException()

    data class RefreshCallbacks(
        val onStart: (() -> Unit),
        val onStop: (() -> Unit),
    )

    companion object {
        private val TAG = RefreshController::class.java.simpleName
    }
}
