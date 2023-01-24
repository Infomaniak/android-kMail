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
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.SignatureController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.cache.mailboxInfo.QuotasController
import com.infomaniak.mail.data.cache.userInfo.AddressBookController
import com.infomaniak.mail.data.cache.userInfo.MergedContactController
import com.infomaniak.mail.data.models.*
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ContactUtils.getPhoneContacts
import com.infomaniak.mail.utils.ContactUtils.mergeApiContactsIntoPhoneContacts
import com.infomaniak.mail.utils.NotificationUtils.cancelNotification
import com.infomaniak.mail.utils.SharedViewModelUtils.markAsSeen
import com.infomaniak.mail.utils.SharedViewModelUtils.refreshFolders
import com.infomaniak.mail.utils.Utils.formatFoldersListWithAllChildren
import com.infomaniak.mail.utils.getFoldersIds
import com.infomaniak.mail.workers.DraftsActionsWorker
import io.realm.kotlin.ext.copyFromRealm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.infomaniak.lib.core.R as RCore

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private inline val context: Context get() = getApplication<Application>()
    val isInternetAvailable = SingleLiveEvent<Boolean>()
    var isDownloadingChanges = MutableLiveData(false)
    var mergedContacts = MutableLiveData<Map<Recipient, MergedContact>?>()
    val snackbarFeedback = SingleLiveEvent<Pair<String, String?>>()

    private var forceRefreshJob: Job? = null

    //region Current Mailbox
    private val currentMailboxObjectId = MutableLiveData<String?>(null)

    val currentMailbox = currentMailboxObjectId.switchMap { mailboxObjectId ->
        liveData(Dispatchers.IO) { mailboxObjectId?.let(MailboxController::getMailbox)?.let { emit(it) } }
    }

    val currentFoldersLive
        get() = currentMailboxObjectId.switchMap { mailboxObjectId ->
            liveData(Dispatchers.IO) {
                mailboxObjectId?.let {
                    emitSource(FolderController.getFoldersAsync().map { getMenuFolders(it.list) }.asLiveData())
                }
            }
        }

    val currentQuotasLive
        get() = currentMailboxObjectId.switchMap { mailboxObjectId ->
            liveData(Dispatchers.IO) { mailboxObjectId?.let { emitSource(QuotasController.getQuotasAsync(it).asLiveData()) } }
        }
    //endregion

    //region Current Folder
    private val currentFolderId = MutableLiveData<String?>(null)

    val currentFolder = currentFolderId.switchMap { folderId ->
        liveData(Dispatchers.IO) { folderId?.let(FolderController::getFolder)?.let { emit(it) } }
    }

    val currentFolderLive
        get() = currentFolderId.switchMap { folderId ->
            liveData(Dispatchers.IO) { folderId?.let { emitSource(FolderController.getFolderAsync(it).asLiveData()) } }
        }

    val currentFilter = SingleLiveEvent(ThreadFilter.ALL)

    val currentThreadsLive
        get() = observeFolderAndFilter().switchMap { (folder, filter) ->
            liveData(Dispatchers.IO) {
                if (folder != null) emitSource(ThreadController.getThreadsAsync(folder.id, filter).asLiveData())
            }
        }

    fun isCurrentFolderRole(role: FolderRole) = currentFolder.value?.role == role
    //endregion

    private fun observeFolderAndFilter() = MediatorLiveData<Pair<Folder?, ThreadFilter>>().apply {
        value = currentFolder.value to currentFilter.value!!
        addSource(currentFolder) { value = it to value!!.second }
        addSource(currentFilter) { value = value?.first to it }
    }

    fun observeMailboxesLive(userId: Int = AccountUtils.currentUserId): LiveData<List<Mailbox>> = liveData(Dispatchers.IO) {
        emitSource(MailboxController.getMailboxesAsync(userId).asLiveData())
    }

    private fun selectMailbox(mailbox: Mailbox) {
        if (mailbox.objectId != currentMailboxObjectId.value) {
            Log.d(TAG, "Select mailbox: ${mailbox.email}")
            if (mailbox.mailboxId != AccountUtils.currentMailboxId) AccountUtils.currentMailboxId = mailbox.mailboxId
            AccountUtils.currentMailboxEmail = mailbox.email
            currentMailboxObjectId.postValue(mailbox.objectId)
            currentFolderId.postValue(null)
        }
    }

    private fun selectFolder(folderId: String) {
        if (folderId != currentFolderId.value) {
            Log.d(TAG, "Select folder: $folderId")
            currentFolderId.postValue(folderId)
        }
    }

    fun updateUserInfo() = viewModelScope.launch(Dispatchers.IO) {
        Log.d(TAG, "Update user info")
        updateAddressBooks()
        updateContacts()
    }

    fun loadCurrentMailbox() = viewModelScope.launch(Dispatchers.IO) {
        loadCurrentMailboxFromLocal()
        loadCurrentMailboxFromRemote()
    }

    private fun loadCurrentMailboxFromLocal() {
        Log.d(TAG, "Load current mailbox from local")
        val userId = AccountUtils.currentUserId
        val mailboxId = AccountUtils.currentMailboxId
        if (userId != AppSettings.DEFAULT_ID && mailboxId != AppSettings.DEFAULT_ID) {
            val mailbox = MailboxController.getMailbox(userId, mailboxId) ?: return
            selectMailbox(mailbox)
            val folder = FolderController.getFolder(DEFAULT_SELECTED_FOLDER) ?: return
            selectFolder(folder.id)
        }
    }

    private suspend fun loadCurrentMailboxFromRemote() {
        Log.d(TAG, "Load current mailbox from remote")
        val mailboxes = ApiRepository.getMailboxes().data ?: return
        MailboxController.updateMailboxes(context, mailboxes)
        MailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)?.let(::openMailbox)
    }

    private fun openMailbox(mailbox: Mailbox) = viewModelScope.launch(Dispatchers.IO) {
        selectMailbox(mailbox)
        updateSignatures(mailbox)
        updateFolders(mailbox)
        FolderController.getFolder(DEFAULT_SELECTED_FOLDER)?.let { folder ->
            selectFolder(folder.id)
            refreshThreads(mailbox, folder.id)
        }
        DraftsActionsWorker.scheduleWork(context)
    }

    fun forceRefreshMailboxes() = viewModelScope.launch(Dispatchers.IO) {
        Log.d(TAG, "Force refresh mailboxes")
        val mailboxes = ApiRepository.getMailboxes().data ?: return@launch
        MailboxController.updateMailboxes(context, mailboxes)
        updateCurrentMailboxQuotas()
    }

    fun dismissCurrentMailboxNotifications() = viewModelScope.launch {
        currentMailbox.value?.let {
            getApplication<Application>().cancelNotification(it.notificationGroupId)
        }
    }

    private fun updateCurrentMailboxQuotas() {
        val mailbox = currentMailbox.value ?: return
        if (mailbox.isLimited) with(ApiRepository.getQuotas(mailbox.hostingId, mailbox.mailboxName)) {
            if (isSuccess()) MailboxController.updateMailbox(mailbox.objectId) {
                it.quotas = data
            }
        }
    }

    fun openFolder(folderId: String) = viewModelScope.launch(Dispatchers.IO) {
        if (folderId == currentFolder.value?.id) return@launch

        selectFolder(folderId)
        refreshThreads(folderId = folderId)
    }

    fun forceRefreshThreads() {
        forceRefreshJob?.cancel()
        forceRefreshJob = viewModelScope.launch(Dispatchers.IO) {
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

    fun observeRealmMergedContacts() = viewModelScope.launch(Dispatchers.IO) {
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
            FolderController.update(folders.formatFoldersListWithAllChildren())
        }
    }

    private fun refreshThreads(
        mailbox: Mailbox? = currentMailbox.value,
        folderId: String? = currentFolder.value?.id,
    ) = viewModelScope.launch(Dispatchers.IO) {

        if (mailbox == null || folderId == null) return@launch

        isDownloadingChanges.postValue(true)

        MessageController.fetchCurrentFolderMessages(mailbox, folderId)

        isDownloadingChanges.postValue(false)
    }

    //region Archive
    fun readAndArchive(thread: Thread) = viewModelScope.launch(Dispatchers.IO) {
        val mailbox = currentMailbox.value ?: return@launch
        val archiveId = FolderController.getFolder(FolderRole.ARCHIVE)!!.id
        val messagesFoldersIds = mutableListOf<String>()

        if (thread.unseenMessagesCount > 0) {
            markAsSeen(thread, mailbox, withRefresh = false)?.also(messagesFoldersIds::addAll)
        }
        archiveThreadOrMessageSync(thread, withRefresh = false)?.also(messagesFoldersIds::addAll)

        refreshFolders(mailbox, messagesFoldersIds, archiveId)
    }

    fun archiveThreadOrMessage(thread: Thread, message: Message? = null) = viewModelScope.launch(Dispatchers.IO) {
        archiveThreadOrMessageSync(thread, message)
    }

    private fun archiveThreadOrMessageSync(thread: Thread, message: Message? = null, withRefresh: Boolean = true): List<String>? {
        val mailbox = currentMailbox.value ?: return null
        val archiveId = FolderController.getFolder(FolderRole.ARCHIVE)!!.id

        val messages = message?.let(thread::getMessageAndDuplicates) ?: thread.getArchivableMessages(currentFolderId.value!!)

        val apiResponse = ApiRepository.moveMessages(mailbox.uuid, messages.map { it.uid }, archiveId)

        showArchiveSnackbar(message, apiResponse)

        if (apiResponse.isSuccess()) {
            val messagesFoldersIds = messages.getFoldersIds(exception = archiveId)
            if (withRefresh) {
                refreshFolders(mailbox, messagesFoldersIds, archiveId)
            } else {
                return messagesFoldersIds
            }
        }

        return null
    }

    private fun showArchiveSnackbar(message: Message?, apiResponse: ApiResponse<MoveResult>) {

        val destination = context.getString(FolderRole.ARCHIVE.folderNameRes)

        val snackbarTitle = when {
            !apiResponse.isSuccess() -> context.getString(RCore.string.anErrorHasOccurred)
            message == null -> context.resources.getQuantityString(R.plurals.snackbarThreadMoved, 1, destination)
            else -> context.getString(R.string.snackbarMessageMoved, destination)
        }

        snackbarFeedback.postValue(snackbarTitle to apiResponse.data?.undoResource)
    }
    //endregion

    //region Delete
    fun deleteThreadOrMessage(thread: Thread, message: Message? = null) = viewModelScope.launch(Dispatchers.IO) {
        val mailbox = currentMailbox.value ?: return@launch
        var trashId: String? = null
        var undoResource: String? = null

        val shouldPermanentlyDelete = isCurrentFolderRole(FolderRole.DRAFT)
                || isCurrentFolderRole(FolderRole.SPAM)
                || isCurrentFolderRole(FolderRole.TRASH)

        val messages = message?.let(thread::getMessageAndDuplicates)
            ?: if (shouldPermanentlyDelete) thread.getPermanentlyDeletableMessages() else thread.getDeletableMessages()
        val uids = messages.map { it.uid }

        val isSuccess = if (shouldPermanentlyDelete) {
            ApiRepository.deleteMessages(mailbox.uuid, uids).isSuccess()
        } else {
            trashId = FolderController.getFolder(FolderRole.TRASH)!!.id
            val apiResponse = ApiRepository.moveMessages(mailbox.uuid, uids, trashId)
            undoResource = apiResponse.data?.undoResource
            apiResponse.isSuccess()
        }

        showDeleteSnackbar(isSuccess, shouldPermanentlyDelete, message, undoResource)

        if (isSuccess) refreshFolders(mailbox, messages.getFoldersIds(exception = trashId), trashId)
    }

    private fun showDeleteSnackbar(
        isSuccess: Boolean,
        shouldPermanentlyDelete: Boolean,
        message: Message?,
        undoResource: String?,
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

        snackbarFeedback.postValue(snackbarTitle to undoResource)
    }
    //endregion

    //region Seen
    fun toggleSeenStatus(thread: Thread) = viewModelScope.launch(Dispatchers.IO) {
        val mailbox = currentMailbox.value ?: return@launch

        if (thread.unseenMessagesCount == 0) {
            markAsUnseen(thread, mailbox)
        } else {
            markAsSeen(thread, mailbox)
        }
    }

    private fun markAsUnseen(thread: Thread, mailbox: Mailbox) {
        val messages = thread.getLastMessageToExecuteAction()

        val isSuccess = ApiRepository.markMessagesAsUnseen(mailbox.uuid, messages.map { it.uid }).isSuccess()

        if (isSuccess) refreshFolders(mailbox, messages.getFoldersIds())
    }
    //endregion

    //region Favorite
    fun toggleThreadFavoriteStatus(thread: Thread) = viewModelScope.launch(Dispatchers.IO) {
        val mailbox = currentMailbox.value ?: return@launch

        val messages = if (thread.isFavorite) thread.getFavoriteMessages() else thread.getLastMessageToExecuteAction()
        val uids = messages.map { it.uid }

        val isSuccess = if (thread.isFavorite) {
            ApiRepository.removeFromFavorites(mailbox.uuid, uids).isSuccess()
        } else {
            ApiRepository.addToFavorites(mailbox.uuid, uids).isSuccess()
        }

        if (isSuccess) refreshFolders(mailbox, messages.getFoldersIds())
    }

    fun toggleMessageFavoriteStatus(thread: Thread, message: Message) = viewModelScope.launch(Dispatchers.IO) {
        val mailbox = currentMailbox.value ?: return@launch

        val messages = thread.getMessageAndDuplicates(message)
        val uids = messages.map { it.uid }

        val isSuccess = if (message.isFavorite) {
            ApiRepository.removeFromFavorites(mailbox.uuid, uids).isSuccess()
        } else {
            ApiRepository.addToFavorites(mailbox.uuid, uids).isSuccess()
        }

        if (isSuccess) refreshFolders(mailbox, messages.getFoldersIds())
    }
    //endregion

    //region Spam
    fun markAsSpamOrHam(thread: Thread, message: Message? = null): Boolean {
        var containsOwnMessage = false
        var onlyContainsOwnMessages = true

        (message?.from ?: thread.from).forEach { if (it.isMe()) containsOwnMessage = true else onlyContainsOwnMessages = false }

        if (onlyContainsOwnMessages) {
            snackbarFeedback.value = context.resources.getQuantityString(R.plurals.errorActionsSpamOwnMessage, 1) to null
        } else {
            toggleSpamOrHam(thread, message)
        }

        return containsOwnMessage
    }

    private fun toggleSpamOrHam(thread: Thread, message: Message? = null) = viewModelScope.launch(Dispatchers.IO) {
        val mailbox = currentMailbox.value ?: return@launch

        val isSpam = message?.isSpam ?: isCurrentFolderRole(FolderRole.SPAM)
        val destinationFolderRole = if (isSpam) FolderRole.INBOX else FolderRole.SPAM
        val destinationFolderId = FolderController.getFolder(destinationFolderRole)!!.id

        val messages = message?.let { if (message.from.first().isMe()) emptyList() else thread.getMessageAndDuplicates(message) }
            ?: thread.getSpamMessages()
        val uids = messages.map { it.uid }

        val apiResponse = if (uids.isEmpty()) null else ApiRepository.moveMessages(mailbox.uuid, uids, destinationFolderId)

        showSpamSnackbar(message, apiResponse, destinationFolderRole)

        if (apiResponse?.isSuccess() == true) {
            refreshFolders(mailbox, messages.getFoldersIds(exception = destinationFolderId), destinationFolderId)
        }
    }

    private fun showSpamSnackbar(message: Message?, apiResponse: ApiResponse<MoveResult>?, destinationRole: FolderRole) {

        val destination = context.getString(destinationRole.folderNameRes)

        val snackbarTitle = when {
            apiResponse == null -> context.resources.getQuantityString(R.plurals.errorActionsSpamOwnMessage, 1)
            !apiResponse.isSuccess() -> context.getString(RCore.string.anErrorHasOccurred)
            message == null -> context.resources.getQuantityString(R.plurals.snackbarThreadMoved, 1, destination)
            else -> context.getString(R.string.snackbarMessageMoved, destination)
        }

        snackbarFeedback.postValue(snackbarTitle to apiResponse?.data?.undoResource)
    }
    //endregion

    private fun getMenuFolders(folders: List<Folder>): Triple<Folder?, List<Folder>, List<Folder>> {
        return folders.toMutableList().let { list ->

            val inbox = list
                .find { it.role == FolderRole.INBOX }
                ?.also(list::remove)

            val defaultFolders = list
                .filter { it.role != null }
                .sortedBy { it.role?.order }
                .also(list::removeAll)

            val customFolders = list
                .filter { it.parentFolder.isEmpty() }
                .sortedByDescending { it.isFavorite }
                .formatFoldersListWithAllChildren()

            Triple(inbox, defaultFolders, customFolders)
        }
    }

    companion object {
        private val TAG: String = MainViewModel::class.java.simpleName
        private val DEFAULT_SELECTED_FOLDER = FolderRole.INBOX
    }
}
