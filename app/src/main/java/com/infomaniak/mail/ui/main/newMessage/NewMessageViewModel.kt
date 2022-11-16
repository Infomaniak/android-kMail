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

import android.app.Application
import android.content.ClipDescription
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import androidx.lifecycle.*
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxContent.DraftController.fetchDraft
import com.infomaniak.mail.data.cache.mailboxContent.DraftController.setPreviousMessage
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.userInfo.MergedContactController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.draft.Priority
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivity.EditorAction
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.getDisplayName
import com.infomaniak.mail.workers.DraftsActionsWorker
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.toRealmList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NewMessageViewModel(application: Application) : AndroidViewModel(application) {

    val mailTo = mutableListOf<Recipient>()
    val mailCc = mutableListOf<Recipient>()
    val mailBcc = mutableListOf<Recipient>()
    var mailSubject = ""
    var mailBody = ""
    val mailAttachments = mutableListOf<Attachment>()

    private var autoSaveJob: Job? = null

    var isAutocompletionOpened = false
    var areAdvancedFieldsOpened = false
    var isEditorExpanded = false

    // Boolean: For toggleable actions, `false` if the formatting has been removed and `true` if the formatting has been applied.
    val editorAction = SingleLiveEvent<Pair<EditorAction, Boolean?>>()
    val importedAttachments = MutableLiveData<MutableList<Attachment>>(mutableListOf())

    lateinit var currentDraftLocalUuid: String

    val shouldCloseActivity = SingleLiveEvent<Boolean?>()

    fun initDraftAndUi(navigationArgs: NewMessageActivityArgs): LiveData<Boolean> = liveData(Dispatchers.IO) {
        with(navigationArgs) {
            val isSuccess = RealmDatabase.mailboxContent().writeBlocking {
                currentDraftLocalUuid = if (draftExists) {
                    draftLocalUuid
                        ?: fetchDraft(draftResource!!, messageUid!!)
                        ?: run {
                            // TODO: Add Loader to block UI while waiting for `fetchDraft` API call to finish
                            return@writeBlocking false
                        }
                } else {
                    createDraft(draftMode, previousMessageUid)
                }
                true
            }

            if (isSuccess) initUiData()
            emit(isSuccess)
        }
    }

    private fun initUiData() {
        DraftController.getDraft(currentDraftLocalUuid)?.let { draft ->
            mailTo.addAll(draft.to)
            mailCc.addAll(draft.cc)
            mailBcc.addAll(draft.bcc)
            mailSubject = draft.subject
            mailBody = draft.body
            mailAttachments.addAll(draft.attachments)
        }
    }

    private fun MutableRealm.createDraft(draftMode: DraftMode, previousMessageUid: String?): String {
        return Draft().apply {
            initLocalValues(priority = Priority.NORMAL, mimeType = ClipDescription.MIMETYPE_TEXT_HTML)
            initSignature(this@createDraft)
            if (draftMode != DraftMode.NEW_MAIL) {
                previousMessageUid
                    ?.let { uid -> MessageController.getMessage(uid, this@createDraft) }
                    ?.let { message -> setPreviousMessage(this, draftMode, message) }
            }
            DraftController.upsertDraft(this, this@createDraft)
        }.localUuid
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
            saveDraftToLocal(DraftAction.SAVE)
        }
    }

    fun saveToLocalAndFinish(action: DraftAction) = viewModelScope.launch(Dispatchers.IO) {
        saveDraftToLocal(action)
        shouldCloseActivity.postValue(true)
    }

    private fun saveDraftToLocal(action: DraftAction) {
        DraftController.updateDraft(currentDraftLocalUuid) { draft ->
            draft.to = mailTo.toRealmList()
            draft.cc = mailCc.toRealmList()
            draft.bcc = mailBcc.toRealmList()
            draft.subject = mailSubject
            draft.body = mailBody
            draft.attachments = mailAttachments.toRealmList()
            draft.action = action
        }
    }

    fun importAttachments(uris: List<Uri>) = viewModelScope.launch(Dispatchers.IO) {
        val newAttachments = mutableListOf<Attachment>()
        uris.forEach { newAttachments.add(importAttachment(it)) }
        importedAttachments.postValue(newAttachments)
    }

    private fun importAttachment(uri: Uri): Attachment {
        val fileName = uri.getDisplayName(getApplication())!!
        val draftUuid = currentDraftLocalUuid

        val attachment = Attachment()
        LocalStorageUtils.copyDataToAttachmentsCache(getApplication(), uri, fileName, draftUuid)?.let { file ->
            val fileExtension = file.path.substringAfterLast(".")
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension) ?: "*/*"

            attachment.initLocalValues(file.name, file.length(), mimeType, file.toUri().toString())
        }

        return attachment
    }

    override fun onCleared() {
        DraftsActionsWorker.scheduleWork(getApplication())
        autoSaveJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val DELAY_BEFORE_AUTO_SAVING_DRAFT = 3_000L
    }
}
