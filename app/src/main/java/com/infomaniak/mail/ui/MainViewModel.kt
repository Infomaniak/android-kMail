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
package com.infomaniak.mail.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.*
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.MatomoMail.trackMultiSelectionEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.*
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.cache.mailboxInfo.PermissionsController
import com.infomaniak.mail.data.cache.mailboxInfo.QuotasController
import com.infomaniak.mail.data.cache.userInfo.AddressBookController
import com.infomaniak.mail.data.cache.userInfo.MergedContactController
import com.infomaniak.mail.data.models.*
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.SelectedThread
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.SnackBarManager
import com.infomaniak.mail.ui.main.SnackBarManager.*
import com.infomaniak.mail.ui.main.folder.ThreadListViewModel
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.ContactUtils.arrangeMergedContacts
import com.infomaniak.mail.utils.ContactUtils.getPhoneContacts
import com.infomaniak.mail.utils.ContactUtils.mergeApiContactsIntoPhoneContacts
import com.infomaniak.mail.utils.NotificationUtils.cancelNotification
import com.infomaniak.mail.utils.SharedViewModelUtils.refreshFolders
import com.infomaniak.mail.workers.DraftsActionsWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.ext.copyFromRealm
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import com.infomaniak.lib.core.R as RCore

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val draftsActionsWorkerScheduler: DraftsActionsWorker.Scheduler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private inline val context: Context get() = getApplication()

    val isInternetAvailable = SingleLiveEvent<Boolean>()
    // First boolean is the download status, second boolean is if the LoadMore button should be displayed
    val isDownloadingChanges: MutableLiveData<Pair<Boolean, Boolean?>> = MutableLiveData(false to null)
    val isNewFolderCreated = SingleLiveEvent<Boolean>()

    // Explanation of this Map : Map<Email, Map<Name, MergedContact>>
    val mergedContacts = MutableLiveData<Map<String, Map<String, MergedContact>>?>()

    //region Multi selection
    val isMultiSelectOnLiveData = MutableLiveData(false)
    inline var isMultiSelectOn
        get() = isMultiSelectOnLiveData.value!!
        set(value) {
            isMultiSelectOnLiveData.value = value
        }

    val selectedThreadsLiveData = MutableLiveData(mutableSetOf<SelectedThread>())
    inline val selectedThreads
        get() = selectedThreadsLiveData.value!!

    val isEverythingSelected get() = selectedThreads.count() == currentThreadsLive.value?.list?.count()
    //endregion

    val snackBarManager by lazy { SnackBarManager() }

    val toggleLightThemeForMessage = SingleLiveEvent<Message>()

    private val coroutineContext = viewModelScope.coroutineContext + ioDispatcher
    private var refreshMailboxesAndFoldersJob: Job? = null

    val mailboxesLive = MailboxController.getMailboxesAsync(AccountUtils.currentUserId).asLiveData(coroutineContext)

    //region Current Mailbox
    private val _currentMailboxObjectId = MutableStateFlow<String?>(null)

    val currentMailbox = _currentMailboxObjectId.mapLatest {
        it?.let(MailboxController::getMailbox)
    }.asLiveData(ioDispatcher)

    val currentFoldersLive = _currentMailboxObjectId.flatMapLatest {
        it?.let { FolderController.getRootsFoldersAsync().map { results -> results.list.getMenuFolders() } } ?: emptyFlow()
    }.asLiveData(coroutineContext)

    val currentQuotasLive = _currentMailboxObjectId.flatMapLatest {
        it?.let(QuotasController::getQuotasAsync) ?: emptyFlow()
    }.asLiveData(coroutineContext)

    val currentPermissionsLive = _currentMailboxObjectId.flatMapLatest {
        it?.let(PermissionsController::getPermissionsAsync) ?: emptyFlow()
    }.asLiveData(coroutineContext)
    //endregion

    //region Current Folder
    private val _currentFolderId = MutableStateFlow<String?>(null)
    val currentFolderId get() = _currentFolderId.value

    val currentFolder = _currentFolderId.mapLatest {
        it?.let(FolderController::getFolder)
    }.asLiveData(ioDispatcher)

    val currentFolderLive = _currentFolderId.flatMapLatest {
        it?.let(FolderController::getFolderAsync) ?: emptyFlow()
    }.asLiveData(coroutineContext)

    val currentFilter = SingleLiveEvent(ThreadFilter.ALL)

    val currentThreadsLive = observeFolderAndFilter().flatMapLatest { (folder, filter) ->
        folder?.let { ThreadController.getThreadsAsync(it, filter) } ?: emptyFlow()
    }.asLiveData(coroutineContext)

    private fun observeFolderAndFilter() = MediatorLiveData<Pair<Folder?, ThreadFilter>>().apply {
        value = currentFolder.value to currentFilter.value!!
        addSource(currentFolder) { value = it to value!!.second }
        addSource(currentFilter) { value = value?.first to it }
    }.asFlow()

    /**
     * Force update the `currentFilter` to its current value.
     * The sole effect will be to force the `currentThreadsLive` to trigger immediately.
     * The idea is to force the Threads adapter to trigger again, to update the sections headers (Today, Yesterday, etc...).
     */
    fun forceTriggerCurrentFolder() {
        currentFilter.apply { value = value }
    }

    fun isCurrentFolderRole(role: FolderRole) = currentFolder.value?.role == role
    //endregion

    private fun selectMailbox(mailbox: Mailbox) {
        if (mailbox.objectId != _currentMailboxObjectId.value) {
            Log.d(TAG, "Select mailbox: ${mailbox.email}")
            if (mailbox.mailboxId != AccountUtils.currentMailboxId) AccountUtils.currentMailboxId = mailbox.mailboxId
            AccountUtils.currentMailboxEmail = mailbox.email
            _currentMailboxObjectId.value = mailbox.objectId
            _currentFolderId.value = null
        }
    }

    private fun selectFolder(folderId: String) {
        if (folderId != currentFolderId) {
            Log.d(TAG, "Select folder: $folderId")
            _currentFolderId.value = folderId
        }
    }

    fun updateUserInfo() = viewModelScope.launch(ioDispatcher) {
        Log.d(TAG, "Update user info")
        updateAddressBooks()
        updateContacts()
    }

    fun loadCurrentMailbox() = liveData(ioDispatcher) {
        loadCurrentMailboxFromLocal()
        loadCurrentMailboxFromRemote()
        emit(null)
    }

    private suspend fun loadCurrentMailboxFromLocal() {
        Log.d(TAG, "Load current mailbox from local")
        val userId = AccountUtils.currentUserId
        val mailboxId = AccountUtils.currentMailboxId
        val mailbox = MailboxController.getMailboxWithFallback(userId, mailboxId) ?: return
        selectMailbox(mailbox)

        if (currentFolderId == null) {
            val folder = FolderController.getFolder(DEFAULT_SELECTED_FOLDER) ?: return
            selectFolder(folder.id)
        }

        // Delete search data in case they couldn't be deleted at the end of the previous Search.
        SearchUtils.deleteRealmSearchData()
    }

    private suspend fun loadCurrentMailboxFromRemote() {
        Log.d(TAG, "Load current mailbox from remote")
        with(ApiRepository.getMailboxes()) {
            if (isSuccess()) {
                val isCurrentMailboxDeleted = MailboxController.updateMailboxes(context, data!!)
                if (isCurrentMailboxDeleted) return
                MailboxController.getMailboxWithFallback(
                    userId = AccountUtils.currentUserId,
                    mailboxId = AccountUtils.currentMailboxId,
                )?.let(::openMailbox)
            }
        }
    }

    private fun openMailbox(mailbox: Mailbox) {
        selectMailbox(mailbox)
        updateSignatures(mailbox)
        updateFolders(mailbox)

        (currentFolderId?.let(FolderController::getFolder)
            ?: FolderController.getFolder(DEFAULT_SELECTED_FOLDER))?.let { folder ->
            selectFolder(folder.id)
            viewModelScope.launch(viewModelScope.handlerIO) {
                refreshThreads(mailbox, folder.id)
            }
        }

        draftsActionsWorkerScheduler.scheduleWork()
    }

    fun forceRefreshMailboxesAndFolders() {

        refreshMailboxesAndFoldersJob?.cancel()
        refreshMailboxesAndFoldersJob = viewModelScope.launch(viewModelScope.handlerIO) {

            Log.d(TAG, "Force refresh mailboxes")
            with(ApiRepository.getMailboxes()) {
                if (isSuccess()) {
                    val isCurrentMailboxDeleted = MailboxController.updateMailboxes(context, data!!)
                    if (isCurrentMailboxDeleted) return@launch
                }
            }

            Log.d(TAG, "Force refresh quotas")
            val mailbox = currentMailbox.value!!
            updateMailboxQuotas(mailbox)

            Log.d(TAG, "Force refresh permissions")
            updateMailboxPermissions(mailbox)

            Log.d(TAG, "Force refresh folders")
            updateFolders(mailbox)
        }
    }

    fun dismissCurrentMailboxNotifications() = viewModelScope.launch {
        currentMailbox.value?.let {
            context.cancelNotification(it.notificationGroupId)
        }
    }

    private fun updateMailboxQuotas(mailbox: Mailbox) {
        if (mailbox.isLimited) with(ApiRepository.getQuotas(mailbox.hostingId, mailbox.mailboxName)) {
            if (isSuccess()) MailboxController.updateMailbox(mailbox.objectId) {
                it.quotas = data
            }
        }
    }

    private fun updateMailboxPermissions(mailbox: Mailbox) {
        with(ApiRepository.getPermissions(mailbox.linkId, mailbox.hostingId)) {
            if (isSuccess()) MailboxController.updateMailbox(mailbox.objectId) {
                it.permissions = data
            }
        }
    }

    fun openFolder(folderId: String) = viewModelScope.launch(ioDispatcher) {
        if (folderId == currentFolderId) return@launch

        if (currentFilter.value != ThreadFilter.ALL) currentFilter.postValue(ThreadFilter.ALL)

        selectFolder(folderId)
        refreshThreads(folderId = folderId)
    }

    fun flushFolder() = viewModelScope.launch(ioDispatcher) {

        val isSuccess = ApiRepository.flushFolder(
            mailboxUuid = currentMailbox.value?.uuid ?: return@launch,
            folderId = currentFolderId ?: return@launch,
        ).isSuccess()

        if (isSuccess) {
            forceRefreshThreads()
        } else {
            snackBarManager.postValue(context.getString(RCore.string.anErrorHasOccurred))
        }
    }

    fun forceRefreshThreads() = viewModelScope.launch(ioDispatcher) {
        Log.d(TAG, "Force refresh threads")
        refreshThreads()
    }

    fun getOnePageOfOldMessages() = viewModelScope.launch(ioDispatcher) {

        if (isDownloadingChanges.value?.first == true) return@launch

        RefreshController.refreshThreads(
            refreshMode = RefreshMode.ONE_PAGE_OF_OLD_MESSAGES,
            mailbox = currentMailbox.value!!,
            folder = currentFolder.value!!,
            started = ::startedDownload,
            stopped = ::stoppedDownload,
        )
    }

    private fun updateAddressBooks() {
        ApiRepository.getAddressBooks().data?.addressBooks?.let(AddressBookController::update)
    }

    private fun updateContacts() {
        ApiRepository.getContacts().data?.let { apiContacts ->
            val phoneMergedContacts = getPhoneContacts(context)
            mergeApiContactsIntoPhoneContacts(apiContacts, phoneMergedContacts)
            MergedContactController.update(phoneMergedContacts.values.toList())
        }
    }

    fun observeMergedContactsLive() = viewModelScope.launch(coroutineContext) {
        MergedContactController.getMergedContactsAsync().collect { contacts ->
            mergedContacts.postValue(arrangeMergedContacts(contacts.list.copyFromRealm()))
        }
    }

    private fun updateSignatures(mailbox: Mailbox) = viewModelScope.launch(ioDispatcher) {

        val apiResponse = ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailboxName)
        val data = apiResponse.data
        val signatures = data?.signatures

        val defaultSignaturesCount = signatures?.count { it.isDefault } ?: -1
        when {
            data == null -> Sentry.withScope { scope ->
                scope.level = SentryLevel.ERROR
                scope.setExtra("email", AccountUtils.currentMailboxEmail.toString())
                scope.setExtra("apiResponse", apiResponse.toString())
                scope.setExtra("status", apiResponse.result.name)
                scope.setExtra("errorCode", "${apiResponse.error?.code}")
                scope.setExtra("errorDescription", "${apiResponse.error?.description}")
                scope.setExtra("errorTranslated", context.getString(apiResponse.translateError()))
                Sentry.captureMessage("Signature: The call to get Signatures returned a `null` data")
            }
            signatures?.isEmpty() == true -> Sentry.withScope { scope ->
                scope.level = SentryLevel.ERROR
                scope.setExtra("email", AccountUtils.currentMailboxEmail.toString())
                Sentry.captureMessage("Signature: This user doesn't have any Signature")
            }
            defaultSignaturesCount == 0 -> Sentry.withScope { scope ->
                scope.level = SentryLevel.ERROR
                scope.setExtra("signaturesCount", "${signatures?.count()}")
                scope.setExtra("email", AccountUtils.currentMailboxEmail.toString())
                Sentry.captureMessage("Signature: This user has Signatures, but no default one")
            }
            defaultSignaturesCount > 1 -> Sentry.withScope { scope ->
                scope.level = SentryLevel.ERROR
                scope.setExtra("defaultSignaturesCount", "$defaultSignaturesCount")
                scope.setExtra("totalSignaturesCount", "${signatures?.count()}")
                scope.setExtra("email", AccountUtils.currentMailboxEmail.toString())
                Sentry.captureMessage("Signature: This user has several default Signatures")
            }
        }

        signatures?.let(SignatureController::update)
    }

    private fun updateFolders(mailbox: Mailbox) {
        val currentRealm = RealmDatabase.mailboxContent()
        ApiRepository.getFolders(mailbox.uuid).data?.let { folders ->
            if (!currentRealm.isClosed()) FolderController.update(folders, currentRealm)
        }
    }

    private suspend fun refreshThreads(mailbox: Mailbox? = currentMailbox.value, folderId: String? = currentFolderId) {

        if (mailbox == null || folderId == null) return

        FolderController.getFolder(folderId)?.let { folder ->
            RefreshController.refreshThreads(
                refreshMode = RefreshMode.REFRESH_FOLDER_WITH_ROLE,
                mailbox = mailbox,
                folder = folder,
                started = ::startedDownload,
                stopped = ::stoppedDownload,
            )
        }
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

    private fun deleteThreadsOrMessage(
        threadsUids: List<String>,
        message: Message? = null,
    ) = viewModelScope.launch(viewModelScope.handlerIO) {
        val mailbox = currentMailbox.value!!
        val threads = getActionThreads(threadsUids).ifEmpty { return@launch }
        var trashId: String? = null
        var undoResource: String? = null

        val shouldPermanentlyDelete = isCurrentFolderRole(FolderRole.DRAFT)
                || isCurrentFolderRole(FolderRole.SPAM)
                || isCurrentFolderRole(FolderRole.TRASH)

        val messages = getMessagesToDelete(threads, message)
        val uids = messages.getUids()

        val isSuccess = if (shouldPermanentlyDelete) {
            ApiRepository.deleteMessages(mailbox.uuid, uids).isSuccess()
        } else {
            trashId = FolderController.getFolder(FolderRole.TRASH)!!.id
            val apiResponse = ApiRepository.moveMessages(mailbox.uuid, uids, trashId)
            undoResource = apiResponse.data?.undoResource
            apiResponse.isSuccess()
        }

        if (isSuccess) {
            refreshFolders(mailbox, messages.getFoldersIds(exception = trashId), trashId, ::startedDownload, ::stoppedDownload)
        }

        val undoDestinationId = message?.folderId ?: threads.first().folderId
        val undoFoldersIds = (messages.getFoldersIds(exception = undoDestinationId) + trashId).filterNotNull()
        showDeleteSnackbar(
            isSuccess,
            shouldPermanentlyDelete,
            message,
            undoResource,
            undoFoldersIds,
            undoDestinationId,
            threads.count(),
        )
    }

    private fun showDeleteSnackbar(
        isSuccess: Boolean,
        shouldPermanentlyDelete: Boolean,
        message: Message?,
        undoResource: String?,
        undoFoldersIds: List<String>,
        undoDestinationId: String?,
        numberOfImpactedThreads: Int,
    ) {

        val snackbarTitle = if (isSuccess) {
            val destination = context.getString(FolderRole.TRASH.folderNameRes)
            when {
                shouldPermanentlyDelete && message == null -> {
                    context.resources.getQuantityString(R.plurals.snackbarThreadDeletedPermanently, numberOfImpactedThreads)
                }
                shouldPermanentlyDelete && message != null -> {
                    context.resources.getString(R.string.snackbarMessageDeletedPermanently)
                }
                !shouldPermanentlyDelete && message == null -> {
                    context.resources.getQuantityString(R.plurals.snackbarThreadMoved, numberOfImpactedThreads, destination)
                }
                else -> {
                    context.resources.getString(R.string.snackbarMessageMoved, destination)
                }
            }
        } else {
            context.getString(RCore.string.anErrorHasOccurred)
        }

        snackBarManager.postValue(snackbarTitle, undoResource?.let { UndoData(it, undoFoldersIds, undoDestinationId) })
    }

    private fun getMessagesToDelete(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap(MessageController::getUnscheduledMessages)
        else -> MessageController.getMessageAndDuplicates(threads.first(), message)
    }
    //endregion

    //region Move
    fun moveThreadsOrMessageTo(
        destinationFolderId: String,
        threadsUids: Array<String>,
        messageUid: String? = null,
    ) = viewModelScope.launch(viewModelScope.handlerIO) {
        val mailbox = currentMailbox.value!!
        val destinationFolder = FolderController.getFolder(destinationFolderId)!!
        val threads = getActionThreads(threadsUids.toList()).ifEmpty { return@launch }
        val message = messageUid?.let { MessageController.getMessage(it)!! }

        val messages = getMessagesToMove(threads, message)

        val apiResponse = ApiRepository.moveMessages(mailbox.uuid, messages.getUids(), destinationFolder.id)

        if (apiResponse.isSuccess()) {
            refreshFolders(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(exception = destinationFolder.id),
                destinationFolderId = destinationFolder.id,
                started = ::startedDownload,
                stopped = ::stoppedDownload,
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

        val destination = destinationFolder.getLocalizedName(context)

        val snackbarTitle = when {
            !apiResponse.isSuccess() -> context.getString(RCore.string.anErrorHasOccurred)
            message == null -> context.resources.getQuantityString(R.plurals.snackbarThreadMoved, threads.count(), destination)
            else -> context.getString(R.string.snackbarMessageMoved, destination)
        }

        val undoData = apiResponse.data?.undoResource?.let { UndoData(it, undoFoldersIds, undoDestinationId) }
        snackBarManager.postValue(snackbarTitle, undoData)
    }

    private fun getMessagesToMove(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap(MessageController::getMovableMessages)
        else -> MessageController.getMessageAndDuplicates(threads.first(), message)
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
    ) = viewModelScope.launch(viewModelScope.handlerIO) {
        val mailbox = currentMailbox.value!!
        val threads = getActionThreads(threadsUids).ifEmpty { return@launch }

        val isArchived = message?.let { it.folder.role == FolderRole.ARCHIVE } ?: isCurrentFolderRole(FolderRole.ARCHIVE)

        val destinationFolderRole = if (isArchived) FolderRole.INBOX else FolderRole.ARCHIVE
        val destinationFolder = FolderController.getFolder(destinationFolderRole)!!

        val messages = getMessagesToMove(threads, message)

        val apiResponse = ApiRepository.moveMessages(mailbox.uuid, messages.getUids(), destinationFolder.id)

        if (apiResponse.isSuccess()) {
            val messagesFoldersIds = messages.getFoldersIds(exception = destinationFolder.id)
            refreshFolders(mailbox, messagesFoldersIds, destinationFolder.id, ::startedDownload, ::stoppedDownload)
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
    ) = viewModelScope.launch(viewModelScope.handlerIO) {
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
            SharedViewModelUtils.markAsSeen(mailbox, threads, message, ::startedDownload, ::stoppedDownload)
        }
    }

    private fun markAsUnseen(
        mailbox: Mailbox,
        threads: List<Thread>,
        message: Message? = null,
    ) = viewModelScope.launch(viewModelScope.handlerIO) {
        val messages = getMessagesToMarkAsUnseen(threads, message)
        val isSuccess = ApiRepository.markMessagesAsUnseen(mailbox.uuid, messages.getUids()).isSuccess()
        if (isSuccess) refreshFolders(mailbox, messages.getFoldersIds(), started = ::startedDownload, stopped = ::stoppedDownload)
    }

    private fun getMessagesToMarkAsUnseen(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap(MessageController::getLastMessageAndItsDuplicatesToExecuteAction)
        else -> MessageController.getMessageAndDuplicates(threads.first(), message)
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
    ) = viewModelScope.launch(viewModelScope.handlerIO) {
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

        if (isSuccess) refreshFolders(mailbox, messages.getFoldersIds(), started = ::startedDownload, stopped = ::stoppedDownload)
    }

    private fun getMessagesToFavorite(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap(MessageController::getLastMessageAndItsDuplicatesToExecuteAction)
        else -> MessageController.getMessageAndDuplicates(threads.first(), message)
    }

    private fun getMessagesToUnfavorite(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap(MessageController::getFavoriteMessages)
        else -> MessageController.getMessageAndDuplicates(threads.first(), message)
    }
    //endregion

    //region Spam
    fun toggleMessageSpamStatus(threadUid: String, message: Message, displaySnackbar: Boolean = true) {
        toggleThreadsOrMessageSpamStatus(threadsUids = listOf(threadUid), message = message, displaySnackbar = displaySnackbar)
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
    ) = viewModelScope.launch(viewModelScope.handlerIO) {
        val mailbox = currentMailbox.value!!
        val threads = getActionThreads(threadsUids).ifEmpty { return@launch }

        val destinationFolderRole = if (isSpam(message)) FolderRole.INBOX else FolderRole.SPAM
        val destinationFolder = FolderController.getFolder(destinationFolderRole)!!

        val messages = getMessagesToSpamOrHam(threads, message)

        val apiResponse = ApiRepository.moveMessages(mailbox.uuid, messages.getUids(), destinationFolder.id)

        if (apiResponse.isSuccess()) {
            refreshFolders(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(exception = destinationFolder.id),
                destinationFolderId = destinationFolder.id,
                started = ::startedDownload,
                stopped = ::stoppedDownload,
            )
        }

        if (displaySnackbar) {
            showMoveSnackbar(threads, message, messages, apiResponse, destinationFolder)
        }
    }

    private fun getMessagesToSpamOrHam(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap(MessageController::getUnscheduledMessages)
        else -> MessageController.getMessageAndDuplicates(threads.first(), message)
    }
    //endregion

    //region Phishing
    fun reportPhishing(threadUid: String, message: Message) = viewModelScope.launch(ioDispatcher) {
        val mailboxUuid = currentMailbox.value?.uuid!!

        val apiResponse = ApiRepository.reportPhishing(mailboxUuid, message.folderId, message.shortUid)

        val snackbarTitle = if (apiResponse.isSuccess()) {
            if (!isCurrentFolderRole(FolderRole.SPAM)) toggleMessageSpamStatus(threadUid, message, displaySnackbar = false)
            R.string.snackbarReportPhishingConfirmation
        } else {
            RCore.string.anErrorHasOccurred
        }

        snackBarManager.postValue(context.getString(snackbarTitle))
    }
    //endregion

    //region BlockUser
    fun blockUser(message: Message) = viewModelScope.launch(ioDispatcher) {
        val mailboxUuid = currentMailbox.value?.uuid!!

        val apiResponse = ApiRepository.blockUser(mailboxUuid, message.folderId, message.shortUid)

        val snackbarTitle = if (apiResponse.isSuccess()) {
            R.string.snackbarBlockUserConfirmation
        } else {
            RCore.string.anErrorHasOccurred
        }

        snackBarManager.postValue(context.getString(snackbarTitle))
    }
    //endregion

    //region Undo action
    fun undoAction(undoData: UndoData) = viewModelScope.launch(viewModelScope.handlerIO) {
        val mailbox = currentMailbox.value!!
        val (resource, foldersIds, destinationFolderId) = undoData

        val snackbarTitle = if (ApiRepository.undoAction(resource).data == true) {
            refreshFolders(mailbox, foldersIds, destinationFolderId, ::startedDownload, ::stoppedDownload)
            R.string.snackbarMoveCancelled
        } else {
            RCore.string.anErrorHasOccurred
        }

        snackBarManager.postValue(context.getString(snackbarTitle))
    }
    //endregion

    //region New Folder
    private fun createNewFolderSync(name: String): String? {
        val mailbox = currentMailbox.value ?: return null
        val apiResponse = ApiRepository.createFolder(mailbox.uuid, name)

        return if (apiResponse.isSuccess()) {
            updateFolders(mailbox)
            apiResponse.data?.id
        } else {
            snackBarManager.postValue(context.getString(apiResponse.translateError()), null)
            null
        }
    }

    fun createNewFolder(name: String) = viewModelScope.launch(ioDispatcher) { createNewFolderSync(name) }

    fun moveToNewFolder(name: String, threadsUids: Array<String>, messageUid: String?) = viewModelScope.launch(ioDispatcher) {
        val newFolderId = createNewFolderSync(name) ?: return@launch
        moveThreadsOrMessageTo(newFolderId, threadsUids, messageUid)
        isNewFolderCreated.postValue(true)
    }
    //endregion

    private fun startedDownload() {
        isDownloadingChanges.postValue(true to null)
    }

    private fun stoppedDownload() {

        val shouldDisplayLoadMore = currentFolderId?.let(FolderController::getFolder)
            ?.let { it.cursor != null && !it.isHistoryComplete }
            ?: false

        isDownloadingChanges.postValue(false to shouldDisplayLoadMore)
    }

    private fun getActionThreads(threadsUids: List<String>): List<Thread> = threadsUids.mapNotNull(ThreadController::getThread)

    fun addContact(recipient: Recipient) = viewModelScope.launch(ioDispatcher) {

        val isSuccess = ApiRepository.addContact(AddressBookController.getDefaultAddressBook().id, recipient).isSuccess()

        val snackbarTitle = if (isSuccess) {
            updateUserInfo()
            R.string.snackbarContactSaved
        } else {
            RCore.string.anErrorHasOccurred
        }

        snackBarManager.postValue(context.getString(snackbarTitle))
    }

    fun getMessage(messageUid: String) = liveData(ioDispatcher) {
        emit(MessageController.getMessage(messageUid)!!)
    }

    private fun isSpam(message: Message?) = message?.isSpam ?: isCurrentFolderRole(FolderRole.SPAM)

    fun navigateToSelectedDraft(message: Message) = liveData(ioDispatcher) {
        val localUuid = DraftController.getDraftByMessageUid(message.uid)?.localUuid
        emit(ThreadListViewModel.SelectedDraft(localUuid, message.draftResource, message.uid))
    }

    fun selectOrUnselectAll() {
        if (isEverythingSelected) {
            context.trackMultiSelectionEvent("none")
            selectedThreads.clear()
        } else {
            context.trackMultiSelectionEvent("all")
            currentThreadsLive.value?.list?.forEach { thread ->
                selectedThreads.add(SelectedThread(thread))
            }
        }

        publishSelectedItems()
    }

    fun publishSelectedItems() {
        selectedThreadsLiveData.value = selectedThreads
    }

    private companion object {
        val TAG: String = MainViewModel::class.java.simpleName
        val DEFAULT_SELECTED_FOLDER = FolderRole.INBOX
    }
}
