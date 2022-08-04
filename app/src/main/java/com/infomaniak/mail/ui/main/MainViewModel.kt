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
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ModelsUtils.formatFoldersListWithAllChildren
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {

    companion object {
        private val TAG = "MainViewModel"
        private val DEFAULT_SELECTED_FOLDER = FolderRole.INBOX

        val currentMailboxFlow = MutableLiveData<Mailbox?>()
        val currentFolderFlow = MutableLiveData<Folder?>()
        val currentThreadFlow = MutableLiveData<Thread?>()
        val currentMessageFlow = MutableLiveData<Message?>()
    }

    val isInternetAvailable = MutableLiveData(false)
    var canContinueToPaginate = true
    var currentOffset = OFFSET_FIRST_PAGE

    fun close() {
        Log.i(TAG, "close")
        RealmController.close()

        currentMessageFlow.value = null
        currentThreadFlow.value = null
        currentFolderFlow.value = null
        currentMailboxFlow.value = null
    }

    private suspend fun selectMailbox(mailbox: Mailbox) {
        if (currentMailboxFlow.value?.objectId != mailbox.objectId) {
            Log.i(TAG, "selectMailbox: ${mailbox.email}")
            AccountUtils.currentMailboxId = mailbox.mailboxId

            withContext(Dispatchers.Main) {
                currentMailboxFlow.value = mailbox

                currentMessageFlow.value = null
                currentThreadFlow.value = null
                currentFolderFlow.value = null
            }
        }
    }

    private suspend fun selectFolder(folder: Folder) {
        if (folder.id != currentFolderFlow.value?.id) {
            Log.i(TAG, "selectFolder: ${folder.name}")
            currentOffset = OFFSET_FIRST_PAGE

            withContext(Dispatchers.Main) {
                currentFolderFlow.value = folder

                currentMessageFlow.value = null
                currentThreadFlow.value = null
            }
        }
    }

    private suspend fun selectThread(thread: Thread) {
        if (thread.uid != currentThreadFlow.value?.uid) {
            Log.i(TAG, "selectThread: ${thread.subject}")

            withContext(Dispatchers.Main) {
                currentThreadFlow.value = thread

                currentMessageFlow.value = null
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
            selectFolder(folder)
            loadThreads(mailbox, folder)
        }
    }

    fun forceRefreshMailboxes() = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "forceRefreshMailboxes")
        loadMailboxes()
    }

    fun loadCurrentMailbox() = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "loadCurrentMailbox")
        val mailboxes = loadMailboxes()
        computeMailboxToSelect(mailboxes)?.let { mailbox ->
            selectMailbox(mailbox)
            val folders = loadFolders(mailbox)
            computeFolderToSelect(folders)?.let { folder ->
                selectFolder(folder)
                loadThreads(mailbox, folder)
            }
        }
    }

    fun openFolder(folderId: String) = viewModelScope.launch(Dispatchers.IO) {
        val mailbox = currentMailboxFlow.value ?: return@launch
        if (folderId == currentFolderFlow.value?.id) return@launch

        val folder = FolderController.getFolderSync(folderId) ?: return@launch
        Log.i(TAG, "openFolder: ${folder.name}")

        selectFolder(folder)
        loadThreads(mailbox, folder)
    }

    fun forceRefreshFolders() = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "forceRefreshFolders")
        currentMailboxFlow.value?.let(::loadFolders)
    }

    fun openThread(thread: Thread) = viewModelScope.launch(Dispatchers.IO) {
        selectThread(thread)
        markAsSeen(thread)
        loadMessages(thread)
    }

    private fun markAsSeen(thread: Thread) {
        if (thread.unseenMessagesCount != 0) {

            val mailboxUuid = currentMailboxFlow.value?.uuid ?: return

            RealmController.mailboxContent.writeBlocking {
                getLatestThreadSync(thread.uid)?.let { latestThread ->

                    val apiResponse = ApiRepository.markMessagesAsSeen(mailboxUuid, latestThread.messages.map { it.uid })

                    if (apiResponse.isSuccess()) {
                        latestThread.apply {
                            messages.forEach { it.seen = true }
                            unseenMessagesCount = 0
                        }
                    }
                }
            }
        }
    }

    fun forceRefreshThreads() = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "forceRefreshThreads")
        val mailbox = currentMailboxFlow.value ?: return@launch
        val folder = currentFolderFlow.value ?: return@launch
        loadThreads(mailbox, folder)
    }

    fun loadMoreThreads(mailbox: Mailbox, folder: Folder, offset: Int) = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "loadMoreThreads: $offset")
        loadThreads(mailbox, folder, offset)
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
            find { it.id == currentFolderFlow.value?.id }
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

    private fun loadThreads(mailbox: Mailbox, folder: Folder, offset: Int = OFFSET_FIRST_PAGE): List<Thread> {
        return ThreadController.upsertApiData(mailbox, folder, offset) {
            canContinueToPaginate = it
        }
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
