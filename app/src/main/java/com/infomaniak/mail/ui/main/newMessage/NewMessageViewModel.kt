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

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.userInfos.ContactController
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivity.EditorAction
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NewMessageViewModel : ViewModel() {

    val recipients = mutableListOf<UiContact>()
    val newMessageCc = mutableListOf<UiContact>()
    val newMessageBcc = mutableListOf<UiContact>()

    var areAdvancedFieldsOpened = false
    var isEditorExpanded = false

    // Boolean : for toggleable actions, false if the formatting has been removed and true if the formatting has been applied
    val editorAction = SingleLiveEvent<Pair<EditorAction, Boolean?>>()

    fun getContacts(): LiveData<List<UiContact>> = liveData(Dispatchers.IO) {
        emit(mutableListOf<UiContact>().apply {
            ContactController.getContacts().forEach { contact ->
                contact.emails.forEach { email ->
                    add(UiContact(email, contact.name))
                }
            }
        })
    }

    fun sendMail(draft: Draft, action: DraftAction, mailbox: Mailbox) {
        fun sendDraft() = ApiRepository.sendDraft(mailbox.uuid, draft.fillForApi("send"))
        fun saveDraft() = ApiRepository.saveDraft(mailbox.uuid, draft.fillForApi("save"))

        viewModelScope.launch(Dispatchers.IO) {
            val signature = ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailbox)
            draft.identityId = signature.data?.defaultSignatureId
            if (action == DraftAction.SEND) sendDraft() else saveDraft()
        }
    }

    private fun Draft.fillForApi(draftAction: String) = apply {
        action = draftAction
        to = recipients.toRealmRecipients() ?: realmListOf()
        cc = newMessageCc.toRealmRecipients()
        bcc = newMessageBcc.toRealmRecipients()

        // TODO: manage advanced functionalities
        // quote = ""
        // references = ""
        // delay = 0
        // inReplyTo = ""
        // inReplyToUid = ""
        // replyTo = realmListOf()
    }

    private fun List<UiContact>.toRealmRecipients(): RealmList<Recipient>? {
        return if (isEmpty()) null else map {
            Recipient().apply {
                email = it.email
                name = it.name ?: ""
            }
        }.toRealmList()
    }
}
