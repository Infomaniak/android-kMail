/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import android.util.Log
import androidx.lifecycle.*
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.SignatureController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.cache.mailboxInfo.QuotasController
import com.infomaniak.mail.data.cache.userInfo.AddressBookController
import com.infomaniak.mail.data.cache.userInfo.MergedContactController
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ContactUtils.getPhoneContacts
import com.infomaniak.mail.utils.ContactUtils.mergeApiContactsIntoPhoneContacts
import com.infomaniak.mail.utils.SharedViewModelUtils.markAsSeen
import com.infomaniak.mail.utils.Utils.formatFoldersListWithAllChildren
import com.infomaniak.mail.workers.DraftsActionsWorker
import io.realm.kotlin.ext.copyFromRealm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.infomaniak.lib.core.R as RCore

class MainViewModel(application: Application) : AndroidViewModel(application) {

    //region Current Mailbox
    private val currentMailboxObjectId = MutableLiveData<String?>(null)

    val currentMailbox = Transformations.switchMap(currentMailboxObjectId) { mailboxObjectId ->
        liveData(Dispatchers.IO) { mailboxObjectId?.let(MailboxController::getMailbox)?.let { emit(it) } }
    }

    val currentFoldersLive = Transformations.switchMap(currentMailboxObjectId) { mailboxObjectId ->
        liveData(Dispatchers.IO) {
            mailboxObjectId?.let { emitSource(FolderController.getFoldersAsync().map { getMenuFolders(it.list) }.asLiveData()) }
        }
    }

    val currentQuotasLive = Transformations.switchMap(currentMailboxObjectId) { mailboxObjectId ->
        liveData(Dispatchers.IO) { mailboxObjectId?.let { emitSource(QuotasController.getQuotasAsync(it).asLiveData()) } }
    }
    //endregion

    //region Current Folder
    private val currentFolderId = MutableLiveData<String?>(null)

    val currentFolder = Transformations.switchMap(currentFolderId) { folderId ->
        liveData(Dispatchers.IO) { folderId?.let(FolderController::getFolder)?.let { emit(it) } }
    }

    val currentFolderLive = Transformations.switchMap(currentFolderId) { folderId ->
        liveData(Dispatchers.IO) { folderId?.let { emitSource(FolderController.getFolderAsync(it).asLiveData()) } }
    }

    val currentFilter = SingleLiveEvent(ThreadFilter.ALL)

    val currentThreadsLive = Transformations.switchMap(observeFolderAndFilter()) { (folder, filter) ->
        liveData(Dispatchers.IO) {
            if (folder != null) emitSource(ThreadController.getThreadsAsync(folder.id, filter).asLiveData())
        }
    }
    //endregion

    private val localSettings by lazy { LocalSettings.getInstance(application) }
    val isInternetAvailable = SingleLiveEvent<Boolean>()
    var isDownloadingChanges = MutableLiveData(false)
    var mergedContacts = MutableLiveData<Map<Recipient, MergedContact>?>()
    val snackbarFeedback = SingleLiveEvent<Pair<String, String?>>()

    private var forceRefreshJob: Job? = null

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
        MailboxController.updateMailboxes(getApplication(), mailboxes)
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
        DraftsActionsWorker.scheduleWork(getApplication())
    }

    fun forceRefreshMailboxes() = viewModelScope.launch(Dispatchers.IO) {
        Log.d(TAG, "Force refresh mailboxes")
        val mailboxes = ApiRepository.getMailboxes().data ?: return@launch
        MailboxController.updateMailboxes(getApplication(), mailboxes)
        updateCurrentMailboxQuotas()
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
            val phoneMergedContacts = getPhoneContacts(getApplication())
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

        MessageController.fetchCurrentFolderMessages(mailbox, folderId, localSettings.threadMode)

        isDownloadingChanges.postValue(false)
    }

    fun archiveThread(threadUid: String) = viewModelScope.launch(Dispatchers.IO) {

        val mailbox = currentMailbox.value ?: return@launch
        val realm = RealmDatabase.mailboxContent()
        val thread = ThreadController.getThread(threadUid, realm) ?: return@launch

        val uids = ThreadController.getSameFolderThreadMessagesUids(thread)
        val archiveId = FolderController.getFolder(FolderRole.ARCHIVE, realm)!!.id

        val apiResponse = ApiRepository.moveMessages(mailbox.uuid, uids, archiveId)

        val context = getApplication<Application>()
        val snackbarTitle = if (apiResponse.isSuccess()) {
            val destination = context.getString(FolderRole.ARCHIVE.folderNameRes)
            context.resources.getQuantityString(R.plurals.snackbarThreadMoved, 1, destination)
        } else {
            context.getString(RCore.string.anErrorHasOccurred)
        }

        snackbarFeedback.postValue(snackbarTitle to apiResponse.data?.undoResource)

        refreshThreads()
    }

    fun deleteThreadOrMessage(threadUid: String, messageUid: String? = null) = viewModelScope.launch(Dispatchers.IO) {
        val realm = RealmDatabase.mailboxContent()
        val mailbox = currentMailbox.value ?: return@launch
        val folderId = currentFolder.value?.id ?: return@launch
        val currentFolderRole = FolderController.getFolder(folderId, realm)?.role
        val thread = ThreadController.getThread(threadUid, realm) ?: return@launch
        val message = messageUid?.let { MessageController.getMessage(it, realm) }

        val messages = if (message == null) {
            thread.messages + thread.duplicates
        } else {
            listOf(message) + thread.getMessageDuplicates(message.messageId)
        }

        var undoResource: String? = null

        val shouldPermanentlyDelete = currentFolderRole == FolderRole.DRAFT
                || currentFolderRole == FolderRole.SPAM
                || currentFolderRole == FolderRole.TRASH

        val isSuccess = if (shouldPermanentlyDelete) {
            val uids = messages.map { it.uid }
            ApiRepository.deleteMessages(mailbox.uuid, uids).isSuccess()
        } else {
            val trashId = FolderController.getFolder(FolderRole.TRASH, realm)!!.id
            val filteredUids = messages.filter { !it.scheduled }.map { it.uid }

            val apiResponse = ApiRepository.moveMessages(mailbox.uuid, filteredUids, trashId)
            undoResource = apiResponse.data?.undoResource
            apiResponse.isSuccess()
        }

        val context = getApplication<Application>()
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

        refreshThreads()
    }

    //region Seen status
    fun toggleSeenStatus(threadUid: String) = viewModelScope.launch(Dispatchers.IO) {
        val mailboxUuid = currentMailbox.value?.uuid ?: return@launch
        val thread = ThreadController.getThread(threadUid) ?: return@launch

        if (thread.unseenMessagesCount == 0) markAsUnseen(thread, mailboxUuid) else markAsSeen(thread, mailboxUuid)
        refreshThreads()
    }

    private fun markAsUnseen(thread: Thread, mailboxUuid: String) {
        val uids = ThreadController.getThreadLastMessageUids(thread)

        ApiRepository.markMessagesAsUnseen(mailboxUuid, uids)
    }
    //endregion

    //region Favorite status
    fun toggleThreadFavoriteStatus(threadUid: String) = viewModelScope.launch(Dispatchers.IO) {
        val mailbox = currentMailbox.value ?: return@launch
        val thread = ThreadController.getThread(threadUid) ?: return@launch

        if (thread.isFavorite) {
            val uids = ThreadController.getThreadFavoritesMessagesUids(thread)
            ApiRepository.removeFromFavorites(mailbox.uuid, uids)
        } else {
            val uids = ThreadController.getThreadLastMessageUids(thread)
            ApiRepository.addToFavorites(mailbox.uuid, uids)
        }

        refreshThreads()
    }

    fun toggleMessageFavoriteStatus(messageUid: String, threadUid: String) = viewModelScope.launch(Dispatchers.IO) {
        val realm = RealmDatabase.mailboxContent()
        val mailbox = currentMailbox.value ?: return@launch
        val thread = ThreadController.getThread(threadUid, realm) ?: return@launch
        val message = MessageController.getMessage(messageUid, realm) ?: return@launch

        val uids = listOf(message.uid) + thread.getMessageDuplicatesUids(message.messageId)

        if (message.isFavorite) {
            ApiRepository.removeFromFavorites(mailbox.uuid, uids)
        } else {
            ApiRepository.addToFavorites(mailbox.uuid, uids)
        }

        refreshThreads()
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
