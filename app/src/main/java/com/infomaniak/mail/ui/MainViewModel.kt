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
import androidx.annotation.StringRes
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
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.legacy.utils.SingleLiveEvent
import com.infomaniak.core.network.NetworkAvailability
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.networking.HttpUtils
import com.infomaniak.core.network.networking.ManualAuthorizationRequired
import com.infomaniak.core.network.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.core.ui.showToast
import com.infomaniak.core.utils.DownloadManagerUtils
import com.infomaniak.emojicomponents.data.Reaction
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackMultiSelectionEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
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
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.forEachNestedItem
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.mailbox.Mailbox.FeatureFlagSet
import com.infomaniak.mail.data.models.mailbox.SendersRestrictions
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.snooze.BatchSnoozeResult
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MailboxInfoRealm
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.SnackbarManager.UndoData
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ContactUtils.getPhoneContacts
import com.infomaniak.mail.utils.ContactUtils.mergeApiContactsIntoPhoneContacts
import com.infomaniak.mail.utils.DraftInitManager
import com.infomaniak.mail.utils.EmojiReactionUtils.hasAvailableReactionSlot
import com.infomaniak.mail.utils.ErrorCode
import com.infomaniak.mail.utils.FeatureAvailability
import com.infomaniak.mail.utils.FolderRoleUtils
import com.infomaniak.mail.utils.MyKSuiteDataUtils
import com.infomaniak.mail.utils.NotificationUtils
import com.infomaniak.mail.utils.NotificationUtils.Companion.cancelNotification
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.SharedUtils.Companion.unsnoozeThreadsWithoutRefresh
import com.infomaniak.mail.utils.SharedUtils.Companion.updateSignatures
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.Utils.EML_CONTENT_TYPE
import com.infomaniak.mail.utils.Utils.isPermanentDeleteFolder
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.coroutineContext
import com.infomaniak.mail.utils.date.DateFormatUtils.dayOfWeekDateWithoutYear
import com.infomaniak.mail.utils.extensions.MergedContactDictionary
import com.infomaniak.mail.utils.extensions.allFailed
import com.infomaniak.mail.utils.extensions.appContext
import com.infomaniak.mail.utils.extensions.atLeastOneFailed
import com.infomaniak.mail.utils.extensions.atLeastOneSucceeded
import com.infomaniak.mail.utils.extensions.getFirstTranslatedError
import com.infomaniak.mail.utils.extensions.getFoldersIds
import com.infomaniak.mail.utils.extensions.getUids
import com.infomaniak.mail.utils.extensions.launchNoValidMailboxesActivity
import com.infomaniak.mail.utils.toFolderUiTree
import com.infomaniak.mail.views.itemViews.AvatarMergedContactData
import com.infomaniak.mail.views.itemViews.KSuiteStorageBanner.StorageLevel
import com.infomaniak.mail.workers.DraftsActionsWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.RealmResults
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
import kotlinx.coroutines.invoke
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
    private val draftController: DraftController,
    private val draftInitManager: DraftInitManager,
    private val draftsActionsWorkerScheduler: DraftsActionsWorker.Scheduler,
    private val folderController: FolderController,
    private val folderRoleUtils: FolderRoleUtils,
    private val localSettings: LocalSettings,
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
    val activityDialogLoaderResetTrigger = SingleLiveEvent<Unit>()
    val flushFolderTrigger = SingleLiveEvent<Unit>()
    val newFolderResultTrigger = MutableLiveData<Unit>()
    val renameFolderResultTrigger = MutableLiveData<Unit>()
    val deleteFolderResultTrigger = MutableLiveData<Unit>()
    val reportPhishingTrigger = SingleLiveEvent<Unit>()
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
    fun deleteMessage(threadUid: String, message: Message) {
        deleteThreadsOrMessage(threadsUids = listOf(threadUid), message = message)
    }

    fun deleteThread(threadUid: String) {
        deleteThreadsOrMessage(threadsUids = listOf(threadUid))
    }

    fun deleteThreads(threadsUids: List<String>) {
        deleteThreadsOrMessage(threadsUids = threadsUids)
    }

    // TODO: When the back is done refactoring how scheduled drafts are deleted, work on this function shall resume.
    private fun deleteThreadsOrMessage(
        threadsUids: List<String>,
        message: Message? = null,
    ) = viewModelScope.launch(ioCoroutineContext) {

        val threads = threadController.getThreads(threadsUids).ifEmpty { return@launch }
        val shouldPermanentlyDelete = isPermanentDeleteFolder(folderRoleUtils.getActionFolderRole(threads, message))
        val messages = getMessagesToDelete(threads, message)

        if (shouldPermanentlyDelete) {
            permanentlyDelete(
                threads = threads,
                threadsUids = threadsUids,
                messagesToDelete = messages,
                message = message,
            )
        } else {
            moveThreadsOrMessageTo(
                destinationFolder = folderController.getFolder(FolderRole.TRASH)!!,
                threadsUids = threadsUids,
                threads = threads,
                message = message,
                messagesToMove = messages,
            )
        }
    }

    private suspend fun permanentlyDelete(
        threads: RealmResults<Thread>,
        threadsUids: List<String>,
        messagesToDelete: List<Message>,
        message: Message?,
    ) {
        val mailbox = currentMailbox.value!!
        val undoResources = emptyList<String>()
        val uids = messagesToDelete.getUids()

        moveOutThreadsLocally(threadsUids, threads, message)

        val apiResponses = ApiRepository.deleteMessages(
            mailboxUuid = mailbox.uuid,
            messagesUids = uids,
            alsoMoveReactionMessages = FeatureAvailability.isReactionsAvailable(featureFlagsLive.value, localSettings)
        )

        activityDialogLoaderResetTrigger.postValue(Unit)

        if (apiResponses.atLeastOneSucceeded()) {
            if (shouldAutoAdvance(message, threadsUids)) autoAdvanceThreadsUids.postValue(threadsUids)

            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messagesToDelete.getFoldersIds(),
                callbacks = RefreshCallbacks(onStart = ::onDownloadStart, onStop = { onDownloadStop(threadsUids) }),
            )
        }

        if (apiResponses.atLeastOneFailed()) threadController.updateIsLocallyMovedOutStatus(threadsUids, hasBeenMovedOut = false)

        val undoDestinationId = message?.folderId ?: threads.first().folderId
        val undoFoldersIds = messagesToDelete.getFoldersIds(exception = undoDestinationId)
        showDeleteSnackbar(
            apiResponses = apiResponses,
            message = message,
            undoResources = undoResources,
            undoFoldersIds = undoFoldersIds,
            undoDestinationId = undoDestinationId,
            numberOfImpactedThreads = threads.count(),
        )
    }

    private fun showDeleteSnackbar(
        apiResponses: List<ApiResponse<*>>,
        message: Message?,
        undoResources: List<String>,
        undoFoldersIds: ImpactedFolders,
        undoDestinationId: String?,
        numberOfImpactedThreads: Int,
    ) {
        val snackbarTitle = if (apiResponses.atLeastOneSucceeded()) {
            if (message == null) {
                appContext.resources.getQuantityString(R.plurals.snackbarThreadDeletedPermanently, numberOfImpactedThreads)
            } else {
                appContext.getString(R.string.snackbarMessageDeletedPermanently)
            }
        } else {
            appContext.getString(apiResponses.first().translateError())
        }

        val undoData = if (undoResources.isEmpty()) null else UndoData(undoResources, undoFoldersIds, undoDestinationId)

        snackbarManager.postValue(snackbarTitle, undoData)
    }

    private suspend fun getMessagesToDelete(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap { messageController.getUnscheduledMessages(it) }
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
        messageUid: String? = null,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val destinationFolder = folderController.getFolder(destinationFolderId)!!
        val threads = threadController.getThreads(threadsUids).ifEmpty { return@launch }
        val message = messageUid?.let { messageController.getMessage(it)!! }
        val messagesToMove = sharedUtils.getMessagesToMove(threads, message)

        moveThreadsOrMessageTo(destinationFolder, threadsUids, threads, message, messagesToMove)
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

        val threads = threadController.getThreads(threadsUids).ifEmpty { return@launch }

        val role = folderRoleUtils.getActionFolderRole(threads, message)
        val isFromArchive = role == FolderRole.ARCHIVE

        val destinationFolderRole = if (isFromArchive) FolderRole.INBOX else FolderRole.ARCHIVE
        val destinationFolder = folderController.getFolder(destinationFolderRole)!!

        val messagesToMove = sharedUtils.getMessagesToMove(threads, message)

        moveThreadsOrMessageTo(destinationFolder, threadsUids, threads, message, messagesToMove)
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
            threads.count() == 1 -> threads.single().isSeen
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

    private suspend fun getMessagesToMarkAsUnseen(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap { thread ->
            messageController.getLastMessageAndItsDuplicatesToExecuteAction(thread, featureFlagsLive.value)
        }
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

    private suspend fun getMessagesToFavorite(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap { thread ->
            messageController.getLastMessageAndItsDuplicatesToExecuteAction(thread, featureFlagsLive.value)
        }
        else -> messageController.getMessageAndDuplicates(threads.first(), message)
    }

    private suspend fun getMessagesToUnfavorite(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap { messageController.getFavoriteMessages(it) }
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

    fun toggleThreadSpamStatus(threadUids: List<String>, displaySnackbar: Boolean = true) {
        toggleThreadsOrMessageSpamStatus(threadsUids = threadUids, displaySnackbar = displaySnackbar)
    }

    private fun toggleThreadsOrMessageSpamStatus(
        threadsUids: List<String>,
        message: Message? = null,
        displaySnackbar: Boolean = true,
    ) = viewModelScope.launch(ioCoroutineContext) {

        val threads = threadController.getThreads(threadsUids).ifEmpty { return@launch }

        val destinationFolderRole = if (folderRoleUtils.getActionFolderRole(threads, message) == FolderRole.SPAM) {
            FolderRole.INBOX
        } else {
            FolderRole.SPAM
        }
        val destinationFolder = folderController.getFolder(destinationFolderRole)!!

        val messages = getMessagesToSpamOrHam(threads, message)

        moveThreadsOrMessageTo(destinationFolder, threadsUids, threads, message, messages, displaySnackbar)
    }

    private suspend fun getMessagesToSpamOrHam(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap { messageController.getUnscheduledMessages(it) }
        else -> listOf(message)
    }
    //endregion

    //region Phishing
    fun reportPhishing(threadUids: List<String>, messages: List<Message>) = viewModelScope.launch(ioCoroutineContext) {
        val mailboxUuid = currentMailbox.value?.uuid!!
        val messagesUids: List<String> = messages.map { it.uid }

        if (messagesUids.isEmpty()) {
            snackbarManager.postValue(appContext.getString(RCore.string.anErrorHasOccurred))
            return@launch
        }

        with(ApiRepository.reportPhishing(mailboxUuid, messagesUids)) {
            val snackbarTitle = if (isSuccess()) {
                // Check the first message, because it is not possible to select messages from multiple folders,
                // so you won't have both SPAM and non-SPAM messages.
                messages.firstOrNull()?.let {
                    if (folderRoleUtils.getActionFolderRole(it) != FolderRole.SPAM) {
                        toggleThreadSpamStatus(threadUids = threadUids, displaySnackbar = false)
                    }
                }

                R.string.snackbarReportPhishingConfirmation
            } else {
                translateError()
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
    fun blockUser(folderId: String, shortUid: Int) = viewModelScope.launch(ioCoroutineContext) {
        val mailboxUuid = currentMailbox.value?.uuid!!
        with(ApiRepository.blockUser(mailboxUuid, folderId, shortUid)) {

            val snackbarTitle = if (isSuccess()) R.string.snackbarBlockUserConfirmation else translateError()
            snackbarManager.postValue(appContext.getString(snackbarTitle))

            reportPhishingTrigger.postValue(Unit)
        }
    }
    //endregion

    //region Snooze
    suspend fun snoozeThreads(date: Date, threadUids: List<String>): Boolean {
        var isSuccess = false

        viewModelScope.launch {
            currentMailbox.value?.let { currentMailbox ->
                val threads = threadUids.mapNotNull { threadController.getThread(it) }

                val messageUids = threads.mapNotNull { thread ->
                    thread.getDisplayedMessages(currentMailbox.featureFlags, localSettings)
                        .lastOrNull { it.folderId == currentFolderId }?.uid
                }

                val responses = ioDispatcher { ApiRepository.snoozeMessages(currentMailbox.uuid, messageUids, date) }

                isSuccess = responses.atLeastOneSucceeded()
                val userFeedbackMessage = if (isSuccess) {
                    // Snoozing threads requires to refresh the snooze folder.
                    // It's the only folder that will update the snooze state of any message.
                    refreshFoldersAsync(currentMailbox, ImpactedFolders(mutableSetOf(FolderRole.SNOOZED)))

                    val formattedDate = appContext.dayOfWeekDateWithoutYear(date)
                    appContext.resources.getQuantityString(R.plurals.snackbarSnoozeSuccess, threads.count(), formattedDate)
                } else {
                    val errorMessageRes = responses.getFirstTranslatedError() ?: RCore.string.anErrorHasOccurred
                    appContext.getString(errorMessageRes)
                }

                snackbarManager.postValue(userFeedbackMessage)
            }
        }.join()

        return isSuccess
    }

    suspend fun rescheduleSnoozedThreads(date: Date, threadUids: List<String>): BatchSnoozeResult {
        var rescheduleResult: BatchSnoozeResult = BatchSnoozeResult.Error.Unknown

        viewModelScope.launch(ioCoroutineContext) {
            val snoozedThreadUuids = threadUids.mapNotNull { threadUid ->
                val thread = threadController.getThread(threadUid) ?: return@mapNotNull null
                thread.snoozeUuid.takeIf { thread.isSnoozed() }
            }
            if (snoozedThreadUuids.isEmpty()) return@launch

            val currentMailbox = currentMailbox.value!!
            val result = rescheduleSnoozedThreads(currentMailbox, snoozedThreadUuids, date)

            val userFeedbackMessage = when (result) {
                is BatchSnoozeResult.Success -> {
                    refreshFoldersAsync(currentMailbox, result.impactedFolders)

                    val formattedDate = appContext.dayOfWeekDateWithoutYear(date)
                    appContext.resources.getQuantityString(R.plurals.snackbarSnoozeSuccess, threadUids.count(), formattedDate)
                }
                is BatchSnoozeResult.Error -> getRescheduleSnoozedErrorMessage(result)
            }

            snackbarManager.postValue(userFeedbackMessage)

            rescheduleResult = result
        }.join()

        return rescheduleResult
    }

    private suspend fun rescheduleSnoozedThreads(
        currentMailbox: Mailbox,
        snoozeUuids: List<String>,
        date: Date,
    ): BatchSnoozeResult {
        return SharedUtils.rescheduleSnoozedThreads(
            mailboxUuid = currentMailbox.uuid,
            snoozeUuids = snoozeUuids,
            newDate = date,
            impactedFolders = ImpactedFolders(mutableSetOf(FolderRole.SNOOZED)),
        )
    }

    private fun getRescheduleSnoozedErrorMessage(errorResult: BatchSnoozeResult.Error): String {
        val errorMessageRes = when (errorResult) {
            BatchSnoozeResult.Error.NoneSucceeded -> R.string.errorSnoozeFailedModify
            is BatchSnoozeResult.Error.ApiError -> errorResult.translatedError
            BatchSnoozeResult.Error.Unknown -> RCore.string.anErrorHasOccurred
        }
        return appContext.getString(errorMessageRes)
    }

    suspend fun unsnoozeThreads(threads: Collection<Thread>): BatchSnoozeResult {
        var unsnoozeResult: BatchSnoozeResult = BatchSnoozeResult.Error.Unknown

        viewModelScope.launch(ioCoroutineContext) {
            val currentMailbox = currentMailbox.value
            unsnoozeResult = if (currentMailbox == null) {
                BatchSnoozeResult.Error.Unknown
            } else {
                ioDispatcher { unsnoozeThreadsWithoutRefresh(scope = null, currentMailbox, threads) }
            }

            unsnoozeResult.let {
                val userFeedbackMessage = when (it) {
                    is BatchSnoozeResult.Success -> {
                        sharedUtils.refreshFolders(mailbox = currentMailbox!!, messagesFoldersIds = it.impactedFolders)
                        appContext.resources.getQuantityString(R.plurals.snackbarUnsnoozeSuccess, threads.count())
                    }
                    is BatchSnoozeResult.Error -> getUnsnoozeErrorMessage(it)
                }

                snackbarManager.postValue(userFeedbackMessage)
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

    //region Emoji reaction
    /**
     * Wrapper method to send an emoji reaction to the api. This method will check if the emoji reaction is allowed before
     * initiating an api call. This is the entry point to add an emoji reaction anywhere in the app.
     *
     * If sending is allowed, the caller place can fake the emoji reaction locally thanks to [onAllowed].
     * If sending is not allowed, it will display the error directly to the user and avoid doing the api call.
     */
    fun trySendEmojiReply(emoji: String, messageUid: String, reactions: Map<String, Reaction>, onAllowed: () -> Unit = {}) {
        viewModelScope.launch {
            when (val status = reactions.getEmojiSendStatus(emoji)) {
                EmojiSendStatus.Allowed -> {
                    onAllowed()
                    sendEmojiReply(emoji, messageUid)
                }
                is EmojiSendStatus.NotAllowed -> snackbarManager.postValue(appContext.getString(status.errorMessageRes))
            }
        }
    }

    private fun Map<String, Reaction>.getEmojiSendStatus(emoji: String): EmojiSendStatus = when {
        this[emoji]?.hasReacted == true -> EmojiSendStatus.NotAllowed.AlreadyUsed
        hasAvailableReactionSlot().not() -> EmojiSendStatus.NotAllowed.MaxReactionReached
        hasNetwork.not() -> EmojiSendStatus.NotAllowed.NoInternet
        else -> EmojiSendStatus.Allowed
    }

    /**
     * The actual logic of sending an emoji reaction to the api. This method initializes a [Draft] instance, stores it into the
     * database and schedules the [DraftsActionsWorker] so the draft is uploaded on the api.
     */
    private suspend fun sendEmojiReply(emoji: String, messageUid: String) {
        val targetMessage = messageController.getMessage(messageUid) ?: return
        val (fullMessage, hasFailedFetching) = draftController.fetchHeavyDataIfNeeded(targetMessage)
        if (hasFailedFetching) return
        val draftMode = Draft.DraftMode.REPLY_ALL

        val draft = Draft().apply {
            with(draftInitManager) {
                setPreviousMessage(draftMode, fullMessage)
            }

            val quote = draftInitManager.createQuote(draftMode, fullMessage, attachments)
            body = EMOJI_REACTION_PLACEHOLDER + quote

            val currentMailbox = currentMailboxLive.asFlow().first()
            with(draftInitManager) {
                // We don't want to send the HTML code of the signature for an emoji reaction but we still need to send the
                // identityId stored in a Signature
                val signature = chooseSignature(currentMailbox.email, currentMailbox.signatures, draftMode, fullMessage)
                setSignatureIdentity(signature)
            }

            mimeType = Utils.TEXT_HTML

            action = Draft.DraftAction.SEND_REACTION
            emojiReaction = emoji
        }

        draftController.upsertDraft(draft)

        draftsActionsWorkerScheduler.scheduleWork(draft.localUuid)
    }

    private sealed interface EmojiSendStatus {
        data object Allowed : EmojiSendStatus

        sealed class NotAllowed(@StringRes val errorMessageRes: Int) : EmojiSendStatus {
            data object AlreadyUsed : NotAllowed(ErrorCode.EmojiReactions.alreadyUsed.translateRes)
            data object MaxReactionReached : NotAllowed(ErrorCode.EmojiReactions.maxReactionReached.translateRes)
            data object NoInternet : NotAllowed(RCore.string.noConnection)
        }
    }
    //endregion

    //region Undo action
    fun undoAction(undoData: UndoData) = viewModelScope.launch(ioCoroutineContext) {

        fun List<ApiResponse<*>>.getFailedCall() = firstOrNull { it.data != true }

        val mailbox = currentMailbox.value!!
        val (resources, foldersIds, destinationFolderId) = undoData

        val apiResponses = resources.map { ApiRepository.undoAction(it) }

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
            else -> failedCall.translateError()
        }

        snackbarManager.postValue(appContext.getString(snackbarTitle))
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
        messageUid: String?,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val newFolderId = createNewFolderSync(name) ?: return@launch
        moveThreadsOrMessageTo(newFolderId, threadsUids, messageUid)
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

        private const val EMOJI_REACTION_PLACEHOLDER = "<div>__REACTION_PLACEMENT__<br></div>"
    }
}

private val HIDDEN_ROLES_WHEN_EMPTY = setOf(FolderRole.SCHEDULED_DRAFTS, FolderRole.SNOOZED)

private fun Flow<List<Folder>>.removeRolesThatHideWhenEmpty(): Flow<List<Folder>> = map {
    it.filterNot { folder -> folder.role in HIDDEN_ROLES_WHEN_EMPTY && folder.threads.isEmpty() }
}

private fun Flow<List<Folder>>.keepTopLevelFolders(): Flow<List<Folder>> = map {
    it.filter { folder -> folder.parent == null }
}
