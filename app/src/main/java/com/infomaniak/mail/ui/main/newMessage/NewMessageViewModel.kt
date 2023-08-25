/*
 * Infomaniak ikMail - Android
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.*
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.MatomoMail.trackSendingDraftEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.SignatureController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.cache.userInfo.MergedContactController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.Companion.encapsulateSignatureContentWithInfomaniakClass
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MainDispatcher
import com.infomaniak.mail.ui.main.SnackBarManager
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivity.EditorAction
import com.infomaniak.mail.ui.main.newMessage.NewMessageFragment.CreationStatus
import com.infomaniak.mail.ui.main.newMessage.NewMessageFragment.FieldType
import com.infomaniak.mail.ui.main.newMessage.NewMessageViewModel.SignatureScore.*
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.ContactUtils.arrangeMergedContacts
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject

@HiltViewModel
class NewMessageViewModel @Inject constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val draftController: DraftController,
    private val globalCoroutineScope: CoroutineScope,
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val mergedContactController: MergedContactController,
    private val notificationManagerCompat: NotificationManagerCompat,
    private val sharedUtils: SharedUtils,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)
    private var autoSaveJob: Job? = null

    var draft: Draft = Draft()
        set(value) {
            field = value
            if (field.body.isNotEmpty()) splitSignatureAndQuoteFromBody()
        }
    var selectedSignatureId = -1

    var isAutoCompletionOpened = false
    var isEditorExpanded = false
    var shouldSendInsteadOfSave = false
    var otherFieldsAreAllEmpty = SingleLiveEvent(true)
    var initializeFieldsAsOpen = SingleLiveEvent<Boolean>()

    // Boolean: For toggleable actions, `false` if the formatting has been removed and `true` if the formatting has been applied.
    val editorAction = SingleLiveEvent<Pair<EditorAction, Boolean?>>()
    val isInitSuccess = SingleLiveEvent<Boolean>()
    val importedAttachments = MutableLiveData<Pair<MutableList<Attachment>, ImportationResult>>()
    val isSendingAllowed = MutableLiveData(false)
    val isExternalBannerVisible = MutableLiveData<Pair<String?, Int>>()

    val snackBarManager by lazy { SnackBarManager() }
    var shouldExecuteDraftActionWhenStopping = true
    var activityCreationStatus = CreationStatus.NOT_YET_CREATED

    private var snapshot: DraftSnapshot? = null

    private var isNewMessage = false

    val currentMailbox by lazy { MailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)!! }

    private val arrivedFromExistingDraft
        inline get() = savedStateHandle.get<Boolean>(NewMessageActivityArgs::arrivedFromExistingDraft.name) ?: false
    private val notificationId
        inline get() = savedStateHandle.get<Int>(NewMessageActivityArgs::notificationId.name) ?: -1
    private val draftLocalUuid
        inline get() = savedStateHandle.get<String?>(NewMessageActivityArgs::draftLocalUuid.name)
    private val draftResource
        inline get() = savedStateHandle.get<String?>(NewMessageActivityArgs::draftResource.name)
    private val messageUid
        inline get() = savedStateHandle.get<String?>(NewMessageActivityArgs::messageUid.name)
    private val draftMode
        inline get() = savedStateHandle.get<DraftMode>(NewMessageActivityArgs::draftMode.name) ?: DraftMode.NEW_MAIL
    private val previousMessageUid
        inline get() = savedStateHandle.get<String?>(NewMessageActivityArgs::previousMessageUid.name)
    private val recipient
        inline get() = savedStateHandle.get<Recipient?>(NewMessageActivityArgs::recipient.name)

    val mergedContacts = liveData(ioCoroutineContext) {
        val list = mergedContactController.getMergedContacts(sorted = true).copyFromRealm()
        emit(list to arrangeMergedContacts(list))
    }

    fun initDraftAndViewModel(): LiveData<Pair<Boolean, List<Signature>>> = liveData(ioCoroutineContext) {

        val realm = mailboxContentRealm()

        var signatures = emptyList<Signature>()

        val isSuccess = realm.writeBlocking {
            runCatching {

                signatures = SignatureController.getAllSignatures(realm)
                if (signatures.isEmpty()) return@writeBlocking false

                val isRecreated = activityCreationStatus == CreationStatus.RECREATED
                val draftExists = arrivedFromExistingDraft || isRecreated

                draft = if (draftExists) {
                    val uuid = draftLocalUuid ?: draft.localUuid
                    getLatestDraft(uuid) ?: run {
                        if (isRecreated && (draftResource == null || messageUid == null)) {
                            // We arrive here if :
                            //    1. the user created a new Draft,
                            //    2. didn't write anything in it,
                            //    3. then recreated the activity.
                            // In this case, we do not have any data in Realm nor from the API,
                            // hence the null `draftResource` & `messageUid`,
                            // so we can just reuse the already existing Draft.
                            draft
                        } else {
                            fetchDraft()
                        } ?: return@writeBlocking false
                    }
                } else {
                    isNewMessage = true
                    createDraft(signatures) ?: return@writeBlocking false
                }

                if (draft.identityId.isNullOrBlank()) draft.addMissingSignatureData(realm = this)
            }.onFailure {
                return@writeBlocking false
            }

            return@writeBlocking true
        }

        if (isSuccess) {
            dismissNotification()
            markAsRead(currentMailbox, realm)
            selectedSignatureId = draft.identityId!!.toInt()
            saveDraftSnapshot()
            if (draft.cc.isNotEmpty() || draft.bcc.isNotEmpty()) {
                otherFieldsAreAllEmpty.postValue(false)
                initializeFieldsAsOpen.postValue(true)
            }
        }

        emit(isSuccess to signatures)
        isInitSuccess.postValue(isSuccess)
    }

    /**
     * If we came from a Notification's action, we need to dismiss the Notification.
     */
    private fun dismissNotification() {
        if (notificationId == -1) return

        notificationManagerCompat.cancel(notificationId)
    }

    /**
     * If we are replying to a Message, we need to mark it as read.
     */
    private suspend fun markAsRead(mailbox: Mailbox, realm: TypedRealm) {
        val message = previousMessageUid?.let { MessageController.getMessage(it, realm) } ?: return
        if (message.isSeen) return

        sharedUtils.markAsSeen(
            mailbox = mailbox,
            threads = message.threads.filter { it.folderId == message.folderId },
            message = message,
            shouldRefreshThreads = false,
        )
    }

    private fun getLatestDraft(draftLocalUuid: String?): Draft? {
        return draftLocalUuid?.let(draftController::getDraft)?.copyFromRealm()
    }

    private fun fetchDraft(): Draft? {
        return ApiRepository.getDraft(draftResource!!).data?.also { draft ->
            draft.initLocalValues(messageUid!!)
        }
    }

    private fun MutableRealm.createDraft(signatures: List<Signature>): Draft? = Draft().apply {
        initLocalValues(mimeType = ClipDescription.MIMETYPE_TEXT_HTML)

        val shouldPreselectSignature = draftMode == DraftMode.REPLY || draftMode == DraftMode.REPLY_ALL
        initSignature(realm = this@createDraft, addContent = !shouldPreselectSignature)

        when (draftMode) {
            DraftMode.NEW_MAIL -> recipient?.let { to = realmListOf(it) }
            DraftMode.REPLY, DraftMode.REPLY_ALL, DraftMode.FORWARD -> {
                previousMessageUid
                    ?.let { uid -> MessageController.getMessage(uid, realm = this@createDraft) }
                    ?.let { message ->
                        val isSuccess = draftController.setPreviousMessage(
                            draft = this,
                            draftMode = draftMode,
                            message = message,
                            realm = this@createDraft,
                        )
                        if (!isSuccess) return null

                        if (shouldPreselectSignature) {
                            val mostFittingSignature = guessMostFittingSignature(message, signatures)
                            identityId = mostFittingSignature.id.toString()
                            body += encapsulateSignatureContentWithInfomaniakClass(mostFittingSignature.content)
                        }
                    }
            }
        }
    }

    private fun guessMostFittingSignature(message: Message, signatures: List<Signature>): Signature {
        var defaultSignature: Signature? = null

        val signatureEmailsMap = signatures.groupBy { signature ->
            if (signature.isDefault) defaultSignature = signature
            signature.senderEmail
        }

        findSignatureInRecipients(message.to, signatureEmailsMap)?.let { return it }
        findSignatureInRecipients(message.from, signatureEmailsMap)?.let { return it }
        findSignatureInRecipients(message.cc, signatureEmailsMap)?.let { return it }

        return defaultSignature!!
    }

    private fun findSignatureInRecipients(
        recipients: RealmList<Recipient>,
        signatureEmailsMap: Map<String, List<Signature>>,
    ): Signature? {

        val matchingEmailRecipients = recipients.filter { it.email in signatureEmailsMap }
        if (matchingEmailRecipients.isEmpty()) return null // If no Recipient represents us, go to next Recipients

        var bestScore = NO_MATCH
        var bestSignature: Signature? = null
        matchingEmailRecipients.forEach { recipient ->
            val (score, signature) = computeScore(recipient, signatureEmailsMap[recipient.email]!!)
            when (score) {
                EXACT_MATCH_AND_IS_DEFAULT -> return signature
                else -> {
                    if (score.strictlyGreaterThan(bestScore)) {
                        bestScore = score
                        bestSignature = signature
                    }
                }
            }
        }

        return bestSignature
    }

    /**
     * Only pass in Signatures that have the same email address as the Recipient
     */
    private fun computeScore(recipient: Recipient, signatures: List<Signature>): Pair<SignatureScore, Signature> {
        var bestScore: SignatureScore = NO_MATCH
        var bestSignature: Signature? = null

        signatures.forEach { signature ->
            when (val score = computeScore(recipient, signature)) {
                EXACT_MATCH_AND_IS_DEFAULT -> return score to signature
                else -> if (score.strictlyGreaterThan(bestScore)) {
                    bestScore = score
                    bestSignature = signature
                }
            }
        }

        return bestScore to bestSignature!!
    }

    /**
     * Only pass in a Signature that has the same email address as the Recipient
     */
    private fun computeScore(recipient: Recipient, signature: Signature): SignatureScore {
        val isExactMatch = recipient.name == signature.senderName
        val isDefault = signature.isDefault

        val score = when {
            isExactMatch && isDefault -> EXACT_MATCH_AND_IS_DEFAULT
            isExactMatch -> EXACT_MATCH
            isDefault -> ONLY_EMAIL_MATCH_AND_IS_DEFAULT
            else -> ONLY_EMAIL_MATCH
        }

        return score
    }

    private fun splitSignatureAndQuoteFromBody() {

        fun Document.split(divClassName: String, defaultValue: String): Pair<String, String?> {
            return getElementsByClass(divClassName).firstOrNull()?.let {
                it.remove()
                val first = body().html()
                val second = if (it.html().isBlank()) null else it.outerHtml()
                first to second
            } ?: (defaultValue to null)
        }

        fun String.lastIndexOfOrMax(string: String): Int {
            val index = lastIndexOf(string)
            return if (index == -1) Int.MAX_VALUE else index
        }

        val doc = Jsoup.parse(draft.body)

        val (bodyWithQuote, signature) = doc.split(MessageBodyUtils.INFOMANIAK_SIGNATURE_HTML_CLASS_NAME, draft.body)

        val replyPosition = draft.body.lastIndexOfOrMax(MessageBodyUtils.INFOMANIAK_REPLY_QUOTE_HTML_CLASS_NAME)
        val forwardPosition = draft.body.lastIndexOfOrMax(MessageBodyUtils.INFOMANIAK_FORWARD_QUOTE_HTML_CLASS_NAME)
        val (body, quote) = if (replyPosition < forwardPosition) {
            doc.split(MessageBodyUtils.INFOMANIAK_REPLY_QUOTE_HTML_CLASS_NAME, bodyWithQuote)
        } else {
            doc.split(MessageBodyUtils.INFOMANIAK_FORWARD_QUOTE_HTML_CLASS_NAME, bodyWithQuote)
        }

        draft.apply {
            uiBody = body.htmlToText()
            uiSignature = signature
            uiQuote = quote
        }
    }

    private fun saveDraftSnapshot() = with(draft) {
        snapshot = DraftSnapshot(
            identityId,
            to.toSet(),
            cc.toSet(),
            bcc.toSet(),
            subject,
            uiBody,
            attachments.map { it.uuid }.toSet(),
        )
    }

    fun updateDraftInLocalIfRemoteHasChanged() = viewModelScope.launch(ioCoroutineContext) {
        if (draft.remoteUuid == null) {
            draftController.getDraft(draft.localUuid)?.let { localDraft ->
                draft.remoteUuid = localDraft.remoteUuid
                draft.messageUid = localDraft.messageUid
            }
        }
    }

    fun addRecipientToField(recipient: Recipient, type: FieldType) = with(draft) {
        if (type == FieldType.CC || type == FieldType.BCC) otherFieldsAreAllEmpty.value = false

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

        if (cc.isEmpty() && bcc.isEmpty()) otherFieldsAreAllEmpty.value = true

        updateIsSendingAllowed()
        saveDraftDebouncing()
        context.trackNewMessageEvent("deleteRecipient")
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

    // In case the app crashes, the battery dies or any other unexpected situation, we always save every modifications of the draft in realm
    fun saveDraftDebouncing() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(ioCoroutineContext) {
            delay(DELAY_BEFORE_AUTO_SAVING_DRAFT)
            saveDraftToLocal(DraftAction.SAVE)
        }
    }

    fun executeDraftActionWhenStopping(
        action: DraftAction,
        isFinishing: Boolean,
        isTaskRoot: Boolean,
        startWorkerCallback: () -> Unit,
    ) = globalCoroutineScope.launch(ioDispatcher) {
        autoSaveJob?.cancel()

        if (isSavingDraftWithoutChanges(action)) {
            if (isNewMessage) removeDraftFromRealm()
            return@launch
        }

        context.trackSendingDraftEvent(action, draft)
        saveDraftToLocal(action)
        showDraftToastToUser(action, isFinishing, isTaskRoot)
        startWorkerCallback()
        if (action == DraftAction.SAVE && !isFinishing) {
            isNewMessage = false
            saveDraftSnapshot()
        }
    }

    private suspend fun showDraftToastToUser(
        action: DraftAction,
        isFinishing: Boolean,
        isTaskRoot: Boolean,
    ) = withContext(mainDispatcher) {
        when (action) {
            DraftAction.SAVE -> {
                if (isFinishing) {
                    if (isTaskRoot) context.showToast(R.string.snackbarDraftSaving)
                } else {
                    context.showToast(R.string.snackbarDraftSaving)
                }
            }
            DraftAction.SEND -> {
                if (isTaskRoot) context.showToast(R.string.snackbarEmailSending)
            }
        }
    }

    private fun removeDraftFromRealm() {
        mailboxContentRealm().writeBlocking {
            DraftController.getDraft(draft.localUuid, realm = this)?.let(::delete)
        }
    }

    fun synchronizeViewModelDraftFromRealm() = viewModelScope.launch(ioCoroutineContext) {
        draftController.getDraft(draft.localUuid)?.let { draft = it.copyFromRealm() }
    }

    private fun saveDraftToLocal(action: DraftAction) {
        SentryLog.d("Draft", "Save Draft to local")

        draft.body = draft.uiBody.textToHtml() + (draft.uiSignature ?: "") + (draft.uiQuote ?: "")
        draft.action = action

        mailboxContentRealm().writeBlocking {
            draftController.upsertDraft(draft, realm = this)
            draft.messageUid?.let { MessageController.getMessage(it, realm = this)?.draftLocalUuid = draft.localUuid }
        }
    }

    private fun isSavingDraftWithoutChanges(action: DraftAction) = action == DraftAction.SAVE && snapshot?.hasChanges() != true

    fun updateIsSendingAllowed() {
        isSendingAllowed.postValue(draft.to.isNotEmpty() || draft.cc.isNotEmpty() || draft.bcc.isNotEmpty())
    }

    fun importAttachments(uris: List<Uri>) = viewModelScope.launch(ioCoroutineContext) {

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
        val (fileName, fileSize) = context.getFileNameAndSize(uri) ?: return null
        if (fileSize > availableSpace) return null to true

        return LocalStorageUtils.saveUploadAttachment(context, uri, fileName, draft.localUuid)
            ?.let { file ->
                val mimeType = file.path.guessMimeType()
                Attachment().apply { initLocalValues(file.name, file.length(), mimeType, file.toUri().toString()) } to false
            } ?: (null to false)
    }

    override fun onCleared() {
        LocalStorageUtils.deleteAttachmentsUploadsDirIfEmpty(context, draft.localUuid)
        autoSaveJob?.cancel()
        super.onCleared()
    }

    enum class ImportationResult {
        SUCCESS,
        FILE_SIZE_TOO_BIG,
    }

    enum class SignatureScore(private val weight: Int) {
        EXACT_MATCH_AND_IS_DEFAULT(4),
        EXACT_MATCH(3),
        ONLY_EMAIL_MATCH_AND_IS_DEFAULT(2),
        ONLY_EMAIL_MATCH(1),
        NO_MATCH(0);

        fun strictlyGreaterThan(other: SignatureScore): Boolean = weight > other.weight
    }

    private data class DraftSnapshot(
        val identityId: String?,
        val to: Set<Recipient>,
        val cc: Set<Recipient>,
        val bcc: Set<Recipient>,
        var subject: String?,
        var body: String,
        val attachmentsUuids: Set<String>,
    )

    private fun DraftSnapshot.hasChanges(): Boolean {
        return identityId != draft.identityId ||
                to != draft.to.toSet() ||
                cc != draft.cc.toSet() ||
                bcc != draft.bcc.toSet() ||
                subject != draft.subject ||
                body != draft.uiBody ||
                attachmentsUuids != draft.attachments.map { it.uuid }.toSet()
    }

    private companion object {
        const val DELAY_BEFORE_AUTO_SAVING_DRAFT = 1_000L
        const val FILE_SIZE_25_MB = 25L * 1_024L * 1_024L
    }
}
