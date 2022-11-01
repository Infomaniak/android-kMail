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
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxContent.DraftController.setPreviousMessage
import com.infomaniak.mail.data.cache.mailboxContent.DraftController.setSignature
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.cache.userInfo.MergedContactController
import com.infomaniak.mail.data.models.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
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

    // Boolean: For toggleable actions, `false` if the formatting has been removed and `true` if the formatting has been applied.
    val editorAction = SingleLiveEvent<Pair<EditorAction, Boolean?>>()

    var currentDraftLocalUuid: String? = null

    val shouldCloseActivity = SingleLiveEvent<Boolean?>()

    fun initializeDraftAndUi(navigationArgs: NewMessageActivityArgs): LiveData<Boolean> = liveData(Dispatchers.IO) {
        with(navigationArgs) {
            RealmDatabase.mailboxContent().writeBlocking {

                currentDraftLocalUuid = when {
                    isDraftExisting && isDraftDownloaded -> draftLocalUuid!!
                    isDraftExisting && !isDraftDownloaded -> {
                        DraftController.fetchDraft(draftResource!!, messageUid!!, this) ?: return@writeBlocking
                    }
                    else -> createDraft(draftMode, previousMessageUid)
                }

                initUiData(currentDraftLocalUuid!!)
            }
        }
        emit(true)
    }

    private fun MutableRealm.initUiData(draftLocalUuid: String) {
        DraftController.getDraft(draftLocalUuid, this)?.let { draft ->
            mailTo.addAll(draft.to.toRecipientsList())
            mailCc.addAll(draft.cc.toRecipientsList())
            mailBcc.addAll(draft.bcc.toRecipientsList())
            mailSubject = draft.subject
            mailBody = draft.body
        }
    }

    private fun MutableRealm.createDraft(draftMode: DraftMode, previousMessageUid: String?): String {
        return Draft()
            .initLocalValues(priority = Priority.NORMAL)
            .apply {
                setSignature(this)
                if (draftMode != DraftMode.NEW_MAIL) setPreviousMessage(this, draftMode, previousMessageUid)
            }
            .also { DraftController.upsertDraft(it, this) }
            .localUuid
    }

    fun getMergedContacts(): LiveData<List<MergedContact>> = liveData(Dispatchers.IO) {
        emit(MergedContactController.getMergedContacts())
    }

    fun updateMailSubject(subject: String) {
        if (subject != mailSubject) {
            mailSubject = subject
            saveDraftDebouncing()
        }
    }

    fun updateMailBody(body: String) {
        if (body != mailBody) {
            mailBody = body
            saveDraftDebouncing()
        }
    }

    fun saveDraftDebouncing() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(DELAY_BEFORE_AUTO_SAVING_DRAFT)
            val mailboxUuid = MainViewModel.currentMailboxObjectId.value?.let(MailboxController::getMailbox)?.uuid!!

            RealmDatabase.mailboxContent().writeBlocking {
                saveDraftToLocal(currentDraftLocalUuid!!, DraftAction.SAVE)

                val draft = DraftController.getDraft(currentDraftLocalUuid!!, this) ?: return@writeBlocking
                DraftController.executeDraftAction(draft, mailboxUuid, this)
            }
        }
    }

    fun saveToLocalAndFinish(action: DraftAction) = viewModelScope.launch(Dispatchers.IO) {
        val draftLocalUuid = currentDraftLocalUuid ?: return@launch
        RealmDatabase.mailboxContent().writeBlocking {
            saveDraftToLocal(draftLocalUuid, action)
        }
        shouldCloseActivity.postValue(true)
    }

    private fun MutableRealm.saveDraftToLocal(draftLocalUuid: String, action: DraftAction) {
        DraftController.updateDraft(draftLocalUuid, this) { draft ->
            draft.to = mailTo.toRealmList()
            draft.cc = mailCc.toRealmList()
            draft.bcc = mailBcc.toRealmList()
            draft.subject = mailSubject
            draft.body = mailBody
            draft.action = action
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
