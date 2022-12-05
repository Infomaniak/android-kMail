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
import androidx.core.net.toUri
import androidx.lifecycle.*
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.lib.core.utils.guessMimeType
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxContent.DraftController.fetchDraft
import com.infomaniak.mail.data.cache.mailboxContent.DraftController.setPreviousMessage
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.cache.userInfo.MergedContactController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.draft.Priority
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivity.EditorAction
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.getFileNameAndSize
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.toRealmList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jsoup.Jsoup

class NewMessageViewModel(application: Application) : AndroidViewModel(application) {

    val mailTo = mutableListOf<Recipient>()
    val mailCc = mutableListOf<Recipient>()
    val mailBcc = mutableListOf<Recipient>()
    var mailSubject: String? = null
    var mailBody = ""
    var mailSignature: String? = null
    val mailAttachments = mutableListOf<Attachment>()

    private var autoSaveJob: Job? = null

    var isAutocompletionOpened = false
    var areAdvancedFieldsOpened = false
    var isEditorExpanded = false

    // Boolean: For toggleable actions, `false` if the formatting has been removed and `true` if the formatting has been applied.
    val editorAction = SingleLiveEvent<Pair<EditorAction, Boolean?>>()
    val importedAttachments = MutableLiveData<Pair<MutableList<Attachment>, ImportationResult>>()

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
            mailAttachments.addAll(draft.attachments)

            val (body, signature) = splitSignatureFromBody(draft.body)
            mailBody = body.htmlToText()
            mailSignature = signature
        }
    }

    private fun splitSignatureFromBody(mailBody: String): Pair<String, String?> {
        val doc = Jsoup.parse(mailBody)
        return doc.getElementsByClass(INFOMANIAK_SIGNATURE_HTML_CLASS_NAME).lastOrNull()?.let {
            it.remove()
            val signature = if (it.html().isBlank()) null else it.outerHtml()
            doc.body().html() to signature
        } ?: (mailBody to null)
    }

    private fun MutableRealm.createDraft(draftMode: DraftMode, previousMessageUid: String?): String {
        return Draft().apply {
            initLocalValues(priority = Priority.NORMAL, mimeType = ClipDescription.MIMETYPE_TEXT_HTML)
            initSignature(realm = this@createDraft)
            if (draftMode != DraftMode.NEW_MAIL) {
                previousMessageUid
                    ?.let { uid -> MessageController.getMessage(uid, realm = this@createDraft) }
                    ?.let { message -> setPreviousMessage(draft = this, draftMode, message) }
            }
            DraftController.upsertDraft(draft = this, realm = this@createDraft)
        }.localUuid
    }

    fun getMergedContacts(): LiveData<List<MergedContact>> = liveData(Dispatchers.IO) {
        emit(MergedContactController.getMergedContacts(sorted = true))
    }

    fun observeMailboxes(): LiveData<Pair<List<Mailbox>, Int>> = liveData(Dispatchers.IO) {

        val mailboxes = MailboxController.getMailboxes(AccountUtils.currentUserId)

        val currentMailboxIndex = mailboxes.indexOfFirst {
            it.userId == AccountUtils.currentUserId && it.mailboxId == AccountUtils.currentMailboxId
        }

        emit(mailboxes to currentMailboxIndex)
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
            draft.body = mailBody.textToHtml() + (mailSignature ?: "")
            draft.attachments = mailAttachments.toRealmList()
            draft.action = action
        }
    }

    fun importAttachments(uris: List<Uri>) = viewModelScope.launch(Dispatchers.IO) {
        val newAttachments = mutableListOf<Attachment>()
        var attachmentsSize = mailAttachments.sumOf { it.size }

        uris.forEach { uri ->
            val availableSpace = FILE_SIZE_25_MB - attachmentsSize
            val (attachment, hasSizeLimitBeenReached) = importAttachment(uri, availableSpace) ?: return@forEach

            if (hasSizeLimitBeenReached) {
                importedAttachments.postValue(newAttachments to ImportationResult.FILE_SIZE_TOO_BIG)
                return@launch
            }

            attachment?.let {
                newAttachments.add(it)
                mailAttachments.add(it)
                attachmentsSize += it.size
            }
        }

        saveDraftToLocal(DraftAction.SAVE)

        importedAttachments.postValue(newAttachments to ImportationResult.SUCCESS)
    }

    private fun importAttachment(uri: Uri, availableSpace: Int): Pair<Attachment?, Boolean>? {
        val (fileName, fileSize) = uri.getFileNameAndSize(getApplication()) ?: return null
        if (fileSize > availableSpace) return null to true

        return LocalStorageUtils.copyDataToAttachmentsCache(getApplication(), uri, fileName, currentDraftLocalUuid)?.let { file ->
            val mimeType = file.path.guessMimeType()
            Attachment().apply { initLocalValues(file.name, file.length(), mimeType, file.toUri().toString()) } to false
        } ?: (null to false)
    }

    private fun String.htmlToText(): String = Jsoup.parse(replace("\r", "").replace("\n", "")).wholeText()
    private fun String.textToHtml(): String = replace("\n", "<br>")

    override fun onCleared() {
        LocalStorageUtils.deleteAttachmentsDirIfEmpty(getApplication(), currentDraftLocalUuid)
        autoSaveJob?.cancel()
        super.onCleared()
    }

    enum class ImportationResult {
        SUCCESS,
        FILE_SIZE_TOO_BIG,
    }

    private companion object {
        const val DELAY_BEFORE_AUTO_SAVING_DRAFT = 3_000L
        const val FILE_SIZE_25_MB = 25 * 1024 * 1024

        const val INFOMANIAK_SIGNATURE_HTML_CLASS_NAME = "editorUserSignature"
    }
}
