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
import com.infomaniak.mail.data.LocalSettings.ThreadMode
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.api.ApiRepository.OFFSET_FIRST_PAGE
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.*
import com.infomaniak.mail.data.cache.mailboxContent.FolderController.incrementFolderUnreadCount
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.deleteMessages
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController.markThreadAsSeen
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController.markThreadAsUnseen
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.cache.userInfo.AddressBookController
import com.infomaniak.mail.data.cache.userInfo.MergedContactController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ContactUtils.getPhoneContacts
import com.infomaniak.mail.utils.ContactUtils.mergeApiContactsIntoPhoneContacts
import com.infomaniak.mail.utils.ModelsUtils.formatFoldersListWithAllChildren
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val localSettings by lazy { LocalSettings.getInstance(application) }

    val isInternetAvailable = SingleLiveEvent<Boolean>()
    var canPaginate = true
    var currentOffset = OFFSET_FIRST_PAGE
    var isDownloadingChanges = MutableLiveData(false)

    var mergedContacts = MutableLiveData<Map<Recipient, MergedContact>?>()

    fun close() {
        Log.i(TAG, "close")
        RealmDatabase.close()
        resetAllCurrentLiveData()
    }

    fun resetAllCurrentLiveData() {
        currentMessageUid.value = null
        currentThreadUid.value = null
        currentFolderId.value = null
        currentMailboxObjectId.value = null
    }

    fun observeMailboxes(userId: Int = AccountUtils.currentUserId): LiveData<List<Mailbox>> = liveData(Dispatchers.IO) {
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

    fun updateUserInfo() = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "updateUserInfo")
        updateAddressBooks()
        updateContacts()
    }

    fun loadCurrentMailbox(threadMode: ThreadMode) = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "loadCurrentMailbox")
        updateMailboxes()
        MailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)
            ?.let { openMailbox(it) }
    }

    fun openMailbox(mailbox: Mailbox) = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "switchToMailbox: ${mailbox.email}")
        selectMailbox(mailbox)
        updateSignatures(mailbox)
        updateFolders(mailbox)
        FolderController.getFolder(DEFAULT_SELECTED_FOLDER)?.let { folder ->
            selectFolder(folder.id)
            refreshThreads(mailbox.uuid, folder.id)
        }
    }

    fun forceRefreshMailboxes() = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "forceRefreshMailboxes")
        updateMailboxes()
        updateCurrentMailboxQuotas()
    }

    private fun updateCurrentMailboxQuotas() {
        val mailbox = currentMailboxObjectId.value?.let(MailboxController::getMailbox) ?: return
        if (mailbox.isLimited) {
            ApiRepository.getQuotas(mailbox.hostingId, mailbox.mailboxName).data?.let { quotas ->
                MailboxController.updateMailbox(mailbox.objectId) {
                    it.quotas = quotas
                }
            }
        }
    }

    fun openFolder(folderId: String) = viewModelScope.launch(Dispatchers.IO) {
        val mailboxObjectId = currentMailboxObjectId.value ?: return@launch
        val mailboxUuid = MailboxController.getMailbox(mailboxObjectId)?.uuid ?: return@launch
        if (folderId == currentFolderId.value) return@launch

        Log.i(TAG, "openFolder: $folderId")

        selectFolder(folderId)
        refreshThreads(mailboxUuid, folderId)
    }

    fun openThread(threadUid: String) = viewModelScope.launch(Dispatchers.IO) {
        val thread = ThreadController.getThread(threadUid) ?: return@launch
        selectThread(thread)
        markAsSeen(thread, currentFolderId.value!!)
        updateMessages(thread)
    }

    fun forceRefreshThreads(filter: ThreadFilter) = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "forceRefreshThreads")
        val mailboxObjectId = currentMailboxObjectId.value ?: return@launch
        val mailboxUuid = MailboxController.getMailbox(mailboxObjectId)?.uuid ?: return@launch
        val folderId = currentFolderId.value ?: return@launch
        currentOffset = OFFSET_FIRST_PAGE
        isDownloadingChanges.postValue(true)
        refreshThreads(mailboxUuid, folderId, filter)
    }

    fun deleteDraft(message: Message) = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "deleteDraft: ${message.body}")
        if (ApiRepository.deleteDraft(message.draftResource).isSuccess()) MessageController.deleteMessage(message.uid)
    }

    private fun updateAddressBooks() {
        val apiAddressBooks = ApiRepository.getAddressBooks().data?.addressBooks ?: emptyList()

        AddressBookController.update(apiAddressBooks)
    }

    private fun updateContacts() {
        val apiContacts = ApiRepository.getContacts().data ?: emptyList()
        val phoneMergedContacts = getPhoneContacts(getApplication())

        mergeApiContactsIntoPhoneContacts(apiContacts, phoneMergedContacts)

        MergedContactController.update(phoneMergedContacts.values.toList())
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

    private fun updateMailboxes() {
        val apiMailboxes = ApiRepository.getMailboxes().data
            ?.map { it.initLocalValues(AccountUtils.currentUserId) }
            ?: emptyList()

        MailboxController.update(apiMailboxes, AccountUtils.currentUserId)
    }

    private fun updateSignatures(mailbox: Mailbox) = viewModelScope.launch(Dispatchers.IO) {
        val apiSignatures = ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailboxName).data?.signatures ?: return@launch

        SignatureController.update(apiSignatures)
    }

    private fun updateFolders(mailbox: Mailbox) {
        val apiFolders = ApiRepository.getFolders(mailbox.uuid).data?.formatFoldersListWithAllChildren() ?: emptyList()

        FolderController.update(apiFolders)
    }

    private fun refreshThreads(
        mailboxUuid: String,
        folderId: String,
        filter: ThreadFilter = ThreadFilter.ALL,
    ) {

        val threadsResult = ApiRepository.getThreads(
            mailboxUuid = mailboxUuid,
            folderId = folderId,
            threadMode = localSettings.threadMode,
            offset = OFFSET_FIRST_PAGE,
            filter = filter,
        ).data ?: return

        RealmDatabase.mailboxContent().writeBlocking {
            canPaginate = ThreadController.refreshThreads(threadsResult, mailboxUuid, folderId, filter, this)

            FolderController.updateFolderLastUpdatedAt(folderId, this)

            val isDraftFolder = FolderController.getFolder(folderId, this)?.role == FolderRole.DRAFT
            if (isDraftFolder) DraftController.cleanOrphans(threadsResult.threads, this)
        }

        isDownloadingChanges.postValue(false)
    }

    fun loadMoreThreads(
        mailboxUuid: String,
        folderId: String,
        offset: Int,
        filter: ThreadFilter,
    ) = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "loadMoreThreads: $offset")
        isDownloadingChanges.postValue(true)

        val threadsResult = ApiRepository.getThreads(
            mailboxUuid = mailboxUuid,
            folderId = folderId,
            threadMode = localSettings.threadMode,
            offset = offset,
            filter = filter,
        ).data ?: return@launch

        canPaginate = ThreadController.loadMoreThreads(threadsResult, mailboxUuid, folderId, offset, filter)
        isDownloadingChanges.postValue(false)
    }

    private fun updateMessages(thread: Thread) {
        val apiMessages = fetchMessages(thread)
        MessageController.update(thread.messages, apiMessages)
    }

    private fun fetchMessages(thread: Thread): List<Message> {
        return thread.messages.mapNotNull { localMessage ->
            if (localMessage.fullyDownloaded) {
                localMessage
            } else {
                ApiRepository.getMessage(localMessage.resource).data?.also { completedMessage ->
                    completedMessage.fullyDownloaded = true
                    if (completedMessage.isDraft) {
                        RealmDatabase.mailboxContent().writeBlocking {
                            val draft = DraftController.getDraftByMessageUid(completedMessage.uid, this)
                            completedMessage.draftLocalUuid = draft?.localUuid
                                ?: DraftController.fetchDraft(completedMessage.draftResource, completedMessage.uid, this)
                        }
                    }
                }
            }
        }
    }

    //region Mark as seen/unseen
    fun toggleSeenStatus(thread: Thread) = viewModelScope.launch(Dispatchers.IO) {
        val folderId = currentFolderId.value!!
        if (thread.unseenMessagesCount == 0) {
            markAsUnseen(thread, folderId)
        } else {
            markAsSeen(thread, folderId)
        }
    }

    private fun markAsUnseen(thread: Thread, folderId: String) {
        RealmDatabase.mailboxContent().writeBlocking {
            val latestThread = findLatest(thread) ?: return@writeBlocking
            val uid = ThreadController.getThreadLastMessageUid(latestThread)
            val apiResponse = ApiRepository.markMessagesAsUnseen(latestThread.mailboxUuid, uid)
            if (apiResponse.isSuccess()) markThreadAsUnseen(latestThread, folderId)
        }
    }

    private fun markAsSeen(thread: Thread, folderId: String) {
        if (thread.unseenMessagesCount == 0) return

        RealmDatabase.mailboxContent().writeBlocking {
            val latestThread = findLatest(thread) ?: return@writeBlocking
            val uids = ThreadController.getThreadUnseenMessagesUids(latestThread)
            val apiResponse = ApiRepository.markMessagesAsSeen(latestThread.mailboxUuid, uids)
            if (apiResponse.isSuccess()) markThreadAsSeen(latestThread, folderId)
        }
    }
    //endregion

    // Delete Thread
    fun deleteThread(thread: Thread, filter: ThreadFilter) = viewModelScope.launch(Dispatchers.IO) {

        val mailboxObjectId = currentMailboxObjectId.value ?: return@launch
        val mailboxUuid = MailboxController.getMailbox(mailboxObjectId)?.uuid ?: return@launch
        val currentFolderId = currentFolderId.value ?: return@launch

        RealmDatabase.mailboxContent().writeBlocking {
            val currentFolderRole = FolderController.getFolder(currentFolderId, this)?.role
            val messagesUids = thread.messages.map { it.uid }

            val isSuccess = if (currentFolderRole == FolderRole.TRASH) {
                ApiRepository.deleteMessages(mailboxUuid, messagesUids).isSuccess()
            } else {
                val trashId = FolderController.getFolder(FolderRole.TRASH, this)!!.id
                ApiRepository.moveMessages(mailboxUuid, messagesUids, trashId).isSuccess()
            }

            if (isSuccess) {
                incrementFolderUnreadCount(currentFolderId, -thread.unseenMessagesCount)
                deleteMessages(thread.messages)
                ThreadController.getThread(thread.uid, this)?.let(::delete)
            } else {
                // When the swiped animation finished, the Thread has been removed from the UI.
                // So if the API call failed, we need to put back this Thread in the UI.
                // Force-refreshing Realm will do that.
                forceRefreshThreads(filter)
            }
        }
    }
    //endregion

    //region New Message
    // TODO: This is temporary, while waiting for a "DraftsManager".
    fun executeDraftsActions() = viewModelScope.launch(Dispatchers.IO) {
        if (RealmDatabase.mailboxContent().isClosed()) return@launch
        RealmDatabase.mailboxContent().writeBlocking {

            fun getCurrentMailboxUuid(drafts: List<Draft>): String? {
                return if (drafts.isNotEmpty()) currentMailboxObjectId.value?.let(MailboxController::getMailbox)?.uuid else null
            }

            val drafts = DraftController.getDrafts(this)
            val mailboxUuid = getCurrentMailboxUuid(drafts) ?: return@writeBlocking

            drafts.reversed().forEach { draft ->
                DraftController.executeDraftAction(draft, mailboxUuid, this)
            }
        }
    }
    //endregion

    companion object {
        private val TAG: String = MainViewModel::class.java.simpleName
        private val DEFAULT_SELECTED_FOLDER = FolderRole.INBOX

        val currentMailboxObjectId = MutableLiveData<String?>()
        val currentFolderId = MutableLiveData<String?>()
        val currentThreadUid = MutableLiveData<String?>()
        val currentMessageUid = MutableLiveData<String?>()
    }
}
