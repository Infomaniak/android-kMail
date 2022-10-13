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
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxInfos.MailboxController
import com.infomaniak.mail.data.cache.userInfos.ContactController
import com.infomaniak.mail.data.models.Draft
import com.infomaniak.mail.data.models.MessagePriority
import com.infomaniak.mail.data.models.MessagePriority.getPriority
import com.infomaniak.mail.data.models.Recipient
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivity.EditorAction
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NewMessageViewModel : ViewModel() {

    val mailTo = MutableLiveData<List<UiContact>?>(emptyList())
    val mailCc = MutableLiveData<List<UiContact>?>(emptyList())
    val mailBcc = MutableLiveData<List<UiContact>?>(emptyList())

    var areAdvancedFieldsOpened = false
    var isEditorExpanded = false
    val editorAction = SingleLiveEvent<EditorAction>()

    val currentDraftUuid = MutableLiveData<String?>()

    fun setupDraft(draftUuid: String?) = viewModelScope.launch(Dispatchers.IO) {
        val uuid = draftUuid ?: run {
            return@run Draft()
                .apply {
                    initLocalValues()
                    priority = MessagePriority.Priority.NORMAL.getPriority()
                }
                .also { DraftController.upsertDraft(it) } // Don't try to write it with `.also(xx::yy)`, it will crash.
                .uuid
        }

        DraftController.getDraft(uuid)?.let { draft ->
            mailTo.postValue(draft.to.toUiContacts())
            mailCc.postValue(draft.cc.toUiContacts())
            mailBcc.postValue(draft.bcc.toUiContacts())
        }

        currentDraftUuid.postValue(uuid)
    }

    fun getDraft(uuid: String): LiveData<Draft?> = liveData(Dispatchers.IO) {
        emit(DraftController.getDraft(uuid))
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

    fun saveMail() = viewModelScope.launch(Dispatchers.IO) {

        val mailboxObjectId = MainViewModel.currentMailboxObjectId.value ?: return@launch
        val mailbox = MailboxController.getMailbox(mailboxObjectId) ?: return@launch

        val signature = ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailbox)

        val draftUuid = currentDraftUuid.value ?: return@launch
        DraftController.updateDraft(draftUuid) {
            it.action = "save"
            it.identityId = signature.data?.defaultSignatureId
        }
        val draft = DraftController.getDraft(draftUuid) ?: return@launch

        ApiRepository.saveDraft(mailbox.uuid, draft)
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

        val signature = ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailbox)

        val draftUuid = currentDraftUuid.value ?: return@launch
        DraftController.updateDraft(draftUuid) {
            it.action = "send"
            it.identityId = signature.data?.defaultSignatureId
        }
        val draft = DraftController.getDraft(draftUuid) ?: run {
            completion(false)
            return@launch
        }

        if (draft.to.isEmpty()) {
            completion(false)
            return@launch
        }

        val isSuccess = ApiRepository.sendDraft(mailbox.uuid, draft).isSuccess()
        completion(isSuccess)
    }

    fun updateDraftFrom(email: String) = viewModelScope.launch(Dispatchers.IO) {
        val draftUuid = currentDraftUuid.value ?: return@launch
        DraftController.updateDraft(draftUuid) {
            it.from = realmListOf(Recipient().apply { this.email = email })
        }
    }

    fun updateDraftTo(to: List<UiContact>) = viewModelScope.launch(Dispatchers.IO) {
        val draftUuid = currentDraftUuid.value ?: return@launch
        DraftController.updateDraft(draftUuid) {
            it.to = to.toRealmRecipients()
        }
    }

    fun updateDraftCc(cc: List<UiContact>) = viewModelScope.launch(Dispatchers.IO) {
        val draftUuid = currentDraftUuid.value ?: return@launch
        DraftController.updateDraft(draftUuid) {
            it.cc = cc.toRealmRecipients()
        }
    }

    fun updateDraftBcc(bcc: List<UiContact>) = viewModelScope.launch(Dispatchers.IO) {
        val draftUuid = currentDraftUuid.value ?: return@launch
        DraftController.updateDraft(draftUuid) {
            it.bcc = bcc.toRealmRecipients()
        }
    }

    fun updateDraftSubject(subject: String) = viewModelScope.launch(Dispatchers.IO) {
        val draftUuid = currentDraftUuid.value ?: return@launch
        DraftController.updateDraft(draftUuid) {
            it.subject = subject
        }
    }

    fun updateDraftBody(body: String) = viewModelScope.launch(Dispatchers.IO) {
        val draftUuid = currentDraftUuid.value ?: return@launch
        DraftController.updateDraft(draftUuid) {
            it.body = body
        }
    }

    private fun RealmList<Recipient>.toUiContacts(): List<UiContact> = map { UiContact(it.email, it.getNameOrEmail()) }

    private fun List<UiContact>.toRealmRecipients(): RealmList<Recipient> {
        return if (isEmpty()) realmListOf() else map {
            Recipient().apply {
                email = it.email
                name = it.name ?: ""
            }
        }.toRealmList()
    }
}
