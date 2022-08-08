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
package com.infomaniak.mail.ui.main.newmessage

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.api.MailApi
import com.infomaniak.mail.data.cache.MailboxContentController
import com.infomaniak.mail.data.models.MessagePriority
import com.infomaniak.mail.data.models.MessagePriority.getPriority
import com.infomaniak.mail.data.models.Recipient
import com.infomaniak.mail.data.models.drafts.Draft
import com.infomaniak.mail.data.models.drafts.Draft.DraftAction
import com.infomaniak.mail.ui.main.newmessage.NewMessageActivity.EditorAction
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NewMessageViewModel : ViewModel() {
    var newMessageTo = mutableListOf<UiContact>()
    var newMessageCc = mutableListOf<UiContact>()
    var newMessageBcc = mutableListOf<UiContact>()
    var areAdvancedFieldsOpened = false
    var isEditorExpanded = false
    val editorAction = MutableLiveData<EditorAction>()
    var currentDraft: SingleLiveEvent<Draft> = SingleLiveEvent()
    var hasStartedEditing = MutableLiveData(false)
    var autoSaveJob: Job? = null

    fun setup(draftResources: String? = null, draftUuid: String? = null, messageUid: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val draft = draftResources?.let { MailApi.fetchDraft(it, messageUid ?: "") }
                ?: draftUuid?.let { MailboxContentController.getDraft(it) }
                ?: Draft().apply { initLocalValues() }
            currentDraft.postValue(draft)
        }
    }

    fun getAllContacts(): List<UiContact> {
        val contacts = mutableListOf<UiContact>()
        MailData.contactsFlow.value?.forEach { contact ->
            contacts.addAll(contact.emails.map { email -> UiContact(email, contact.name) })
        }

        return contacts
    }

    fun startAutoSave(email: String, subject: String, body: String) {
        hasStartedEditing.value = true
        clearJobs()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(3_000L)
            sendMail(DraftAction.SAVE, email, subject, body)
        }
    }

    fun sendMail(action: DraftAction, email: String, subject: String, body: String): Boolean {
        if (action == DraftAction.SAVE && hasStartedEditing.value == false ||
            action == DraftAction.SEND && newMessageTo.isEmpty()
        ) {
            return false
        }

        currentDraft.value?.let { draft ->
            draft.fill(draftAction = action, messageEmail = email, messageSubject = subject, messageBody = body)
            viewModelScope.launch(Dispatchers.IO) { sendOrSaveMail(draft) }
        }

        return true
    }

    private fun sendOrSaveMail(draft: Draft) {
        val mailbox = MailData.currentMailboxFlow.value ?: return
        val draftWithSignature = if (draft.identityId == null) MailData.setDraftSignature(draft) else draft
        // TODO: better handling of api response
        if (draftWithSignature.action == DraftAction.SEND.apiName) {
            MailData.sendDraft(draftWithSignature, mailbox.uuid)
        } else {
            MailData.saveDraft(draftWithSignature, mailbox.uuid).data?.let {
                currentDraft.value?.apply {
                    uuid = it.uuid
                    parentMessageUid = it.uid
                    isOffline = false
                }
            }
        }
    }

    fun clearJobs() {
        autoSaveJob?.cancel()
    }

    private fun Draft.fill(draftAction: DraftAction, messageEmail: String, messageSubject: String, messageBody: String) {
        // TODO: Should userInformation (here 'from') be stored in mainViewModel? See ApiRepository.getUser()
        MailboxContentController.updateDraft(this) {
            it.from = realmListOf(Recipient().apply { email = messageEmail })
            it.subject = messageSubject
            it.body = messageBody
            it.priority = MessagePriority.Priority.NORMAL.getPriority()
            it.action = draftAction.apiName
            it.to = newMessageTo.toRealmRecipients()
            it.cc = newMessageCc.toRealmRecipients()
            it.bcc = newMessageBcc.toRealmRecipients()

            // TODO: Manage advanced functionalities
            // it.quote = ""
            // it.references = ""
            // it.delay = 0
            // it.inReplyTo = ""
            // it.inReplyToUid = ""
            // it.replyTo = realmListOf()
        }
    }

    private fun List<UiContact>.toRealmRecipients(): RealmList<Recipient> {
        return if (isEmpty()) realmListOf() else map {
            Recipient().apply {
                email = it.email
                name = it.name
            }
        }.toRealmList()
    }
}
