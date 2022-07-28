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
package com.infomaniak.mail.data

import android.util.Log
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.api.ApiRepository.OFFSET_FIRST_PAGE
import com.infomaniak.mail.data.cache.RealmController
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.FolderController.deleteLatestFolder
import com.infomaniak.mail.data.cache.mailboxContent.FolderController.getLatestFolderSync
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.deleteMessages
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.getLatestMessageSync
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController.deleteLatestThread
import com.infomaniak.mail.data.cache.mailboxInfos.MailboxController
import com.infomaniak.mail.data.cache.userInfos.AddressBookController
import com.infomaniak.mail.data.cache.userInfos.ContactController
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.Contact
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.addressBook.AddressBook
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ModelsUtils.formatFoldersListWithAllChildren
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object MailData {

    private val DEFAULT_FOLDER_ROLE = FolderRole.INBOX

    private val mutableAddressBooksFlow = MutableStateFlow<List<AddressBook>?>(null)
    private val mutableContactsFlow = MutableStateFlow<List<Contact>?>(null)
    private val mutableMailboxesFlow = MutableStateFlow<List<Mailbox>?>(null)
    private val mutableFoldersFlow = MutableStateFlow<List<Folder>?>(null)
    private val mutableThreadsFlow = MutableStateFlow<List<Thread>?>(null)
    private val mutableMessagesFlow = MutableStateFlow<List<Message>?>(null)
    val addressBooksFlow = mutableAddressBooksFlow.asStateFlow()
    val contactsFlow = mutableContactsFlow.asStateFlow()
    val mailboxesFlow = mutableMailboxesFlow.asStateFlow()
    val foldersFlow = mutableFoldersFlow.asStateFlow()
    val threadsFlow = mutableThreadsFlow.asStateFlow()
    val messagesFlow = mutableMessagesFlow.asStateFlow()

    private val mutableCurrentMailboxFlow = MutableStateFlow<Mailbox?>(null)
    private val mutableCurrentFolderFlow = MutableStateFlow<Folder?>(null)
    private val mutableCurrentThreadFlow = MutableStateFlow<Thread?>(null)
    private val mutableCurrentMessageFlow = MutableStateFlow<Message?>(null)
    val currentMailboxFlow = mutableCurrentMailboxFlow.asStateFlow()
    val currentFolderFlow = mutableCurrentFolderFlow.asStateFlow()
    val currentThreadFlow = mutableCurrentThreadFlow.asStateFlow()
    val currentMessageFlow = mutableCurrentMessageFlow.asStateFlow()

    fun close() {
        RealmController.close()

        closeFlows()
        closeCurrentFlows()
    }

    private fun closeFlows() {
        mutableMessagesFlow.value = null
        mutableThreadsFlow.value = null
        mutableFoldersFlow.value = null
        mutableMailboxesFlow.value = null
        mutableContactsFlow.value = null
        mutableAddressBooksFlow.value = null
    }

    private fun closeCurrentFlows() {
        mutableCurrentMessageFlow.value = null
        mutableCurrentThreadFlow.value = null
        mutableCurrentFolderFlow.value = null
        mutableCurrentMailboxFlow.value = null
    }

    /**
     * Load Data
     */
    fun loadAddressBooksAndContacts() {
        loadAddressBooks {
            loadContacts()
        }
    }

    private fun loadAddressBooks(completion: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val realmAddressBooks = AddressBookController.getAddressBooksSync()

            mutableAddressBooksFlow.value = realmAddressBooks

            val apiAddressBooks = ApiRepository.getAddressBooks().data?.addressBooks ?: emptyList()
            val mergedAddressBooks = mergeAddressBooks(realmAddressBooks, apiAddressBooks)

            mutableAddressBooksFlow.value = mergedAddressBooks

            completion()
        }
    }

    private fun loadContacts() {
        CoroutineScope(Dispatchers.IO).launch {
            val realmContacts = ContactController.getContactsSync()

            mutableContactsFlow.value = realmContacts

            val apiContacts = ApiRepository.getContacts().data ?: emptyList()
            val mergedContacts = mergeContacts(realmContacts, apiContacts)

            mutableContactsFlow.value = mergedContacts
        }
    }

    fun loadInboxContent() {
        getMailboxesFromRealm { realmMailboxes ->
            if (realmMailboxes.isEmpty()) {
                getInboxContentFromApi()
            } else {
                computeMailboxToSelect(realmMailboxes)
                getFoldersFromRealm()
            }
        }
    }

    fun loadMailboxes() {
        getMailboxesFromRealm {
            getMailboxesFromApi()
        }
    }

    fun loadThreads(folder: Folder, mailbox: Mailbox, offset: Int) {
        val realmThreads = getThreadsFromRealm(folder, offset)
        getThreadsFromApi(folder, mailbox, offset, realmThreads)
    }

    fun refreshThreads(folder: Folder, mailbox: Mailbox) {
        getThreadsFromApi(folder, mailbox, OFFSET_FIRST_PAGE, forceRefresh = true)
    }

    fun loadMessages(thread: Thread) {
        getMessagesFromRealm(thread)
        getMessagesFromApi(thread)
    }

    fun deleteDraft(message: Message) {
        if (ApiRepository.deleteDraft(message.draftResource).isSuccess()) MessageController.deleteMessage(message.uid)
    }

    fun selectMailbox(mailbox: Mailbox) {
        if (currentMailboxFlow.value?.objectId != mailbox.objectId) {
            AccountUtils.currentMailboxId = mailbox.mailboxId
            mutableCurrentMailboxFlow.value = mailbox

            mutableCurrentMessageFlow.value = null
            mutableCurrentThreadFlow.value = null
            mutableCurrentFolderFlow.value = null
            mutableMessagesFlow.value = null
            mutableThreadsFlow.value = null
            mutableFoldersFlow.value = null
        }
    }

    fun selectFolder(folder: Folder) {
        if (folder.id != currentFolderFlow.value?.id) {
            mutableCurrentFolderFlow.value = folder

            mutableCurrentMessageFlow.value = null
            mutableCurrentThreadFlow.value = null
            mutableMessagesFlow.value = null
            mutableThreadsFlow.value = null
        }
    }

    fun selectThread(thread: Thread) {
        if (thread.uid != currentThreadFlow.value?.uid) {
            mutableCurrentThreadFlow.value = thread

            mutableCurrentMessageFlow.value = null
            mutableMessagesFlow.value = null
        }
    }

    fun selectMessage(message: Message) {
        if (message.uid != currentMessageFlow.value?.uid) {
            mutableCurrentMessageFlow.value = message
        }
    }

    private fun computeMailboxToSelect(mailboxes: List<Mailbox>): Mailbox {
        val mailbox = with(mailboxes) {
            find { it.mailboxId == AccountUtils.currentMailboxId }
                ?: first()
        }

        selectMailbox(mailbox)
        return mailbox
    }

    private fun computeFolderToSelect(folders: List<Folder>): Folder {
        val folder = with(folders) {
            find { it.id == currentFolderFlow.value?.id }
                ?: find { it.role == DEFAULT_FOLDER_ROLE }
                ?: first()
        }

        selectFolder(folder)
        return folder
    }

    /**
     * Read Realm
     */
    private fun getMailboxesFromRealm(completion: (List<Mailbox>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val realmMailboxes = MailboxController.getMailboxesSync(AccountUtils.currentUserId)

            mutableMailboxesFlow.value = realmMailboxes

            completion(realmMailboxes)
        }
    }

    private fun getFoldersFromRealm() {
        CoroutineScope(Dispatchers.IO).launch {
            val realmFolders = FolderController.getFoldersSync()

            mutableFoldersFlow.value = realmFolders

            if (realmFolders.isEmpty()) {
                getInboxContentFromApi()
            } else {
                val selectedFolder = computeFolderToSelect(realmFolders)
                getThreadsFromRealm(selectedFolder, OFFSET_FIRST_PAGE)
                getInboxContentFromApi()
            }
        }
    }

    private fun getThreadsFromRealm(folder: Folder, offset: Int): List<Thread> {
        val realmThreads = RealmController.mailboxContent.writeBlocking { getLatestFolderSync(folder.id) }?.threads ?: emptyList()
        if (offset == OFFSET_FIRST_PAGE) mutableThreadsFlow.value = realmThreads
        return realmThreads
    }

    private fun getMessagesFromRealm(thread: Thread) {
        val realmMessages = thread.messages
        mutableMessagesFlow.value = realmMessages
    }

    /**
     * Fetch API
     */
    private fun getInboxContentFromApi() {
        val mergedMailboxes = getMailboxesFromApi()
        val selectedMailbox = computeMailboxToSelect(mergedMailboxes)
        getFoldersFromApi(selectedMailbox)
    }

    private fun getMailboxesFromApi(): List<Mailbox> {
        val realmMailboxes = mutableMailboxesFlow.value
        val apiMailboxes = ApiRepository.getMailboxes().data?.map {
            val quotas = if (it.isLimited) ApiRepository.getQuotas(it.hostingId, it.mailbox).data else null
            it.initLocalValues(AccountUtils.currentUserId, quotas)
        }
        val mergedMailboxes = mergeMailboxes(realmMailboxes, apiMailboxes)

        mutableMailboxesFlow.value = mergedMailboxes

        return mergedMailboxes
    }

    private fun getFoldersFromApi(mailbox: Mailbox) {
        val realmFolders = mutableFoldersFlow.value
        val apiFolders = ApiRepository.getFolders(mailbox.uuid).data
        val mergedFolders = mergeFolders(realmFolders, apiFolders)

        mutableFoldersFlow.value = mergedFolders

        val selectedFolder = computeFolderToSelect(mergedFolders)
        getThreadsFromApi(selectedFolder, mailbox, OFFSET_FIRST_PAGE)
    }

    private fun getThreadsFromApi(
        folder: Folder,
        mailbox: Mailbox,
        offset: Int,
        realmThreads: List<Thread>? = null,
        forceRefresh: Boolean = false,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val apiThreads = ApiRepository.getThreads(mailbox.uuid, folder.id, offset).data?.threads?.map { it.initLocalValues() }
            val mergedThreads = mergeThreads(realmThreads ?: mutableThreadsFlow.value, apiThreads, folder, offset)

            if (forceRefresh || mergedThreads.isEmpty()) mutableThreadsFlow.forceRefresh()
            mutableThreadsFlow.value = mergedThreads
        }
    }

    private fun getMessagesFromApi(thread: Thread) {
        CoroutineScope(Dispatchers.IO).launch {
            val realmMessages = mutableMessagesFlow.value
            val apiMessages = fetchMessages(thread)
            val mergedMessages = mergeMessages(realmMessages, apiMessages)

            mutableMessagesFlow.value = mergedMessages
        }
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

    /**
     * Merge Realm & API data
     */
    private fun mergeAddressBooks(realmAddressBooks: List<AddressBook>, apiAddressBooks: List<AddressBook>): List<AddressBook> {

        // Get outdated data
        Log.d("API", "AddressBooks: Get outdated data")
        // val deletableAddressBooks = ContactsController.getDeletableAddressBooks(apiAddressBooks)
        val deletableAddressBooks = realmAddressBooks.filter { realmContact ->
            apiAddressBooks.none { it.id == realmContact.id }
        }

        // Save new data
        Log.d("API", "AddressBooks: Save new data")
        AddressBookController.upsertAddressBooks(apiAddressBooks)

        // Delete outdated data
        Log.d("API", "AddressBooks: Delete outdated data")
        AddressBookController.deleteAddressBooks(deletableAddressBooks)

        return apiAddressBooks
    }

    private fun mergeContacts(realmContacts: List<Contact>, apiContacts: List<Contact>): List<Contact> {

        // Get outdated data
        Log.d("API", "Contacts: Get outdated data")
        // val deletableContacts = ContactsController.getDeletableContacts(apiContacts)
        val deletableContacts = realmContacts.filter { realmContact ->
            apiContacts.none { it.id == realmContact.id }
        }

        // Save new data
        Log.d("API", "Contacts: Save new data")
        ContactController.upsertContacts(apiContacts)

        // Delete outdated data
        Log.d("API", "Contacts: Delete outdated data")
        ContactController.deleteContacts(deletableContacts)

        return apiContacts
    }

    private fun mergeMailboxes(realmMailboxes: List<Mailbox>?, apiMailboxes: List<Mailbox>?): List<Mailbox> {
        if (apiMailboxes == null) return realmMailboxes ?: emptyList()

        // Get outdated data
        Log.d("API", "Mailboxes: Get outdated data")
        // val deletableMailboxes = MailboxInfoController.getDeletableMailboxes(apiMailboxes)
        val deletableMailboxes = realmMailboxes?.filter { realmMailbox ->
            apiMailboxes.none { apiMailbox -> apiMailbox.mailboxId == realmMailbox.mailboxId }
        } ?: emptyList()

        // Save new data
        Log.d("API", "Mailboxes: Save new data")
        MailboxController.upsertMailboxes(apiMailboxes)

        // Delete outdated data
        Log.d("API", "Mailboxes: Delete outdated data")
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

    private fun mergeFolders(realmFolders: List<Folder>?, apiFoldersWithoutChildren: List<Folder>?): List<Folder> {
        if (apiFoldersWithoutChildren == null) return realmFolders ?: emptyList()

        val apiFolders = apiFoldersWithoutChildren.formatFoldersListWithAllChildren()

        // Get outdated data
        Log.d("API", "Folders: Get outdated data")
        // val deletableFolders = MailboxContentController.getDeletableFolders(foldersFromApi)
        val deletableFolders = realmFolders?.filter { realmFolder ->
            apiFolders.none { apiFolder -> apiFolder.id == realmFolder.id }
        } ?: emptyList()
        val possiblyDeletableThreads = deletableFolders.flatMap { it.threads }
        val deletableMessages = possiblyDeletableThreads.flatMap { it.messages }.filter { message ->
            deletableFolders.any { folder -> folder.id == message.folderId }
        }
        val deletableThreads = possiblyDeletableThreads.filter { thread ->
            thread.messages.all { message -> deletableMessages.any { it.uid == message.uid } }
        }

        RealmController.mailboxContent.writeBlocking {
            // Save new data
            Log.d("API", "Folders: Save new data")
            apiFolders.forEach { apiFolder ->
                realmFolders?.find { it.id == apiFolder.id }?.threads
                    ?.mapNotNull(::findLatest)
                    ?.let { apiFolder.threads = it.toRealmList() }
                copyToRealm(apiFolder, UpdatePolicy.ALL)
            }

            // Delete outdated data
            Log.d("API", "Folders: Delete outdated data")
            deleteMessages(deletableMessages)
            deleteThreads(deletableThreads)
            deleteFolders(deletableFolders)
        }

        return apiFolders
    }

    private fun mergeThreads(
        realmThreads: List<Thread>?,
        apiThreadsSinceOffset: List<Thread>?,
        folder: Folder,
        offset: Int,
    ): List<Thread> {
        if (apiThreadsSinceOffset == null) return realmThreads ?: emptyList()

        val apiThreads = if (offset == OFFSET_FIRST_PAGE) {
            apiThreadsSinceOffset
        } else {
            (realmThreads ?: emptyList())
                .plus(apiThreadsSinceOffset)
                .distinctBy { it.uid }
        }

        // Get outdated data
        Log.d("API", "Threads: Get outdated data")
        // val deletableThreads = MailboxContentController.getDeletableThreads(threadsFromApi)
        val deletableThreads = if (offset == OFFSET_FIRST_PAGE) {
            realmThreads?.filter { realmThread ->
                apiThreads.none { apiThread -> apiThread.uid == realmThread.uid }
            } ?: emptyList()
        } else {
            emptyList()
        }
        val deletableMessages = deletableThreads.flatMap { thread -> thread.messages.filter { it.folderId == folder.id } }

        RealmController.mailboxContent.writeBlocking {
            // Save new data
            Log.d("API", "Threads: Save new data")
            val newPageSize = apiThreads.size - offset
            if (newPageSize > 0) {
                apiThreads.takeLast(newPageSize).forEach { apiThread ->
                    val realmThread = realmThreads?.find { it.uid == apiThread.uid }
                    val mergedThread = getMergedThread(apiThread, realmThread)
                    copyToRealm(mergedThread, UpdatePolicy.ALL)
                }
                updateFolder(folder, apiThreads)
            }

            // Delete outdated data
            Log.d("API", "Threads: Delete outdated data")
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

    private fun mergeMessages(realmMessages: List<Message>?, apiMessages: List<Message>): List<Message> {

        // Get outdated data
        Log.d("API", "Messages: Get outdated data")
        // val deletableMessages = MailboxContentController.getDeletableMessages(messagesFromApi)
        val deletableMessages = realmMessages?.filter { realmMessage ->
            apiMessages.none { apiMessage -> apiMessage.uid == realmMessage.uid }
        } ?: emptyList()

        RealmController.mailboxContent.writeBlocking {
            // Save new data
            Log.d("API", "Messages: Save new data")
            apiMessages.forEach { apiMessage ->
                if (!apiMessage.isManaged()) copyToRealm(apiMessage, UpdatePolicy.ALL)
            }

            // Delete outdated data
            Log.d("API", "Messages: Delete outdated data")
            deleteMessages(deletableMessages)
        }

        return apiMessages
    }

    private fun MutableRealm.deleteThreads(deletableThreads: List<Thread>) {
        deletableThreads.forEach { deleteLatestThread(it.uid) }
    }

    private fun MutableRealm.deleteFolders(deletableFolders: List<Folder>) {
        deletableFolders.forEach { deleteLatestFolder(it.id) }
    }

    /**
     * Utils
     */
    private suspend fun <T> MutableStateFlow<T?>.forceRefresh() {
        value = null
        delay(1L)
    }

    private fun <T> RealmList<T>.setRealmListValues(values: RealmList<T>) {
        if (isNotEmpty()) clear()
        addAll(values)
    }
}
