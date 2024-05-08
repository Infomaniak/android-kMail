/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.lib.core.utils.DownloadManagerUtils
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.MatomoMail.trackMultiSelectionEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
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
import com.infomaniak.mail.data.models.MoveResult
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.SnackbarManager.UndoData
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ContactUtils.getPhoneContacts
import com.infomaniak.mail.utils.ContactUtils.mergeApiContactsIntoPhoneContacts
import com.infomaniak.mail.utils.NotificationUtils
import com.infomaniak.mail.utils.NotificationUtils.Companion.cancelNotification
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.SharedUtils.Companion.updateSignatures
import com.infomaniak.mail.utils.Utils.isPermanentDeleteFolder
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.coroutineContext
import com.infomaniak.mail.utils.extensions.*
import com.infomaniak.mail.views.itemViews.AvatarMergedContactData
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.notifications.ResultsChange
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Date
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
    private val notificationUtils: NotificationUtils,
    private val permissionsController: PermissionsController,
    private val quotasController: QuotasController,
    private val refreshController: RefreshController,
    private val sharedUtils: SharedUtils,
    private val threadController: ThreadController,
    private val snackbarManager: SnackbarManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)
    private var refreshEverythingJob: Job? = null

    // First boolean is the download status, second boolean is if the LoadMore button should be displayed
    val isDownloadingChanges: MutableLiveData<Boolean> = MutableLiveData(false)
    val isInternetAvailable = MutableLiveData<Boolean>()
    val isMovedToNewFolder = SingleLiveEvent<Boolean>()
    val toggleLightThemeForMessage = SingleLiveEvent<Message>()
    val deletedMessages = SingleLiveEvent<Set<String>>()
    val deleteThreadOrMessageTrigger = SingleLiveEvent<Unit>()
    val flushFolderTrigger = SingleLiveEvent<Unit>()
    val newFolderResultTrigger = MutableLiveData<Unit>()
    val reportPhishingTrigger = SingleLiveEvent<Unit>()
    val canInstallUpdate = MutableLiveData(false)

    val threadUidsToDeleteOrArchive = MutableLiveData<List<String>>()

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

    val currentDefaultFoldersLive = _currentMailboxObjectId.flatMapLatest { objectId ->
        objectId?.let { folderController.getDefaultFoldersAsync().map { it.list.getDefaultMenuFolders() } } ?: emptyFlow()
    }.asLiveData(ioCoroutineContext)

    val currentCustomFoldersLive = _currentMailboxObjectId.flatMapLatest { objectId ->
        objectId
            ?.let { folderController.getCustomFoldersAsync().map { it.list.getCustomMenuFolders(dismissHiddenChildren = true) } }
            ?: emptyFlow()
    }.asLiveData(ioCoroutineContext)

    val currentQuotasLive = _currentMailboxObjectId.flatMapLatest {
        it?.let(quotasController::getQuotasAsync) ?: emptyFlow()
    }.asLiveData(ioCoroutineContext)

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
        refreshController.clearCallbacks()
        super.onCleared()
        RealmDatabase.closeOldRealms()
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

        if (mailbox.isLocked || !mailbox.isPasswordValid) {
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
            updateFeatureFlag(mailbox)
            updateExternalMailInfo(mailbox)

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
        updateSignatures(mailbox, mailboxContentRealm())
    }

    private fun updateFeatureFlag(mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        SentryLog.d(TAG, "Force refresh Features flags")
        sharedUtils.updateAiFeatureFlag(mailbox.objectId, mailbox.uuid)
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

    private fun updateFolders(mailbox: Mailbox) {
        SentryLog.d(TAG, "Force refresh Folders")
        ApiRepository.getFolders(mailbox.uuid).data?.let { folders ->
            if (!mailboxContentRealm().isClosed()) folderController.update(folders, mailboxContentRealm())
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
            folder = currentFolder.value!!,
            realm = mailboxContentRealm(),
            callbacks = RefreshCallbacks(::onDownloadStart, ::onDownloadStop),
        )
    }

    private fun updateAddressBooks() {
        ApiRepository.getAddressBooks().data?.addressBooks?.let(addressBookController::update)
    }

    private fun updateContacts() {
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
        val folder = folderController.getFolder(folderId) ?: return

        val callbacks = if (showSwipeRefreshLayout) RefreshCallbacks(::onDownloadStart, ::onDownloadStop) else null

        refreshController.refreshThreads(
            refreshMode = RefreshMode.REFRESH_FOLDER_WITH_ROLE,
            mailbox = mailbox,
            folder = folder,
            realm = mailboxContentRealm(),
            callbacks = callbacks,
        )
    }

    //region Delete
    fun deleteMessage(threadUid: String, message: Message) {
        val threadUidsList = listOf(threadUid)
        if (threadHasOnlyOneMessageLeftInCurrentFolder(threadUid)) threadUidsToDeleteOrArchive.postValue(threadUidsList)
        deleteThreadsOrMessage(threadsUids = threadUidsList, message = message)
    }

    fun deleteThread(threadUid: String, isSwipe: Boolean = false) {
        val threadUidList = listOf(threadUid)
        threadUidsToDeleteOrArchive.postValue(threadUidList)
        deleteThreadsOrMessage(threadsUids = threadUidList, isSwipe = isSwipe)
    }

    fun deleteThreads(threadsUids: List<String>) {
        threadUidsToDeleteOrArchive.postValue(threadsUids)
        deleteThreadsOrMessage(threadsUids = threadsUids)
    }

    private fun deleteThreadsOrMessage(
        threadsUids: List<String>,
        message: Message? = null,
        isSwipe: Boolean = false,
    ) = viewModelScope.launch(ioCoroutineContext) {

        val mailbox = currentMailbox.value!!
        val threads = getActionThreads(threadsUids).ifEmpty { return@launch }
        var trashId: String? = null
        var undoResource: String? = null
        val shouldPermanentlyDelete = isPermanentDeleteFolder(getActionFolderRole(threads, message))

        val messages = getMessagesToDelete(threads, message)
        val uids = messages.getUids()

        val apiResponse = if (shouldPermanentlyDelete) {
            ApiRepository.deleteMessages(mailbox.uuid, uids)
        } else {
            trashId = folderController.getFolder(FolderRole.TRASH)!!.id
            ApiRepository.moveMessages(mailbox.uuid, uids, trashId).also {
                undoResource = it.data?.undoResource
            }
        }

        deleteThreadOrMessageTrigger.postValue(Unit)
        if (apiResponse.isSuccess()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(exception = trashId),
                destinationFolderId = trashId,
                callbacks = RefreshCallbacks(::onDownloadStart, ::onDownloadStop),
            )
        } else if (isSwipe) {
            // We need to make the swiped Thread come back, so we reassign the LiveData with Realm values
            reassignCurrentThreadsLive()
        }

        val undoDestinationId = message?.folderId ?: threads.first().folderId
        val undoFoldersIds = (messages.getFoldersIds(exception = undoDestinationId) + trashId).filterNotNull()
        showDeleteSnackbar(
            apiResponse,
            shouldPermanentlyDelete,
            message,
            undoResource,
            undoFoldersIds,
            undoDestinationId,
            threads.count(),
        )
    }

    private fun showDeleteSnackbar(
        apiResponse: ApiResponse<*>,
        shouldPermanentlyDelete: Boolean,
        message: Message?,
        undoResource: String?,
        undoFoldersIds: List<String>,
        undoDestinationId: String?,
        numberOfImpactedThreads: Int,
    ) {

        val snackbarTitle = if (apiResponse.isSuccess()) {
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
            appContext.getString(apiResponse.translatedError)
        }

        snackbarManager.postValue(snackbarTitle, undoResource?.let { UndoData(it, undoFoldersIds, undoDestinationId) })
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
            refreshFoldersAsync(mailbox, listOf(draftFolderId))
        }

        showDraftDeletedSnackbar(apiResponse)
    }

    private fun showDraftDeletedSnackbar(apiResponse: ApiResponse<Unit>) {
        val titleRes = if (apiResponse.isSuccess()) R.string.snackbarDraftDeleted else apiResponse.translateError()
        snackbarManager.postValue(appContext.getString(titleRes))
    }
    //endregion

    //region Move
    fun moveThreadsOrMessageTo(
        destinationFolderId: String,
        threadsUids: Array<String>,
        messageUid: String? = null,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value!!
        val destinationFolder = folderController.getFolder(destinationFolderId)!!
        val threads = getActionThreads(threadsUids.toList()).ifEmpty { return@launch }
        val message = messageUid?.let { messageController.getMessage(it)!! }

        val messages = sharedUtils.getMessagesToMove(threads, message)

        val apiResponse = ApiRepository.moveMessages(mailbox.uuid, messages.getUids(), destinationFolder.id)

        threadUidsToDeleteOrArchive.postValue(threadsUids.toList())

        if (apiResponse.isSuccess()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(exception = destinationFolder.id),
                destinationFolderId = destinationFolder.id,
                callbacks = RefreshCallbacks(::onDownloadStart, ::onDownloadStop),
            )
        }

        showMoveSnackbar(threads, message, messages, apiResponse, destinationFolder)
    }

    private fun showMoveSnackbar(
        threads: List<Thread>,
        message: Message?,
        messages: List<Message>,
        apiResponse: ApiResponse<MoveResult>,
        destinationFolder: Folder,
    ) {
        val undoDestinationId = message?.folderId ?: threads.first().folderId
        val undoFoldersIds = messages.getFoldersIds(exception = undoDestinationId) + destinationFolder.id

        val destination = destinationFolder.getLocalizedName(appContext)

        val snackbarTitle = when {
            !apiResponse.isSuccess() -> appContext.getString(apiResponse.translatedError)
            message == null -> appContext.resources.getQuantityString(R.plurals.snackbarThreadMoved, threads.count(), destination)
            else -> appContext.getString(R.string.snackbarMessageMoved, destination)
        }

        val undoData = apiResponse.data?.undoResource?.let { UndoData(it, undoFoldersIds, undoDestinationId) }
        snackbarManager.postValue(snackbarTitle, undoData)
    }
    //endregion

    //region Archive
    fun archiveMessage(threadUid: String, message: Message) {
        val threadUidsList = listOf(threadUid)
        if (threadHasOnlyOneMessageLeftInCurrentFolder(threadUid)) threadUidsToDeleteOrArchive.postValue(threadUidsList)
        archiveThreadsOrMessage(threadsUids = threadUidsList, message = message)
    }

    fun archiveThread(threadUid: String) {
        val threadUidsList = listOf(threadUid)
        threadUidsToDeleteOrArchive.postValue(threadUidsList)
        archiveThreadsOrMessage(threadsUids = threadUidsList)
    }

    fun archiveThreads(threadsUids: List<String>) {
        threadUidsToDeleteOrArchive.postValue(threadsUids)
        archiveThreadsOrMessage(threadsUids = threadsUids)
    }

    private fun archiveThreadsOrMessage(
        threadsUids: List<String>,
        message: Message? = null,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value!!
        val threads = getActionThreads(threadsUids).ifEmpty { return@launch }

        val role = getActionFolderRole(threads, message)
        val isFromArchive = role == FolderRole.ARCHIVE

        val destinationFolderRole = if (isFromArchive) FolderRole.INBOX else FolderRole.ARCHIVE
        val destinationFolder = folderController.getFolder(destinationFolderRole)!!

        val messages = sharedUtils.getMessagesToMove(threads, message)

        val apiResponse = ApiRepository.moveMessages(mailbox.uuid, messages.getUids(), destinationFolder.id)

        if (apiResponse.isSuccess()) {
            val messagesFoldersIds = messages.getFoldersIds(exception = destinationFolder.id)
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messagesFoldersIds,
                destinationFolderId = destinationFolder.id,
                callbacks = RefreshCallbacks(::onDownloadStart, ::onDownloadStop),
            )
        }

        showMoveSnackbar(threads, message, messages, apiResponse, destinationFolder)
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
        val threads = getActionThreads(threadsUids).ifEmpty { return@launch }

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

    private fun markAsUnseen(
        mailbox: Mailbox,
        threads: List<Thread>,
        message: Message? = null,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val messages = getMessagesToMarkAsUnseen(threads, message)
        val isSuccess = ApiRepository.markMessagesAsUnseen(mailbox.uuid, messages.getUids()).isSuccess()
        if (isSuccess) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(),
                callbacks = RefreshCallbacks(::onDownloadStart, ::onDownloadStop),
            )
        }
    }

    private fun getMessagesToMarkAsUnseen(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap(messageController::getLastMessageAndItsDuplicatesToExecuteAction)
        else -> messageController.getMessageAndDuplicates(threads.first(), message)
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
        val threads = getActionThreads(threadsUids).ifEmpty { return@launch }

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

        val isSuccess = if (isFavorite) {
            ApiRepository.removeFromFavorites(mailbox.uuid, uids).isSuccess()
        } else {
            ApiRepository.addToFavorites(mailbox.uuid, uids).isSuccess()
        }

        if (isSuccess) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(),
                callbacks = RefreshCallbacks(::onDownloadStart, ::onDownloadStop),
            )
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
        val threads = getActionThreads(threadsUids).ifEmpty { return@launch }

        val destinationFolderRole = if (getActionFolderRole(threads, message) == FolderRole.SPAM) {
            FolderRole.INBOX
        } else {
            FolderRole.SPAM
        }
        val destinationFolder = folderController.getFolder(destinationFolderRole)!!

        val messages = getMessagesToSpamOrHam(threads, message)

        val apiResponse = ApiRepository.moveMessages(mailbox.uuid, messages.getUids(), destinationFolder.id)

        if (apiResponse.isSuccess()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(exception = destinationFolder.id),
                destinationFolderId = destinationFolder.id,
                callbacks = RefreshCallbacks(::onDownloadStart, ::onDownloadStop),
            )
        }

        if (displaySnackbar) showMoveSnackbar(threads, message, messages, apiResponse, destinationFolder)
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

    //region BlockUser
    fun blockUser(message: Message) = viewModelScope.launch(ioCoroutineContext) {
        val mailboxUuid = currentMailbox.value?.uuid!!

        with(ApiRepository.blockUser(mailboxUuid, message.folderId, message.shortUid)) {

            val snackbarTitle = if (isSuccess()) R.string.snackbarBlockUserConfirmation else translatedError

            snackbarManager.postValue(appContext.getString(snackbarTitle))
        }
    }
    //endregion

    //region Undo action
    fun undoAction(undoData: UndoData) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value!!
        val (resource, foldersIds, destinationFolderId) = undoData

        with(ApiRepository.undoAction(resource)) {

            val snackbarTitle = if (data == true) {
                // Don't use `refreshFoldersAsync` here, it will make the Snackbars blink.
                sharedUtils.refreshFolders(
                    mailbox = mailbox,
                    messagesFoldersIds = foldersIds,
                    destinationFolderId = destinationFolderId,
                )
                R.string.snackbarMoveCancelled
            } else {
                if (translatedError == 0) RCore.string.anErrorHasOccurred else translatedError
            }

            snackbarManager.postValue(appContext.getString(snackbarTitle))
        }
    }
    //endregion

    //region New Folder
    private fun createNewFolderSync(name: String): String? {
        val mailbox = currentMailbox.value ?: return null
        val apiResponse = ApiRepository.createFolder(mailbox.uuid, name)

        newFolderResultTrigger.postValue(Unit)

        return if (apiResponse.isSuccess()) {
            updateFolders(mailbox)
            apiResponse.data?.id
        } else {
            snackbarManager.postValue(title = appContext.getString(apiResponse.translateError()), undoData = null)
            null
        }
    }

    fun createNewFolder(name: String) = viewModelScope.launch(ioCoroutineContext) { createNewFolderSync(name) }

    fun moveToNewFolder(
        name: String,
        threadsUids: Array<String>,
        messageUid: String?,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val newFolderId = createNewFolderSync(name) ?: return@launch
        moveThreadsOrMessageTo(newFolderId, threadsUids, messageUid)
        isMovedToNewFolder.postValue(true)
    }
    //endregion

    private fun refreshFoldersAsync(
        mailbox: Mailbox,
        messagesFoldersIds: List<String>,
        destinationFolderId: String? = null,
        callbacks: RefreshCallbacks? = null,
    ) = viewModelScope.launch(ioCoroutineContext) {
        sharedUtils.refreshFolders(mailbox, messagesFoldersIds, destinationFolderId, currentFolderId, callbacks)
    }

    private fun onDownloadStart() {
        isDownloadingChanges.postValue(true)
    }

    private fun onDownloadStop() {
        isDownloadingChanges.postValue(false)
    }

    private fun getActionThreads(threadsUids: List<String>): List<Thread> = threadsUids.mapNotNull(threadController::getThread)

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
            threads?.firstOrNull()?.folder?.id == FolderController.SEARCH_FOLDER_ID -> {
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

    fun refreshDraftFolderWhenDraftArrives(scheduledDate: Long) = viewModelScope.launch(ioCoroutineContext) {
        val folder = folderController.getFolder(FolderRole.DRAFT)

        if (folder?.cursor != null) {

            val timeNow = Date().time
            val delay = REFRESH_DELAY + max(scheduledDate - timeNow, 0L)
            delay(min(delay, MAX_REFRESH_DELAY))

            refreshController.refreshThreads(
                refreshMode = RefreshMode.REFRESH_FOLDER_WITH_ROLE,
                mailbox = currentMailbox.value!!,
                folder = folder,
                realm = mailboxContentRealm(),
            )
        }
    }

    fun handleDeletedMessages(uids: Set<String>) = viewModelScope.launch(ioCoroutineContext) {

        snackbarManager.postValue(appContext.getString(R.string.snackbarDeletedConversation))

        val mailbox = currentMailbox.value ?: return@launch
        val realm = mailboxContentRealm()

        val foldersToUpdate = realm.writeBlocking {
            uids.mapNotNull { MessageController.getMessage(it, realm = this)?.folder?.copyFromRealm() }.toSet()
        }

        foldersToUpdate.forEach { folder ->
            refreshController.refreshThreads(
                refreshMode = RefreshMode.REFRESH_FOLDER,
                mailbox = mailbox,
                folder = folder,
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

    private fun threadHasOnlyOneMessageLeftInCurrentFolder(threadUid: String): Boolean {
        val folderId = currentFolderId ?: return false
        return messageController.getMessageCountInThreadForFolder(threadUid, folderId, mailboxContentRealm()) == 1L
    }

    companion object {
        private val TAG: String = MainViewModel::class.java.simpleName
        private val DEFAULT_SELECTED_FOLDER = FolderRole.INBOX
        private const val REFRESH_DELAY = 2_000L // We add this delay because `etop` isn't always big enough.
        private const val MAX_REFRESH_DELAY = 6_000L
    }
}
