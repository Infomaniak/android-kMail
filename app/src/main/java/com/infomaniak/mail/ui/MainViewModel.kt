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
package com.infomaniak.mail.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.infomaniak.core.common.utils.DownloadManagerUtils
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.legacy.utils.SingleLiveEvent
import com.infomaniak.core.network.NetworkAvailability
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.networking.HttpUtils
import com.infomaniak.core.network.networking.ManualAuthorizationRequired
import com.infomaniak.core.network.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.core.ui.showToast
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackMultiSelectionEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.ImpactedFolders
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshCallbacks
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.cache.mailboxInfo.PermissionsController
import com.infomaniak.mail.data.cache.mailboxInfo.QuotasController
import com.infomaniak.mail.data.cache.userInfo.AddressBookController
import com.infomaniak.mail.data.cache.userInfo.MergedContactController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.FolderUi
import com.infomaniak.mail.data.models.MoveResult
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.forEachNestedItem
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.mailbox.Mailbox.FeatureFlagSet
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MailboxInfoRealm
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.SnackbarManager.UndoData
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ContactUtils.getPhoneContacts
import com.infomaniak.mail.utils.ContactUtils.mergeApiContactsIntoPhoneContacts
import com.infomaniak.mail.utils.FeatureAvailability
import com.infomaniak.mail.utils.MyKSuiteDataUtils
import com.infomaniak.mail.utils.NotificationUtils.Companion.cancelNotification
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.SharedUtils.Companion.updateSignatures
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.Utils.EML_CONTENT_TYPE
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.coroutineContext
import com.infomaniak.mail.utils.extensions.MergedContactDictionary
import com.infomaniak.mail.utils.extensions.allFailed
import com.infomaniak.mail.utils.extensions.appContext
import com.infomaniak.mail.utils.extensions.atLeastOneFailed
import com.infomaniak.mail.utils.extensions.atLeastOneSucceeded
import com.infomaniak.mail.utils.extensions.getFoldersIds
import com.infomaniak.mail.utils.extensions.getUids
import com.infomaniak.mail.utils.extensions.launchNoValidMailboxesActivity
import com.infomaniak.mail.utils.toFolderUiTree
import com.infomaniak.mail.views.itemViews.AvatarMergedContactData
import com.infomaniak.mail.views.itemViews.KSuiteStorageBanner.StorageLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.notifications.ResultsChange
import io.sentry.Attachment
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import com.infomaniak.core.legacy.R as RCore

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    avatarMergedContactData: AvatarMergedContactData,
    private val addressBookController: AddressBookController,
    private val folderController: FolderController,
    private val localSettings: LocalSettings,
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val mailboxController: MailboxController,
    private val mergedContactController: MergedContactController,
    private val messageController: MessageController,
    private val myKSuiteDataUtils: MyKSuiteDataUtils,
    private val permissionsController: PermissionsController,
    private val quotasController: QuotasController,
    private val refreshController: RefreshController,
    private val sharedUtils: SharedUtils,
    private val snackbarManager: SnackbarManager,
    private val threadController: ThreadController,
    @MailboxInfoRealm private val mailboxInfoRealm: Realm,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)
    private var refreshEverythingJob: Job? = null

    val isDownloadingChanges: MutableLiveData<Boolean> = MutableLiveData(false)
    val isMovedToNewFolder = SingleLiveEvent<Boolean>()
    val toggleLightThemeForMessage = SingleLiveEvent<Message>()
    val deletedMessages = SingleLiveEvent<Set<String>>()
    val flushFolderTrigger = SingleLiveEvent<Unit>()
    val newFolderResultTrigger = MutableLiveData<Unit>()
    val renameFolderResultTrigger = MutableLiveData<Unit>()
    val deleteFolderResultTrigger = MutableLiveData<Unit>()
    val reportDisplayProblemTrigger = SingleLiveEvent<Unit>()
    val canInstallUpdate = MutableLiveData(false)

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

    val currentMailbox = _currentMailboxObjectId.mapLatest { id ->
        id?.let { mailboxController.getMailbox(it) }
    }.asLiveData(ioCoroutineContext)

    private val currentMailboxLive = _currentMailboxObjectId.filterNotNull().flatMapLatest { objectId ->
        mailboxController.getMailboxAsync(objectId).mapNotNull { it.obj }
    }.asLiveData(ioCoroutineContext)

    val featureFlagsLive = currentMailboxLive.map { it.featureFlags }

    private val defaultFoldersFlow = _currentMailboxObjectId.filterNotNull().flatMapLatest {
        folderController
            .getMenuDrawerDefaultFoldersAsync()
            .map { it.list }
            .removeRolesThatHideWhenEmpty()
            .map { it.toFolderUiTree(isInDefaultFolderSection = true) }
    }.catch {}

    private val customFoldersFlow = _currentMailboxObjectId.filterNotNull().flatMapLatest {
        folderController
            .getMenuDrawerCustomFoldersAsync()
            .map { it.list }
            .keepTopLevelFolders()
            .map { it.toFolderUiTree(isInDefaultFolderSection = false) }
    }.catch {}

    val displayedFoldersFlow = combine(defaultFoldersFlow, customFoldersFlow) { default, custom ->
        DisplayedFolders(default, custom)
    }

    val displayedFoldersLive = displayedFoldersFlow.asLiveData(ioCoroutineContext)

    val currentQuotasLive = _currentMailboxObjectId.flatMapLatest {
        it?.let(quotasController::getQuotasAsync) ?: emptyFlow()
    }.asLiveData(ioCoroutineContext)

    val storageBannerStatus = currentQuotasLive.map { quotas ->
        val progress = quotas?.getProgress()
        when {
            quotas == null -> null
            quotas.isFull -> StorageLevel.getFullStorageBanner(currentMailbox.value?.kSuite)
            progress != null && progress > StorageLevel.WARNING_THRESHOLD -> {
                if (!localSettings.hasClosedStorageBanner || localSettings.storageBannerDisplayAppLaunches % 10 == 0) {
                    localSettings.hasClosedStorageBanner = false
                    if (currentMailbox.value?.kSuite is KSuite.Perso) StorageLevel.Warning.Perso else StorageLevel.Warning.Pro
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
        it?.let { folderController.getFolder(it) }
    }.asLiveData(ioCoroutineContext)

    val currentFolderLive = _currentFolderId.flatMapLatest {
        it?.let(folderController::getFolderAsync) ?: emptyFlow()
    }.asLiveData(ioCoroutineContext)

    val swipeActionContext = Utils.waitInitMediator(featureFlagsLive, currentFolderLive) {
        (it[0] as FeatureFlagSet) to (it[1] as Folder).role
    }.distinctUntilChanged()

    val currentFilter = SingleLiveEvent(ThreadFilter.ALL)

    val currentThreadsLive = MutableLiveData<ResultsChange<Thread>>()

    val isNetworkAvailable = NetworkAvailability(appContext).isNetworkAvailable
    var hasNetwork: Boolean = true
        private set

    private var currentThreadsLiveJob: Job? = null

    init {
        viewModelScope.launch {
            isNetworkAvailable.collect {
                SentryLog.d("Internet availability", if (it) "Available" else "Unavailable")
                hasNetwork = it
            }
        }
    }

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

    private suspend fun openMailbox(): Mailbox? {
        SentryLog.d(TAG, "Load current mailbox from local")

        val mailbox = mailboxController.getMailboxWithFallback(
            userId = AccountUtils.currentUserId,
            mailboxId = AccountUtils.currentMailboxId,
        ) ?: return null

        if (mailbox.isLocked) {
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
            (currentFolderId
                ?.let { folderController.getFolder(it) }
                ?: folderController.getFolder(DEFAULT_SELECTED_FOLDER))?.let { folder ->
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

        if (mailbox.isLimited) {
            with(ApiRepository.getQuotas(mailbox.hostingId, mailbox.mailboxName)) {
                if (isSuccess()) {
                    mailboxController.updateMailbox(mailbox.objectId) {
                        data?.initMaxStorage(it.maxStorage)
                        it.quotas = data
                    }
                }
            }
        } else {
            mailboxController.updateMailbox(mailbox.objectId) {
                it.quotas = null
            }
        }
    }

    private fun updatePermissions(mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        SentryLog.d(TAG, "Force refresh Permissions")
        with(ApiRepository.getPermissions(mailbox.linkId, mailbox.hostingId)) {
            if (isSuccess()) {
                mailboxController.updateMailbox(mailbox.objectId) {
                    it.permissions = data
                }
            }
        }
    }

    private fun updateSignatures(mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        SentryLog.d(TAG, "Force refresh Signatures")
        updateSignatures(mailbox, mailboxInfoRealm)
    }

    //region Spam

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
            if (!mailboxContentRealm().isClosed()) {
                folderController.update(mailbox, folders, mailboxContentRealm())
                expandFoldersWithoutChildren()
            }
        }
    }

    /**
     *  Recomputes the [Folder.isCollapsed] state so a parent which has no more children is correctly expanded.
     */
    private suspend fun expandFoldersWithoutChildren() {
        val folders = displayedFoldersFlow.first()
        mailboxContentRealm().write {
            folders.custom.updateCollapsedState(realm = this)
            folders.default.updateCollapsedState(realm = this)
        }
    }

    private fun List<FolderUi>.updateCollapsedState(realm: MutableRealm) {
        forEachNestedItem { folderUi, _ ->
            // If we detect that a folder doesn't have any children anymore, if it was collapsed, automatically expand it
            val collapseStateNeedsReset = folderUi.children.isEmpty() && folderUi.folder.isCollapsed
            if (collapseStateNeedsReset) FolderController.expand(folderUi.folder.id, realm)
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
                snackbarManager.postValue(appContext.getString(translateError()))
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
                    snackbarManager.postValue(title = appContext.getString(translateError()))
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
            snackbarManager.postValue(title = appContext.getString(apiResponse.translateError()))
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

        fun openDraftFolder() = viewModelScope.launch { folderController.getFolder(FolderRole.DRAFT)?.id?.let(::openFolder) }

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
        messagesUid: List<String>? = null,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val destinationFolder = folderController.getFolder(destinationFolderId)!!
        val threads = threadController.getThreads(threadsUids).ifEmpty { return@launch }
        val messages = messagesUid?.let { messageController.getMessages(it) }
        val messagesToMove = sharedUtils.getMessagesToMove(threads, messages, currentFolderId)

        moveThreadsOrMessageTo(destinationFolder, threadsUids, threads, null, messagesToMove)
    }

    private suspend fun moveThreadsOrMessageTo(
        destinationFolder: Folder,
        threadsUids: List<String>,
        threads: List<Thread>,
        message: Message? = null,
        messagesToMove: List<Message>,
        shouldDisplaySnackbar: Boolean = true,
    ) {
        val mailbox = currentMailbox.value!!

        moveOutThreadsLocally(threadsUids, threads, message)

        val apiResponses = moveMessages(
            mailbox = mailbox,
            messagesToMove = messagesToMove,
            destinationFolder = destinationFolder,
            alsoMoveReactionMessages = FeatureAvailability.isReactionsAvailable(featureFlagsLive.value, localSettings),
        )

        if (apiResponses.atLeastOneSucceeded()) {
            if (shouldAutoAdvance(message, threadsUids)) autoAdvanceThreadsUids.postValue(threadsUids)

            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messagesToMove.getFoldersIds(exception = destinationFolder.id),
                destinationFolderId = destinationFolder.id,
                callbacks = RefreshCallbacks(onStart = ::onDownloadStart, onStop = { onDownloadStop(threadsUids) }),
            )
        }

        if (apiResponses.atLeastOneFailed()) threadController.updateIsLocallyMovedOutStatus(threadsUids, hasBeenMovedOut = false)

        if (shouldDisplaySnackbar) showMoveSnackbar(threads, message, messagesToMove, apiResponses, destinationFolder)
    }

    private suspend fun moveMessages(
        mailbox: Mailbox,
        messagesToMove: List<Message>,
        destinationFolder: Folder,
        alsoMoveReactionMessages: Boolean,
    ): List<ApiResponse<MoveResult>> {
        val apiResponses = ApiRepository.moveMessages(
            mailboxUuid = mailbox.uuid,
            messagesUids = messagesToMove.getUids(),
            destinationId = destinationFolder.id,
            alsoMoveReactionMessages = alsoMoveReactionMessages,
        )

        // TODO: Will unsync permantly the mailbox if one message in one of the batches did succeed but some other messages in the
        //  same batch or in other batches that are target by emoji reactions did not
        if (alsoMoveReactionMessages && apiResponses.atLeastOneSucceeded()) deleteEmojiReactionMessagesLocally(messagesToMove)

        return apiResponses
    }

    /**
     * When deleting a message targeted by emoji reactions inside of a thread, the emoji reaction messages from another folder
     * that were targeting this message will display for a brief moment until we refresh their folders. This is because those
     * messages don't have a target message anymore and emoji reactions messages with no target in their thread need to be
     * displayed.
     *
     * Deleting them from the database in the first place will prevent them from being shown and the messages will be deleted by
     * the api at the same time anyway.
     */
    private suspend fun deleteEmojiReactionMessagesLocally(messagesToMove: List<Message>) {
        for (messageToMove in messagesToMove) {
            if (messageToMove.emojiReactions.isEmpty()) continue

            mailboxContentRealm().write {
                messageToMove.emojiReactions.forEach { reaction ->
                    reaction.authors.forEach { author ->
                        MessageController.deleteMessageByUidBlocking(author.sourceMessageUid, this)
                    }
                }
            }
        }
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
            apiResponses.allFailed() -> appContext.getString(apiResponses.first().translateError())
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

    //region Manage Folder
    private suspend fun createNewFolderSync(name: String): String? {
        val mailbox = currentMailbox.value ?: return null
        val apiResponse = ApiRepository.createFolder(mailbox.uuid, name)

        newFolderResultTrigger.postValue(Unit)

        return apiResponseIsSuccess(apiResponse, mailbox)
    }

    fun createNewFolder(name: String) = viewModelScope.launch(ioCoroutineContext) { createNewFolderSync(name) }

    fun modifyNameFolder(name: String, folderId: String) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value ?: return@launch
        val apiResponse = ApiRepository.renameFolder(mailbox.uuid, folderId, name)

        renameFolderResultTrigger.postValue(Unit)

        apiResponseIsSuccess(apiResponse, mailbox)
    }

    fun deleteFolder(folderId: String) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value ?: return@launch
        val apiResponse = ApiRepository.deleteFolder(mailbox.uuid, folderId)

        deleteFolderResultTrigger.postValue(Unit)

        if (apiResponse.isSuccess()) {
            updateFolders(mailbox)
        } else {
            snackbarManager.postValue(title = appContext.getString(apiResponse.translateError()))
        }
    }

    private suspend fun apiResponseIsSuccess(apiResponse: ApiResponse<Folder>, mailbox: Mailbox): String? {
        return if (apiResponse.isSuccess()) {
            updateFolders(mailbox)
            apiResponse.data?.id
        } else {
            snackbarManager.postValue(title = appContext.getString(apiResponse.translateError()))
            null
        }
    }

    fun moveToNewFolder(
        name: String,
        threadsUids: List<String>,
        messagesUids: List<String>?,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val newFolderId = createNewFolderSync(name) ?: return@launch
        moveThreadsOrMessageTo(newFolderId, threadsUids, messagesUids)
        isMovedToNewFolder.postValue(true)
    }
    //endregion

    private suspend fun moveOutThreadsLocally(
        threadsUids: List<String>,
        threads: List<Thread>,
        message: Message?,
    ) {
        val uidsToMove = if (message == null) {
            threadsUids
        } else {
            mutableListOf<String>().apply {
                threads.forEach { thread ->
                    var nbMessagesInCurrentFolder = thread.messages.count { it.folderId == currentFolderId }
                    if (message.folderId == currentFolderId) nbMessagesInCurrentFolder--
                    if (nbMessagesInCurrentFolder == 0) add(thread.uid)
                }
            }
        }
        if (uidsToMove.isNotEmpty()) threadController.updateIsLocallyMovedOutStatus(uidsToMove, hasBeenMovedOut = true)
    }

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

    fun addContact(recipient: Recipient) = viewModelScope.launch(ioCoroutineContext) {

        with(ApiRepository.addContact(addressBookController.getDefaultAddressBook().id, recipient)) {

            val snackbarTitle = if (isSuccess()) {
                updateUserInfo()
                R.string.snackbarContactSaved
            } else {
                translateError()
            }

            snackbarManager.postValue(appContext.getString(snackbarTitle))
        }
    }

    suspend fun getMessage(messageUid: String): Message = messageController.getMessage(messageUid)!!

    fun selectOrUnselectAll() {
        if (isEverythingSelected) {
            trackMultiSelectionEvent(MatomoName.None)
            selectedThreads.clear()
        } else {
            trackMultiSelectionEvent(MatomoName.All)
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
            messagesUids.mapNotNullTo(mutableSetOf()) { MessageController.getMessageBlocking(it, realm = this)?.folderId }
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

    @OptIn(ManualAuthorizationRequired::class)
    fun scheduleDownload(downloadUrl: String, filename: String) = viewModelScope.launch(ioCoroutineContext) {
        val snackbarTitleRes = if (ApiRepository.ping().isSuccess()) {
            val userBearerToken = AccountUtils.currentUser?.apiToken?.accessToken
            DownloadManagerUtils.scheduleDownload(
                context = appContext,
                url = downloadUrl,
                name = filename,
                userBearerToken = userBearerToken,
                extraHeaders = HttpUtils.getHeaders(),
                onError = { resourceStringId ->
                    appContext.showToast(resourceStringId)
                }
            )
            R.string.snackbarDownloadInProgress
        } else {
            RCore.string.errorDownload
        }

        snackbarManager.postValue(appContext.getString(snackbarTitleRes))
    }

    fun deleteThreadInRealm(threadUid: String) = viewModelScope.launch(ioCoroutineContext) {
        threadController.deleteThread(threadUid)
    }

    private suspend fun shouldAutoAdvance(message: Message?, threadsUids: List<String>): Boolean {
        val isWorkingWithThread = message == null
        return isWorkingWithThread || threadHasOnlyOneMessageLeft(threadsUids.first())
    }

    private suspend fun threadHasOnlyOneMessageLeft(threadUid: String): Boolean {
        return messageController.getMessagesCountInThread(threadUid, featureFlagsLive.value, mailboxContentRealm()) == 1
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

    /**
     *  Contains a list of nested folders. This means each list is only comprised of the root folders and each subfolder is
     *  stored inside [FolderUi.children].
     */
    data class DisplayedFolders(val default: List<FolderUi>, val custom: List<FolderUi>)

    companion object {
        private val TAG: String = MainViewModel::class.java.simpleName
        private val DEFAULT_SELECTED_FOLDER = FolderRole.INBOX
        private const val REFRESH_DELAY = 2_000L // We add this delay because `etop` isn't always big enough.
        private const val MAX_REFRESH_DELAY = 6_000L
    }
}

private val HIDDEN_ROLES_WHEN_EMPTY = setOf(FolderRole.SCHEDULED_DRAFTS, FolderRole.SNOOZED)

private fun Flow<List<Folder>>.removeRolesThatHideWhenEmpty(): Flow<List<Folder>> = map {
    it.filterNot { folder -> folder.role in HIDDEN_ROLES_WHEN_EMPTY && folder.threads.isEmpty() }
}

private fun Flow<List<Folder>>.keepTopLevelFolders(): Flow<List<Folder>> = map {
    it.filter { folder -> folder.parent == null }
}
