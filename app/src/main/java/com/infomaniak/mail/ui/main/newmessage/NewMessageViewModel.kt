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
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxInfos.MailboxController
import com.infomaniak.mail.data.models.Recipient
import com.infomaniak.mail.data.models.drafts.Draft
import com.infomaniak.mail.data.models.drafts.Draft.DraftAction
import com.infomaniak.mail.ui.main.MainViewModel
import com.infomaniak.mail.ui.main.newmessage.NewMessageActivity.EditorAction
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NewMessageViewModel : ViewModel() {

    private var autoSaveJob: Job? = null

    var draftEmail: String = ""
    var draftSubject: String = ""
    var draftBody: String = ""
    var draftTo = mutableListOf<UiContact>()
    var draftCc = mutableListOf<UiContact>()
    var draftBcc = mutableListOf<UiContact>()

    var areAdvancedFieldsOpened = false
    var isEditorExpanded = false

    val editorAction = MutableLiveData<EditorAction?>()
    var currentDraftUuid = MutableLiveData<String?>()
    var hasStartedEditing = false
    var isNewMessage: Boolean = false

    fun saveDraft(completion: (Boolean) -> Unit) {
        if (!hasStartedEditing) {
            completion(false)
        } else {
            executeDraftAction(DraftAction.SAVE) { completion(it) }
        }
    }

    fun sendDraft(completion: (Boolean) -> Unit) {
        if (draftTo.isEmpty()) {
            completion(false)
        } else {
            executeDraftAction(DraftAction.SEND) { completion(it) }
        }
    }

    private fun executeDraftAction(draftAction: DraftAction, completion: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val uuid = currentDraftUuid.value ?: return@launch
            val mailbox = MainViewModel.currentMailboxObjectId.value?.let(MailboxController::getMailboxSync) ?: return@launch
            val identityId = DraftController.getDraftSync(uuid)?.identityId
            val defaultSignatureId = identityId
                ?: ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailbox).data?.defaultSignatureId
            val draft = updateDraftContent(uuid, defaultSignatureId, draftAction) ?: return@launch

            if (draft.action == DraftAction.SEND) {
                sendDraft(draft, mailbox.uuid, isNewMessage)
            } else {
                val draftUuid = MainViewModel.saveDraft(draft, mailbox.uuid, isNewMessage)
                currentDraftUuid.postValue(draftUuid)
            }

            completion?.invoke(true)
        }
    }

    private fun sendDraft(draft: Draft, mailboxUuid: String, isNewMessage: Boolean) {
        val apiResponse = ApiRepository.sendDraft(mailboxUuid, draft)
        if (apiResponse.data == true) {
            if (isNewMessage) {
                DraftController.deleteDraft(draft.uuid)
            } else {
                DraftController.deleteDraftAndItsParents(draft.uuid, draft.messageUid)
            }
        } else {
            val updatedDraft = DraftController.updateDraft(draft.uuid) { it.apply { it.action = DraftAction.SAVE } } ?: return
            MainViewModel.saveDraft(updatedDraft, mailboxUuid, isNewMessage)
        }
    }

    private fun updateDraftContent(uuid: String, defaultSignatureId: Int?, draftAction: DraftAction): Draft? {
        return DraftController.updateDraft(uuid) {
            it.apply {
                from = realmListOf(Recipient().apply { email = draftEmail }.initLocalValues())
                to = draftTo.toRealmRecipients()
                cc = draftCc.toRealmRecipients()
                bcc = draftBcc.toRealmRecipients()
                subject = draftSubject
                body = draftBody
                identityId = defaultSignatureId
                action = draftAction
                // TODO: Manage advanced functionalities
                // quote = ""
                // references = ""
                // delay = 0
                // inReplyTo = ""
                // inReplyToUid = ""
                // replyTo = realmListOf()
                // attachments = realmListOf()
            }
        }
    }

    fun startDraftAutoSave() {
        hasStartedEditing = true
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(DELAY_BEFORE_AUTO_SAVING_DRAFT)
            executeDraftAction(DraftAction.SAVE)
        }
    }

    override fun onCleared() {
        autoSaveJob?.cancel()
        super.onCleared()
    }

    private fun List<UiContact>.toRealmRecipients(): RealmList<Recipient> {
        return if (isEmpty()) realmListOf() else map {
            Recipient().apply {
                email = it.email
                name = it.name ?: ""
            }.initLocalValues()
        }.toRealmList()
    }

    private companion object {
        const val DELAY_BEFORE_AUTO_SAVING_DRAFT = 3_000L
    }
}
