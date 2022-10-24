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
import com.infomaniak.mail.data.cache.userInfo.MergedContactController
import com.infomaniak.mail.data.models.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.draft.Priority
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivity.EditorAction
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NewMessageViewModel : ViewModel() {

    val mailTo = mutableListOf<Recipient>()
    val mailCc = mutableListOf<Recipient>()
    val mailBcc = mutableListOf<Recipient>()
    var mailSubject = ""
    var mailBody = ""

    private var autoSaveJob: Job? = null

    var isAutocompletionOpened = false
    var areAdvancedFieldsOpened = false
    var isEditorExpanded = false

    // Boolean : for toggleable actions, false if the formatting has been removed and true if the formatting has been applied
    val editorAction = SingleLiveEvent<Pair<EditorAction, Boolean?>>()
    val currentDraftUuid = MutableLiveData<String?>()
    val shouldCloseActivity = SingleLiveEvent<Boolean?>()

    fun fetchAndConfigureDraft(
        navigationArgs: NewMessageActivityArgs,
        mainViewModel: MainViewModel,
    ) = viewModelScope.launch(Dispatchers.IO) {
        with(navigationArgs) {
            if (isOpeningExistingDraft) {
                if (isExistingDraftAlreadyDownloaded) {
                    configureDraft(draftUuid)
                } else {
                    val draftUuid = mainViewModel.fetchDraft(draftResource!!, messageUid!!)
                    configureDraft(draftUuid)
                }
            } else {
                configureDraft()
            }
        }
    }

    private fun configureDraft(draftUuid: String? = null) = viewModelScope.launch(Dispatchers.IO) {
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
            mailTo.addAll(draft.to.toRecipientsList())
            mailCc.addAll(draft.cc.toRecipientsList())
            mailBcc.addAll(draft.bcc.toRecipientsList())
            mailSubject = draft.subject
            mailBody = draft.body
        }
    }

    fun getMergedContacts(): LiveData<List<MergedContact>> = liveData(Dispatchers.IO) {
        emit(MergedContactController.getMergedContacts())
    }

    fun saveMail(action: DraftAction) = viewModelScope.launch(Dispatchers.IO) {
        val draftUuid = currentDraftUuid.value ?: return@launch
        val mailbox = MainViewModel.currentMailboxObjectId.value?.let(MailboxController::getMailbox) ?: return@launch
        val signature = ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailboxName)
        RealmDatabase.mailboxContent().writeBlocking {
            saveDraft(draftUuid, signature.data?.defaultSignatureId, action, this)
            shouldCloseActivity.postValue(true)
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

    fun autoSaveDraft() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(DELAY_BEFORE_AUTO_SAVING_DRAFT)
            val draftUuid = currentDraftUuid.value ?: return@launch
            saveDraft(draftUuid)
        }
    }

    private fun saveDraft(draftUuid: String, identityId: Int? = null, action: DraftAction? = null, realm: MutableRealm? = null) {
        DraftController.updateDraft(draftUuid, realm) { draft ->
            draft.to = mailTo.toRealmList()
            draft.cc = mailCc.toRealmList()
            draft.bcc = mailBcc.toRealmList()
            draft.subject = mailSubject
            draft.body = mailBody
            identityId?.let { draft.identityId = it }
            action?.let { draft.action = it }
        }
    }

    override fun onCleared() {
        autoSaveJob?.cancel()
        super.onCleared()
    }

    private fun RealmList<Recipient>.toRecipientsList(): List<Recipient> {
        return map { Recipient().initLocalValues(it.email, it.name) }
    }

    private companion object {
        const val DELAY_BEFORE_AUTO_SAVING_DRAFT = 3_000L
    }
}
