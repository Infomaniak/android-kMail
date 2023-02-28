/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxContent.DraftController.setPreviousMessage
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.cache.userInfo.MergedContactController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.Companion.INFOMANIAK_SIGNATURE_HTML_CLASS_NAME
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.draft.Priority
import com.infomaniak.mail.ui.main.SnackBarManager
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivity.EditorAction
import com.infomaniak.mail.ui.main.newMessage.NewMessageFragment.FieldType
import com.infomaniak.mail.utils.*
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.realmListOf
import kotlinx.coroutines.*
import org.jsoup.Jsoup

class NewMessageViewModel(application: Application) : AndroidViewModel(application) {

    var draft: Draft = Draft()

    private val coroutineContext = viewModelScope.coroutineContext + Dispatchers.IO
    private var autoSaveJob: Job? = null

    var isAutoCompletionOpened = false
    var isEditorExpanded = false

    // Boolean: For toggleable actions, `false` if the formatting has been removed and `true` if the formatting has been applied.
    val editorAction = SingleLiveEvent<Pair<EditorAction, Boolean?>>()
    val importedAttachments = MutableLiveData<Pair<MutableList<Attachment>, ImportationResult>>()

    val shouldCloseActivity = SingleLiveEvent<Boolean?>()
    val isSendingAllowed = MutableLiveData(false)
    val snackBarManager by lazy { SnackBarManager() }

    private var snapshot: DraftSnapshot? = null

    private var isNewMessage = false

    val mergedContacts = liveData(coroutineContext) {
        emit(MergedContactController.getMergedContacts(sorted = true))
    }

    val mailboxes = liveData(coroutineContext) {

        val mailboxes = MailboxController.getMailboxes(AccountUtils.currentUserId)

        val currentMailboxIndex = mailboxes.indexOfFirst {
            it.userId == AccountUtils.currentUserId && it.mailboxId == AccountUtils.currentMailboxId
        }

        emit(mailboxes to currentMailboxIndex)
    }

    fun initDraftAndViewModel(navigationArgs: NewMessageActivityArgs): LiveData<Boolean> = liveData(Dispatchers.IO) {
        with(navigationArgs) {
            val isSuccess = RealmDatabase.mailboxContent().writeBlocking {
                draft = if (draftExists) {
                    draftLocalUuid?.let { DraftController.getDraft(it, realm = this)?.copyFromRealm() }
                        ?: fetchDraft(draftResource!!, messageUid!!)
                        ?: run {
                            // TODO: Add Loader to block UI while waiting for `fetchDraft` API call to finish
                            return@writeBlocking false
                        }
                } else {
                    isNewMessage = true
                    createDraft(draftMode, previousMessageUid, recipient)
                }
                true
            }

            if (isSuccess) {
                splitSignatureFromBody()
                saveDraftSnapshot()
                updateIsSendingAllowed()
            }

            emit(isSuccess)
        }
    }

    private fun fetchDraft(draftResource: String, messageUid: String): Draft? {
        return ApiRepository.getDraft(draftResource).data?.also { draft ->
            draft.initLocalValues(messageUid)
        }
    }

    private fun MutableRealm.createDraft(draftMode: DraftMode, previousMessageUid: String?, recipient: Recipient?): Draft {
        return Draft().apply {
            initLocalValues(priority = Priority.NORMAL, mimeType = ClipDescription.MIMETYPE_TEXT_HTML)
            initSignature(realm = this@createDraft)
            when (draftMode) {
                DraftMode.NEW_MAIL -> recipient?.let { to = realmListOf(it) }
                DraftMode.REPLY, DraftMode.REPLY_ALL, DraftMode.FORWARD -> {
                    previousMessageUid
                        ?.let { uid -> MessageController.getMessage(uid, realm = this@createDraft) }
                        ?.let { message -> setPreviousMessage(draftMode, message) }
                }
            }
        }
    }

    private fun splitSignatureFromBody() {
        val doc = Jsoup.parse(draft.body)

        val (body, signature) = doc.getElementsByClass(INFOMANIAK_SIGNATURE_HTML_CLASS_NAME).lastOrNull()?.let {
            it.remove()
            val signature = if (it.html().isBlank()) null else it.outerHtml()
            doc.body().html() to signature
        } ?: (draft.body to null)

        draft.uiBody = body.htmlToText()
        draft.uiSignature = signature
    }

    private fun saveDraftSnapshot() = with(draft) {
        snapshot = DraftSnapshot(
            to.toSet(),
            cc.toSet(),
            bcc.toSet(),
            subject,
            uiBody,
            attachments.map { it.uuid }.toSet(),
        )
    }

    fun updateDraftInLocalIfRemoteHasChanged() = viewModelScope.launch(Dispatchers.IO) {
        if (draft.remoteUuid == null) {
            DraftController.getDraft(draft.localUuid)?.let { localDraft ->
                draft.remoteUuid = localDraft.remoteUuid
                draft.messageUid = localDraft.messageUid
            }
        }
    }

    fun addRecipientToField(recipient: Recipient, type: FieldType) = with(draft) {
        val field = when (type) {
            FieldType.TO -> to
            FieldType.CC -> cc
            FieldType.BCC -> bcc
        }
        field.add(recipient)
        updateIsSendingAllowed()
        saveDraftDebouncing()
    }

    fun removeRecipientFromField(recipient: Recipient, type: FieldType) = with(draft) {
        val field = when (type) {
            FieldType.TO -> to
            FieldType.CC -> cc
            FieldType.BCC -> bcc
        }
        field.remove(recipient)
        updateIsSendingAllowed()
        saveDraftDebouncing()
    }

    fun updateMailSubject(newSubject: String?) = with(draft) {
        if (newSubject != subject) {
            subject = newSubject
            saveDraftDebouncing()
        }
    }

    fun updateMailBody(newBody: String) = with(draft) {
        if (newBody != uiBody) {
            uiBody = newBody
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

    fun saveToLocalAndFinish(action: DraftAction, displayToast: () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        autoSaveJob?.cancel()

        if (shouldExecuteAction(action)) {
            saveDraftToLocal(action)
            withContext(Dispatchers.Main) { displayToast() }
        } else if (isNewMessage) {
            RealmDatabase.mailboxContent().writeBlocking {
                DraftController.getDraft(draft.localUuid, realm = this)?.let(::delete)
            }
        }

        shouldCloseActivity.postValue(true)
    }

    private fun saveDraftToLocal(action: DraftAction) {

        draft.body = draft.uiBody.textToHtml() + (draft.uiSignature ?: "")
        draft.action = action

        RealmDatabase.mailboxContent().writeBlocking {
            DraftController.upsertDraft(draft, realm = this)
            draft.messageUid?.let { MessageController.getMessage(it, realm = this)?.draftLocalUuid = draft.localUuid }
        }
    }

    private fun shouldExecuteAction(action: DraftAction) = action == DraftAction.SEND || snapshot?.hasChanges() == true

    private fun updateIsSendingAllowed() {
        isSendingAllowed.postValue(draft.to.isNotEmpty() || draft.cc.isNotEmpty() || draft.bcc.isNotEmpty())
    }

    fun importAttachments(uris: List<Uri>) = viewModelScope.launch(Dispatchers.IO) {
        val newAttachments = mutableListOf<Attachment>()
        var attachmentsSize = draft.attachments.sumOf { it.size }

        uris.forEach { uri ->
            val availableSpace = FILE_SIZE_25_MB - attachmentsSize
            val (attachment, hasSizeLimitBeenReached) = importAttachment(uri, availableSpace) ?: return@forEach

            if (hasSizeLimitBeenReached) {
                importedAttachments.postValue(newAttachments to ImportationResult.FILE_SIZE_TOO_BIG)
                return@launch
            }

            attachment?.let {
                newAttachments.add(it)
                draft.attachments.add(it)
                attachmentsSize += it.size
            }
        }

        saveDraftDebouncing()

        importedAttachments.postValue(newAttachments to ImportationResult.SUCCESS)
    }

    private fun importAttachment(uri: Uri, availableSpace: Long): Pair<Attachment?, Boolean>? {
        val (fileName, fileSize) = uri.getFileNameAndSize(getApplication()) ?: return null
        if (fileSize > availableSpace) return null to true

        return LocalStorageUtils.saveUploadAttachment(getApplication(), uri, fileName, draft.localUuid)
            ?.let { file ->
                val mimeType = file.path.guessMimeType()
                Attachment().apply { initLocalValues(file.name, file.length(), mimeType, file.toUri().toString()) } to false
            } ?: (null to false)
    }

    override fun onCleared() {
        LocalStorageUtils.deleteAttachmentsUploadsDirIfEmpty(getApplication(), draft.localUuid)
        autoSaveJob?.cancel()
        super.onCleared()
    }

    enum class ImportationResult {
        SUCCESS,
        FILE_SIZE_TOO_BIG,
    }

    private data class DraftSnapshot(
        val to: Set<Recipient>,
        val cc: Set<Recipient>,
        val bcc: Set<Recipient>,
        var subject: String?,
        var body: String,
        val attachmentsUuids: Set<String>,
    )

    private fun DraftSnapshot.hasChanges(): Boolean {
        return to != draft.to.toSet() ||
                cc != draft.cc.toSet() ||
                bcc != draft.bcc.toSet() ||
                subject != draft.subject ||
                body != draft.uiBody ||
                attachmentsUuids != draft.attachments.map { it.uuid }.toSet()
    }

    private companion object {
        const val DELAY_BEFORE_AUTO_SAVING_DRAFT = 3_000L
        const val FILE_SIZE_25_MB = 25L * 1_024L * 1_024L
    }
}
