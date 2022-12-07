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
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.FolderController.incrementFolderUnreadCount
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.deleteMessages
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
import com.infomaniak.mail.utils.Utils.formatFoldersListWithAllChildren
import com.infomaniak.mail.workers.DraftsActionsWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    //region Current Mailbox
    private val currentMailboxObjectId = MutableLiveData<String?>(null)

    val currentMailbox = MutableLiveData<Mailbox>()

    val currentFoldersLive = Transformations.switchMap(currentMailbox) {
        liveData(Dispatchers.IO) { emitSource(FolderController.getFoldersAsync().asLiveData()) }
    }

    val currentQuotasLive = Transformations.switchMap(currentMailbox) {
        liveData(Dispatchers.IO) { emitSource(QuotasController.getQuotasAsync(it.objectId).asLiveData()) }
    }
    //endregion

    //region Current Folder
    private val currentFolderId = MutableLiveData<String?>(null)

    val currentFolder = MutableLiveData<Folder>()

    val currentFolderLive = Transformations.switchMap(currentFolder) {
        liveData(Dispatchers.IO) { emitSource(FolderController.getFolderAsync(it.id).asLiveData()) }
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

    private var forceRefreshJob: Job? = null

    private fun observeFolderAndFilter() = MediatorLiveData<Pair<Folder?, ThreadFilter>>().apply {
        value = currentFolder.value to currentFilter.value!!
        addSource(currentFolder) { value = it to value!!.second }
        addSource(currentFilter) { value = value?.first to it }
    }

    fun collectCurrentData() {

        viewModelScope.launch(Dispatchers.IO) {
            currentMailboxObjectId.asFlow().distinctUntilChanged().collect { mailboxObjectId ->
                mailboxObjectId?.let(MailboxController::getMailbox)?.let {
                    currentMailbox.postValue(it)
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            currentFolderId.asFlow().distinctUntilChanged().collect { folderId ->
                folderId?.let(FolderController::getFolder)?.let {
                    currentFolder.postValue(it)
                }
            }
        }
    }

    fun observeMailboxesLive(userId: Int = AccountUtils.currentUserId): LiveData<List<Mailbox>> = liveData(Dispatchers.IO) {
        emitSource(MailboxController.getMailboxesAsync(userId).asLiveData())
    }

    private fun selectMailbox(mailbox: Mailbox) {
        if (mailbox.objectId != currentMailboxObjectId.value) {
            Log.d(TAG, "Select mailbox: ${mailbox.email}")
            AccountUtils.currentMailboxId = mailbox.mailboxId
            AccountUtils.currentMailboxObjectId = mailbox.objectId
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
        MailboxController.updateMailboxes(getApplication())
        MailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)?.let(::openMailbox)
    }

    fun openMailbox(mailbox: Mailbox) = viewModelScope.launch(Dispatchers.IO) {
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
        MailboxController.updateMailboxes(getApplication())
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
        val mailbox = currentMailbox.value ?: return@launch
        if (folderId == currentFolder.value?.id) return@launch

        selectFolder(folderId)
        refreshThreads(mailbox, folderId)
    }

    fun forceRefreshThreads() {
        forceRefreshJob?.cancel()
        forceRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Force refresh threads")
            val mailbox = currentMailbox.value ?: return@launch
            val folderId = currentFolder.value?.id ?: return@launch
            refreshThreads(mailbox, folderId)
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
        MergedContactController.getMergedContactsAsync().collect {
            mergedContacts.postValue(
                it.list.associateBy { mergedContact ->
                    Recipient().initLocalValues(mergedContact.email, mergedContact.name)
                }
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

    private fun refreshThreads(mailbox: Mailbox, folderId: String) = viewModelScope.launch(Dispatchers.IO) {

        isDownloadingChanges.postValue(true)

        MessageController.fetchCurrentFolderMessages(mailbox, folderId, localSettings.threadMode)

        isDownloadingChanges.postValue(false)
    }

    fun deleteThread(thread: Thread) = viewModelScope.launch(Dispatchers.IO) {

        val mailbox = currentMailbox.value ?: return@launch
        val folderId = currentFolder.value?.id ?: return@launch

        RealmDatabase.mailboxContent().writeBlocking {
            val currentFolderRole = FolderController.getFolder(folderId, realm = this)?.role
            val messagesUids = thread.messages.map { it.uid }

            val isSuccess = if (currentFolderRole == FolderRole.TRASH) {
                ApiRepository.deleteMessages(mailbox.uuid, messagesUids).isSuccess()
            } else {
                val trashId = FolderController.getFolder(FolderRole.TRASH, realm = this)!!.id
                ApiRepository.moveMessages(mailbox.uuid, messagesUids, trashId).isSuccess()
            }

            if (isSuccess) {
                incrementFolderUnreadCount(folderId, -thread.unseenMessagesCount, mailbox.objectId)
                deleteMessages(thread.messages)
                ThreadController.getThread(thread.uid, realm = this)?.let(::delete)
            }
        }
    }

    companion object {
        private val TAG: String = MainViewModel::class.java.simpleName
        private val DEFAULT_SELECTED_FOLDER = FolderRole.INBOX
    }
}
