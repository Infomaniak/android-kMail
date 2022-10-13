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
import kotlinx.coroutines.launch

class NewMessageViewModel : ViewModel() {

    val mailTo = MutableLiveData<List<UiContact>?>(emptyList())
    val mailCc = MutableLiveData<List<UiContact>?>(emptyList())
    val mailBcc = MutableLiveData<List<UiContact>?>(emptyList())

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
            mailTo.postValue(draft.to.toUiContacts())
            mailCc.postValue(draft.cc.toUiContacts())
            mailBcc.postValue(draft.bcc.toUiContacts())
        }
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

        fun makeApiCall(): Draft? {
            val mailboxObjectId = MainViewModel.currentMailboxObjectId.value ?: return null
            val mailbox = MailboxController.getMailbox(mailboxObjectId) ?: return null

            return RealmDatabase.mailboxContent().writeBlocking {
                val draftUuid = currentDraftUuid.value ?: return@writeBlocking null
                val draft = DraftController.getDraft(draftUuid, this) ?: return@writeBlocking null
                if (!draft.hasBeenModified) return@writeBlocking draft

                val signature = ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailboxName)
                draft.identityId = signature.data?.defaultSignatureId
                draft.action = DraftAction.SAVE

                ApiRepository.saveDraft(mailbox.uuid, draft)

                return@writeBlocking draft
            }
        }

        makeApiCall()?.let { draft ->
            RealmDatabase.mailboxContent().writeBlocking { findLatest(draft)?.let(::delete) }
        }
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

        RealmDatabase.mailboxContent().writeBlocking {
            val draftUuid = currentDraftUuid.value ?: return@writeBlocking
            val draft = DraftController.getDraft(draftUuid, this) ?: return@writeBlocking
            if (!draft.hasBeenModified || draft.to.isEmpty()) return@writeBlocking

            val signature = ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailboxName)
            draft.identityId = signature.data?.defaultSignatureId
            draft.action = DraftAction.SEND

            val isSuccess = ApiRepository.sendDraft(mailbox.uuid, draft).isSuccess()
            if (isSuccess) delete(draft)

            completion(isSuccess)
        }

        completion(false)
    }

    fun updateDraftFrom(email: String, isDefaultEmail: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {
        val draftUuid = currentDraftUuid.value ?: return@launch
        DraftController.updateDraft(draftUuid) {
            it.from = realmListOf(Recipient().apply { this.email = email })
            it.hasBeenModified = !isDefaultEmail
        }
    }

    fun updateDraftTo(to: List<UiContact>) = viewModelScope.launch(Dispatchers.IO) {
        val draftUuid = currentDraftUuid.value ?: return@launch
        DraftController.updateDraft(draftUuid) {
            it.to = to.toRealmRecipients()
            it.hasBeenModified = true
        }
    }

    fun updateDraftCc(cc: List<UiContact>) = viewModelScope.launch(Dispatchers.IO) {
        val draftUuid = currentDraftUuid.value ?: return@launch
        DraftController.updateDraft(draftUuid) {
            it.cc = cc.toRealmRecipients()
            it.hasBeenModified = true
        }
    }

    fun updateDraftBcc(bcc: List<UiContact>) = viewModelScope.launch(Dispatchers.IO) {
        val draftUuid = currentDraftUuid.value ?: return@launch
        DraftController.updateDraft(draftUuid) {
            it.bcc = bcc.toRealmRecipients()
            it.hasBeenModified = true
        }
    }

    fun updateDraftSubject(subject: String) = viewModelScope.launch(Dispatchers.IO) {
        val draftUuid = currentDraftUuid.value ?: return@launch
        DraftController.updateDraft(draftUuid) {
            it.subject = subject
            it.hasBeenModified = true
        }
    }

    fun updateDraftBody(body: String) = viewModelScope.launch(Dispatchers.IO) {
        val draftUuid = currentDraftUuid.value ?: return@launch
        DraftController.updateDraft(draftUuid) {
            it.body = body
            it.hasBeenModified = true
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
