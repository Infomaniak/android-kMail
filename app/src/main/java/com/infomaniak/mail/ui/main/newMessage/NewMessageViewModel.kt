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
package com.infomaniak.mail.ui.main.newMessage

import androidx.lifecycle.*
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.cache.userInfo.ContactController
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.draft.Priority
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivity.EditorAction
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NewMessageViewModel : ViewModel() {

    var mailFrom = ""
    var mailTo = emptyList<UiContact>()
    var mailCc = emptyList<UiContact>()
    var mailBcc = emptyList<UiContact>()
    var mailSubject = ""
    var mailBody = ""
    var draftHasBeenModified = false

    private var autoSaveJob: Job? = null

    var isAutocompletionOpened = false
    var areAdvancedFieldsOpened = false
    var isEditorExpanded = false

    // Boolean : for toggleable actions, false if the formatting has been removed and true if the formatting has been applied
    val editorAction = SingleLiveEvent<Pair<EditorAction, Boolean?>>()

    val currentDraftUuid = MutableLiveData<String?>()

    fun setupDraft(draftUuid: String?) = viewModelScope.launch(Dispatchers.IO) {
        val uuid = RealmDatabase.mailboxContent().writeBlocking {
            return@writeBlocking if (draftUuid == null) {
                createDraft()
            } else {
                updateLiveData(draftUuid)
                draftUuid
            }
        }

        currentDraftUuid.postValue(uuid)
    }

    private fun MutableRealm.createDraft(): String {
        return Draft().initLocalValues(priority = Priority.NORMAL)
            .also { DraftController.upsertDraft(it, this) }
            .uuid
    }

    private fun MutableRealm.updateLiveData(uuid: String) {
        DraftController.getDraft(uuid, this)?.let { draft ->
            mailTo = draft.to.toUiContacts()
            mailCc = draft.cc.toUiContacts()
            mailBcc = draft.bcc.toUiContacts()
            mailSubject = draft.subject
            mailBody = draft.body
        }
    }

    fun getContacts(): LiveData<List<UiContact>> = liveData(Dispatchers.IO) {
        emit(mutableListOf<UiContact>().apply {
            ContactController.getContacts().forEach { contact ->
                contact.emails.forEach { email ->
                    add(UiContact(email, contact.name))
                }
            }
        })
    }

    fun saveMail(completion: () -> Unit) = viewModelScope.launch(Dispatchers.IO) {

        val mailboxObjectId = MainViewModel.currentMailboxObjectId.value ?: run {
            completion()
            return@launch
        }
        val mailbox = MailboxController.getMailbox(mailboxObjectId) ?: run {
            completion()
            return@launch
        }

        RealmDatabase.mailboxContent().writeBlocking {
            val draftUuid = currentDraftUuid.value ?: return@writeBlocking

            saveDraft(draftUuid, this)

            val draft = DraftController.getDraft(draftUuid, this) ?: return@writeBlocking
            if (!draft.hasBeenModified) return@writeBlocking

            val signature = ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailboxName)
            draft.identityId = signature.data?.defaultSignatureId
            draft.action = DraftAction.SAVE

            ApiRepository.saveDraft(mailbox.uuid, draft)
        }

        completion()
    }

    fun sendMail(completion: (isSuccess: Boolean) -> Unit) = viewModelScope.launch(Dispatchers.IO) {

        val mailboxObjectId = MainViewModel.currentMailboxObjectId.value ?: run {
            completion(false)
            return@launch
        }
        val mailbox = MailboxController.getMailbox(mailboxObjectId) ?: run {
            completion(false)
            return@launch
        }

        var isSuccess = false

        RealmDatabase.mailboxContent().writeBlocking {
            val draftUuid = currentDraftUuid.value ?: return@writeBlocking
            val draft = DraftController.getDraft(draftUuid, this) ?: return@writeBlocking
            if (!draft.hasBeenModified || draft.to.isEmpty()) return@writeBlocking

            val signature = ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailboxName)
            draft.identityId = signature.data?.defaultSignatureId
            draft.action = DraftAction.SEND

            isSuccess = ApiRepository.sendDraft(mailbox.uuid, draft).isSuccess()
        }

        completion(isSuccess)
    }

    fun deleteDraft() = viewModelScope.launch(Dispatchers.IO) {
        val draftUuid = currentDraftUuid.value ?: return@launch
        RealmDatabase.mailboxContent().writeBlocking {
            val draft = DraftController.getDraft(draftUuid, this) ?: return@writeBlocking
            delete(draft)
        }
    }

    fun updateMailFrom(email: String) {
        if (email != mailFrom) {
            mailFrom = email
            autoSaveDraft()
        }
    }

    fun updateMailTo(to: List<UiContact>) {
        if (to != mailTo) {
            mailTo = to
            autoSaveDraft()
        }
    }

    fun updateMailCc(cc: List<UiContact>) {
        if (cc != mailCc) {
            mailCc = cc
            autoSaveDraft()
        }
    }

    fun updateMailBcc(bcc: List<UiContact>) {
        if (bcc != mailBcc) {
            mailBcc = bcc
            autoSaveDraft()
        }
    }

    fun updateMailSubject(subject: String) {
        if (subject != mailSubject) {
            mailSubject = subject
            autoSaveDraft()
        }
    }

    fun updateMailBody(body: String) {
        if (body != mailBody) {
            mailBody = body
            autoSaveDraft()
        }
    }

    private fun autoSaveDraft() {
        draftHasBeenModified = true
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(DELAY_BEFORE_AUTO_SAVING_DRAFT)
            val draftUuid = currentDraftUuid.value ?: return@launch
            saveDraft(draftUuid)
        }
    }

    private fun saveDraft(draftUuid: String, realm: MutableRealm? = null) {
        DraftController.updateDraft(draftUuid, realm) {
            it.from = realmListOf(Recipient().apply { this.email = mailFrom })
            it.to = mailTo.toRealmRecipients()
            it.cc = mailCc.toRealmRecipients()
            it.bcc = mailBcc.toRealmRecipients()
            it.subject = mailSubject
            it.body = mailBody
            it.hasBeenModified = draftHasBeenModified
        }
    }

    override fun onCleared() {
        autoSaveJob?.cancel()
        super.onCleared()
    }

    private fun RealmList<Recipient>.toUiContacts(): MutableList<UiContact> {
        return map { UiContact(it.email, it.getNameOrEmail()) }.toMutableList()
    }

    private fun List<UiContact>.toRealmRecipients(): RealmList<Recipient> {
        return if (isEmpty()) realmListOf() else map {
            Recipient().apply {
                email = it.email
                name = it.name ?: ""
            }
        }.toRealmList()
    }

    private companion object {
        const val DELAY_BEFORE_AUTO_SAVING_DRAFT = 3_000L
    }
}
