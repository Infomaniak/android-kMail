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
package com.infomaniak.mail.ui.main

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.api.ApiRepository.OFFSET_FIRST_PAGE
import com.infomaniak.mail.data.cache.RealmController
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.FolderController.decrementFolderUnreadCount
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController.getLatestThreadSync
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.infomaniak.mail.data.models.user.UserPreferences.ThreadMode

class MainViewModel : ViewModel() {

    companion object {
        private val TAG = "MainViewModel"
        private val DEFAULT_SELECTED_FOLDER = FolderRole.INBOX

        val currentMailboxObjectId = MutableLiveData<String?>()
        val currentFolderId = MutableLiveData<String?>()
        val currentThreadUid = MutableLiveData<String?>()
        val currentMessageUid = MutableLiveData<String?>()
    }

    val isInternetAvailable = MutableLiveData(false)
    var canContinueToPaginate = true
    var currentOffset = OFFSET_FIRST_PAGE
    var isDownloadingChanges = false
    var threadDisplayMode = ThreadMode.THREADS

    fun close() {
        Log.i(TAG, "close")
        RealmController.close()

        currentMessageUid.value = null
        currentThreadUid.value = null
        currentFolderId.value = null
        currentMailboxObjectId.value = null
    }

    private suspend fun selectMailbox(mailbox: Mailbox) {
        if (mailbox.objectId != currentMailboxObjectId.value) {
            Log.i(TAG, "selectMailbox: ${mailbox.email}")
            AccountUtils.currentMailboxId = mailbox.mailboxId

            withContext(Dispatchers.Main) {
                currentMailboxObjectId.value = mailbox.objectId

                currentMessageUid.value = null
                currentThreadUid.value = null
                currentFolderId.value = null
            }
        }
    }

    private suspend fun selectFolder(folderId: String) {
        if (folderId != currentFolderId.value) {
            Log.i(TAG, "selectFolder: $folderId")
            currentOffset = OFFSET_FIRST_PAGE

            withContext(Dispatchers.Main) {
                currentFolderId.value = folderId

                currentMessageUid.value = null
                currentThreadUid.value = null
            }
        }
    }

    private suspend fun selectThread(thread: Thread) {
        if (thread.uid != currentThreadUid.value) {
            Log.i(TAG, "selectThread: ${thread.subject}")

            withContext(Dispatchers.Main) {
                currentThreadUid.value = thread.uid

                currentMessageUid.value = null
            }
        }
    }

    fun loadAddressBooksAndContacts() = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "loadAddressBooksAndContacts")
        loadAddressBooks()
        loadContacts()
    }

    fun openMailbox(mailbox: Mailbox) = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "switchToMailbox: ${mailbox.email}")
        selectMailbox(mailbox)
        val folders = loadFolders(mailbox)
        computeFolderToSelect(folders)?.let { folder ->
            selectFolder(folder.id)
            loadThreads(mailbox.uuid, folder.id)
        }
    }

    fun loadCurrentMailbox() = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "loadCurrentMailbox")
        val mailboxes = loadMailboxes()
        computeMailboxToSelect(mailboxes)?.let { mailbox ->
            selectMailbox(mailbox)
            val folders = loadFolders(mailbox)
            computeFolderToSelect(folders)?.let { folder ->
                selectFolder(folder.id)
                loadThreads(mailbox.uuid, folder.id)
            }
        }
    }

    fun forceRefreshMailboxes() = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "forceRefreshMailboxes")
        loadMailboxes()
    }

    fun openFolder(folderId: String) = viewModelScope.launch(Dispatchers.IO) {
        val mailboxObjectId = currentMailboxObjectId.value ?: return@launch
        val mailboxUuid = MailboxController.getMailboxSync(mailboxObjectId)?.uuid ?: return@launch
        if (folderId == currentFolderId.value) return@launch

        Log.i(TAG, "openFolder: $folderId")

        selectFolder(folderId)
        loadThreads(mailboxUuid, folderId)
    }

    fun openThread(thread: Thread) = viewModelScope.launch(Dispatchers.IO) {
        selectThread(thread)
        markAsSeen(thread)
        loadMessages(thread)
    }

    private fun markAsSeen(thread: Thread) {
        if (thread.unseenMessagesCount != 0) {

            RealmController.mailboxContent.writeBlocking {
                getLatestThreadSync(thread.uid)?.let { latestThread ->

                    val apiResponse = ApiRepository.markMessagesAsSeen(thread.mailboxUuid, latestThread.messages.map { it.uid })

                    if (apiResponse.isSuccess()) {
                        currentFolderId.value?.let { decrementFolderUnreadCount(it) }
                        latestThread.apply {
                            messages.forEach { it.seen = true }
                            unseenMessagesCount = 0
                        }
                    }
                }
            }
        }
    }

    fun forceRefreshThreads(filter: ThreadFilter) = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "forceRefreshThreads")
        val mailboxObjectId = currentMailboxObjectId.value ?: return@launch
        val mailboxUuid = MailboxController.getMailboxSync(mailboxObjectId)?.uuid ?: return@launch
        val folderId = currentFolderId.value ?: return@launch
        currentOffset = OFFSET_FIRST_PAGE
        isDownloadingChanges = true
        loadThreads(mailboxUuid, folderId, currentOffset, filter)
    }

    fun loadMoreThreads(
        mailboxUuid: String,
        folderId: String,
        offset: Int,
        filter: ThreadFilter,
    ) = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "loadMoreThreads: $offset")
        isDownloadingChanges = true
        loadThreads(mailboxUuid, folderId, offset, filter)
    }

    fun deleteDraft(message: Message) = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "deleteDraft: ${message.body}")
        if (ApiRepository.deleteDraft(message.draftResource).isSuccess()) MessageController.deleteMessage(message.uid)
    }

    private fun computeMailboxToSelect(mailboxes: List<Mailbox>): Mailbox? {
        return with(mailboxes) {
            find { it.mailboxId == AccountUtils.currentMailboxId }
                ?: firstOrNull()
        }
    }

    private fun computeFolderToSelect(folders: List<Folder>): Folder? {
        return with(folders) {
            find { it.id == currentFolderId.value }
                ?: find { it.role == DEFAULT_SELECTED_FOLDER }
                ?: firstOrNull()
        }
    }

    private fun loadAddressBooks() {
        val apiAddressBooks = ApiRepository.getAddressBooks().data?.addressBooks ?: emptyList()

        AddressBookController.upsertApiData(apiAddressBooks)
    }

    private fun loadContacts() {
        val apiContacts = ApiRepository.getContacts().data ?: emptyList()

        ContactController.upsertApiData(apiContacts)
    }

    private fun loadMailboxes(): List<Mailbox> {
        val apiMailboxes = ApiRepository.getMailboxes().data?.map {
            val quotas = if (it.isLimited) ApiRepository.getQuotas(it.hostingId, it.mailbox).data else null
            it.initLocalValues(AccountUtils.currentUserId, quotas)
        } ?: emptyList()

        return MailboxController.upsertApiData(apiMailboxes)
    }

    private fun loadFolders(mailbox: Mailbox): List<Folder> {
        val apiFolders = ApiRepository.getFolders(mailbox.uuid).data?.formatFoldersListWithAllChildren() ?: emptyList()

        return FolderController.upsertApiData(apiFolders)
    }

    private fun loadThreads(
        mailboxUuid: String,
        folderId: String,
        offset: Int = OFFSET_FIRST_PAGE,
        filter: ThreadFilter = ThreadFilter.ALL,
    ) {
        canContinueToPaginate = ThreadController.upsertApiData(mailboxUuid, folderId, offset, filter)
    }

    private fun loadMessages(thread: Thread) {
        val apiMessages = fetchMessages(thread)

        MessageController.upsertApiData(apiMessages, thread)
    }

    private fun fetchMessages(thread: Thread): List<Message> {
        return thread.messages.map { realmMessage ->
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
                }.let { apiMessage ->
                    apiMessage ?: realmMessage
                }
            }
        }
    }
}
