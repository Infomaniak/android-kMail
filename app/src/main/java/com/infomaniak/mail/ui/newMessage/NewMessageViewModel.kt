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
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.newMessage.NewMessageEditorManager.EditorAction
import com.infomaniak.mail.ui.newMessage.NewMessageRecipientFieldsManager.FieldType
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel.SignatureScore.*
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.ContactUtils.arrangeMergedContacts
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.extensions.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
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
    private val snackbarManager: SnackbarManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    //region UI data
    val fromLiveData = MutableLiveData<UiFrom>()
    val toLiveData = MutableLiveData<UiRecipients>()
    val ccLiveData = MutableLiveData<UiRecipients>()
    val bccLiveData = MutableLiveData<UiRecipients>()
    val attachmentsLiveData = MutableLiveData<List<Attachment>>()
    val uiSignatureLiveData = MutableLiveData<String?>()
    val uiQuoteLiveData = MutableLiveData<String?>()
    //endregion

    var lastOnStopSubjectValue = ""
    var lastOnStopBodyValue = ""

    var isAutoCompletionOpened = false
    var isEditorExpanded = false
    var isExternalBannerManuallyClosed = false
    var shouldSendInsteadOfSave = false
    var signaturesCount = 0
    private var isNewMessage = false

    private var snapshot: DraftSnapshot? = null

    var otherRecipientsFieldsAreEmpty = MutableLiveData(true)
    var initializeFieldsAsOpen = SingleLiveEvent<Boolean>()
    val importAttachmentsLiveData = SingleLiveEvent<List<Uri>>()
    val importAttachmentsResult = SingleLiveEvent<ImportationResult>()
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

    fun initDraftAndViewModel(intent: Intent): LiveData<Draft?> = liveData(ioCoroutineContext) {

        val realm = mailboxContentRealm()
        var signatures = emptyList<Signature>()

        var draft: Draft? = null

        runCatching {

            signatures = SignatureController.getAllSignatures(realm)
                .also { signaturesCount = it.count() }
                .toMutableList()
                .apply { add(index = 0, element = Signature.getDummySignature(appContext, email = currentMailbox.email)) }

            isNewMessage = !arrivedFromExistingDraft && draftLocalUuid == null

            draft = if (isNewMessage) {
                getNewDraft(signatures, intent, realm) ?: return@runCatching
            } else {
                getExistingDraft(draftLocalUuid, realm) ?: return@runCatching
            }
        }.onFailure(Sentry::captureException)

        draft?.let {

            it.flagRecipientsAsAutomaticallyEntered()

            dismissNotification()
            markAsRead(currentMailbox, realm)

            realm.writeBlocking { draftController.upsertDraft(it, realm = this) }
            it.saveSnapshot()
            it.initLiveData(signatures)

            initResult.postValue(InitResult(it, signatures))
        }

        emit(draft)
    }

    private fun getExistingDraft(localUuid: String?, realm: Realm): Draft? {
        return getLocalOrRemoteDraft(localUuid)?.also { draft ->
            saveNavArgsToSavedState(draft.localUuid)
            if (draft.identityId.isNullOrBlank()) {
                draft.identityId = SignatureController.getDefaultSignatureWithFallback(realm, draftMode)?.id?.toString()
            }
            if (draft.body.isNotEmpty()) splitSignatureAndQuoteFromBody(draft)
        }
    }

    private suspend fun getNewDraft(signatures: List<Signature>, intent: Intent, realm: Realm): Draft? = Draft().apply {

        var previousMessage: Message? = null

        initLocalValues(mimeType = ClipDescription.MIMETYPE_TEXT_HTML)
        saveNavArgsToSavedState(localUuid)

        when (draftMode) {
            DraftMode.NEW_MAIL -> recipient?.let { to = realmListOf(it) }
            DraftMode.REPLY, DraftMode.REPLY_ALL, DraftMode.FORWARD -> {
                previousMessageUid
                    ?.let { uid -> MessageController.getMessage(uid, realm) }
                    ?.let { message ->
                        val (fullMessage, hasFailedFetching) = draftController.fetchHeavyDataIfNeeded(message, realm)
                        if (hasFailedFetching) return null

                        draftController.setPreviousMessage(draft = this, draftMode = draftMode, previousMessage = fullMessage)

                        val isAiEnabled = currentMailbox.featureFlags.contains(FeatureFlag.AI)
                        if (isAiEnabled) parsePreviousMailToAnswerWithAi(fullMessage.body!!, fullMessage.uid)

                        previousMessage = fullMessage
                    }
            }
        }

        val defaultSignature = SignatureController.getDefaultSignature(realm, draftMode)
        val shouldPreselectSignature = draftMode == DraftMode.REPLY || draftMode == DraftMode.REPLY_ALL
        val signature = if (shouldPreselectSignature) {
            defaultSignature ?: guessMostFittingSignature(previousMessage!!, signatures)
        } else {
            defaultSignature
        }

        signatureUtils.initSignature(
            draft = this,
            signature = signature ?: Signature.getDummySignature(appContext, email = currentMailbox.email, isDefault = true),
        )

        populateWithExternalMailDataIfNeeded(draft = this, intent)

        body = getWholeBody()
    }

    private fun saveNavArgsToSavedState(localUuid: String) {
        savedStateHandle[NewMessageActivityArgs::draftLocalUuid.name] = localUuid

        // If the user put the app in background before we put the fetched Draft in Realm, and the system
        // kill the app, then we won't be able to fetch the Draft anymore as the `draftResource` will be null.
        savedStateHandle[NewMessageActivityArgs::draftResource.name] = draftResource
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

    private fun Draft.saveSnapshot() {
        snapshot = DraftSnapshot(
            identityId = identityId,
            to = to.toSet(),
            cc = cc.toSet(),
            bcc = bcc.toSet(),
            subject = subject,
            uiBody = uiBody,
            attachmentsLocalUuids = attachments.map { it.localUuid }.toSet(),
        )
    }

    private fun Draft.initLiveData(signatures: List<Signature>) {

        fromLiveData.postValue(
            UiFrom(
                signature = signatures.single { it.id == identityId?.toInt() },
                shouldUpdateBodySignature = false,
            ),
        )

        toLiveData.postValue(UiRecipients(recipients = to, otherFieldsAreEmpty = cc.isEmpty() && bcc.isEmpty()))
        ccLiveData.postValue(UiRecipients(recipients = cc))
        bccLiveData.postValue(UiRecipients(recipients = bcc))

        attachmentsLiveData.postValue(attachments)

        uiSignatureLiveData.postValue(uiSignature)
        uiQuoteLiveData.postValue(uiQuote)

        if (cc.isNotEmpty() || bcc.isNotEmpty()) {
            otherRecipientsFieldsAreEmpty.postValue(false)
            initializeFieldsAsOpen.postValue(true)
        }
    }

    private fun getLocalOrRemoteDraft(localUuid: String?): Draft? {

        @Suppress("UNUSED_PARAMETER")
        fun trackOpenLocal(draft: Draft) { // Unused but required to use references inside the `also` block, used for readability
            appContext.trackNewMessageEvent(OPEN_LOCAL_DRAFT, TrackerAction.DATA, value = 1.0f)
        }

        @Suppress("UNUSED_PARAMETER")
        fun trackOpenRemote(draft: Draft) { // Unused but required to use references inside the `also` block, used for readability
            appContext.trackNewMessageEvent(OPEN_LOCAL_DRAFT, TrackerAction.DATA, value = 0.0f)
        }

        return getLatestLocalDraft(localUuid)?.also(::trackOpenLocal) ?: fetchDraft()?.also(::trackOpenRemote)
    }

    private fun getLatestLocalDraft(localUuid: String?) = localUuid?.let(draftController::getDraft)?.copyFromRealm()

    private fun fetchDraft(): Draft? {
        return ApiRepository.getDraft(draftResource!!).data?.also {
            /**
             * If we are opening for the 1st time an existing Draft created somewhere else
             * (ex: webmail), we need to create the link between the Draft and its Message.
             * - The link in the Draft is added here, when creating the Draft.
             * - The link in the Message is added later, when saving the Draft.
             */
            it.initLocalValues(messageUid!!)
        }
    }

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

    private fun guessMostFittingSignature(message: Message, signatures: List<Signature>): Signature? {

        val signatureEmailsMap = signatures.groupBy { it.senderEmail }

        return findSignatureInRecipients(message.to, signatureEmailsMap)
            ?: findSignatureInRecipients(message.from, signatureEmailsMap)
            ?: findSignatureInRecipients(message.cc, signatureEmailsMap)
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
            val signatures = signatureEmailsMap[recipient.email] ?: return@forEach
            val (score, signature) = computeScore(recipient, signatures)
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

        if (hasExtra(Intent.EXTRA_EMAIL)) {
            handleMailTo(draft, intent.data, intent)
        } else if (hasExtra(Intent.EXTRA_TEXT)) {
            getStringExtra(Intent.EXTRA_SUBJECT)?.let { draft.subject = it }
            getStringExtra(Intent.EXTRA_TEXT)?.let { draft.uiBody = it }
        }

        if (hasExtra(Intent.EXTRA_STREAM)) {
            (parcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)
                ?.let { importAttachments(currentAttachments = draft.attachments, uris = listOf(it)) }
                ?.let(draft.attachments::addAll)
        }
    }

    private fun handleMultipleSendIntent(draft: Draft, intent: Intent) {
        intent.parcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
            ?.filterIsInstance<Uri>()
            ?.let { importAttachments(currentAttachments = draft.attachments, uris = it) }
            ?.let(draft.attachments::addAll)
    }

    /**
     * Handle `mailTo` from [Intent.ACTION_VIEW], [Intent.ACTION_SENDTO] or [Intent.ACTION_SEND].
     * Get [Intent.ACTION_VIEW] data with [MailTo] and [Intent.ACTION_SENDTO] or [Intent.ACTION_SEND] with [Intent].
     *
     * [Intent.ACTION_SEND] shouldn't be used for `mailTo` as it isn't meant to pass the Recipient of the mail,
     * but some apps don't follow the guidelines, so we support it anyway.
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

        fun Intent.getRecipientsFromIntent(recipientsFlag: String): List<Recipient>? {
            return getStringArrayExtra(recipientsFlag)?.map { Recipient().initLocalValues(it, it) }
        }

        val mailToIntent = runCatching { MailTo.parse(uri!!) }.getOrNull()
        if (mailToIntent == null && intent?.hasExtra(Intent.EXTRA_EMAIL) != true) return

        val splitTo = mailToIntent?.to?.splitToRecipientList()
            ?: intent?.getRecipientsFromIntent(Intent.EXTRA_EMAIL)
            ?: emptyList()
        val splitCc = mailToIntent?.cc?.splitToRecipientList()
            ?: intent?.getRecipientsFromIntent(Intent.EXTRA_CC)
            ?: emptyList()
        val splitBcc = mailToIntent?.bcc?.splitToRecipientList()
            ?: intent?.getRecipientsFromIntent(Intent.EXTRA_BCC)
            ?: emptyList()

        draft.apply {
            to.addAll(splitTo)
            cc.addAll(splitCc)
            bcc.addAll(splitBcc)

            subject = mailToIntent?.subject ?: intent?.getStringExtra(Intent.EXTRA_SUBJECT)
            uiBody = mailToIntent?.body ?: intent?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        }
    }

    fun importNewAttachments(
        currentAttachments: List<Attachment>,
        uris: List<Uri>,
        completion: (List<Attachment>) -> Unit,
    ) = viewModelScope.launch(ioCoroutineContext) {
        completion(importAttachments(currentAttachments, uris))
    }

    private fun importAttachments(currentAttachments: List<Attachment>, uris: List<Uri>): List<Attachment> {

        val newAttachments = mutableListOf<Attachment>()
        var attachmentsSize = currentAttachments.sumOf { it.size }
        var result = ImportationResult.SUCCESS

        uris.forEach { uri ->
            val availableSpace = ATTACHMENTS_MAX_SIZE - attachmentsSize
            val (attachment, hasSizeLimitBeenReached) = importAttachment(uri, availableSpace) ?: return@forEach

            if (hasSizeLimitBeenReached) result = ImportationResult.ATTACHMENTS_TOO_BIG

            attachment?.let {
                newAttachments.add(it)
                attachmentsSize += it.size
            }
        }

        importAttachmentsResult.postValue(result)

        return newAttachments
    }

    private fun importAttachment(uri: Uri, availableSpace: Long): Pair<Attachment?, Boolean>? {

        val (fileName, fileSize) = appContext.getFileNameAndSize(uri) ?: return null
        val attachment = Attachment()

        return LocalStorageUtils.saveAttachmentToUploadDir(
            context = appContext,
            uri = uri,
            fileName = fileName,
            draftLocalUuid = draftLocalUuid!!,
            attachmentLocalUuid = attachment.localUuid,
            snackbarManager = snackbarManager,
        )?.let { file ->
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

    fun addRecipientToField(recipient: Recipient, type: FieldType) {

        if (type == FieldType.CC || type == FieldType.BCC) otherRecipientsFieldsAreEmpty.value = false

        val recipientsLiveData = when (type) {
            FieldType.TO -> toLiveData
            FieldType.CC -> ccLiveData
            FieldType.BCC -> bccLiveData
        }

        recipientsLiveData.addRecipientThenSetValue(recipient)
    }

    fun removeRecipientFromField(recipient: Recipient, type: FieldType) {

        val recipientsLiveData = when (type) {
            FieldType.TO -> toLiveData
            FieldType.CC -> ccLiveData
            FieldType.BCC -> bccLiveData
        }

        recipientsLiveData.removeRecipientThenSetValue(recipient)

        appContext.trackNewMessageEvent("deleteRecipient")
        if (recipient.isDisplayedAsExternal) appContext.trackExternalEvent("deleteRecipient")
    }

    fun deleteAttachment(position: Int) {
        runCatching {
            val attachments = attachmentsLiveData.valueOrEmpty().toMutableList()
            val attachment = attachments[position]
            attachment.getUploadLocalFile()?.delete()
            LocalStorageUtils.deleteAttachmentUploadDir(appContext, draftLocalUuid!!, attachment.localUuid)
            attachments.removeAt(position)
            attachmentsLiveData.value = attachments
        }.onFailure { exception ->
            // TODO: If we don't see this Sentry after mid-2024, we can remove it.
            SentryLog.e(TAG, " Attachment $position doesn't exist", exception)
        }
    }

    fun updateIsSendingAllowed(
        attachments: List<Attachment> = attachmentsLiveData.valueOrEmpty(),
        type: FieldType? = null,
        recipients: List<Recipient> = emptyList(),
    ) {

        val allRecipients = when (type) {
            FieldType.TO -> recipients + ccLiveData.valueOrEmpty() + bccLiveData.valueOrEmpty()
            FieldType.CC -> toLiveData.valueOrEmpty() + recipients + bccLiveData.valueOrEmpty()
            FieldType.BCC -> toLiveData.valueOrEmpty() + ccLiveData.valueOrEmpty() + recipients
            null -> toLiveData.valueOrEmpty() + ccLiveData.valueOrEmpty() + bccLiveData.valueOrEmpty()
        }

        isSendingAllowed.value = if (allRecipients.isNotEmpty()) {
            var size = 0L
            var isSizeCorrect = true
            for (attachment in attachments) {
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

    fun updateOtherRecipientsFieldsAreEmpty(cc: List<Recipient>, bcc: List<Recipient>) {
        if (cc.isEmpty() && bcc.isEmpty()) otherRecipientsFieldsAreEmpty.value = true
    }

    fun updateBodySignature(signature: Signature) {
        uiSignatureLiveData.value = if (signature.isDummy) {
            null
        } else {
            signatureUtils.encapsulateSignatureContentWithInfomaniakClass(signature.content)
        }
    }

    fun uploadAttachmentsToServer(uiAttachments: List<Attachment>) = viewModelScope.launch(ioDispatcher) {
        val localUuid = draftLocalUuid ?: return@launch
        val localDraft = mailboxContentRealm().writeBlocking {
            DraftController.getDraft(localUuid, realm = this)?.also { it.updateDraftAttachmentsWithLiveData(uiAttachments) }
        } ?: return@launch

        runCatching {
            localDraft.uploadAttachments(currentMailbox, draftController, mailboxContentRealm())
        }.onFailure(Sentry::captureException)
    }

    fun executeDraftActionWhenStopping(
        action: DraftAction,
        isFinishing: Boolean,
        isTaskRoot: Boolean,
        subjectValue: String,
        uiBodyValue: String,
        startWorkerCallback: () -> Unit,
    ) = globalCoroutineScope.launch(ioDispatcher) {

        val localUuid = draftLocalUuid ?: return@launch
        val subject = subjectValue.ifBlank { null }?.take(SUBJECT_MAX_LENGTH)

        if (action == DraftAction.SAVE && isSnapshotTheSame(subject, uiBodyValue)) {
            if (isFinishing && isNewMessage) removeDraftFromRealm(localUuid)
            return@launch
        }

        val hasFailed = mailboxContentRealm().writeBlocking {
            DraftController.getDraft(localUuid, realm = this)
                ?.updateDraftFromLiveData(action, isFinishing, subject, uiBodyValue, realm = this@writeBlocking)
                ?: return@writeBlocking true
            return@writeBlocking false
        }

        if (hasFailed) return@launch

        showDraftToastToUser(action, isFinishing, isTaskRoot)
        startWorkerCallback()

        appContext.trackSendingDraftEvent(
            action = action,
            to = toLiveData.valueOrEmpty(),
            cc = ccLiveData.valueOrEmpty(),
            bcc = bccLiveData.valueOrEmpty(),
            externalMailFlagEnabled = currentMailbox.externalMailFlagEnabled,
        )
    }

    override fun onCleared() {
        draftLocalUuid?.let { LocalStorageUtils.deleteDraftUploadDir(appContext, draftLocalUuid = it) }
        super.onCleared()
    }

    private fun Draft.updateDraftFromLiveData(
        draftAction: DraftAction,
        isFinishing: Boolean,
        subjectValue: String?,
        uiBodyValue: String,
        realm: MutableRealm,
    ) {

        action = draftAction
        identityId = fromLiveData.value?.signature?.id.toString()

        to = toLiveData.valueOrEmpty().toRealmList()
        cc = ccLiveData.valueOrEmpty().toRealmList()
        bcc = bccLiveData.valueOrEmpty().toRealmList()

        updateDraftAttachmentsWithLiveData(attachmentsLiveData.valueOrEmpty())

        subject = subjectValue

        uiBody = uiBodyValue
        uiSignature = uiSignatureLiveData.value
        uiQuote = uiQuoteLiveData.value

        body = getWholeBody()

        /**
         * If we are opening for the 1st time an existing Draft created somewhere else
         * (ex: webmail), we need to create the link between the Draft and its Message.
         * - The link in the Draft is already added, when creating the Draft.
         * - The link in the Message is added here, when saving the Draft.
         */
        messageUid?.let { MessageController.getMessage(uid = it, realm)?.draftLocalUuid = localUuid }

        // Only if `!isFinishing`, because if we are finishing, well… We're out of here so we don't care about all of that.
        if (!isFinishing) {
            copyFromRealm()
                .apply {
                    uiBody = this@updateDraftFromLiveData.uiBody
                    uiSignature = this@updateDraftFromLiveData.uiSignature
                    uiQuote = this@updateDraftFromLiveData.uiQuote
                }
                .saveSnapshot()
            isNewMessage = false
        }
    }

    private fun Draft.updateDraftAttachmentsWithLiveData(uiAttachments: List<Attachment>) {

        /**
         * If :
         * - we are in FORWARD mode,
         * - all Attachments have no `uploadLocalUri` (meaning they are all from the original forwarded Message),
         * - there quantity is the same in UI and in Realm,
         * Then it means the Attachments list hasn't be edited by the user, so we have nothing to do here.
         */
        if (draftMode == DraftMode.FORWARD &&
            uiAttachments.all { it.uploadLocalUri == null } &&
            uiAttachments.count() == attachments.count()
        ) {
            return
        }

        val updatedAttachments = uiAttachments.map { uiAttachment ->
            val localAttachment = attachments
                /**
                 * If a localAttachment has the same `uploadLocalUri` than a UI one, it means it represents the same Attachment.
                 * But an Attachment only has an `uploadLocalUri` if the user added it by himself to the Draft.
                 * If it was added by Message forwarding, it won't have any `uploadLocalUri`, so we don't check this.
                 */
                .filter { it.uploadLocalUri != null && it.uploadLocalUri == uiAttachment.uploadLocalUri }
                .also {
                    // If this Sentry never triggers, remove it and replace the
                    // `attachments.filter { … }.also { … }.firstOrNull()` with `attachments.singleOrNull { … }`
                    if (it.count() > 1) Sentry.captureMessage("Found several Attachments with the same uploadLocalUri")
                }.firstOrNull()

            /**
             * The DraftsActionWorker will possibly upload the Attachments beforehand, so there will possibly already be
             * some data for Attachments in Realm (for example, the `uuid`). If we don't take back the Realm version of
             * the Attachment, this data will be lost forever and we won't be able to save/send the Draft.
             */
            return@map if (localAttachment?.uuid != null) localAttachment.copyFromRealm() else uiAttachment
        }

        attachments.apply {
            clear()
            addAll(updatedAttachments)
        }
    }

    private fun Draft.getWholeBody(): String = uiBody.textToHtml() + (uiSignature ?: "") + (uiQuote ?: "")

    private fun isSnapshotTheSame(subjectValue: String?, uiBodyValue: String): Boolean {
        return snapshot?.let { draftSnapshot ->
            draftSnapshot.identityId == fromLiveData.value?.signature?.id?.toString() &&
                    draftSnapshot.to == toLiveData.valueOrEmpty().toSet() &&
                    draftSnapshot.cc == ccLiveData.valueOrEmpty().toSet() &&
                    draftSnapshot.bcc == bccLiveData.valueOrEmpty().toSet() &&
                    draftSnapshot.subject == subjectValue &&
                    draftSnapshot.uiBody == uiBodyValue &&
                    draftSnapshot.attachmentsLocalUuids == attachmentsLiveData.valueOrEmpty().map { it.localUuid }.toSet()
        } ?: false
    }

    private fun removeDraftFromRealm(localUuid: String) {
        mailboxContentRealm().writeBlocking {
            DraftController.getDraft(localUuid, realm = this)?.let(::delete)
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
                    if (isTaskRoot) appContext.showToast(R.string.snackbarDraftSaving)
                } else {
                    appContext.showToast(R.string.snackbarDraftSaving)
                }
            }
            DraftAction.SEND -> {
                if (isTaskRoot) appContext.showToast(R.string.snackbarEmailSending)
            }
        }
    }

    private fun MutableLiveData<UiRecipients>.addRecipientThenSetValue(recipient: Recipient) {
        updateRecipientsThenSetValue { it.add(recipient) }
    }

    private fun MutableLiveData<UiRecipients>.removeRecipientThenSetValue(recipient: Recipient) {
        updateRecipientsThenSetValue { it.remove(recipient) }
    }

    private fun MutableLiveData<UiRecipients>.updateRecipientsThenSetValue(update: (MutableList<Recipient>) -> Unit) {
        value = UiRecipients(
            recipients = valueOrEmpty().toMutableList().apply { update(this) },
            otherFieldsAreEmpty = value!!.otherFieldsAreEmpty,
        )
    }

    enum class ImportationResult {
        SUCCESS,
        ATTACHMENTS_TOO_BIG,
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

    data class UiFrom(
        val signature: Signature,
        val shouldUpdateBodySignature: Boolean = true,
    )

    data class UiRecipients(
        val recipients: List<Recipient>,
        val otherFieldsAreEmpty: Boolean = true,
    )

    private data class DraftSnapshot(
        val identityId: String?,
        val to: Set<Recipient>,
        val cc: Set<Recipient>,
        val bcc: Set<Recipient>,
        var subject: String?,
        var uiBody: String,
        val attachmentsLocalUuids: Set<String>,
    )

    companion object {
        private val TAG = NewMessageViewModel::class.java.simpleName
        private const val ATTACHMENTS_MAX_SIZE = 25L * 1_024L * 1_024L // 25 MB
        private const val SUBJECT_MAX_LENGTH = 998
    }
}
