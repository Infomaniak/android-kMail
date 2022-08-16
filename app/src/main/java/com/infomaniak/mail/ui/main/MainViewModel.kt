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
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.api.ApiRepository.OFFSET_FIRST_PAGE
import com.infomaniak.mail.data.cache.RealmController
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxInfos.MailboxController
import com.infomaniak.mail.data.cache.userInfos.AddressBookController
import com.infomaniak.mail.data.cache.userInfos.ContactController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.drafts.Draft
import com.infomaniak.mail.data.models.drafts.Draft.DraftAction
import com.infomaniak.mail.data.models.drafts.DraftSaveResult
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.data.models.user.UserPreferences.ThreadMode
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ModelsUtils.formatFoldersListWithAllChildren
import com.infomaniak.mail.utils.toDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {

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
            loadThreads(mailbox.uuid)
        }
    }

    suspend fun loadCurrentMailbox() {
        Log.i(TAG, "loadCurrentMailbox")
        val mailboxes = loadMailboxes()
        computeMailboxToSelect(mailboxes)?.let { mailbox ->
            selectMailbox(mailbox)
            val folders = loadFolders(mailbox)
            computeFolderToSelect(folders)?.let { folder ->
                selectFolder(folder.id)
                loadThreads(mailbox.uuid)
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
        loadThreads(mailboxUuid)
    }

    fun openThread(thread: Thread) = viewModelScope.launch(Dispatchers.IO) {
        selectThread(thread)
        ThreadController.markAsSeen(thread)
        loadMessages(thread)
    }

    fun forceRefreshThreads(filter: ThreadFilter) = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "forceRefreshThreads")
        val mailboxObjectId = currentMailboxObjectId.value ?: return@launch
        val mailboxUuid = MailboxController.getMailboxSync(mailboxObjectId)?.uuid ?: return@launch
        currentOffset = OFFSET_FIRST_PAGE
        isDownloadingChanges = true
        loadThreads(mailboxUuid, currentOffset, filter)
    }

    fun loadMoreThreads(mailboxUuid: String, offset: Int, filter: ThreadFilter) = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "loadMoreThreads: $offset")
        isDownloadingChanges = true
        loadThreads(mailboxUuid, offset, filter)
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
        offset: Int = OFFSET_FIRST_PAGE,
        filter: ThreadFilter = ThreadFilter.ALL,
    ) = viewModelScope.launch(Dispatchers.IO) {

        val folder = currentFolderId.value?.let(FolderController::getFolderSync) ?: return@launch
        val realmThreads = folder.threads.filter {
            when (filter) {
                ThreadFilter.SEEN -> it.unseenMessagesCount == 0
                ThreadFilter.UNSEEN -> it.unseenMessagesCount > 0
                ThreadFilter.STARRED -> it.isFavorite
                ThreadFilter.ATTACHMENTS -> it.hasAttachments
                else -> true
            }
        }

        val isInternetAvailable = true // TODO
        if (folder.isDraftFolder == true && isInternetAvailable) {
            realmThreads
                .flatMap { it.messages }
                .filter { it.isDraft }
                .mapNotNull { it.draftUuid?.let(DraftController::getDraftSync) }
                .filter { it.isOffline || it.isModifiedOffline }
                .forEach { draft -> saveOfflineDraftToApi(draft) }
        }

        canContinueToPaginate = ThreadController.upsertApiData(realmThreads, mailboxUuid, folder, offset, filter)
    }

    private fun saveOfflineDraftToApi(draft: Draft) {
        val draftMailboxUuid = MailboxController.getMailboxesSync(AccountUtils.currentUserId)
            .find { it.email == draft.from.firstOrNull()?.email }
            ?.uuid ?: return
        if (draft.isLastUpdateOnline(draftMailboxUuid)) return

        val draftForApi = draft.updateForApi(DraftAction.SAVE)
        saveDraft(draftForApi, draftMailboxUuid).data?.let {
            fetchDraft("/api/mail/${draftMailboxUuid}/draft/${it.uuid}", it.uid)
        }
    }

    private fun Draft.isLastUpdateOnline(mailboxUuid: String): Boolean {
        if (isOffline) return false
        if (!isModifiedOffline) return true

        val apiDraft = ApiRepository.getDraft(mailboxUuid, uuid).data

        return apiDraft?.date?.toDate()?.after(date?.toDate()) == true
    }

//    fun sendDraft(draft: Draft, mailboxUuid: String): ApiResponse<Boolean> {
//        val apiResponse = ApiRepository.sendDraft(mailboxUuid, draft)
//        if (apiResponse.data == true) {
//            DraftController.removeDraft(draft.uuid, draft.messageUid)
//        } else {
//            DraftController.updateDraft(draft.uuid) { it.apply { action = DraftAction.SAVE } }
//            saveDraft(draft, mailboxUuid)
//        }
//
//        return apiResponse
//    }
//
//    fun saveDraft(draft: Draft, mailboxUuid: String): ApiResponse<DraftSaveResult> {
//        val apiResponse = ApiRepository.saveDraft(mailboxUuid, draft)
//        apiResponse.data?.let { apiData ->
//            DraftController.removeDraft(draft.uuid, draft.messageUid)
//            val newDraft = ApiRepository.getDraft(mailboxUuid, apiData.uuid).data
//            newDraft?.apply {
//                isOffline = false
//                isModifiedOffline = false
//                messageUid = apiData.uid
//                // attachments = apiData.attachments // TODO? Not sure.
//                DraftController.manageDraftAutoSave(newDraft, false)
//            }
//        } ?: DraftController.manageDraftAutoSave(draft, true)
//
//        return apiResponse
//    }
//
//    fun setDraftSignature(draft: Draft, draftAction: DraftAction? = null): Draft {
//        val mailbox = currentMailboxObjectId.value?.let(MailboxController::getMailboxSync) ?: return draft
//        val apiResponse = ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailbox)
//
//        return DraftController.updateDraft(draft.uuid) { draftToUpdate ->
//            draftToUpdate.apply {
//                identityId = apiResponse.data?.defaultSignatureId
//                draftAction?.let { action = it }
//            }
//        } ?: draft
//    }

    private fun loadMessages(thread: Thread) {
        val apiMessages = fetchMessages(thread)

        MessageController.upsertApiData(apiMessages, thread)
    }

    private fun fetchMessages(thread: Thread): List<Message> {
        return thread.messages.map { realmMessage ->
            if (realmMessage.fullyDownloaded) {
                realmMessage
            } else {
                // TODO: Handle if this API call fails
                ApiRepository.getMessage(realmMessage.resource).data?.also { completedMessage ->
                    completedMessage.apply {
                        initLocalValues() // TODO: Remove this when we have EmbeddedObjects
                        body?.initLocalValues(uid) // TODO: Remove this when we have EmbeddedObjects
                        // TODO: Remove this `forEachIndexed` when we have EmbeddedObjects
                        @Suppress("SAFE_CALL_WILL_CHANGE_NULLABILITY", "UNNECESSARY_SAFE_CALL")
                        attachments?.forEachIndexed { index, attachment -> attachment.initLocalValues(index, uid) }

                        if (isDraft) fetchDraft(draftResource, uid)
                        fullyDownloaded = true
                    }
                }.let { apiMessage ->
                    apiMessage ?: realmMessage
                }
            }
        }
    }

    fun fetchDraft(draftResource: String, messageUid: String): Draft? {
        return ApiRepository.getDraft(draftResource).data?.apply {
            initLocalValues(messageUid)
            // TODO: Remove this `forEachIndexed` when we have EmbeddedObjects
            attachments.forEachIndexed { index, attachment -> attachment.initLocalValues(index, messageUid) }
            DraftController.upsertDraft(this)
        }
    }

    companion object {
        private val TAG = "MainViewModel"
        private val DEFAULT_SELECTED_FOLDER = FolderRole.INBOX

        val currentMailboxObjectId = MutableLiveData<String?>()
        val currentFolderId = MutableLiveData<String?>()
        val currentThreadUid = MutableLiveData<String?>()
        val currentMessageUid = MutableLiveData<String?>()

        fun sendDraft(draft: Draft, mailboxUuid: String): ApiResponse<Boolean> {
            val apiResponse = ApiRepository.sendDraft(mailboxUuid, draft)
            if (apiResponse.data == true) {
                DraftController.removeDraft(draft.uuid, draft.messageUid)
            } else {
                draft.updateForApi(DraftAction.SAVE)
                saveDraft(draft, mailboxUuid)
            }

            return apiResponse
        }

        fun saveDraft(draft: Draft, mailboxUuid: String): ApiResponse<DraftSaveResult> {
            val apiResponse = ApiRepository.saveDraft(mailboxUuid, draft)
            apiResponse.data?.let { apiData ->
                DraftController.removeDraft(draft.uuid, draft.messageUid)
                val newDraft = ApiRepository.getDraft(mailboxUuid, apiData.uuid).data
                newDraft?.apply {
                    isOffline = false
                    isModifiedOffline = false
                    messageUid = apiData.uid
                    // attachments = apiData.attachments // TODO? Not sure.
                    DraftController.manageDraftAutoSave(newDraft, false)
                }
            } ?: DraftController.manageDraftAutoSave(draft, true)

            return apiResponse
        }

        fun Draft.updateForApi(draftAction: DraftAction? = null): Draft {

            fun updateDraft(draftToUpdate: Draft, defaultSignatureId: Int?, draftAction: DraftAction?) = draftToUpdate.apply {
                identityId = defaultSignatureId
                draftAction?.let { action = it }
            }

            val mailbox = currentMailboxObjectId.value?.let(MailboxController::getMailboxSync) ?: return this
            val defaultSignatureId = ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailbox).data?.defaultSignatureId

            return DraftController.updateDraft(uuid) { draftToUpdate ->
                updateDraft(draftToUpdate, defaultSignatureId, draftAction)
            } ?: updateDraft(this, defaultSignatureId, draftAction)
        }
    }
}
