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
package com.infomaniak.mail.ui

import android.app.Application
import androidx.lifecycle.*
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.NetworkAvailability
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.lib.core.utils.DownloadManagerUtils
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.MatomoMail.trackMultiSelectionEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.*
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshCallbacks
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.cache.mailboxInfo.PermissionsController
import com.infomaniak.mail.data.cache.mailboxInfo.QuotasController
import com.infomaniak.mail.data.cache.userInfo.AddressBookController
import com.infomaniak.mail.data.cache.userInfo.MergedContactController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.MoveResult
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.mailbox.SendersRestrictions
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.snooze.BatchSnoozeResponse.Companion.computeSnoozeResult
import com.infomaniak.mail.data.models.snooze.BatchSnoozeResult
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MailboxInfoRealm
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.SnackbarManager.UndoData
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.ContactUtils.getPhoneContacts
import com.infomaniak.mail.utils.ContactUtils.mergeApiContactsIntoPhoneContacts
import com.infomaniak.mail.utils.NotificationUtils.Companion.cancelNotification
import com.infomaniak.mail.utils.SharedUtils.Companion.updateSignatures
import com.infomaniak.mail.utils.Utils.EML_CONTENT_TYPE
import com.infomaniak.mail.utils.Utils.isPermanentDeleteFolder
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.extensions.*
import com.infomaniak.mail.views.itemViews.AvatarMergedContactData
import com.infomaniak.mail.views.itemViews.MyKSuiteStorageBanner.StorageLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.notifications.ResultsChange
import io.sentry.Attachment
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import com.infomaniak.lib.core.R as RCore

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    avatarMergedContactData: AvatarMergedContactData,
    private val addressBookController: AddressBookController,
    private val folderController: FolderController,
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val mailboxController: MailboxController,
    private val mergedContactController: MergedContactController,
    private val messageController: MessageController,
    private val myKSuiteDataUtils: MyKSuiteDataUtils,
    private val notificationUtils: NotificationUtils,
    private val permissionsController: PermissionsController,
    private val quotasController: QuotasController,
    private val refreshController: RefreshController,
    private val sharedUtils: SharedUtils,
    private val threadController: ThreadController,
    private val snackbarManager: SnackbarManager,
    @MailboxInfoRealm private val mailboxInfoRealm: Realm,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)
    private var refreshEverythingJob: Job? = null

    @Inject
    lateinit var localSettings: LocalSettings

    val isDownloadingChanges: MutableLiveData<Boolean> = MutableLiveData(false)
    val isMovedToNewFolder = SingleLiveEvent<Boolean>()
    val toggleLightThemeForMessage = SingleLiveEvent<Message>()
    val deletedMessages = SingleLiveEvent<Set<String>>()
    val deleteThreadOrMessageTrigger = SingleLiveEvent<Unit>()
    val flushFolderTrigger = SingleLiveEvent<Unit>()
    val newFolderResultTrigger = MutableLiveData<Unit>()
    val reportPhishingTrigger = SingleLiveEvent<Unit>()
    val reportDisplayProblemTrigger = SingleLiveEvent<Unit>()
    val canInstallUpdate = MutableLiveData(false)
    val messageOfUserToBlock = SingleLiveEvent<Message>()

    val autoAdvanceThreadsUids = SingleLiveEvent<List<String>>()

    val mailboxesLive = mailboxController.getMailboxesAsync(AccountUtils.currentUserId).asLiveData(ioCoroutineContext)

    //region Multi selection
    val isMultiSelectOnLiveData = MutableLiveData(false)
    inline var isMultiSelectOn
        get() = isMultiSelectOnLiveData.value!!
        set(value) {
            isMultiSelectOnLiveData.value = value
        }

    val selectedThreadsLiveData = MutableLiveData(mutableSetOf<Thread>())
    inline val selectedThreads
        get() = selectedThreadsLiveData.value!!

    val isEverythingSelected
        get() = runCatchingRealm { selectedThreads.count() == currentThreadsLive.value?.list?.count() }.getOrDefault(false)
    //endregion

    //region Current Mailbox
    private val _currentMailboxObjectId = MutableStateFlow<String?>(null)

    val currentMailbox = _currentMailboxObjectId.mapLatest {
        it?.let(mailboxController::getMailbox)
    }.asLiveData(ioCoroutineContext)

    val defaultFoldersLive = _currentMailboxObjectId.filterNotNull().flatMapLatest {
        folderController.getMenuDrawerDefaultFoldersAsync()
            .map { it.list.flattenFolderChildrenAndRemoveMessages(dismissHiddenChildren = true) }
    }.asLiveData(ioCoroutineContext)

    val customFoldersLive = _currentMailboxObjectId.filterNotNull().flatMapLatest {
        folderController.getMenuDrawerCustomFoldersAsync()
            .map { it.list.flattenFolderChildrenAndRemoveMessages(dismissHiddenChildren = true) }
    }.asLiveData(ioCoroutineContext)

    val currentQuotasLive = _currentMailboxObjectId.flatMapLatest {
        it?.let(quotasController::getQuotasAsync) ?: emptyFlow()
    }.asLiveData(ioCoroutineContext)

    val storageBannerStatus = currentQuotasLive.map { quotas ->
        when {
            quotas == null -> null
            quotas.isFull -> StorageLevel.Full
            quotas.getProgress() > StorageLevel.WARNING_THRESHOLD -> {
                if (!localSettings.hasClosedStorageBanner || localSettings.storageBannerDisplayAppLaunches % 10 == 0) {
                    localSettings.hasClosedStorageBanner = false
                    StorageLevel.Warning
                } else {
                    StorageLevel.Normal
                }
            }
            else -> StorageLevel.Normal
        }
    }

    val currentPermissionsLive = _currentMailboxObjectId.flatMapLatest {
        it?.let(permissionsController::getPermissionsAsync) ?: emptyFlow()
    }.asLiveData(ioCoroutineContext)
    //endregion

    //region Current Folder
    private val _currentFolderId = MutableStateFlow<String?>(null)
    val currentFolderId get() = _currentFolderId.value

    val currentFolder = _currentFolderId.mapLatest {
        it?.let(folderController::getFolder)
    }.asLiveData(ioCoroutineContext)

    val currentFolderLive = _currentFolderId.flatMapLatest {
        it?.let(folderController::getFolderAsync) ?: emptyFlow()
    }.asLiveData(ioCoroutineContext)

    val currentFilter = SingleLiveEvent(ThreadFilter.ALL)

    val currentThreadsLive = MutableLiveData<ResultsChange<Thread>>()

    val isNetworkAvailable = NetworkAvailability(appContext).isNetworkAvailable
        .mapLatest {
            SentryLog.d("Internet availability", if (it) "Available" else "Unavailable")
            it
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    inline val hasNetwork get() = isNetworkAvailable.value != false

    private var currentThreadsLiveJob: Job? = null

    fun reassignCurrentThreadsLive() {
        currentThreadsLiveJob?.cancel()
        currentThreadsLiveJob = viewModelScope.launch(ioCoroutineContext) {
            observeFolderAndFilter()
                .flatMapLatest { (folder, filter) ->
                    folder?.let { threadController.getThreadsAsync(it, filter) } ?: emptyFlow()
                }
                .collect(currentThreadsLive::postValue)
        }
    }

    private fun observeFolderAndFilter() = MediatorLiveData<Pair<Folder?, ThreadFilter>>().apply {
        postValue(currentFolder.value to currentFilter.value!!)
        addSource(currentFolder) { postValue(it to value!!.second) }
        addSource(currentFilter) { postValue(value?.first to it) }
    }.asFlow()

    override fun onCleared() {
        RealmDatabase.closeOldRealms()
        super.onCleared()
    }

    /**
     * Force update the `currentFilter` to its current value.
     * The sole effect will be to force the `currentThreadsLive` to trigger immediately.
     * The idea is to force the Threads adapter to trigger again, to update the sections headers (Today, Yesterday, etc...).
     */
    fun forceTriggerCurrentFolder() {
        currentFilter.apply { value = value }
    }
    //endregion

    //region Merged Contacts
    val mergedContactsLive: LiveData<MergedContactDictionary> = avatarMergedContactData.mergedContactLiveData
    //endregion

    //region Scheduled Draft
    var draftResource: String? = null
    //endregion

    //region Share Thread URL
    private val _shareThreadUrlResult = MutableSharedFlow<String?>()
    val shareThreadUrlResult = _shareThreadUrlResult.shareIn(viewModelScope, SharingStarted.Lazily)
    //endregion

    fun updateUserInfo() = viewModelScope.launch(ioCoroutineContext) {
        SentryLog.d(TAG, "Update user info")
        updateAddressBooks()
        updateContacts()
    }

    fun loadCurrentMailboxFromLocal() = liveData(ioCoroutineContext) {
        emit(openMailbox())
    }

    private fun openMailbox(): Mailbox? {
        SentryLog.d(TAG, "Load current mailbox from local")

        val mailbox = mailboxController.getMailboxWithFallback(
            userId = AccountUtils.currentUserId,
            mailboxId = AccountUtils.currentMailboxId,
        ) ?: return null

        if (!mailbox.isAvailable) {
            switchToValidMailbox()
            return null
        }

        selectMailbox(mailbox)

        if (currentFolderId == null) {
            folderController.getFolder(DEFAULT_SELECTED_FOLDER)?.let { folder ->
                selectFolder(folder.id)
            }
        }

        return mailbox
    }

    private fun switchToValidMailbox() = viewModelScope.launch(ioCoroutineContext) {
        mailboxController.getFirstValidMailbox(AccountUtils.currentUserId)?.let {
            AccountUtils.switchToMailbox(it.mailboxId)
        } ?: appContext.launchNoValidMailboxesActivity()
    }

    fun dismissCurrentMailboxNotifications() = viewModelScope.launch(ioCoroutineContext) {
        currentMailbox.value?.let {
            appContext.cancelNotification(it.notificationGroupId)
        }
    }

    fun refreshEverything() {

        refreshEverythingJob?.cancel()
        refreshEverythingJob = viewModelScope.launch(ioCoroutineContext) {

            // Refresh User
            AccountUtils.updateCurrentUser()

            // Refresh My kSuite asynchronously, because it's not required for the threads list display
            launch { myKSuiteDataUtils.fetchData() }

            // Refresh Mailboxes
            SentryLog.d(TAG, "Refresh mailboxes from remote")
            with(ApiRepository.getMailboxes()) {
                if (isSuccess()) {
                    mailboxController.updateMailboxes(data!!)

                    val shouldStop = AccountUtils.manageMailboxesEdgeCases(appContext, data!!)
                    if (shouldStop) return@launch
                }
            }

            // Refresh Mailbox content
            val mailbox = currentMailbox.value ?: openMailbox() ?: return@launch

            // These updates are parallelized so they won't slow down the flow.
            updateQuotas(mailbox)
            updatePermissions(mailbox)
            updateSignatures(mailbox)
            updateSendersRestrictions(mailbox)
            updateFeatureFlag(mailbox)
            updateExternalMailInfo(mailbox)

            removeThreadsWithParentalIssues()

            // This update is blocking because we need it for the rest of the flow : `selectFolder()` needs the Folders.
            updateFolders(mailbox)

            // Refresh Threads
            (currentFolderId?.let(folderController::getFolder) ?: folderController.getFolder(DEFAULT_SELECTED_FOLDER))
                ?.let { folder ->
                    selectFolder(folder.id)
                    viewModelScope.launch(ioCoroutineContext) {
                        refreshThreads(mailbox, folder.id)
                    }
                }
        }
    }

    private fun selectMailbox(mailbox: Mailbox) {
        if (mailbox.objectId != _currentMailboxObjectId.value) {
            SentryLog.d(TAG, "Select mailbox: ${mailbox.email}")
            if (mailbox.mailboxId != AccountUtils.currentMailboxId) AccountUtils.currentMailboxId = mailbox.mailboxId
            AccountUtils.currentMailboxEmail = mailbox.email
            _currentMailboxObjectId.value = mailbox.objectId
            _currentFolderId.value = null
            notificationUtils.initMailNotificationChannel(mailbox)
        }
    }

    private fun selectFolder(folderId: String) {
        if (folderId != currentFolderId) {
            SentryLog.d(TAG, "Select folder: $folderId")
            _currentFolderId.value = folderId
        }
    }

    private fun updateQuotas(mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        SentryLog.d(TAG, "Force refresh Quotas")
        if (mailbox.isLimited) with(ApiRepository.getQuotas(mailbox.hostingId, mailbox.mailboxName)) {
            if (isSuccess()) {
                mailboxController.updateMailbox(mailbox.objectId) {
                    it.quotas = data
                }
            }
        }
    }

    private fun updatePermissions(mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        SentryLog.d(TAG, "Force refresh Permissions")
        with(ApiRepository.getPermissions(mailbox.linkId, mailbox.hostingId)) {
            if (isSuccess()) mailboxController.updateMailbox(mailbox.objectId) {
                it.permissions = data
            }
        }
    }

    private fun updateSignatures(mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        SentryLog.d(TAG, "Force refresh Signatures")
        updateSignatures(mailbox, mailboxInfoRealm)
    }

    //region Spam
    fun moveToSpamFolder(threadUid: String, messageUid: String) = viewModelScope.launch(ioCoroutineContext) {
        val message = messageController.getMessage(messageUid) ?: return@launch
        toggleMessageSpamStatus(threadUid, message)
    }

    fun activateSpamFilter() = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value ?: return@launch

        ApiRepository.setSpamFilter(
            mailboxHostingId = mailbox.hostingId,
            mailboxName = mailbox.mailboxName,
            activateSpamFilter = true,
        )
    }

    fun unblockMail(email: String) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value ?: return@launch

        with(ApiRepository.getSendersRestrictions(mailbox.hostingId, mailbox.mailboxName)) {
            if (isSuccess()) {
                val updatedSendersRestrictions = data!!.apply {
                    blockedSenders.removeIf { it.email == email }
                }
                updateBlockedSenders(mailbox, updatedSendersRestrictions)
            }
        }
    }

    private suspend fun updateBlockedSenders(mailbox: Mailbox, updatedSendersRestrictions: SendersRestrictions) {
        with(ApiRepository.updateBlockedSenders(mailbox.hostingId, mailbox.mailboxName, updatedSendersRestrictions)) {
            if (isSuccess()) {
                mailboxController.updateMailbox(mailbox.objectId) {
                    it.sendersRestrictions = updatedSendersRestrictions
                }
            }
        }
    }

    private fun updateSendersRestrictions(mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        SentryLog.d(TAG, "Force refresh Senders Restrictions")

        with(ApiRepository.getSendersRestrictions(mailbox.hostingId, mailbox.mailboxName)) {
            if (isSuccess()) {
                mailboxController.updateMailbox(mailbox.objectId) {
                    it.sendersRestrictions = data
                }
            }
        }
    }
    //endregion

    private fun updateFeatureFlag(mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        SentryLog.d(TAG, "Force refresh Features flags")
        sharedUtils.updateFeatureFlags(mailbox.objectId, mailbox.uuid)
    }

    private fun updateExternalMailInfo(mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        SentryLog.d(TAG, "Force refresh External Mail info")
        with(ApiRepository.getExternalMailInfo(mailbox.hostingId, mailbox.mailboxName)) {
            if (!isSuccess()) return@launch
            data?.let { externalMailInfo ->
                mailboxController.updateMailbox(mailbox.objectId) {
                    it.externalMailFlagEnabled = externalMailInfo.externalMailFlagEnabled
                    it.trustedDomains = externalMailInfo.trustedDomains.toRealmList()
                }
            }
        }
    }

    private suspend fun updateFolders(mailbox: Mailbox) {
        SentryLog.d(TAG, "Force refresh Folders")
        ApiRepository.getFolders(mailbox.uuid).data?.let { folders ->
            if (!mailboxContentRealm().isClosed()) folderController.update(mailbox, folders, mailboxContentRealm())
        }
    }

    fun openFolder(folderId: String) = viewModelScope.launch(ioCoroutineContext) {
        if (folderId == currentFolderId) return@launch

        if (currentFilter.value != ThreadFilter.ALL) currentFilter.postValue(ThreadFilter.ALL)

        selectFolder(folderId)
        refreshThreads(folderId = folderId)
    }

    fun flushFolder() = viewModelScope.launch(ioCoroutineContext) {
        val mailboxUuid = currentMailbox.value?.uuid ?: return@launch
        val folderId = currentFolderId ?: return@launch

        with(ApiRepository.flushFolder(mailboxUuid, folderId)) {
            flushFolderTrigger.postValue(Unit)
            if (isSuccess()) {
                forceRefreshThreads()
            } else {
                snackbarManager.postValue(appContext.getString(translatedError))
            }
        }
    }

    fun forceRefreshThreads(showSwipeRefreshLayout: Boolean = true) = viewModelScope.launch(ioCoroutineContext) {
        SentryLog.d(TAG, "Force refresh threads")
        refreshThreads(showSwipeRefreshLayout = showSwipeRefreshLayout)
    }

    fun getOnePageOfOldMessages() = viewModelScope.launch(ioCoroutineContext) {

        if (isDownloadingChanges.value == true) return@launch

        refreshController.refreshThreads(
            refreshMode = RefreshMode.ONE_PAGE_OF_OLD_MESSAGES,
            mailbox = currentMailbox.value!!,
            folderId = currentFolderId!!,
            realm = mailboxContentRealm(),
            callbacks = RefreshCallbacks(::onDownloadStart, ::onDownloadStop),
        )
    }

    private suspend fun updateAddressBooks() {
        ApiRepository.getAddressBooks().data?.addressBooks?.let { addressBookController.update(it) }
    }

    private suspend fun updateContacts() {
        ApiRepository.getContacts().data?.let { apiContacts ->
            val phoneMergedContacts = getPhoneContacts(appContext)
            mergeApiContactsIntoPhoneContacts(apiContacts, phoneMergedContacts)
            mergedContactController.update(phoneMergedContacts.values.toList())
        }
    }

    private suspend fun refreshThreads(
        mailbox: Mailbox? = currentMailbox.value,
        folderId: String? = currentFolderId,
        showSwipeRefreshLayout: Boolean = true,
    ) {
        if (mailbox == null || folderId == null) return

        val callbacks = if (showSwipeRefreshLayout) RefreshCallbacks(::onDownloadStart, ::onDownloadStop) else null

        refreshController.refreshThreads(
            refreshMode = RefreshMode.REFRESH_FOLDER_WITH_ROLE,
            mailbox = mailbox,
            folderId = folderId,
            realm = mailboxContentRealm(),
            callbacks = callbacks,
        )
    }

    //region Delete
    fun deleteMessage(threadUid: String, message: Message) {
        deleteThreadsOrMessage(threadsUids = listOf(threadUid), message = message)
    }

    fun deleteThread(threadUid: String, isSwipe: Boolean = false) {
        deleteThreadsOrMessage(threadsUids = listOf(threadUid), isSwipe = isSwipe)
    }

    fun deleteThreads(threadsUids: List<String>) {
        deleteThreadsOrMessage(threadsUids = threadsUids)
    }

    // TODO: When the back is done refactoring how scheduled drafts are deleted, work on this function shall resume.
    private fun deleteThreadsOrMessage(
        threadsUids: List<String>,
        message: Message? = null,
        isSwipe: Boolean = false,
    ) = viewModelScope.launch(ioCoroutineContext) {

        val mailbox = currentMailbox.value!!
        val threads = threadController.getThreads(threadsUids).ifEmpty { return@launch }
        var trashId: String? = null
        var undoResources = emptyList<String>()
        val shouldPermanentlyDelete = isPermanentDeleteFolder(getActionFolderRole(threads, message))

        val messages = getMessagesToDelete(threads, message)
        val uids = messages.getUids()

        threadController.updateIsLocallyMovedOutStatus(threadsUids, hasBeenMovedOut = true)

        val apiResponses = if (shouldPermanentlyDelete) {
            ApiRepository.deleteMessages(mailbox.uuid, uids)
        } else {
            trashId = folderController.getFolder(FolderRole.TRASH)!!.id
            ApiRepository.moveMessages(mailbox.uuid, uids, trashId).also {
                undoResources = it.mapNotNull { apiResponse -> apiResponse.data?.undoResource }
            }
        }

        deleteThreadOrMessageTrigger.postValue(Unit)

        if (apiResponses.atLeastOneSucceeded()) {
            if (shouldAutoAdvance(message, threadsUids)) autoAdvanceThreadsUids.postValue(threadsUids)

            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(exception = trashId),
                destinationFolderId = trashId,
                callbacks = RefreshCallbacks(onStart = ::onDownloadStart, onStop = { onDownloadStop(threadsUids) }),
            )
        } else if (isSwipe) {
            // We need to make the swiped Thread come back, so we reassign the LiveData with Realm values
            reassignCurrentThreadsLive()
        }

        threadController.updateIsLocallyMovedOutStatus(threadsUids, hasBeenMovedOut = false)

        val undoDestinationId = message?.folderId ?: threads.first().folderId
        val undoFoldersIds = messages.getFoldersIds(exception = undoDestinationId)
        if (trashId != null) undoFoldersIds += trashId
        showDeleteSnackbar(
            apiResponses,
            shouldPermanentlyDelete,
            message,
            undoResources,
            undoFoldersIds,
            undoDestinationId,
            threads.count(),
        )
    }

    private fun showDeleteSnackbar(
        apiResponses: List<ApiResponse<*>>,
        shouldPermanentlyDelete: Boolean,
        message: Message?,
        undoResources: List<String>,
        undoFoldersIds: ImpactedFolders,
        undoDestinationId: String?,
        numberOfImpactedThreads: Int,
    ) {

        val snackbarTitle = if (apiResponses.atLeastOneSucceeded()) {
            val destination = appContext.getString(FolderRole.TRASH.folderNameRes)
            when {
                shouldPermanentlyDelete && message == null -> {
                    appContext.resources.getQuantityString(R.plurals.snackbarThreadDeletedPermanently, numberOfImpactedThreads)
                }
                shouldPermanentlyDelete && message != null -> {
                    appContext.getString(R.string.snackbarMessageDeletedPermanently)
                }
                !shouldPermanentlyDelete && message == null -> {
                    appContext.resources.getQuantityString(R.plurals.snackbarThreadMoved, numberOfImpactedThreads, destination)
                }
                else -> appContext.getString(R.string.snackbarMessageMoved, destination)
            }
        } else {
            appContext.getString(apiResponses.first().translatedError)
        }

        val undoData = if (undoResources.isEmpty()) null else UndoData(undoResources, undoFoldersIds, undoDestinationId)

        snackbarManager.postValue(snackbarTitle, undoData)
    }

    private fun getMessagesToDelete(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap(messageController::getUnscheduledMessages)
        else -> messageController.getMessageAndDuplicates(threads.first(), message)
    }

    fun deleteDraft(targetMailboxUuid: String, remoteDraftUuid: String) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value!!
        val apiResponse = ApiRepository.deleteDraft(targetMailboxUuid, remoteDraftUuid)

        if (apiResponse.isSuccess() && mailbox.uuid == targetMailboxUuid) {
            val draftFolderId = folderController.getFolder(FolderRole.DRAFT)!!.id
            refreshFoldersAsync(mailbox, ImpactedFolders(mutableSetOf(draftFolderId)))
        }

        showDeletedDraftSnackbar(apiResponse)
    }

    private fun showDeletedDraftSnackbar(apiResponse: ApiResponse<Unit>) {
        val titleRes = if (apiResponse.isSuccess()) R.string.snackbarDraftDeleted else apiResponse.translateError()
        snackbarManager.postValue(appContext.getString(titleRes))
    }
    //endregion

    //region Scheduled Drafts
    fun rescheduleDraft(scheduleDate: Date) = viewModelScope.launch(ioCoroutineContext) {
        draftResource?.takeIf { it.isNotBlank() }?.let { resource ->
            with(ApiRepository.rescheduleDraft(resource, scheduleDate)) {
                if (isSuccess()) {
                    refreshFoldersAsync(currentMailbox.value!!, ImpactedFolders(mutableSetOf(FolderRole.SCHEDULED_DRAFTS)))
                } else {
                    snackbarManager.postValue(title = appContext.getString(translatedError))
                }
            }
        } ?: run {
            snackbarManager.postValue(title = appContext.getString(RCore.string.anErrorHasOccurred))
        }
    }

    fun modifyScheduledDraft(
        unscheduleDraftUrl: String,
        onSuccess: () -> Unit,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value!!
        val apiResponse = ApiRepository.unscheduleDraft(unscheduleDraftUrl)

        if (apiResponse.isSuccess()) {
            val scheduledDraftsFolderId = folderController.getFolder(FolderRole.SCHEDULED_DRAFTS)!!.id
            refreshFoldersAsync(mailbox, ImpactedFolders(mutableSetOf(scheduledDraftsFolderId)))
            onSuccess()
        } else {
            snackbarManager.postValue(title = appContext.getString(apiResponse.translatedError))
        }
    }

    fun unscheduleDraft(unscheduleDraftUrl: String) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value!!
        val apiResponse = ApiRepository.unscheduleDraft(unscheduleDraftUrl)

        if (apiResponse.isSuccess()) {
            val scheduledDraftsFolderId = folderController.getFolder(FolderRole.SCHEDULED_DRAFTS)!!.id
            refreshFoldersAsync(mailbox, ImpactedFolders(mutableSetOf(scheduledDraftsFolderId)))
        }

        showUnscheduledDraftSnackbar(apiResponse)
    }

    private fun showUnscheduledDraftSnackbar(apiResponse: ApiResponse<Unit>) {

        fun openDraftFolder() = folderController.getFolder(FolderRole.DRAFT)?.id?.let(::openFolder)

        if (apiResponse.isSuccess()) {
            snackbarManager.postValue(
                title = appContext.getString(R.string.snackbarSaveInDraft),
                buttonTitle = R.string.draftFolder,
                customBehavior = ::openDraftFolder,
            )
        } else {
            snackbarManager.postValue(appContext.getString(apiResponse.translateError()))
        }
    }
    //endregion

    //region Move
    fun moveThreadsOrMessageTo(
        destinationFolderId: String,
        threadsUids: List<String>,
        messageUid: String? = null,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value!!
        val destinationFolder = folderController.getFolder(destinationFolderId)!!
        val threads = threadController.getThreads(threadsUids).ifEmpty { return@launch }
        val message = messageUid?.let { messageController.getMessage(it)!! }

        val messages = sharedUtils.getMessagesToMove(threads, message)

        threadController.updateIsLocallyMovedOutStatus(threadsUids, hasBeenMovedOut = true)

        val apiResponses = ApiRepository.moveMessages(mailbox.uuid, messages.getUids(), destinationFolder.id)

        if (apiResponses.atLeastOneSucceeded()) {
            if (shouldAutoAdvance(message, threadsUids)) autoAdvanceThreadsUids.postValue(threadsUids)

            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(exception = destinationFolder.id),
                destinationFolderId = destinationFolder.id,
                callbacks = RefreshCallbacks(onStart = ::onDownloadStart, onStop = { onDownloadStop(threadsUids) }),
            )
        }

        threadController.updateIsLocallyMovedOutStatus(threadsUids, hasBeenMovedOut = false)

        showMoveSnackbar(threads, message, messages, apiResponses, destinationFolder)
    }

    private fun showMoveSnackbar(
        threads: List<Thread>,
        message: Message?,
        messages: List<Message>,
        apiResponses: List<ApiResponse<MoveResult>>,
        destinationFolder: Folder,
    ) {

        val destination = destinationFolder.getLocalizedName(appContext)

        val snackbarTitle = when {
            apiResponses.allFailed() -> appContext.getString(apiResponses.first().translatedError)
            message == null -> appContext.resources.getQuantityString(R.plurals.snackbarThreadMoved, threads.count(), destination)
            else -> appContext.getString(R.string.snackbarMessageMoved, destination)
        }

        val undoResources = apiResponses.mapNotNull { it.data?.undoResource }
        val undoData = if (undoResources.isEmpty()) {
            null
        } else {
            val undoDestinationId = message?.folderId ?: threads.first().folderId
            val foldersIds = messages.getFoldersIds(exception = undoDestinationId)
            foldersIds += destinationFolder.id
            UndoData(
                resources = apiResponses.mapNotNull { it.data?.undoResource },
                foldersIds = foldersIds,
                destinationFolderId = undoDestinationId,
            )
        }

        snackbarManager.postValue(snackbarTitle, undoData)
    }
    //endregion

    //region Archive
    fun archiveMessage(threadUid: String, message: Message) {
        archiveThreadsOrMessage(threadsUids = listOf(threadUid), message = message)
    }

    fun archiveThread(threadUid: String) {
        archiveThreadsOrMessage(threadsUids = listOf(threadUid))
    }

    fun archiveThreads(threadsUids: List<String>) {
        archiveThreadsOrMessage(threadsUids = threadsUids)
    }

    private fun archiveThreadsOrMessage(
        threadsUids: List<String>,
        message: Message? = null,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value!!
        val threads = threadController.getThreads(threadsUids).ifEmpty { return@launch }

        val role = getActionFolderRole(threads, message)
        val isFromArchive = role == FolderRole.ARCHIVE

        val destinationFolderRole = if (isFromArchive) FolderRole.INBOX else FolderRole.ARCHIVE
        val destinationFolder = folderController.getFolder(destinationFolderRole)!!

        val messages = sharedUtils.getMessagesToMove(threads, message)

        val apiResponses = ApiRepository.moveMessages(mailbox.uuid, messages.getUids(), destinationFolder.id)

        if (apiResponses.atLeastOneSucceeded()) {
            if (shouldAutoAdvance(message, threadsUids)) autoAdvanceThreadsUids.postValue(threadsUids)

            val messagesFoldersIds = messages.getFoldersIds(exception = destinationFolder.id)
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messagesFoldersIds,
                destinationFolderId = destinationFolder.id,
                callbacks = RefreshCallbacks(onStart = ::onDownloadStart, onStop = { onDownloadStop(threadsUids) }),
            )
        }

        showMoveSnackbar(threads, message, messages, apiResponses, destinationFolder)
    }
    //endregion

    //region Seen
    fun toggleMessageSeenStatus(threadUid: String, message: Message) {
        toggleThreadsOrMessageSeenStatus(threadsUids = listOf(threadUid), message = message)
    }

    fun toggleThreadSeenStatus(threadUid: String) {
        toggleThreadsOrMessageSeenStatus(threadsUids = listOf(threadUid))
    }

    fun toggleThreadsSeenStatus(threadsUids: List<String>, shouldRead: Boolean) {
        toggleThreadsOrMessageSeenStatus(threadsUids = threadsUids, shouldRead = shouldRead)
    }

    private fun toggleThreadsOrMessageSeenStatus(
        threadsUids: List<String>,
        message: Message? = null,
        shouldRead: Boolean = true,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value!!
        val threads = threadController.getThreads(threadsUids).ifEmpty { return@launch }

        val isSeen = when {
            message != null -> message.isSeen
            threads.count() == 1 -> threads.single().unseenMessagesCount == 0
            else -> !shouldRead
        }

        if (isSeen) {
            markAsUnseen(mailbox, threads, message)
        } else {
            sharedUtils.markAsSeen(
                mailbox = mailbox,
                threads = threads,
                message = message,
                currentFolderId = currentFolderId,
                callbacks = RefreshCallbacks(::onDownloadStart, ::onDownloadStop),
            )
        }
    }

    private suspend fun markAsUnseen(
        mailbox: Mailbox,
        threads: List<Thread>,
        message: Message? = null,
    ) {

        val messages = getMessagesToMarkAsUnseen(threads, message)
        val threadsUids = threads.map { it.uid }
        val messagesUids = messages.map { it.uid }

        updateSeenStatus(threadsUids, messagesUids, isSeen = false)

        val apiResponses = ApiRepository.markMessagesAsUnseen(mailbox.uuid, messagesUids)

        if (apiResponses.atLeastOneSucceeded()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(),
                callbacks = RefreshCallbacks(::onDownloadStart, ::onDownloadStop),
            )
        } else {
            updateSeenStatus(threadsUids, messagesUids, isSeen = true)
        }
    }

    private fun getMessagesToMarkAsUnseen(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap(messageController::getLastMessageAndItsDuplicatesToExecuteAction)
        else -> messageController.getMessageAndDuplicates(threads.first(), message)
    }

    private suspend fun updateSeenStatus(threadsUids: List<String>, messagesUids: List<String>, isSeen: Boolean) {
        mailboxContentRealm().write {
            MessageController.updateSeenStatus(messagesUids, isSeen, realm = this)
            ThreadController.updateSeenStatus(threadsUids, isSeen, realm = this)
        }
    }
    //endregion

    //region Favorite
    fun toggleMessageFavoriteStatus(threadUid: String, message: Message) {
        toggleThreadsOrMessageFavoriteStatus(threadsUids = listOf(threadUid), message = message)
    }

    fun toggleThreadFavoriteStatus(threadUid: String) {
        toggleThreadsOrMessageFavoriteStatus(threadsUids = listOf(threadUid))
    }

    fun toggleThreadsFavoriteStatus(threadsUids: List<String>, shouldFavorite: Boolean) {
        toggleThreadsOrMessageFavoriteStatus(threadsUids = threadsUids, shouldFavorite = shouldFavorite)
    }

    private fun toggleThreadsOrMessageFavoriteStatus(
        threadsUids: List<String>,
        message: Message? = null,
        shouldFavorite: Boolean = true,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value!!
        val threads = threadController.getThreads(threadsUids).ifEmpty { return@launch }

        val isFavorite = when {
            message != null -> message.isFavorite
            threads.count() == 1 -> threads.single().isFavorite
            else -> !shouldFavorite
        }

        val messages = if (isFavorite) {
            getMessagesToUnfavorite(threads, message)
        } else {
            getMessagesToFavorite(threads, message)
        }
        val uids = messages.getUids()

        updateFavoriteStatus(threadsUids = threadsUids, messagesUids = uids, isFavorite = !isFavorite)

        val apiResponses = if (isFavorite) {
            ApiRepository.removeFromFavorites(mailbox.uuid, uids)
        } else {
            ApiRepository.addToFavorites(mailbox.uuid, uids)
        }

        if (apiResponses.atLeastOneSucceeded()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(),
                callbacks = RefreshCallbacks(::onDownloadStart, ::onDownloadStop),
            )
        } else {
            updateFavoriteStatus(threadsUids = threadsUids, messagesUids = uids, isFavorite = isFavorite)
        }
    }

    private fun getMessagesToFavorite(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap(messageController::getLastMessageAndItsDuplicatesToExecuteAction)
        else -> messageController.getMessageAndDuplicates(threads.first(), message)
    }

    private fun getMessagesToUnfavorite(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap(messageController::getFavoriteMessages)
        else -> messageController.getMessageAndDuplicates(threads.first(), message)
    }

    private suspend fun updateFavoriteStatus(threadsUids: List<String>, messagesUids: List<String>, isFavorite: Boolean) {
        mailboxContentRealm().write {
            MessageController.updateFavoriteStatus(messagesUids, isFavorite, realm = this)
            ThreadController.updateFavoriteStatus(threadsUids, isFavorite, realm = this)
        }
    }
    //endregion

    //region Spam
    private fun toggleMessageSpamStatus(threadUid: String, message: Message) {
        toggleThreadsOrMessageSpamStatus(threadsUids = listOf(threadUid), message = message, displaySnackbar = false)
    }

    fun toggleThreadSpamStatus(threadUid: String) {
        toggleThreadsOrMessageSpamStatus(threadsUids = listOf(threadUid))
    }

    fun toggleThreadsSpamStatus(threadsUids: List<String>) {
        toggleThreadsOrMessageSpamStatus(threadsUids = threadsUids)
    }

    private fun toggleThreadsOrMessageSpamStatus(
        threadsUids: List<String>,
        message: Message? = null,
        displaySnackbar: Boolean = true,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value!!
        val threads = threadController.getThreads(threadsUids).ifEmpty { return@launch }

        val destinationFolderRole = if (getActionFolderRole(threads, message) == FolderRole.SPAM) {
            FolderRole.INBOX
        } else {
            FolderRole.SPAM
        }
        val destinationFolder = folderController.getFolder(destinationFolderRole)!!

        val messages = getMessagesToSpamOrHam(threads, message)

        threadController.updateIsLocallyMovedOutStatus(threadsUids, hasBeenMovedOut = true)

        val apiResponses = ApiRepository.moveMessages(mailbox.uuid, messages.getUids(), destinationFolder.id)

        if (apiResponses.atLeastOneSucceeded()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(exception = destinationFolder.id),
                destinationFolderId = destinationFolder.id,
                callbacks = RefreshCallbacks(onStart = ::onDownloadStart, onStop = { onDownloadStop(threadsUids) }),
            )
        } else {
            threadController.updateIsLocallyMovedOutStatus(threadsUids, hasBeenMovedOut = false)
        }

        if (displaySnackbar) showMoveSnackbar(threads, message, messages, apiResponses, destinationFolder)
    }

    private fun getMessagesToSpamOrHam(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap(messageController::getUnscheduledMessages)
        else -> messageController.getMessageAndDuplicates(threads.first(), message)
    }
    //endregion

    //region Phishing
    fun reportPhishing(threadUid: String, message: Message) = viewModelScope.launch(ioCoroutineContext) {
        val mailboxUuid = currentMailbox.value?.uuid!!

        with(ApiRepository.reportPhishing(mailboxUuid, message.folderId, message.shortUid)) {

            val snackbarTitle = if (isSuccess()) {
                if (getActionFolderRole(message.threads, message) != FolderRole.SPAM) toggleMessageSpamStatus(threadUid, message)
                R.string.snackbarReportPhishingConfirmation
            } else {
                translatedError
            }

            reportPhishingTrigger.postValue(Unit)
            snackbarManager.postValue(appContext.getString(snackbarTitle))
        }
    }
    //endregion

    //region Display problem
    fun reportDisplayProblem(messageUid: String) = viewModelScope.launch(ioCoroutineContext) {

        val message = messageController.getMessage(messageUid) ?: return@launch
        val mailbox = currentMailbox.value ?: return@launch

        val apiResponse = ApiRepository.getDownloadedMessage(mailbox.uuid, message.folderId, message.shortUid)

        if (apiResponse.body == null || !apiResponse.isSuccessful) {
            reportDisplayProblemTrigger.postValue(Unit)
            snackbarManager.postValue(appContext.getString(RCore.string.anErrorHasOccurred))

            return@launch
        }

        val filename = UUID.randomUUID().toString()
        val emlAttachment = Attachment(apiResponse.body?.bytes(), filename, EML_CONTENT_TYPE)
        Sentry.captureMessage("Message display problem reported", SentryLevel.ERROR) { scope ->
            scope.addAttachment(emlAttachment)
        }

        reportDisplayProblemTrigger.postValue(Unit)
        snackbarManager.postValue(appContext.getString(R.string.snackbarDisplayProblemReported))
    }
    //endregion

    //region BlockUser
    fun blockUser(message: Message) = viewModelScope.launch(ioCoroutineContext) {
        val mailboxUuid = currentMailbox.value?.uuid!!

        with(ApiRepository.blockUser(mailboxUuid, message.folderId, message.shortUid)) {

            val snackbarTitle = if (isSuccess()) R.string.snackbarBlockUserConfirmation else translatedError

            snackbarManager.postValue(appContext.getString(snackbarTitle))
        }
    }
    //endregion

    //region Snooze
    suspend fun rescheduleSnoozedThreads(date: Date, threads: List<Thread>): BatchSnoozeResult {
        var rescheduleResult: BatchSnoozeResult = BatchSnoozeResult.Error.Unknown

        viewModelScope.launch(ioCoroutineContext) {
            val snoozedThreadUuids = threads.mapNotNull { thread -> thread.snoozeUuid.takeIf { thread.isSnoozed() } }
            if (snoozedThreadUuids.isEmpty()) return@launch

            val currentMailbox = currentMailbox.value!!
            val result = rescheduleSnoozedThreads(currentMailbox, snoozedThreadUuids, date)
            when (result) {
                is BatchSnoozeResult.Success -> refreshFoldersAsync(currentMailbox, result.impactedFolders)
                is BatchSnoozeResult.Error -> snackbarManager.postValue(getRescheduleSnoozedErrorMessage(result))
            }

            rescheduleResult = result
        }.join()

        return rescheduleResult
    }

    private fun rescheduleSnoozedThreads(currentMailbox: Mailbox, snoozeUuids: List<String>, date: Date): BatchSnoozeResult {
        val responses = ApiRepository.rescheduleSnoozedThreads(currentMailbox.uuid, snoozeUuids, date)
        return responses.computeSnoozeResult(ImpactedFolders(mutableSetOf(FolderRole.SNOOZED)))
    }

    private fun getRescheduleSnoozedErrorMessage(errorResult: BatchSnoozeResult.Error): String {
        val errorMessageRes = when (errorResult) {
            BatchSnoozeResult.Error.NoneSucceeded -> R.string.errorSnoozeFailedModify
            is BatchSnoozeResult.Error.ApiError -> errorResult.translatedError
            BatchSnoozeResult.Error.Unknown -> RCore.string.anErrorHasOccurred
        }
        return appContext.getString(errorMessageRes)
    }

    suspend fun unsnoozeThreads(threads: List<Thread>): BatchSnoozeResult {
        var unsnoozeResult: BatchSnoozeResult = BatchSnoozeResult.Error.Unknown

        viewModelScope.launch(ioCoroutineContext) {
            val currentMailbox = currentMailbox.value
            unsnoozeResult = if (currentMailbox == null) {
                BatchSnoozeResult.Error.Unknown
            } else {
                sharedUtils.unsnoozeThreads(currentMailbox, threads)
            }

            unsnoozeResult.let {
                if (it is BatchSnoozeResult.Error) snackbarManager.postValue(getUnsnoozeErrorMessage(it))
            }
        }.join()

        return unsnoozeResult
    }

    private fun getUnsnoozeErrorMessage(errorResult: BatchSnoozeResult.Error): String {
        val errorMessageRes = when (errorResult) {
            BatchSnoozeResult.Error.NoneSucceeded -> R.string.errorSnoozeFailedCancel
            is BatchSnoozeResult.Error.ApiError -> errorResult.translatedError
            BatchSnoozeResult.Error.Unknown -> RCore.string.anErrorHasOccurred
        }

        return appContext.getString(errorMessageRes)
    }
    //endregion

    //region Undo action
    fun undoAction(undoData: UndoData) = viewModelScope.launch(ioCoroutineContext) {

        fun List<ApiResponse<*>>.getFailedCall() = firstOrNull { it.data != true }

        val mailbox = currentMailbox.value!!
        val (resources, foldersIds, destinationFolderId) = undoData

        val apiResponses = resources.map(ApiRepository::undoAction)

        if (apiResponses.atLeastOneSucceeded()) {
            // Don't use `refreshFoldersAsync` here, it will make the Snackbars blink.
            sharedUtils.refreshFolders(
                mailbox = mailbox,
                messagesFoldersIds = foldersIds,
                destinationFolderId = destinationFolderId,
            )
        }

        val failedCall = apiResponses.getFailedCall()

        val snackbarTitle = when {
            failedCall == null -> R.string.snackbarMoveCancelled
            failedCall.translatedError == 0 -> RCore.string.anErrorHasOccurred
            else -> failedCall.translatedError
        }

        snackbarManager.postValue(appContext.getString(snackbarTitle))
    }
    //endregion

    //region New Folder
    private suspend fun createNewFolderSync(name: String): String? {
        val mailbox = currentMailbox.value ?: return null
        val apiResponse = ApiRepository.createFolder(mailbox.uuid, name)

        newFolderResultTrigger.postValue(Unit)

        return if (apiResponse.isSuccess()) {
            updateFolders(mailbox)
            apiResponse.data?.id
        } else {
            snackbarManager.postValue(title = appContext.getString(apiResponse.translateError()))
            null
        }
    }

    fun createNewFolder(name: String) = viewModelScope.launch(ioCoroutineContext) { createNewFolderSync(name) }

    fun moveToNewFolder(
        name: String,
        threadsUids: List<String>,
        messageUid: String?,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val newFolderId = createNewFolderSync(name) ?: return@launch
        moveThreadsOrMessageTo(newFolderId, threadsUids, messageUid)
        isMovedToNewFolder.postValue(true)
    }
    //endregion

    private fun refreshFoldersAsync(
        mailbox: Mailbox,
        messagesFoldersIds: ImpactedFolders,
        destinationFolderId: String? = null,
        callbacks: RefreshCallbacks? = null,
    ) = viewModelScope.launch(ioCoroutineContext) {
        sharedUtils.refreshFolders(mailbox, messagesFoldersIds, destinationFolderId, currentFolderId, callbacks)
    }

    private fun onDownloadStart() {
        isDownloadingChanges.postValue(true)
    }

    private fun onDownloadStop(threadsUids: List<String> = emptyList()) = viewModelScope.launch(ioCoroutineContext) {
        threadController.updateIsLocallyMovedOutStatus(threadsUids, hasBeenMovedOut = false)
        isDownloadingChanges.postValue(false)
    }

    fun getActionFolderRole(thread: Thread?): FolderRole? {
        return getActionFolderRole(thread?.let(::listOf))
    }

    fun getActionFolderRole(message: Message): FolderRole? {
        return getActionFolderRole(message.threads, message)
    }

    // TODO: Handle this correctly if MultiSelect feature is added in the Search.
    /**
     * Get the FolderRole of a Message or a list of Threads.
     *
     * @param threads The list of Threads to find the FolderRole. They should ALL be from the same Folder. For now, it's
     * always the case. But it could change in the future (for example, if the MultiSelect feature is added in the Search).
     */
    private fun getActionFolderRole(threads: List<Thread>?, message: Message? = null): FolderRole? {
        return when {
            message != null -> {
                message.folder.role
            }
            threads?.firstOrNull()?.folderId == FolderController.SEARCH_FOLDER_ID -> {
                folderController.getFolder(threads.first().folderId)?.role
            }
            else -> {
                threads?.firstOrNull()?.folder?.role
            }
        }
    }

    fun addContact(recipient: Recipient) = viewModelScope.launch(ioCoroutineContext) {

        with(ApiRepository.addContact(addressBookController.getDefaultAddressBook().id, recipient)) {

            val snackbarTitle = if (isSuccess()) {
                updateUserInfo()
                R.string.snackbarContactSaved
            } else {
                translatedError
            }

            snackbarManager.postValue(appContext.getString(snackbarTitle))
        }
    }

    fun getMessage(messageUid: String) = liveData(ioCoroutineContext) {
        emit(messageController.getMessage(messageUid)!!)
    }

    fun hasOtherExpeditors(threadUid: String) = liveData(ioCoroutineContext) {
        val hasOtherExpeditors = threadController.getThread(threadUid)?.messages?.flatMap { it.from }?.any { !it.isMe() } == true
        emit(hasOtherExpeditors)
    }

    fun hasMoreThanOneExpeditor(threadUid: String) = liveData(ioCoroutineContext) {
        val hasMoreThanOneExpeditor =
            (threadController.getThread(threadUid)?.messages?.flatMap { it.from }?.filter { it.isMe() }?.size ?: 0) > 1
        emit(hasMoreThanOneExpeditor)
    }

    fun getMessagesFromUniqueExpeditors(threadUid: String) = liveData(ioCoroutineContext) {
        val messageToRecipient = threadController.getThread(threadUid)?.messages?.distinctBy { it.from }?.flatMap { message ->
            message.from.filterNot { it.isMe() }
                .distinct()
                .map { from -> message to from }
        }
        emit(messageToRecipient)
    }

    fun selectOrUnselectAll() {
        if (isEverythingSelected) {
            appContext.trackMultiSelectionEvent("none")
            selectedThreads.clear()
        } else {
            appContext.trackMultiSelectionEvent("all")
            currentThreadsLive.value?.list?.forEach { thread -> selectedThreads.add(thread) }
        }

        publishSelectedItems()
    }

    fun publishSelectedItems() {
        selectedThreadsLiveData.value = selectedThreads
    }

    fun refreshDraftFolderWhenDraftArrives(scheduledMessageEtop: Long) = viewModelScope.launch(ioCoroutineContext) {
        val folder = folderController.getFolder(FolderRole.DRAFT)

        if (folder?.cursor != null) {

            val timeNow = Date().time
            val delay = REFRESH_DELAY + max(scheduledMessageEtop - timeNow, 0L)
            delay(min(delay, MAX_REFRESH_DELAY))

            refreshController.refreshThreads(
                refreshMode = RefreshMode.REFRESH_FOLDER_WITH_ROLE,
                mailbox = currentMailbox.value!!,
                folderId = folder.id,
                realm = mailboxContentRealm(),
            )
        }
    }

    fun handleDeletedMessages(messagesUids: Set<String>) = viewModelScope.launch(ioCoroutineContext) {

        snackbarManager.postValue(appContext.getString(R.string.snackbarDeletedConversation))

        val mailbox = currentMailbox.value ?: return@launch
        val realm = mailboxContentRealm()

        val foldersToUpdate = realm.write {
            messagesUids.mapNotNullTo(mutableSetOf()) { MessageController.getMessage(it, realm = this)?.folderId }
        }

        foldersToUpdate.forEach { folderId ->
            refreshController.refreshThreads(
                refreshMode = RefreshMode.REFRESH_FOLDER,
                mailbox = mailbox,
                folderId = folderId,
                realm = realm,
            )
        }
    }

    fun scheduleDownload(downloadUrl: String, filename: String) = viewModelScope.launch(ioCoroutineContext) {
        val snackbarTitleRes = if (ApiRepository.ping().isSuccess()) {
            DownloadManagerUtils.scheduleDownload(appContext, downloadUrl, filename)
            R.string.snackbarDownloadInProgress
        } else {
            RCore.string.errorDownload
        }

        snackbarManager.postValue(appContext.getString(snackbarTitleRes))
    }

    fun deleteThreadInRealm(threadUid: String) = viewModelScope.launch(ioCoroutineContext) {
        threadController.deleteThread(threadUid)
    }

    private fun shouldAutoAdvance(message: Message?, threadsUids: List<String>): Boolean {
        val isWorkingWithThread = message == null
        return isWorkingWithThread || threadHasOnlyOneMessageLeft(threadsUids.first())
    }

    private fun threadHasOnlyOneMessageLeft(threadUid: String): Boolean {
        return messageController.getMessagesCountInThread(threadUid, mailboxContentRealm()) == 1
    }

    fun shareThreadUrl(messageUid: String) {
        val mailboxUuid = currentMailbox.value?.uuid

        viewModelScope.launch {
            if (mailboxUuid == null || !hasNetwork) {
                _shareThreadUrlResult.emit(null)
                return@launch
            }

            withContext(ioCoroutineContext) {
                messageController.getMessage(messageUid)?.let { message ->
                    val response = ApiRepository.getShareLink(mailboxUuid, message.folderId, message.shortUid)
                    _shareThreadUrlResult.emit(response.data?.url)
                }
            }
        }
    }

    // TODO: Remove this function when the Threads parental issues are fixed
    private fun removeThreadsWithParentalIssues() = viewModelScope.launch(ioCoroutineContext) {
        SentryLog.d(TAG, "Remove Threads with parental issues")
        threadController.removeThreadsWithParentalIssues()
    }

    companion object {
        private val TAG: String = MainViewModel::class.java.simpleName
        private val DEFAULT_SELECTED_FOLDER = FolderRole.INBOX
        private const val REFRESH_DELAY = 2_000L // We add this delay because `etop` isn't always big enough.
        private const val MAX_REFRESH_DELAY = 6_000L
    }
}
