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
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxInfos.MailboxController
import com.infomaniak.mail.data.cache.userInfos.ContactController
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.Recipient
import com.infomaniak.mail.data.models.drafts.Draft
import com.infomaniak.mail.data.models.drafts.Draft.DraftAction
import com.infomaniak.mail.ui.main.MainViewModel
import com.infomaniak.mail.ui.main.MainViewModel.Companion.updateForApi
import com.infomaniak.mail.ui.main.newmessage.NewMessageActivity.EditorAction
import com.infomaniak.mail.utils.AccountUtils
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NewMessageViewModel : ViewModel() {

    val allContacts = MutableLiveData<List<UiContact>?>()
    val mailboxes = MutableLiveData<List<Mailbox>?>()

    var newMessageTo = mutableListOf<UiContact>()
    var newMessageCc = mutableListOf<UiContact>()
    var newMessageBcc = mutableListOf<UiContact>()

    var areAdvancedFieldsOpened = false
    var isEditorExpanded = false

    val editorAction = MutableLiveData<EditorAction>()
    var currentDraft: SingleLiveEvent<Draft> = SingleLiveEvent()
    var hasStartedEditing = MutableLiveData(false)
    var autoSaveJob: Job? = null

    fun loadDraft(apiDraft: Draft?, draftUuid: String?) {
        val draft = apiDraft
            ?: draftUuid?.let(DraftController::getDraftSync)
            ?: Draft().apply {
                isOffline = true
                initLocalValues()
            }
        currentDraft.postValue(draft)
    }

    fun listenToAllContacts() = viewModelScope.launch(Dispatchers.IO) {
        ContactController.getContactsAsync().collect {
            val contacts = mutableListOf<UiContact>()
            it.list.forEach { contact ->
                contacts.addAll(contact.emails.map { email -> UiContact(email, contact.name) })
            }
            allContacts.postValue(contacts)
        }
    }

    fun listenToMailboxes() = viewModelScope.launch(Dispatchers.IO) {
        MailboxController.getMailboxesAsync(AccountUtils.currentUserId).collect {
            mailboxes.postValue(it.list)
        }
    }

    fun startAutoSave(email: String, subject: String, body: String) {
        hasStartedEditing.value = true
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(3_000L)
            sendDraftAction(DraftAction.SAVE, email, subject, body)
        }
    }

    fun sendDraftAction(action: DraftAction, email: String, subject: String, body: String): Boolean {
        if (action == DraftAction.SAVE && hasStartedEditing.value == false ||
            action == DraftAction.SEND && newMessageTo.isEmpty()
        ) {
            return false
        }

        currentDraft.value?.let { draft ->
            viewModelScope.launch(Dispatchers.IO) { draft.update(action, email, subject, body).sendOrSaveDraft() }
        }

        return true
    }

    private fun Draft.sendOrSaveDraft() {
        val mailbox = MainViewModel.currentMailboxObjectId.value?.let(MailboxController::getMailboxSync) ?: return
        // TODO: Better handling of API response
        val draftWithSignature = if (identityId == null) updateForApi() else this
        if (draftWithSignature.action == DraftAction.SEND) {
            MainViewModel.sendDraft(draftWithSignature, mailbox.uuid)
        } else {
            MainViewModel.saveDraft(draftWithSignature, mailbox.uuid).data?.let {
                currentDraft.value?.apply {
                    uuid = it.uuid
                    messageUid = it.uid
                    isOffline = false
                }
            }
        }
    }

    override fun onCleared() {
        autoSaveJob?.cancel()
        super.onCleared()
    }

    private fun Draft.update(draftAction: DraftAction, messageEmail: String, messageSubject: String, messageBody: String): Draft {
        fun Draft.updateData(
            draftAction: DraftAction,
            messageEmail: String,
            messageSubject: String,
            messageBody: String,
        ) = apply {
            from = realmListOf(Recipient().apply { email = messageEmail })
            subject = messageSubject
            body = messageBody
            action = draftAction
            to = newMessageTo.toRealmRecipients()
            cc = newMessageCc.toRealmRecipients()
            bcc = newMessageBcc.toRealmRecipients()

            // TODO: Manage advanced functionalities
            // it.quote = ""
            // it.references = ""
            // it.delay = 0
            // it.inReplyTo = ""
            // it.inReplyToUid = ""
            // it.replyTo = realmListOf()
            // it.attachments = realmListOf()
        }

        // TODO: Should userInformation (here 'from') be stored in mainViewModel? See ApiRepository.getUser()
        return DraftController.updateDraft(uuid) { it.updateData(draftAction, messageEmail, messageSubject, messageBody) }
            ?: updateData(draftAction, messageEmail, messageSubject, messageBody)
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
