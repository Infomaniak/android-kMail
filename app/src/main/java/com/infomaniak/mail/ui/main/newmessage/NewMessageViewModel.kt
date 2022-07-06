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

import android.app.Activity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.api.MailApi
import com.infomaniak.mail.data.cache.MailboxContentController
import com.infomaniak.mail.data.models.MessagePriority
import com.infomaniak.mail.data.models.MessagePriority.getPriority
import com.infomaniak.mail.data.models.Recipient
import com.infomaniak.mail.data.models.drafts.Draft
import com.infomaniak.mail.ui.main.newmessage.NewMessageActivity.EditorAction
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NewMessageViewModel : ViewModel() {
    var newMessageTo = mutableListOf<UiContact>()
    var newMessageCc = mutableListOf<UiContact>()
    var newMessageBcc = mutableListOf<UiContact>()
    var areAdvancedFieldsOpened = false
    var isEditorExpanded = false
    val editorAction = MutableLiveData<EditorAction>()
    var hasStartedEditing = MutableLiveData(false)
    var autoSaveJob: Job? = null
    var currentDraft: MutableLiveData<Draft?> = MutableLiveData()

    fun setUp(activity: Activity, draftResources: String? = null, draftUuid: String? = null, messageUid: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val draft = if (draftResources.isNullOrEmpty() || messageUid.isNullOrEmpty()) {
                draftUuid?.let { MailboxContentController.getDraft(draftUuid) }
            } else {
                MailApi.fetchDraft(draftResources, messageUid)
            }
            activity.runOnUiThread { currentDraft.value = draft ?: Draft().apply { initLocalValues("") } }
        }
    }

    fun getAllContacts(): List<UiContact> {
        val contacts = mutableListOf<UiContact>()
        MailData.contactsFlow.value?.forEach { contact ->
            contacts.addAll(contact.emails.map { email -> UiContact(email, contact.name) })
        }
        return contacts
    }

    fun sendMail(draft: Draft) {
        val mailbox = MailData.currentMailboxFlow.value ?: return
        fun sendDraft() = MailData.sendDraft(draft, mailbox.uuid)
        fun saveDraft() = MailData.saveDraft(draft, mailbox.uuid)

        viewModelScope.launch(Dispatchers.IO) {
            val signature = ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailbox)
            MailboxContentController.updateDraft(draft) {
                it.identityId = signature.data?.defaultSignatureId
            }
            // TODO: better handling of api response
            if (draft.action == Draft.DraftAction.SEND.name.lowercase()) {
                sendDraft()
            } else {
                saveDraft().data?.let {
                    currentDraft.value?.uuid = it.uuid
                    currentDraft.value?.parentMessageUid = it.uid
                }
            }
        }
    }

    fun clearJobs() {
        autoSaveJob?.cancel()
    }

    fun Draft.fill(draftAction: Draft.DraftAction, messageEmail: String, messageSubject: String, messageBody: String) {
        // TODO: should userInformation (here 'from') be stored in mainViewModel ? see ApiRepository.getUser()
        MailboxContentController.updateDraft(this) {
            it.from = realmListOf(Recipient().apply { email = messageEmail })
            it.subject = messageSubject
            it.body = messageBody
            it.priority = MessagePriority.Priority.NORMAL.getPriority()
            it.action = draftAction.name.lowercase()
            it.to = newMessageTo.toRealmRecipients()
            it.cc = newMessageCc.toRealmRecipients()
            it.bcc = newMessageBcc.toRealmRecipients()

//        // TODO: manage advanced functionalities
//        it.quote = ""
//        it.references = ""
//        it.delay = 0
//        it.inReplyTo = ""
//        it.inReplyToUid = ""
//        it.replyTo = realmListOf()
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
