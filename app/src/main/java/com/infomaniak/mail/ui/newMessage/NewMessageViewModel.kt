/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.newMessage

import android.app.Application
import android.content.ClipDescription
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.MailTo
import androidx.core.net.toUri
import androidx.lifecycle.*
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.MatomoMail.OPEN_LOCAL_DRAFT
import com.infomaniak.mail.MatomoMail.trackExternalEvent
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
import com.infomaniak.mail.data.models.FeatureFlag
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Body
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MainDispatcher
import com.infomaniak.mail.ui.newMessage.NewMessageEditorManager.EditorAction
import com.infomaniak.mail.ui.newMessage.NewMessageRecipientFieldsManager.FieldType
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel.SignatureScore.*
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.ContactUtils.arrangeMergedContacts
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.extensions.appContext
import com.infomaniak.mail.utils.extensions.htmlToText
import com.infomaniak.mail.utils.extensions.isEmail
import com.infomaniak.mail.utils.extensions.textToHtml
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject

@HiltViewModel
class NewMessageViewModel @Inject constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val aiSharedData: AiSharedData,
    private val draftController: DraftController,
    private val globalCoroutineScope: CoroutineScope,
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val mailboxController: MailboxController,
    private val mergedContactController: MergedContactController,
    private val notificationManagerCompat: NotificationManagerCompat,
    private val sharedUtils: SharedUtils,
    private val signatureUtils: SignatureUtils,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    var draftInRAM: Draft = Draft()
    var isAutoCompletionOpened = false
    var isEditorExpanded = false
    var isExternalBannerManuallyClosed = false
    var shouldSendInsteadOfSave = false

    private var snapshot: DraftSnapshot? = null

    var otherFieldsAreAllEmpty = MutableLiveData(true)
    var initializeFieldsAsOpen = SingleLiveEvent<Boolean>()
    val importedAttachments = SingleLiveEvent<Pair<MutableList<Attachment>, ImportationResult>>()
    val isSendingAllowed = SingleLiveEvent(false)
    val externalRecipientCount = SingleLiveEvent<Pair<String?, Int>>()
    // Boolean: For toggleable actions, `false` if the formatting has been removed and `true` if the formatting has been applied.
    val editorAction = SingleLiveEvent<Pair<EditorAction, Boolean?>>()
    // Needs to trigger every time the Fragment is recreated
    val initResult = MutableLiveData<InitResult>()

    val currentMailbox by lazy { mailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)!! }

    val currentMailboxLive = mailboxController.getMailboxAsync(
        AccountUtils.currentUserId,
        AccountUtils.currentMailboxId,
    ).map { it.obj }.asLiveData(ioCoroutineContext)

    val mergedContacts = liveData(ioCoroutineContext) {
        val list = mergedContactController.getMergedContacts(sorted = true).copyFromRealm()
        emit(list to arrangeMergedContacts(list))
    }

    private val arrivedFromExistingDraft
        inline get() = savedStateHandle.get<Boolean>(NewMessageActivityArgs::arrivedFromExistingDraft.name) ?: false
    private val draftLocalUuid
        inline get() = savedStateHandle.get<String?>(NewMessageActivityArgs::draftLocalUuid.name)
    private val draftMode
        inline get() = savedStateHandle.get<DraftMode>(NewMessageActivityArgs::draftMode.name) ?: DraftMode.NEW_MAIL
    private val draftResource
        inline get() = savedStateHandle.get<String?>(NewMessageActivityArgs::draftResource.name)
    private val mailToUri
        inline get() = savedStateHandle.get<Uri?>(NewMessageActivityArgs::mailToUri.name)
    private val messageUid
        inline get() = savedStateHandle.get<String?>(NewMessageActivityArgs::messageUid.name)
    private val notificationId
        inline get() = savedStateHandle.get<Int>(NewMessageActivityArgs::notificationId.name) ?: -1
    private val previousMessageUid
        inline get() = savedStateHandle.get<String?>(NewMessageActivityArgs::previousMessageUid.name)
    private val recipient
        inline get() = savedStateHandle.get<Recipient?>(NewMessageActivityArgs::recipient.name)
    private val shouldLoadDistantResources
        inline get() = savedStateHandle.get<Boolean>(NewMessageActivityArgs::shouldLoadDistantResources.name) ?: false

    fun arrivedFromExistingDraft() = arrivedFromExistingDraft
    fun draftLocalUuid() = draftLocalUuid
    fun draftMode() = draftMode
    fun recipient() = recipient
    fun shouldLoadDistantResources() = shouldLoadDistantResources

    fun initDraftAndViewModel(intent: Intent): LiveData<Boolean> = liveData(ioCoroutineContext) {

        val realm = mailboxContentRealm()
        var signatures = emptyList<Signature>()

        val draft = runCatching {

            signatures = SignatureController.getAllSignatures(realm)
            if (signatures.isEmpty()) return@runCatching null

            val draftExists = arrivedFromExistingDraft || draftLocalUuid != null

            val newDraft = if (draftExists) {
                getExistingDraft(draftLocalUuid, realm) ?: return@runCatching null
            } else {
                getNewDraft(signatures, realm) ?: return@runCatching null
            }

            if (newDraft.body.isNotEmpty()) splitSignatureAndQuoteFromBody(newDraft)
            if (!draftExists) populateWithExternalMailDataIfNeeded(newDraft, intent)
            newDraft.flagRecipientsAsAutomaticallyEntered()

            return@runCatching newDraft
        }.getOrElse {
            Sentry.captureException(it)
            return@getOrElse null
        }

        if (draft != null) {

            dismissNotification()
            markAsRead(currentMailbox, realm)

            realm.writeBlocking { draftController.upsertDraft(draft, realm = this) }
            moveDataFromDraftToLiveData(draft)
            draft.saveDraftSnapshot()

            if (draft.cc.isNotEmpty() || draft.bcc.isNotEmpty()) {
                otherFieldsAreAllEmpty.postValue(false)
                initializeFieldsAsOpen.postValue(true)
            }

            draftInRAM = draft

            initResult.postValue(InitResult(draft, signatures))
        }

        val isSuccess = draft != null
        emit(isSuccess)
    }

    //region Initialization: 1st level of private fun
    private fun getExistingDraft(localUuid: String?, realm: Realm): Draft? {
        return getLocalOrRemoteDraft(localUuid)?.also {
            if (it.identityId.isNullOrBlank()) signatureUtils.addMissingSignatureData(it, realm)
        }
    }

    private suspend fun getNewDraft(signatures: List<Signature>, realm: Realm): Draft? = Draft().apply {

        this.initLocalValues(mimeType = ClipDescription.MIMETYPE_TEXT_HTML)

        val shouldPreselectSignature = draftMode == DraftMode.REPLY || draftMode == DraftMode.REPLY_ALL
        signatureUtils.initSignature(draft = this, realm, addContent = !shouldPreselectSignature)

        when (draftMode) {
            DraftMode.NEW_MAIL -> recipient?.let { to = realmListOf(it) }
            DraftMode.REPLY, DraftMode.REPLY_ALL, DraftMode.FORWARD -> {
                previousMessageUid
                    ?.let { uid -> MessageController.getMessage(uid, realm) }
                    ?.let { previousMessage ->
                        val (fullMessage, hasFailedFetching) = draftController.fetchHeavyDataIfNeeded(previousMessage, realm)

                        if (hasFailedFetching) return null

                        draftController.setPreviousMessage(draft = this, draftMode = draftMode, previousMessage = fullMessage)

                        val isAiEnabled = currentMailbox.featureFlags.contains(FeatureFlag.AI)
                        if (isAiEnabled) parsePreviousMailToAnswerWithAi(fullMessage.body!!, fullMessage.uid)
                        if (shouldPreselectSignature) preSelectSignature(previousMessage, signatures)
                    }
            }
        }
    }

    private fun splitSignatureAndQuoteFromBody(draft: Draft) {

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

        val doc = Jsoup.parse(draft.body).also { it.outputSettings().prettyPrint(false) }

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

    private fun populateWithExternalMailDataIfNeeded(draft: Draft, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> handleSingleSendIntent(draft, intent)
            Intent.ACTION_SEND_MULTIPLE -> handleMultipleSendIntent(draft, intent)
            Intent.ACTION_VIEW, Intent.ACTION_SENDTO -> handleMailTo(draft, intent.data, intent)
        }

        if (mailToUri != null) handleMailTo(draft, mailToUri)
    }

    private fun Draft.flagRecipientsAsAutomaticallyEntered() {
        to.flagRecipientsAsAutomaticallyEntered()
        cc.flagRecipientsAsAutomaticallyEntered()
        bcc.flagRecipientsAsAutomaticallyEntered()
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

    private fun moveDataFromDraftToLiveData(draft: Draft) {

        savedStateHandle[NewMessageActivityArgs::draftLocalUuid.name] = draft.localUuid
    }

    private fun Draft.saveDraftSnapshot() {
        snapshot = DraftSnapshot(
            identityId = identityId,
            to = to.toSet(),
            cc = cc.toSet(),
            bcc = bcc.toSet(),
            subject = subject,
            body = uiBody,
            attachmentsUuids = attachments.map { it.uuid }.toSet(),
        )
    }
    //endregion

    //region Initialization: 2nd level of private fun
    private fun getLocalOrRemoteDraft(localUuid: String?): Draft? {

        fun trackOpenLocal(draft: Draft) { // Unused but required to use references inside the `also` block, used for readability
            appContext.trackNewMessageEvent(OPEN_LOCAL_DRAFT, TrackerAction.DATA, value = 1.0f)
        }

        fun trackOpenRemote(draft: Draft) { // Unused but required to use references inside the `also` block, used for readability
            appContext.trackNewMessageEvent(OPEN_LOCAL_DRAFT, TrackerAction.DATA, value = 0.0f)
        }

        return getLatestLocalDraft(localUuid)?.also(::trackOpenLocal) ?: fetchDraft()?.also(::trackOpenRemote)
    }

    private fun getLatestLocalDraft(localUuid: String?) = localUuid?.let(draftController::getDraft)?.copyFromRealm()

    private fun fetchDraft(): Draft? = ApiRepository.getDraft(draftResource!!).data?.also { it.initLocalValues(messageUid!!) }

    private suspend fun parsePreviousMailToAnswerWithAi(previousMessageBody: Body, messageUid: String) {
        if (draftMode == DraftMode.REPLY || draftMode == DraftMode.REPLY_ALL) {
            aiSharedData.previousMessageBodyPlainText = previousMessageBody.asPlainText(messageUid)
        }
    }

    private suspend fun Body.asPlainText(messageUid: String): String = when (type) {
        Utils.TEXT_HTML -> {
            val splitBodyContent = MessageBodyUtils.splitContentAndQuote(this).content
            val fullBody = MessageBodyUtils.mergeSplitBodyAndSubBodies(splitBodyContent, subBodies, messageUid)
            fullBody.htmlToText()
        }
        else -> value
    }

    private fun Draft.preSelectSignature(message: Message, signatures: List<Signature>) {
        val mostFittingSignature = guessMostFittingSignature(message, signatures)
        identityId = mostFittingSignature.id.toString()
        body += signatureUtils.encapsulateSignatureContentWithInfomaniakClass(mostFittingSignature.content)
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

    private fun handleSingleSendIntent(draft: Draft, intent: Intent) = with(intent) {

        if (hasExtra(Intent.EXTRA_TEXT)) {
            getStringExtra(Intent.EXTRA_SUBJECT)?.let { draft.subject = it }
            getStringExtra(Intent.EXTRA_TEXT)?.let { draft.uiBody = it }
        }

        if (hasExtra(Intent.EXTRA_STREAM)) {
            (parcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uri ->
                importAttachments(uris = listOf(uri), draft)
            }
        }
    }

    private fun handleMultipleSendIntent(draft: Draft, intent: Intent) {
        intent
            .parcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
            ?.filterIsInstance<Uri>()
            ?.let { importAttachments(uris = it, draft) }
    }

    /**
     * Handle `MailTo` from [Intent.ACTION_VIEW] or [Intent.ACTION_SENDTO]
     * Get [Intent.ACTION_VIEW] data with [MailTo] and [Intent.ACTION_SENDTO] with [Intent]
     */
    private fun handleMailTo(draft: Draft, uri: Uri?, intent: Intent? = null) {

        /**
         * Mailto grammar accept 'name_of_recipient<email>' for recipients
         */
        fun parseEmailWithName(recipient: String): Recipient? {
            val nameAndEmail = Regex("(.+)<(.+)>").find(recipient)?.destructured
            return nameAndEmail?.let { (name, email) -> if (email.isEmail()) Recipient().initLocalValues(email, name) else null }
        }

        fun String.splitToRecipientList() = split(",", ";").mapNotNull {
            val email = it.trim()
            if (email.isEmail()) Recipient().initLocalValues(email, email) else parseEmailWithName(email)
        }

        if (uri == null || !MailTo.isMailTo(uri)) return

        val mailToIntent = MailTo.parse(uri)
        val splitTo = mailToIntent.to?.splitToRecipientList()
            ?: emptyList()
        val splitCc = mailToIntent.cc?.splitToRecipientList()
            ?: intent?.getStringArrayExtra(Intent.EXTRA_CC)?.map { Recipient().initLocalValues(it, it) }
            ?: emptyList()
        val splitBcc = mailToIntent.bcc?.splitToRecipientList()
            ?: intent?.getStringArrayExtra(Intent.EXTRA_BCC)?.map { Recipient().initLocalValues(it, it) }
            ?: emptyList()

        draft.apply {
            to.addAll(splitTo)
            cc.addAll(splitCc)
            bcc.addAll(splitBcc)

            subject = mailToIntent.subject ?: intent?.getStringExtra(Intent.EXTRA_SUBJECT)
            uiBody = mailToIntent.body ?: intent?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        }
    }

    private fun importAttachments(uris: List<Uri>, draft: Draft) = viewModelScope.launch(ioCoroutineContext) {

        val newAttachments = mutableListOf<Attachment>()
        var attachmentsSize = draft.attachments.sumOf { it.size }
        var result = ImportationResult.SUCCESS

        uris.forEach { uri ->
            val availableSpace = ATTACHMENTS_MAX_SIZE - attachmentsSize
            val (attachment, hasSizeLimitBeenReached) = importAttachment(draft.localUuid, uri, availableSpace) ?: return@forEach

            if (hasSizeLimitBeenReached) result = ImportationResult.FILE_SIZE_TOO_BIG

            attachment?.let {
                newAttachments.add(it)
                attachmentsSize += it.size
            }
        }

        importedAttachments.postValue(newAttachments to result)
    }

    private fun importAttachment(localUuid: String, uri: Uri, availableSpace: Long): Pair<Attachment?, Boolean>? {

        val (fileName, fileSize) = appContext.getFileNameAndSize(uri) ?: return null
        val attachment = Attachment()

        return LocalStorageUtils.saveAttachmentToUploadDir(appContext, uri, fileName, localUuid, attachment.localUuid)
            ?.let { file ->
                Pair(
                    attachment.initLocalValues(fileName, file.length(), file.path.guessMimeType(), file.toUri().toString()),
                    fileSize > availableSpace,
                )
            }
    }

    private fun RealmList<Recipient>.flagRecipientsAsAutomaticallyEntered() {
        forEach { recipient ->
            recipient.isManuallyEntered = false
        }
    }
    //endregion

    fun addRecipientToField(recipient: Recipient, type: FieldType) = with(draftInRAM) {

        if (type == FieldType.CC || type == FieldType.BCC) otherFieldsAreAllEmpty.value = false

        val field = when (type) {
            FieldType.TO -> to
            FieldType.CC -> cc
            FieldType.BCC -> bcc
        }
        field.add(recipient)

        updateIsSendingAllowed()
    }

    fun removeRecipientFromField(recipient: Recipient, type: FieldType) = with(draftInRAM) {

        val field = when (type) {
            FieldType.TO -> to
            FieldType.CC -> cc
            FieldType.BCC -> bcc
        }
        field.remove(recipient)

        if (cc.isEmpty() && bcc.isEmpty()) otherFieldsAreAllEmpty.value = true

        updateIsSendingAllowed()

        appContext.trackNewMessageEvent("deleteRecipient")
        if (recipient.isDisplayedAsExternal) appContext.trackExternalEvent("deleteRecipient")
    }

    fun updateIsSendingAllowed() {
        isSendingAllowed.value = if (draftInRAM.hasRecipient()) {
            var size = 0L
            var isSizeCorrect = true
            for (attachment in draftInRAM.attachments) {
                size += attachment.size
                if (size > ATTACHMENTS_MAX_SIZE) {
                    isSizeCorrect = false
                    break
                }
            }
            isSizeCorrect
        } else {
            false
        }
    }

    fun updateMailSubject(newSubject: String?) = with(draftInRAM) {
        if (newSubject != subject) subject = newSubject
    }

    fun updateMailBody(newBody: String) = with(draftInRAM) {
        if (newBody != uiBody) uiBody = newBody
    }

    fun importAttachmentsToCurrentDraft(uris: List<Uri>) {
        importAttachments(uris, draftInRAM)
    }

    fun executeDraftActionWhenStopping(
        action: DraftAction,
        isFinishing: Boolean,
        isTaskRoot: Boolean,
        startWorkerCallback: () -> Unit,
    ) = globalCoroutineScope.launch(ioDispatcher) {

        val draft = getLatestLocalDraft(draftLocalUuid) ?: return@launch

        draft.updateDraft(action)

        if (!arrivedFromExistingDraft && isFinishing && isSavingDraftWithoutChanges(draft, action)) {
            removeDraftFromRealm(draft.localUuid)
            return@launch
        }

        appContext.trackSendingDraftEvent(action, draft, currentMailbox.externalMailFlagEnabled)

        saveDraftToLocal(draft)

        if (isFinishing) {
            if (isTaskRoot) showDraftToastToUser(action)
            startWorkerCallback()
        }
    }

    override fun onCleared() {
        draftLocalUuid?.let { LocalStorageUtils.deleteDraftUploadDir(appContext, draftLocalUuid = it) }
        super.onCleared()
    }

    private fun Draft.updateDraft(draftAction: DraftAction) {

        action = draftAction
        identityId = draftInRAM.identityId

        to = draftInRAM.to
        cc = draftInRAM.cc
        bcc = draftInRAM.bcc

        attachments.apply {
            clear()
            addAll(draftInRAM.attachments)
        }

        subject = draftInRAM.subject?.take(SUBJECT_MAX_LENGTH)

        uiBody = draftInRAM.uiBody
        uiSignature = draftInRAM.uiSignature
        uiQuote = draftInRAM.uiQuote

        body = uiBody.textToHtml() + (uiSignature ?: "") + (uiQuote ?: "")
    }

    private fun isSavingDraftWithoutChanges(draft: Draft, action: DraftAction): Boolean {
        return action == DraftAction.SAVE && snapshot?.hasChanges(draft) != true
    }

    private fun DraftSnapshot.hasChanges(draft: Draft): Boolean {
        return identityId != draft.identityId ||
                to != draft.to.toSet() ||
                cc != draft.cc.toSet() ||
                bcc != draft.bcc.toSet() ||
                subject != draft.subject ||
                body != draft.uiBody ||
                attachmentsUuids != draft.attachments.map { it.uuid }.toSet()
    }

    private fun removeDraftFromRealm(localUuid: String) {
        mailboxContentRealm().writeBlocking {
            DraftController.getDraft(localUuid, realm = this)?.let(::delete)
        }
    }

    private fun saveDraftToLocal(draft: Draft) {
        SentryLog.d("Draft", "Save Draft to local")
        mailboxContentRealm().writeBlocking {
            draftController.upsertDraft(draft, realm = this)
            messageUid?.let { MessageController.getMessage(uid = it, realm = this)?.draftLocalUuid = draft.localUuid }
        }
    }

    private suspend fun showDraftToastToUser(action: DraftAction) = withContext(mainDispatcher) {
        val resId = when (action) {
            DraftAction.SAVE -> R.string.snackbarDraftSaving
            DraftAction.SEND -> R.string.snackbarEmailSending
        }
        appContext.showToast(resId)
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

    data class InitResult(
        val draft: Draft,
        val signatures: List<Signature>,
    )

    private data class DraftSnapshot(
        val identityId: String?,
        val to: Set<Recipient>,
        val cc: Set<Recipient>,
        val bcc: Set<Recipient>,
        var subject: String?,
        var body: String,
        val attachmentsUuids: Set<String>,
    )

    companion object {
        private const val ATTACHMENTS_MAX_SIZE = 25L * 1_024L * 1_024L // 25 MB
        private const val SUBJECT_MAX_LENGTH = 998
    }
}
