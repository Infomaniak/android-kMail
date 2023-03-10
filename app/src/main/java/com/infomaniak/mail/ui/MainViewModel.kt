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
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.*
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.cache.mailboxInfo.QuotasController
import com.infomaniak.mail.data.cache.userInfo.AddressBookController
import com.infomaniak.mail.data.cache.userInfo.MergedContactController
import com.infomaniak.mail.data.models.*
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.SelectedThread
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.ui.main.SnackBarManager
import com.infomaniak.mail.ui.main.SnackBarManager.*
import com.infomaniak.mail.ui.main.folder.ThreadListViewModel
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.ApiErrorException.ErrorCodes
import com.infomaniak.mail.utils.ContactUtils.getPhoneContacts
import com.infomaniak.mail.utils.ContactUtils.mergeApiContactsIntoPhoneContacts
import com.infomaniak.mail.utils.NotificationUtils.cancelNotification
import com.infomaniak.mail.utils.SharedViewModelUtils.markAsSeen
import com.infomaniak.mail.utils.SharedViewModelUtils.refreshFolders
import com.infomaniak.mail.workers.DraftsActionsWorker
import io.realm.kotlin.ext.copyFromRealm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.infomaniak.lib.core.R as RCore

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private inline val context: Context get() = getApplication<Application>()

    val isInternetAvailable = SingleLiveEvent<Boolean>()
    val isDownloadingChanges = MutableLiveData(false)
    val isNewFolderCreated = SingleLiveEvent<Boolean>()
    val mergedContacts = MutableLiveData<Map<Recipient, MergedContact>?>()

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

    val isEverythingSelected get() = selectedThreads.count() == currentThreadsLiveToObserve.value?.list?.count()
    //endregion

    val snackBarManager by lazy { SnackBarManager() }

    val toggleLightThemeForMessage = SingleLiveEvent<Message>()

    private val coroutineContext = viewModelScope.coroutineContext + Dispatchers.IO
    private var refreshThreadsJob: Job? = null
    private var refreshMailboxesAndFoldersJob: Job? = null

    private var isLoadingMailbox = false

    val mailboxesLive = MailboxController.getMailboxesAsync(AccountUtils.currentUserId).asLiveData(coroutineContext)

    //region Current Mailbox
    private val _currentMailboxObjectId = MutableStateFlow<String?>(null)

    val currentMailbox = _currentMailboxObjectId.mapLatest {
        it?.let(MailboxController::getMailbox)
    }.asLiveData(Dispatchers.IO)

    val currentFoldersLive = _currentMailboxObjectId.flatMapLatest {
        it?.let { FolderController.getRootsFoldersAsync().map { results -> results.list.getMenuFolders() } } ?: emptyFlow()
    }.asLiveData(coroutineContext)

    val currentQuotasLive = _currentMailboxObjectId.flatMapLatest {
        it?.let(QuotasController::getQuotasAsync) ?: emptyFlow()
    }.asLiveData(coroutineContext)
    //endregion

    //region Current Folder
    private val _currentFolderId = MutableStateFlow<String?>(null)
    val currentFolderId get() = _currentFolderId.value

    val currentFolder = _currentFolderId.mapLatest {
        it?.let(FolderController::getFolder)
    }.asLiveData(coroutineContext)

    val currentFolderLive = _currentFolderId.flatMapLatest {
        it?.let(FolderController::getFolderAsync) ?: emptyFlow()
    }.asLiveData(coroutineContext)

    val currentFilter = SingleLiveEvent(ThreadFilter.ALL)

    val currentThreadsLiveToObserve = observeFolderAndFilter().flatMapLatest { (folder, filter) ->
        folder?.let { ThreadController.getThreadsAsync(it, filter) } ?: emptyFlow()
    }.asLiveData(coroutineContext)

    private fun observeFolderAndFilter() = MediatorLiveData<Pair<Folder?, ThreadFilter>>().apply {
        value = currentFolder.value to currentFilter.value!!
        addSource(currentFolder) { value = it to value!!.second }
        addSource(currentFilter) { value = value?.first to it }
    }.asFlow()

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

    fun updateUserInfo() = viewModelScope.launch(Dispatchers.IO) {
        Log.d(TAG, "Update user info")
        updateAddressBooks()
        updateContacts()
    }

    fun loadCurrentMailbox() {
        isLoadingMailbox = true
        viewModelScope.launch(Dispatchers.IO) {
            loadCurrentMailboxFromLocal()
            loadCurrentMailboxFromRemote()
            isLoadingMailbox = false
        }
    }

    private suspend fun loadCurrentMailboxFromLocal() {
        Log.d(TAG, "Load current mailbox from local")
        val userId = AccountUtils.currentUserId
        val mailboxId = AccountUtils.currentMailboxId
        if (userId != AppSettings.DEFAULT_ID && mailboxId != AppSettings.DEFAULT_ID) {

            val mailbox = MailboxController.getMailbox(userId, mailboxId) ?: return
            selectMailbox(mailbox)

            if (currentFolderId == null) {
                val folder = FolderController.getFolder(DEFAULT_SELECTED_FOLDER) ?: return
                selectFolder(folder.id)
            }

            // Delete search data in case they couldn't be deleted at the end of the previous Search.
            SearchUtils.deleteRealmSearchData()
        }
    }

    private fun loadCurrentMailboxFromRemote() {
        Log.d(TAG, "Load current mailbox from remote")
        with(ApiRepository.getMailboxes()) {
            if (isSuccess()) {
                val isCurrentMailboxDeleted = MailboxController.updateMailboxes(context, data!!)
                if (isCurrentMailboxDeleted) return
                MailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)?.let(::openMailbox)
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
            refreshThreads(mailbox, folder.id)
        }

        DraftsActionsWorker.scheduleWork(context)
    }

    fun forceRefreshMailboxesAndFolders() {

        if (isLoadingMailbox) return

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

    fun openFolder(folderId: String) = viewModelScope.launch(Dispatchers.IO) {
        if (folderId == currentFolderId) return@launch

        selectFolder(folderId)
        refreshThreads(folderId = folderId)
    }

    fun flushFolder() = viewModelScope.launch(Dispatchers.IO) {

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

    fun forceRefreshThreads() {
        refreshThreadsJob?.cancel()
        refreshThreadsJob = viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Force refresh threads")
            refreshThreads()
        }
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

    fun observeMergedContactsLive() = viewModelScope.launch(Dispatchers.IO) {
        MergedContactController.getMergedContactsAsync().collect { contacts ->
            // TODO: We had this issue: https://sentry.infomaniak.com/share/issue/111cc162315d4873844c9b79be5b2491/
            // TODO: We fixed it by doing an `associate` with `copyFromRealm`, instead of an `associateBy`.
            // TODO: But we don't really know why it crashed in the first place. Maybe there's a memory leak somewhere?
            // TODO: Previous version: `contacts.list.associateBy { Recipient().initLocalValues(it.email, it.name) }`
            mergedContacts.postValue(
                contacts.list.associate { Recipient().initLocalValues(it.email, it.name) to it.copyFromRealm(UInt.MIN_VALUE) }
            )
        }
    }

    private fun updateSignatures(mailbox: Mailbox) = viewModelScope.launch(Dispatchers.IO) {
        ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailboxName).data?.signatures?.let(SignatureController::update)
    }

    private fun updateFolders(mailbox: Mailbox) {
        ApiRepository.getFolders(mailbox.uuid).data?.let { folders ->
            FolderController.update(folders)
        }
    }

    private fun refreshThreads(
        mailbox: Mailbox? = currentMailbox.value,
        folderId: String? = currentFolderId,
    ) = viewModelScope.launch(viewModelScope.handlerIO) {

        if (mailbox == null || folderId == null) return@launch

        FolderController.getFolder(folderId)?.let { folder ->
            isDownloadingChanges.postValue(true)
            MessageController.fetchCurrentFolderMessages(mailbox, folder)
            isDownloadingChanges.postValue(false)
        }
    }

    //region Delete
    fun deleteThreadOrMessage(threadUid: String, message: Message? = null) = viewModelScope.launch(Dispatchers.IO) {
        val mailbox = currentMailbox.value!!
        val thread = ThreadController.getThread(threadUid)!!
        var trashId: String? = null
        var undoResource: String? = null

        val shouldPermanentlyDelete = isCurrentFolderRole(FolderRole.DRAFT)
                || isCurrentFolderRole(FolderRole.SPAM)
                || isCurrentFolderRole(FolderRole.TRASH)

        val messages = when {
            message != null -> MessageController.getMessageAndDuplicates(thread, message)
            shouldPermanentlyDelete -> thread.messages + thread.duplicates
            else -> MessageController.getUnscheduledMessages(thread)
        }
        val uids = messages.getUids()

        val isSuccess = if (shouldPermanentlyDelete) {
            ApiRepository.deleteMessages(mailbox.uuid, uids).isSuccess()
        } else {
            trashId = FolderController.getFolder(FolderRole.TRASH)!!.id
            val apiResponse = ApiRepository.moveMessages(mailbox.uuid, uids, trashId)
            undoResource = apiResponse.data?.undoResource
            apiResponse.isSuccess()
        }

        if (isSuccess) refreshFolders(mailbox, messages.getFoldersIds(exception = trashId), trashId)

        val undoDestinationId = message?.folderId ?: thread.folderId
        val undoFoldersIds = (messages.getFoldersIds(exception = undoDestinationId) + trashId).filterNotNull()
        showDeleteSnackbar(isSuccess, shouldPermanentlyDelete, message, undoResource, undoFoldersIds, undoDestinationId)
    }

    private fun showDeleteSnackbar(
        isSuccess: Boolean,
        shouldPermanentlyDelete: Boolean,
        message: Message?,
        undoResource: String?,
        undoFoldersIds: List<String>,
        undoDestinationId: String?,
    ) {

        val snackbarTitle = if (isSuccess) {
            val destination = context.getString(FolderRole.TRASH.folderNameRes)
            when {
                shouldPermanentlyDelete && message == null -> {
                    context.resources.getQuantityString(R.plurals.snackbarThreadDeletedPermanently, 1)
                }
                shouldPermanentlyDelete && message != null -> {
                    context.resources.getString(R.string.snackbarMessageDeletedPermanently)
                }
                !shouldPermanentlyDelete && message == null -> {
                    context.resources.getQuantityString(R.plurals.snackbarThreadMoved, 1, destination)
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
    //endregion

    //region Move
    fun moveTo(
        destinationFolderId: String,
        threadUid: String,
        messageUid: String? = null,
    ) = viewModelScope.launch(Dispatchers.IO) {
        val mailbox = currentMailbox.value!!
        val destinationFolder = FolderController.getFolder(destinationFolderId)!!
        val thread = ThreadController.getThread(threadUid)!!
        val message = messageUid?.let { MessageController.getMessage(it)!! }
        val messages = getMessagesToMove(thread, message)

        val apiResponse = ApiRepository.moveMessages(mailbox.uuid, messages.getUids(), destinationFolderId)

        if (apiResponse.isSuccess()) {
            refreshFolders(mailbox, messages.getFoldersIds(exception = destinationFolderId), destinationFolderId)
        }

        showMoveSnackbar(thread.folderId, message, messages, apiResponse, destinationFolder)
    }

    private fun showMoveSnackbar(
        threadFolderId: String,
        message: Message?,
        messages: List<Message>,
        apiResponse: ApiResponse<MoveResult>,
        destinationFolder: Folder,
    ) {
        val undoDestinationId = message?.folderId ?: threadFolderId
        val undoFoldersIds = messages.getFoldersIds(exception = undoDestinationId) + destinationFolder.id

        val destination = destinationFolder.getLocalizedName(context)

        val snackbarTitle = when {
            !apiResponse.isSuccess() -> context.getString(RCore.string.anErrorHasOccurred)
            message == null -> context.resources.getQuantityString(R.plurals.snackbarThreadMoved, 1, destination)
            else -> context.getString(R.string.snackbarMessageMoved, destination)
        }

        val undoData = apiResponse.data?.undoResource?.let { UndoData(it, undoFoldersIds, undoDestinationId) }
        snackBarManager.postValue(snackbarTitle, undoData)
    }

    private fun getMessagesToMove(thread: Thread, message: Message?) = when (message) {
        null -> MessageController.getMovableMessages(thread)
        else -> MessageController.getMessageAndDuplicates(thread, message)
    }
    //endregion

    //region Archive
    fun archiveThreadOrMessage(threadUid: String, message: Message? = null) = viewModelScope.launch(Dispatchers.IO) {
        val mailbox = currentMailbox.value!!
        val thread = ThreadController.getThread(threadUid)!!

        val isArchived = message?.let { it.folder.role == FolderRole.ARCHIVE } ?: isCurrentFolderRole(FolderRole.ARCHIVE)

        val destinationFolderRole = if (isArchived) FolderRole.INBOX else FolderRole.ARCHIVE
        val destinationFolder = FolderController.getFolder(destinationFolderRole)!!

        val messages = getMessagesToMove(thread, message)

        val apiResponse = ApiRepository.moveMessages(mailbox.uuid, messages.getUids(), destinationFolder.id)

        if (apiResponse.isSuccess()) {
            val messagesFoldersIds = messages.getFoldersIds(exception = destinationFolder.id)
            refreshFolders(mailbox, messagesFoldersIds, destinationFolder.id)
        }

        showMoveSnackbar(thread.folderId, message, messages, apiResponse, destinationFolder)
    }
    //endregion

    //region Seen
    fun toggleSeenStatus(threadUid: String, message: Message? = null) = viewModelScope.launch(Dispatchers.IO) {
        val mailbox = currentMailbox.value!!
        val thread = ThreadController.getThread(threadUid)!!

        val isSeen = message?.isSeen ?: (thread.unseenMessagesCount == 0)

        if (isSeen) {
            markAsUnseen(mailbox, thread, message)
        } else {
            markAsSeen(mailbox, thread, message)
        }
    }

    private suspend fun markAsUnseen(mailbox: Mailbox, thread: Thread, message: Message? = null) {

        val messages = when (message) {
            null -> MessageController.getLastMessageToExecuteAction(thread)
            else -> MessageController.getMessageAndDuplicates(thread, message)
        }

        val isSuccess = ApiRepository.markMessagesAsUnseen(mailbox.uuid, messages.getUids()).isSuccess()

        if (isSuccess) refreshFolders(mailbox, messages.getFoldersIds())
    }
    //endregion

    //region Favorite
    fun toggleFavoriteStatus(threadUid: String, message: Message? = null) = viewModelScope.launch(Dispatchers.IO) {
        val mailbox = currentMailbox.value!!
        val thread = ThreadController.getThread(threadUid)!!

        val messages = when {
            message != null -> MessageController.getMessageAndDuplicates(thread, message)
            thread.isFavorite -> MessageController.getFavoriteMessages(thread)
            else -> MessageController.getLastMessageToExecuteAction(thread)
        }
        val uids = messages.getUids()

        val isSuccess = if (message?.isFavorite ?: thread.isFavorite) {
            ApiRepository.removeFromFavorites(mailbox.uuid, uids).isSuccess()
        } else {
            ApiRepository.addToFavorites(mailbox.uuid, uids).isSuccess()
        }

        if (isSuccess) refreshFolders(mailbox, messages.getFoldersIds())
    }
    //endregion

    //region Spam
    fun toggleSpamOrHam(
        threadUid: String,
        message: Message? = null,
        displaySnackbar: Boolean = true,
    ) = viewModelScope.launch(Dispatchers.IO) {
        val mailbox = currentMailbox.value!!
        val thread = ThreadController.getThread(threadUid)!!

        val destinationFolderRole = if (isSpam(message)) FolderRole.INBOX else FolderRole.SPAM
        val destinationFolder = FolderController.getFolder(destinationFolderRole)!!

        val messages = when (message) {
            null -> MessageController.getUnscheduledMessages(thread)
            else -> MessageController.getMessageAndDuplicates(thread, message)
        }

        val apiResponse = ApiRepository.moveMessages(mailbox.uuid, messages.getUids(), destinationFolder.id)

        if (apiResponse.isSuccess()) {
            refreshFolders(mailbox, messages.getFoldersIds(exception = destinationFolder.id), destinationFolder.id)
        }

        if (displaySnackbar) {
            showMoveSnackbar(thread.folderId, message, messages, apiResponse, destinationFolder)
        }
    }
    //endregion

    //region Phishing
    fun reportPhishing(threadUid: String, message: Message) = viewModelScope.launch(Dispatchers.IO) {
        val mailboxUuid = currentMailbox.value?.uuid!!

        val apiResponse = ApiRepository.reportPhishing(mailboxUuid, message.folderId, message.shortUid)

        val snackbarTitle = if (apiResponse.isSuccess()) {
            if (!isCurrentFolderRole(FolderRole.SPAM)) toggleSpamOrHam(threadUid, message, displaySnackbar = false)
            R.string.snackbarReportPhishingConfirmation
        } else {
            RCore.string.anErrorHasOccurred
        }

        snackBarManager.postValue(context.getString(snackbarTitle))
    }
    //endregion

    //region BlockUser
    fun blockUser(message: Message) = viewModelScope.launch(Dispatchers.IO) {
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
    fun undoAction(undoData: UndoData) {
        viewModelScope.launch(Dispatchers.IO) {
            val mailbox = currentMailbox.value!!
            val (resource, foldersIds, destinationFolderId) = undoData

            val snackbarTitle = if (ApiRepository.undoAction(resource).data == true) {
                refreshFolders(mailbox, foldersIds, destinationFolderId)
                R.string.snackbarMoveCancelled
            } else {
                RCore.string.anErrorHasOccurred
            }

            snackBarManager.postValue(context.getString(snackbarTitle))
        }
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
            val snackbarTitle = if (apiResponse.error?.code == ErrorCodes.FOLDER_ALREADY_EXISTS) {
                R.string.errorNewFolderAlreadyExists
            } else {
                RCore.string.anErrorHasOccurred
            }
            snackBarManager.postValue(context.getString(snackbarTitle), null)
            null
        }
    }

    fun createNewFolder(name: String) = viewModelScope.launch(Dispatchers.IO) { createNewFolderSync(name) }

    fun moveToNewFolder(name: String, threadUid: String, messageUid: String?) = viewModelScope.launch(Dispatchers.IO) {
        val newFolderId = createNewFolderSync(name) ?: return@launch
        moveTo(newFolderId, threadUid, messageUid)
        isNewFolderCreated.postValue(true)
    }
    //endregion

    fun addContact(recipient: Recipient) = viewModelScope.launch(Dispatchers.IO) {

        val isSuccess = ApiRepository.addContact(AddressBookController.getDefaultAddressBook().id, recipient).isSuccess()

        val snackbarTitle = if (isSuccess) {
            updateUserInfo()
            R.string.snackbarContactSaved
        } else {
            RCore.string.anErrorHasOccurred
        }

        snackBarManager.postValue(context.getString(snackbarTitle))
    }

    fun getMessage(messageUid: String) = liveData(coroutineContext) {
        emit(MessageController.getMessage(messageUid)!!)
    }

    private fun isSpam(message: Message?) = message?.isSpam ?: isCurrentFolderRole(FolderRole.SPAM)

    fun navigateToSelectedDraft(message: Message) = liveData(coroutineContext) {
        val localUuid = DraftController.getDraftByMessageUid(message.uid)?.localUuid
        emit(ThreadListViewModel.SelectedDraft(localUuid, message.draftResource, message.uid))
    }

    fun selectOrUnselectAll() {
        if (isEverythingSelected) {
            selectedThreads.clear()
        } else {
            currentThreadsLiveToObserve.value?.list?.forEach { thread ->
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
