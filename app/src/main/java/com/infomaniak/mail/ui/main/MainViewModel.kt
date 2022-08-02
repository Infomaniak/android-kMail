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
import com.infomaniak.mail.data.api.ApiRepository.PER_PAGE
import com.infomaniak.mail.data.cache.RealmController
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.FolderController.deleteFolders
import com.infomaniak.mail.data.cache.mailboxContent.FolderController.getLatestFolderSync
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.deleteMessages
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.getLatestMessageSync
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController.deleteThreads
import com.infomaniak.mail.data.cache.mailboxInfos.MailboxController
import com.infomaniak.mail.data.cache.userInfos.AddressBookController
import com.infomaniak.mail.data.cache.userInfos.ContactController
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ModelsUtils.formatFoldersListWithAllChildren
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    companion object {
        private val TAG = "MainViewModel"
        private val DEFAULT_SELECTED_FOLDER = FolderRole.INBOX

        private val mutableCurrentMailboxFlow = MutableStateFlow<Mailbox?>(null)
        private val mutableCurrentFolderFlow = MutableStateFlow<Folder?>(null)
        private val mutableCurrentThreadFlow = MutableStateFlow<Thread?>(null)
        private val mutableCurrentMessageFlow = MutableStateFlow<Message?>(null)
        val currentMailboxFlow = mutableCurrentMailboxFlow.asStateFlow()
        val currentFolderFlow = mutableCurrentFolderFlow.asStateFlow()
        val currentThreadFlow = mutableCurrentThreadFlow.asStateFlow()
        val currentMessageFlow = mutableCurrentMessageFlow.asStateFlow()

        var currentOffset = OFFSET_FIRST_PAGE

        fun selectMailbox(mailbox: Mailbox) {
            if (currentMailboxFlow.value?.objectId != mailbox.objectId) {
                Log.i(TAG, "selectMailbox: ${mailbox.email}")
                AccountUtils.currentMailboxId = mailbox.mailboxId
                mutableCurrentMailboxFlow.value = mailbox

                mutableCurrentMessageFlow.value = null
                mutableCurrentThreadFlow.value = null
                mutableCurrentFolderFlow.value = null
            }
        }

        fun selectFolder(folder: Folder) {
            if (folder.id != currentFolderFlow.value?.id) {
                Log.i(TAG, "selectFolder: ${folder.name}")
                currentOffset = OFFSET_FIRST_PAGE
                mutableCurrentFolderFlow.value = folder

                mutableCurrentMessageFlow.value = null
                mutableCurrentThreadFlow.value = null
            }
        }

        fun selectThread(thread: Thread) {
            if (thread.uid != currentThreadFlow.value?.uid) {
                Log.i(TAG, "selectThread: ${thread.subject}")
                mutableCurrentThreadFlow.value = thread

                mutableCurrentMessageFlow.value = null
            }
        }
    }

    val isInternetAvailable = MutableLiveData(false)
    var canContinueToPaginate = true

    fun close() {
        Log.i(TAG, "close")
        RealmController.close()

        mutableCurrentMessageFlow.value = null
        mutableCurrentThreadFlow.value = null
        mutableCurrentFolderFlow.value = null
        mutableCurrentMailboxFlow.value = null
    }

    fun loadAddressBooksAndContacts() = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "loadAddressBooksAndContacts")
        loadAddressBooks()
        loadContacts()
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

    fun switchToMailbox(mailbox: Mailbox) = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "switchToMailbox: ${mailbox.email}")
        selectMailbox(mailbox)
        val folders = loadFolders(mailbox)
        computeFolderToSelect(folders)?.let { folder ->
            selectFolder(folder)
            loadThreads(mailbox, folder)
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

    fun deleteDraft(message: Message) {
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
        Log.i(TAG, "loadAddressBooks (merge)")

        // Get current data
        Log.d(TAG, "AddressBooks: Get current data")
        val realmAddressBooks = AddressBookController.getAddressBooksSync()
        val apiAddressBooks = ApiRepository.getAddressBooks().data?.addressBooks ?: emptyList()

        // Get outdated data
        Log.d(TAG, "AddressBooks: Get outdated data")
        // val deletableAddressBooks = ContactsController.getDeletableAddressBooks(apiAddressBooks)
        val deletableAddressBooks = realmAddressBooks.filter { realmContact ->
            apiAddressBooks.none { it.id == realmContact.id }
        }

        // Save new data
        Log.d(TAG, "AddressBooks: Save new data")
        AddressBookController.upsertAddressBooks(apiAddressBooks)

        // Delete outdated data
        Log.d(TAG, "AddressBooks: Delete outdated data")
        AddressBookController.deleteAddressBooks(deletableAddressBooks)
    }

    private fun loadContacts() {
        Log.i(TAG, "loadContacts (merge)")

        // Get current data
        Log.d(TAG, "Contacts: Get current data")
        val realmContacts = ContactController.getContactsSync()
        val apiContacts = ApiRepository.getContacts().data ?: emptyList()

        // Get outdated data
        Log.d(TAG, "Contacts: Get outdated data")
        // val deletableContacts = ContactsController.getDeletableContacts(apiContacts)
        val deletableContacts = realmContacts.filter { realmContact ->
            apiContacts.none { it.id == realmContact.id }
        }

        // Save new data
        Log.d(TAG, "Contacts: Save new data")
        ContactController.upsertContacts(apiContacts)

        // Delete outdated data
        Log.d(TAG, "Contacts: Delete outdated data")
        ContactController.deleteContacts(deletableContacts)
    }

    private fun loadMailboxes(): List<Mailbox> {
        Log.i(TAG, "loadMailboxes (merge)")

        // Get current data
        Log.d(TAG, "Mailboxes: Get current data")
        val realmMailboxes = MailboxController.getMailboxesSync(AccountUtils.currentUserId)
        val apiMailboxes = ApiRepository.getMailboxes().data?.map {
            val quotas = if (it.isLimited) ApiRepository.getQuotas(it.hostingId, it.mailbox).data else null
            it.initLocalValues(AccountUtils.currentUserId, quotas)
        } ?: emptyList()

        // Get outdated data
        Log.d(TAG, "Mailboxes: Get outdated data")
        // val deletableMailboxes = MailboxInfoController.getDeletableMailboxes(apiMailboxes)
        val deletableMailboxes = realmMailboxes.filter { realmMailbox ->
            apiMailboxes.none { apiMailbox -> apiMailbox.mailboxId == realmMailbox.mailboxId }
        }

        // Save new data
        Log.d(TAG, "Mailboxes: Save new data")
        MailboxController.upsertMailboxes(apiMailboxes)

        // Delete outdated data
        Log.d(TAG, "Mailboxes: Delete outdated data")
        val isCurrentMailboxDeleted = deletableMailboxes.any { it.mailboxId == AccountUtils.currentMailboxId }
        if (isCurrentMailboxDeleted) {
            RealmController.closeMailboxContent()
            AccountUtils.currentMailboxId = AppSettings.DEFAULT_ID
        }
        MailboxController.deleteMailboxes(deletableMailboxes)
        deletableMailboxes.forEach { RealmController.deleteMailboxContent(it.mailboxId) }

        return if (isCurrentMailboxDeleted) {
            AccountUtils.reloadApp()
            emptyList()
        } else {
            apiMailboxes
        }
    }

    private fun loadFolders(mailbox: Mailbox): List<Folder> {
        Log.i(TAG, "loadFolders (merge)")

        // Get current data
        Log.d(TAG, "Folders: Get current data")
        val realmFolders = FolderController.getFoldersSync()
        val apiFolders = ApiRepository.getFolders(mailbox.uuid).data?.formatFoldersListWithAllChildren() ?: emptyList()

        // Get outdated data
        Log.d(TAG, "Folders: Get outdated data")
        // val deletableFolders = MailboxContentController.getDeletableFolders(foldersFromApi)
        val deletableFolders = realmFolders.filter { realmFolder ->
            apiFolders.none { apiFolder -> apiFolder.id == realmFolder.id }
        }
        val possiblyDeletableThreads = deletableFolders.flatMap { it.threads }
        val deletableMessages = possiblyDeletableThreads.flatMap { it.messages }.filter { message ->
            deletableFolders.any { folder -> folder.id == message.folderId }
        }
        val deletableThreads = possiblyDeletableThreads.filter { thread ->
            thread.messages.all { message -> deletableMessages.any { it.uid == message.uid } }
        }

        RealmController.mailboxContent.writeBlocking {
            // Save new data
            Log.d(TAG, "Folders: Save new data")
            apiFolders.forEach { apiFolder ->
                realmFolders.find { it.id == apiFolder.id }?.threads
                    ?.mapNotNull(::findLatest)
                    ?.let { apiFolder.threads = it.toRealmList() }
                copyToRealm(apiFolder, UpdatePolicy.ALL)
            }

            // Delete outdated data
            Log.d(TAG, "Folders: Delete outdated data")
            deleteMessages(deletableMessages)
            deleteThreads(deletableThreads)
            deleteFolders(deletableFolders)
        }

        return apiFolders
    }

    private fun loadThreads(mailbox: Mailbox, folder: Folder, offset: Int = OFFSET_FIRST_PAGE): List<Thread> {
        Log.i(TAG, "loadThreads (merge)")

        // Get current data
        Log.d(TAG, "Threads: Get current data")
        val realmThreads = FolderController.getFolderSync(folder.id)?.threads ?: emptyList()
        val apiThreadsSinceOffset = ApiRepository.getThreads(mailbox.uuid, folder.id, offset).data?.also { threadsResult ->
            canContinueToPaginate = threadsResult.messagesCount >= PER_PAGE
        }?.threads?.map { it.initLocalValues() } ?: emptyList()
        val apiThreads = if (offset == OFFSET_FIRST_PAGE) {
            apiThreadsSinceOffset
        } else {
            realmThreads.plus(apiThreadsSinceOffset).distinctBy { it.uid }
        }

        // Get outdated data
        Log.d(TAG, "Threads: Get outdated data")
        // val deletableThreads = MailboxContentController.getDeletableThreads(threadsFromApi)
        val deletableThreads = if (offset == OFFSET_FIRST_PAGE) {
            realmThreads.filter { realmThread ->
                apiThreads.none { apiThread -> apiThread.uid == realmThread.uid }
            }
        } else {
            emptyList()
        }
        val deletableMessages = deletableThreads.flatMap { thread -> thread.messages.filter { it.folderId == folder.id } }

        RealmController.mailboxContent.writeBlocking {
            // Save new data
            Log.d(TAG, "Threads: Save new data")
            val newPageSize = apiThreads.size - offset
            if (newPageSize > 0) {
                apiThreads.takeLast(newPageSize).forEach { apiThread ->
                    val realmThread = realmThreads.find { it.uid == apiThread.uid }
                    val mergedThread = getMergedThread(apiThread, realmThread)
                    copyToRealm(mergedThread, UpdatePolicy.ALL)
                }
                updateFolder(folder, apiThreads)
            }

            // Delete outdated data
            Log.d(TAG, "Threads: Delete outdated data")
            deleteMessages(deletableMessages)
            deleteThreads(deletableThreads)
        }

        return apiThreads
    }

    private fun MutableRealm.getMergedThread(apiThread: Thread, realmThread: Thread?): Thread {
        return apiThread.apply {
            if (realmThread != null) {
                messages.forEach { apiMessage ->
                    realmThread.messages.find { realmMessage -> realmMessage.uid == apiMessage.uid }
                        ?.let { realmMessage -> getLatestMessageSync(realmMessage.uid) }
                        ?.let { realmMessage -> saveMessageWithBackedUpData(apiMessage, realmMessage) }
                }
            }
        }
    }

    private fun MutableRealm.saveMessageWithBackedUpData(apiMessage: Message, realmMessage: Message) {
        apiMessage.apply {
            fullyDownloaded = realmMessage.fullyDownloaded
            body = realmMessage.body
            attachmentsResource = realmMessage.attachmentsResource
            attachments.setRealmListValues(realmMessage.attachments)
        }
        copyToRealm(apiMessage, UpdatePolicy.ALL)
    }

    private fun MutableRealm.updateFolder(folder: Folder, apiThreads: List<Thread>) {
        val latestFolder = getLatestFolderSync(folder.id) ?: folder
        latestFolder.threads = apiThreads.map { if (it.isManaged()) findLatest(it) ?: it else it }.toRealmList()
        copyToRealm(latestFolder, UpdatePolicy.ALL)
    }

    private fun <T> RealmList<T>.setRealmListValues(values: RealmList<T>) {
        if (isNotEmpty()) clear()
        addAll(values)
    }
}
