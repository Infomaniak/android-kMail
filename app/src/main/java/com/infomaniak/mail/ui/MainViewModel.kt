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

import android.util.Log
import androidx.lifecycle.*
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.data.UiSettings.ThreadMode
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.api.ApiRepository.OFFSET_FIRST_PAGE
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DuplicateController
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController.markThreadAsSeen
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController.markThreadAsUnseen
import com.infomaniak.mail.data.cache.mailboxInfos.MailboxController
import com.infomaniak.mail.data.cache.userInfos.AddressBookController
import com.infomaniak.mail.data.cache.userInfos.ContactController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ModelsUtils.formatFoldersListWithAllChildren
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    companion object {
        private val TAG: String = MainViewModel::class.java.simpleName
        private val DEFAULT_SELECTED_FOLDER = FolderRole.INBOX

        val currentMailboxObjectId = MutableLiveData<String?>()
        val currentFolderId = MutableLiveData<String?>()
        val currentThreadUid = MutableLiveData<String?>()
        val currentMessageUid = MutableLiveData<String?>()
    }

    val isInternetAvailable = SingleLiveEvent<Boolean>()
    var canPaginate = true
    var currentOffset = OFFSET_FIRST_PAGE
    var isDownloadingChanges = MutableLiveData(false)

    fun close() {
        Log.i(TAG, "close")
        RealmDatabase.close()

        currentMessageUid.value = null
        currentThreadUid.value = null
        currentFolderId.value = null
        currentMailboxObjectId.value = null
    }

    fun listenToMailboxes(userId: Int = AccountUtils.currentUserId): LiveData<List<Mailbox>> = liveData(Dispatchers.IO) {
        emitSource(
            MailboxController.getMailboxesAsync(userId)
                .map { it.list }
                .asLiveData()
        )
    }

    fun getMailbox(objectId: String): LiveData<Mailbox?> = liveData(Dispatchers.IO) {
        emit(MailboxController.getMailbox(objectId))
    }

    fun getFolder(folderId: String): LiveData<Folder?> = liveData(Dispatchers.IO) {
        emit(FolderController.getFolder(folderId))
    }

    private fun selectMailbox(mailbox: Mailbox) {
        if (mailbox.objectId != currentMailboxObjectId.value) {
            Log.i(TAG, "selectMailbox: ${mailbox.email}")
            AccountUtils.currentMailboxId = mailbox.mailboxId

            currentMailboxObjectId.postValue(mailbox.objectId)

            currentMessageUid.postValue(null)
            currentThreadUid.postValue(null)
            currentFolderId.postValue(null)
        }
    }

    private fun selectFolder(folderId: String) {
        if (folderId != currentFolderId.value) {
            Log.i(TAG, "selectFolder: $folderId")
            currentOffset = OFFSET_FIRST_PAGE

            currentFolderId.postValue(folderId)

            currentMessageUid.postValue(null)
            currentThreadUid.postValue(null)
        }
    }

    private fun selectThread(thread: Thread) {
        if (thread.uid != currentThreadUid.value) {
            Log.i(TAG, "selectThread: ${thread.subject}")

            currentThreadUid.postValue(thread.uid)

            currentMessageUid.postValue(null)
        }
    }

    fun updateUserInfos() = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "updateUserInfos")
        updateUserPreferences()
        updateAddressBooks()
        updateContacts()
    }

    fun loadCurrentMailbox(threadMode: ThreadMode) {
        Log.i(TAG, "loadCurrentMailbox")
        updateMailboxes()
        MailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)
            ?.let { openMailbox(it, threadMode) }
    }

    fun openMailbox(mailbox: Mailbox, threadMode: ThreadMode) = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "switchToMailbox: ${mailbox.email}")
        selectMailbox(mailbox)
        updateMailboxQuotas(mailbox)
        updateFolders(mailbox)
        FolderController.getFolder(DEFAULT_SELECTED_FOLDER)?.let { folder ->
            selectFolder(folder.id)
            refreshThreads(mailbox.uuid, folder.id, threadMode)
        }
    }

    private fun updateMailboxQuotas(mailbox: Mailbox) = viewModelScope.launch(Dispatchers.IO) {
        if (mailbox.isLimited) {
            ApiRepository.getQuotas(mailbox.hostingId, mailbox.mailbox).data?.let { quotas ->
                quotas.mailboxObjectId = mailbox.objectId
                MailboxController.updateMailbox(mailbox.objectId) {
                    it.quotas = quotas
                }
            }
        }
    }

    fun forceRefreshMailboxes() = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "forceRefreshMailboxes")
        updateMailboxes()
    }

    fun openFolder(folderId: String, threadMode: ThreadMode) = viewModelScope.launch(Dispatchers.IO) {
        val mailboxObjectId = currentMailboxObjectId.value ?: return@launch
        val mailboxUuid = MailboxController.getMailbox(mailboxObjectId)?.uuid ?: return@launch
        if (folderId == currentFolderId.value) return@launch

        Log.i(TAG, "openFolder: $folderId")

        selectFolder(folderId)
        refreshThreads(mailboxUuid, folderId, threadMode)
    }

    fun openThread(thread: Thread) = viewModelScope.launch(Dispatchers.IO) {
        selectThread(thread)
        markAsSeen(thread, currentFolderId.value!!)
        updateMessages(thread)
    }

    fun forceRefreshThreads(filter: ThreadFilter, threadMode: ThreadMode) = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "forceRefreshThreads")
        val mailboxObjectId = currentMailboxObjectId.value ?: return@launch
        val mailboxUuid = MailboxController.getMailbox(mailboxObjectId)?.uuid ?: return@launch
        val folderId = currentFolderId.value ?: return@launch
        currentOffset = OFFSET_FIRST_PAGE
        isDownloadingChanges.postValue(true)
        refreshThreads(mailboxUuid, folderId, threadMode, filter)
    }

    fun deleteDraft(message: Message) = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "deleteDraft: ${message.body}")
        if (ApiRepository.deleteDraft(message.draftResource).isSuccess()) MessageController.deleteMessage(message.uid)
    }

    // TODO: Save wanted API UserPreferences into Realm.
    private fun updateUserPreferences() {
        // val userPreferences =  ApiRepository.getUser().data?.preferences

        // UserPreferencesController.upsertApiData(userPreferences)
    }

    private fun updateAddressBooks() {
        val apiAddressBooks = ApiRepository.getAddressBooks().data?.addressBooks ?: emptyList()

        AddressBookController.update(apiAddressBooks)
    }

    private fun updateContacts() {
        val apiContacts = ApiRepository.getContacts().data ?: emptyList()

        ContactController.update(apiContacts)
    }

    private fun updateMailboxes() {
        val apiMailboxes = ApiRepository.getMailboxes().data?.map {
            it.initLocalValues(AccountUtils.currentUserId)
        } ?: emptyList()

        MailboxController.update(apiMailboxes)
    }

    private fun updateFolders(mailbox: Mailbox) {
        val apiFolders = ApiRepository.getFolders(mailbox.uuid).data?.formatFoldersListWithAllChildren() ?: emptyList()

        FolderController.update(apiFolders)
    }

    private fun refreshThreads(
        mailboxUuid: String,
        folderId: String,
        threadMode: ThreadMode,
        filter: ThreadFilter = ThreadFilter.ALL,
    ) {
        DuplicateController.removeDuplicates()
        val threadsResult = ApiRepository.getThreads(mailboxUuid, folderId, threadMode, OFFSET_FIRST_PAGE, filter).data ?: return
        canPaginate = ThreadController.refreshThreads(threadsResult, mailboxUuid, folderId, filter)
        FolderController.updateFolderLastUpdatedAt(folderId)
        isDownloadingChanges.postValue(false)
    }

    fun loadMoreThreads(
        mailboxUuid: String,
        folderId: String,
        threadMode: ThreadMode,
        offset: Int,
        filter: ThreadFilter,
    ) = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "loadMoreThreads: $offset")
        isDownloadingChanges.postValue(true)
        DuplicateController.removeDuplicates()
        val threadsResult = ApiRepository.getThreads(mailboxUuid, folderId, threadMode, offset, filter).data ?: return@launch
        canPaginate = ThreadController.loadMoreThreads(threadsResult, mailboxUuid, folderId, offset, filter)
        isDownloadingChanges.postValue(false)
    }

    private fun updateMessages(thread: Thread) {
        val apiMessages = fetchMessages(thread)
        MessageController.update(thread.messages, apiMessages)
    }

    private fun fetchMessages(thread: Thread): List<Message> {
        return thread.messages.mapNotNull { realmMessage ->
            if (realmMessage.fullyDownloaded) {
                realmMessage
            } else {
                ApiRepository.getMessage(realmMessage.resource).data?.also { completedMessage ->
                    completedMessage.apply {
                        initLocalValues() // TODO: Remove this when we have EmbeddedObjects
                        fullyDownloaded = true
                        body?.initLocalValues(uid) // TODO: Remove this when we have EmbeddedObjects
                        // TODO: Remove this `forEachIndexed` when we have EmbeddedObjects
                        @Suppress("SAFE_CALL_WILL_CHANGE_NULLABILITY", "UNNECESSARY_SAFE_CALL")
                        attachments?.forEachIndexed { index, attachment -> attachment.initLocalValues(index, uid) }
                    }
                    // TODO: Uncomment this when managing Drafts folder
                    // if (completedMessage.isDraft && currentFolder.role = Folder.FolderRole.DRAFT) {
                    //     Log.e("TAG", "fetchMessagesFromApi: ${completedMessage.subject} | ${completedMessage.body?.value}")
                    //     val draft = fetchDraft(completedMessage.draftResource, completedMessage.uid)
                    //     completedMessage.draftUuid = draft?.uuid
                    // }
                }
            }
        }
    }

    fun toggleSeenStatus(thread: Thread, folderId: String) = viewModelScope.launch(Dispatchers.IO) {
        if (thread.unseenMessagesCount == 0) {
            markAsUnseen(thread, folderId)
        } else {
            markAsSeen(thread, folderId)
        }
    }

    private fun markAsUnseen(thread: Thread, folderId: String) {
        RealmDatabase.mailboxContent.writeBlocking {
            val latestThread = findLatest(thread) ?: return@writeBlocking
            val uids = ThreadController.getThreadLastMessageUids(latestThread)
            val apiResponse = ApiRepository.markMessagesAsUnseen(latestThread.mailboxUuid, uids)
            if (apiResponse.isSuccess()) markThreadAsUnseen(latestThread, folderId)
        }
    }

    private fun markAsSeen(thread: Thread, folderId: String) {
        if (thread.unseenMessagesCount == 0) return

        RealmDatabase.mailboxContent.writeBlocking {
            val latestThread = findLatest(thread) ?: return@writeBlocking
            val uids = ThreadController.getThreadUnseenMessagesUids(latestThread)
            val apiResponse = ApiRepository.markMessagesAsSeen(latestThread.mailboxUuid, uids)
            if (apiResponse.isSuccess()) markThreadAsSeen(latestThread, folderId)
        }
    }
}
