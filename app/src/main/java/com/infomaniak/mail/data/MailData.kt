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
import com.infomaniak.mail.data.api.MailApi
import com.infomaniak.mail.data.cache.MailRealm
import com.infomaniak.mail.data.cache.MailboxContentController
import com.infomaniak.mail.data.cache.MailboxContentController.getLatestFolder
import com.infomaniak.mail.data.cache.MailboxContentController.getLatestMessage
import com.infomaniak.mail.data.cache.MailboxInfoController
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.AccountUtils
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.toRealmList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object MailData {

    private val DEFAULT_FOLDER_ROLE = FolderRole.INBOX

    private val mutableMailboxesFlow: MutableStateFlow<List<Mailbox>?> = MutableStateFlow(null)
    private val mutableFoldersFlow: MutableStateFlow<List<Folder>?> = MutableStateFlow(null)
    private val mutableThreadsFlow: MutableStateFlow<List<Thread>?> = MutableStateFlow(null)
    private val mutableMessagesFlow: MutableStateFlow<List<Message>?> = MutableStateFlow(null)
    val mailboxesFlow = mutableMailboxesFlow.asStateFlow()
    val foldersFlow = mutableFoldersFlow.asStateFlow()
    val threadsFlow = mutableThreadsFlow.asStateFlow()
    val messagesFlow = mutableMessagesFlow.asStateFlow()

    private val mutableCurrentMailboxFlow: MutableStateFlow<Mailbox?> = MutableStateFlow(null)
    private val mutableCurrentFolderFlow: MutableStateFlow<Folder?> = MutableStateFlow(null)
    private val mutableCurrentThreadFlow: MutableStateFlow<Thread?> = MutableStateFlow(null)
    private val mutableCurrentMessageFlow: MutableStateFlow<Message?> = MutableStateFlow(null)
    val currentMailboxFlow = mutableCurrentMailboxFlow.asStateFlow()
    val currentFolderFlow = mutableCurrentFolderFlow.asStateFlow()
    val currentThreadFlow = mutableCurrentThreadFlow.asStateFlow()
    val currentMessageFlow = mutableCurrentMessageFlow.asStateFlow()

    ///////////////
    // LOAD MAIL //
    ///////////////

    fun close() {
        MailRealm.close()

        closeFlows()
        closeCurrentFlows()
    }

    private fun closeFlows() {
        mutableMessagesFlow.value = null
        mutableThreadsFlow.value = null
        mutableFoldersFlow.value = null
        mutableMailboxesFlow.value = null
    }

    private fun closeCurrentFlows() {
        mutableCurrentMessageFlow.value = null
        mutableCurrentThreadFlow.value = null
        mutableCurrentFolderFlow.value = null
        mutableCurrentMailboxFlow.value = null
    }

    fun loadMailData() {
        readRealmData()
    }

    fun loadMailboxes() {
        readMailboxes {
            fetchMailboxes()
        }
    }

    fun loadMessages(thread: Thread) {
        readMessages(thread)
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

    private fun handleMailboxes(mailboxes: List<Mailbox>, completion: (mailbox: Mailbox) -> Unit) {
        val mailbox = with(mailboxes) {
            find { it.mailboxId == AccountUtils.currentMailboxId }
            // ?: find { it.email == "kevin.boulongne@ik.me" } // TODO: Remove this, it's for dev only
                ?: find { it.email == "kevin.boulongne@infomaniak.com" } // TODO: Remove this, it's for dev only
                ?: firstOrNull()
                ?: return
        }

        selectMailbox(mailbox)
        completion(mailbox)
    }

    private fun handleFolders(folders: List<Folder>, completion: (folder: Folder) -> Unit) {
        val folder = with(folders) {
            find { it.role == DEFAULT_FOLDER_ROLE }
                ?: firstOrNull()
                ?: return
        }

        selectFolder(folder)
        completion(folder)
    }

    ////////////////
    // READ REALM //
    ////////////////

    private fun readRealmData() {
        readMailboxes { realmMailboxes ->
            if (realmMailboxes.isEmpty()) {
                fetchApiData()
            } else {
                handleMailboxes(realmMailboxes) {
                    readFolders()
                }
            }
        }
    }

    private fun readMailboxes(completion: (List<Mailbox>) -> Unit) {
        var job: Job? = null
        job = CoroutineScope(Dispatchers.IO).launch {
            MailRealm.readMailboxes().collect {

                val realmMailboxes = it.list.toList()
                mutableMailboxesFlow.value = realmMailboxes

                completion(realmMailboxes)

                job?.cancel()
            }
        }
    }

    private fun readFolders() {
        var job: Job? = null
        job = CoroutineScope(Dispatchers.IO).launch {
            MailRealm.readFolders().collectLatest {

                val realmFolders = it.list.toList()
                mutableFoldersFlow.value = realmFolders

                if (realmFolders.isEmpty()) {
                    fetchApiData()
                } else {
                    handleFolders(realmFolders) { folder ->
                        readThreads(folder)
                    }
                }

                job?.cancel()
            }
        }
    }

    private fun readThreads(folder: Folder) {
        val realmThreads = MailRealm.readThreads(folder)
        mutableThreadsFlow.value = realmThreads

        fetchApiData()
    }

    private fun readMessages(thread: Thread) {
        val realmMessages = MailRealm.readMessages(thread)
        mutableMessagesFlow.value = realmMessages

        fetchMessages(thread)
    }

    fun deleteDraft(message: Message) {
        if (ApiRepository.deleteDraft(message.draftResource).isSuccess()) MailboxContentController.deleteMessage(message.uid)
    }

    ///////////////
    // FETCH API //
    ///////////////

    private fun fetchApiData() {
        fetchMailboxes { mergedMailboxes ->
            handleMailboxes(mergedMailboxes) { mailbox ->
                fetchFolders(mailbox)
            }
        }
    }

    private fun fetchMailboxes(completion: ((List<Mailbox>) -> Unit)? = null) {
        val realmMailboxes = mutableMailboxesFlow.value
        val apiMailboxes = MailApi.fetchMailboxes()
        val mergedMailboxes = mergeMailboxes(realmMailboxes, apiMailboxes)

        mutableMailboxesFlow.value = mergedMailboxes

        completion?.invoke(mergedMailboxes)
    }

    private fun fetchFolders(mailbox: Mailbox) {
        val realmFolders = mutableFoldersFlow.value
        val apiFolders = MailApi.fetchFolders(mailbox)
        val mergedFolders = mergeFolders(realmFolders, apiFolders)

        mutableFoldersFlow.value = mergedFolders

        handleFolders(mergedFolders) { folder ->
            fetchThreads(folder, mailbox)
        }
    }

    fun fetchThreads(folder: Folder, mailbox: Mailbox) {
        CoroutineScope(Dispatchers.IO).launch {
            val realmThreads = mutableThreadsFlow.value
            val apiThreads = MailApi.fetchThreads(folder, mailbox.uuid)
            val mergedThreads = mergeThreads(realmThreads, apiThreads, folder)

            mutableThreadsFlow.value = mergedThreads
        }
    }

    private fun fetchMessages(thread: Thread) {
        CoroutineScope(Dispatchers.IO).launch {
            val realmMessages = mutableMessagesFlow.value
            val apiMessages = MailApi.fetchMessages(thread)
            val mergedMessages = mergeMessages(realmMessages, apiMessages)

            mutableMessagesFlow.value = mergedMessages
        }
    }

    private fun mergeMailboxes(realmMailboxes: List<Mailbox>?, apiMailboxes: List<Mailbox>?): List<Mailbox> {
        if (apiMailboxes == null) return realmMailboxes ?: emptyList()

        // Get outdated data
        Log.d("API", "Mailboxes: Get outdated data")
        // val deletableMailboxes = MailboxInfoController.getDeletableMailboxes(apiMailboxes)
        val deletableMailboxes = realmMailboxes?.filter { realmMailbox ->
            !apiMailboxes.any { apiMailbox -> apiMailbox.mailboxId == realmMailbox.mailboxId }
        } ?: emptyList()

        // Save new data
        Log.d("API", "Mailboxes: Save new data")
        MailboxInfoController.upsertMailboxes(apiMailboxes)

        // Delete outdated data
        Log.d("API", "Mailboxes: Delete outdated data")
        val isCurrentMailboxDeleted = deletableMailboxes.any { it.mailboxId == AccountUtils.currentMailboxId }
        if (isCurrentMailboxDeleted) {
            MailRealm.closeMailboxContent()
            AccountUtils.currentMailboxId = AppSettings.DEFAULT_ID
        }
        MailboxInfoController.deleteMailboxes(deletableMailboxes)
        deletableMailboxes.forEach { Realm.deleteRealm(MailRealm.getMailboxConfiguration(it.mailboxId)) }

        return if (isCurrentMailboxDeleted) {
            AccountUtils.reloadApp()
            emptyList()
        } else {
            apiMailboxes
        }
    }

    private fun mergeFolders(realmFolders: List<Folder>?, apiFolders: List<Folder>?): List<Folder> {
        if (apiFolders == null) return realmFolders ?: emptyList()

        // Get outdated data
        Log.d("API", "Folders: Get outdated data")
        // val deletableFolders = MailboxContentController.getDeletableFolders(foldersFromApi)
        val deletableFolders = realmFolders?.filter { realmFolder ->
            !apiFolders.any { apiFolder -> apiFolder.id == realmFolder.id }
        } ?: emptyList()
        val possiblyDeletableThreads = deletableFolders.flatMap { it.threads }
        val deletableMessages = possiblyDeletableThreads.flatMap { it.messages }.filter { message ->
            deletableFolders.any { folder -> folder.id == message.folderId }
        }
        val deletableThreads = possiblyDeletableThreads.filter { thread ->
            thread.messages.all { message -> deletableMessages.any { it.uid == message.uid } }
        }

        // Save new data
        Log.d("API", "Folders: Save new data")
        MailRealm.mailboxContent.writeBlocking {
            apiFolders.forEach { apiFolder ->
                realmFolders?.find { it.id == apiFolder.id }?.threads
                    ?.mapNotNull(::findLatest)
                    ?.let { apiFolder.threads = it.toRealmList() }
                copyToRealm(apiFolder, UpdatePolicy.ALL)
            }
        }

        // Delete outdated data
        Log.d("API", "Folders: Delete outdated data")
        MailboxContentController.deleteMessages(deletableMessages)
        MailboxContentController.deleteThreads(deletableThreads)
        MailboxContentController.deleteFolders(deletableFolders)

        return apiFolders
    }

    private fun mergeThreads(realmThreads: List<Thread>?, apiThreads: List<Thread>?, folder: Folder): List<Thread> {
        if (apiThreads == null) return realmThreads ?: emptyList()

        // Get outdated data
        Log.d("API", "Threads: Get outdated data")
        // val deletableThreads = MailboxContentController.getDeletableThreads(threadsFromApi)
        val deletableThreads = realmThreads?.filter { fromRealm ->
            !apiThreads.any { fromApi -> fromApi.uid == fromRealm.uid }
        } ?: emptyList()
        val deletableMessages = deletableThreads.flatMap { thread -> thread.messages.filter { it.folderId == folder.id } }

        // Save new data
        Log.d("API", "Threads: Save new data")
        MailRealm.mailboxContent.writeBlocking {
            apiThreads.forEach { apiThread ->
                realmThreads?.find { it.uid == apiThread.uid }?.let { realmThread ->
                    apiThread.messages.forEach { apiMessage ->
                        realmThread.messages.find { it.uid == apiMessage.uid }
                            ?.let { getLatestMessage(it.uid) }
                            ?.let { realmMessage ->
                                apiMessage.apply {
                                    fullyDownloaded = realmMessage.fullyDownloaded
                                    body = realmMessage.body
                                    attachments = realmMessage.attachments
                                }
                                copyToRealm(apiMessage, UpdatePolicy.ALL)
                            }
                    }
                    copyToRealm(apiThread, UpdatePolicy.ALL)
                }
            }

            val liveFolder = getLatestFolder(folder.id) ?: folder
            liveFolder.threads = apiThreads.toRealmList()
            copyToRealm(liveFolder, UpdatePolicy.ALL)
        }

        // Delete outdated data
        Log.d("API", "Threads: Delete outdated data")
        MailboxContentController.deleteMessages(deletableMessages)
        MailboxContentController.deleteThreads(deletableThreads)

        return apiThreads
    }

    private fun mergeMessages(realmMessages: List<Message>?, apiMessages: List<Pair<Message, Boolean>>): List<Message> {

        // Get outdated data
        Log.d("API", "Messages: Get outdated data")
        // val deletableMessages = MailboxContentController.getDeletableMessages(messagesFromApi)
        val deletableMessages = realmMessages?.filter { realmMessage ->
            !apiMessages.any { (apiMessage, _) -> apiMessage.uid == realmMessage.uid }
        } ?: emptyList()

        // Save new data
        Log.d("API", "Messages: Save new data")
        MailRealm.mailboxContent.writeBlocking {
            apiMessages.forEach { (apiMessage, isFromRealm) ->
                if (!isFromRealm) copyToRealm(apiMessage, UpdatePolicy.ALL)
            }
        }

        // Delete outdated data
        Log.d("API", "Messages: Delete outdated data")
        MailboxContentController.deleteMessages(deletableMessages)

        return apiMessages.map { (apiMessage, _) -> apiMessage }
    }
}
