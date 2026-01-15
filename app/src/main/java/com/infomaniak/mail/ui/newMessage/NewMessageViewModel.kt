/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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
@file:OptIn(ExperimentalSplittiesApi::class)

package com.infomaniak.mail.ui.newMessage

import android.app.Application
import android.content.ClipDescription
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.MailTo
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.infomaniak.core.common.cancellable
import com.infomaniak.core.common.utils.FORMAT_ISO_8601_WITH_TIMEZONE_SEPARATOR
import com.infomaniak.core.common.utils.format
import com.infomaniak.core.legacy.utils.SingleLiveEvent
import com.infomaniak.core.legacy.utils.getFileNameAndSize
import com.infomaniak.core.legacy.utils.guessMimeType
import com.infomaniak.core.legacy.utils.parcelableArrayListExtra
import com.infomaniak.core.legacy.utils.parcelableExtra
import com.infomaniak.core.matomo.Matomo.TrackerAction
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.core.ui.showToast
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackExternalEvent
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.MatomoMail.trackSendingDraftEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.cache.userInfo.AddressBookController
import com.infomaniak.mail.data.cache.userInfo.ContactGroupController
import com.infomaniak.mail.data.cache.userInfo.MergedContactController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.AttachmentUploadStatus
import com.infomaniak.mail.data.models.FeatureFlag
import com.infomaniak.mail.data.models.addressBook.AddressBook
import com.infomaniak.mail.data.models.addressBook.ContactGroup
import com.infomaniak.mail.data.models.correspondent.ContactAutocompletable
import com.infomaniak.mail.data.models.correspondent.MergedContact
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
import com.infomaniak.mail.ui.newMessage.NewMessageActivity.DraftSaveConfiguration
import com.infomaniak.mail.ui.newMessage.NewMessageEditorManager.EditorAction
import com.infomaniak.mail.ui.newMessage.NewMessageRecipientFieldsManager.FieldType
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ContactUtils.arrangeMergedContacts
import com.infomaniak.mail.utils.DraftInitManager
import com.infomaniak.mail.utils.JsoupParserUtil.jsoupParseWithLog
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.MessageBodyUtils
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.SignatureUtils
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.coroutineContext
import com.infomaniak.mail.utils.extensions.AttachmentExt.findSpecificAttachment
import com.infomaniak.mail.utils.extensions.appContext
import com.infomaniak.mail.utils.extensions.htmlToText
import com.infomaniak.mail.utils.extensions.isEmail
import com.infomaniak.mail.utils.extensions.valueOrEmpty
import com.infomaniak.mail.utils.uploadAttachmentsWithMutex
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmList
import io.sentry.Sentry
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import splitties.experimental.ExperimentalSplittiesApi
import java.util.Date
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
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
    private val addressBookController: AddressBookController,
    private val contactGroupController: ContactGroupController,
    private val notificationManagerCompat: NotificationManagerCompat,
    private val draftInitManager: DraftInitManager,
    private val sharedUtils: SharedUtils,
    private val signatureUtils: SignatureUtils,
    private val snackbarManager: SnackbarManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    //region Initial data
    private var initialBody: BodyContentPayload = BodyContentPayload.emptyBody()
    private var initialSignature: String? = null
    private var initialQuote: String? = null
    //endregion

    //region UI data
    val fromLiveData = MutableLiveData<UiFrom>()
    val toLiveData = MutableLiveData<UiRecipients>()
    val ccLiveData = MutableLiveData<UiRecipients>()
    val bccLiveData = MutableLiveData<UiRecipients>()
    val attachmentsLiveData = MutableLiveData<List<Attachment>>()
    val uiSignatureLiveData = MutableLiveData<String?>()
    val uiQuoteLiveData = MutableLiveData<String?>()
    inline val allRecipients get() = toLiveData.valueOrEmpty() + ccLiveData.valueOrEmpty() + bccLiveData.valueOrEmpty()
    //endregion

    val editorBodyInitializer = SingleLiveEvent<BodyContentPayload>()

    // 1. Navigating to AiPropositionFragment causes NewMessageFragment to export its body to `subjectAndBodyChannel`.
    // 2. Inserting the AI proposition navigates back to NewMessageFragment.
    // 3. When saving or sending the draft now, the channel still holds the previous body as it wasn't consumed.
    // channelExpirationIdTarget lets us identify and discard outdated messages, ensuring the correct message is processed.
    private var channelExpirationIdTarget = 0
    private val _subjectAndBodyChannel: Channel<SubjectAndBodyData> = Channel(capacity = CONFLATED)
    private val subjectAndBodyChannel: ReceiveChannel<SubjectAndBodyData> = _subjectAndBodyChannel
    private var subjectAndBodyJob: Job? = null

    var isAutoCompletionOpened = false
    var isEditorExpanded = false
    var isExternalBannerManuallyClosed = false
    var draftAction = DraftAction.SAVE
    var signaturesCount = 0
    private var isNewMessage = false

    private var snapshot: DraftSnapshot? = null

    val otherRecipientsFieldsAreEmpty = MutableLiveData(true)
    val externalRecipientCount = SingleLiveEvent<Pair<String?, Int>>()
    val isEditorWebViewFocusedLiveData = MutableLiveData<Boolean>()

    val initializeFieldsAsOpen = SingleLiveEvent<Boolean>()
    // Needs to trigger every time the Fragment is recreated
    val initResult = MutableLiveData<InitResult>()

    //region Attachments
    val importAttachmentsLiveData = SingleLiveEvent<List<Uri>>()
    val importAttachmentsResult = SingleLiveEvent<ImportationResult>()
    //endRegion

    //region Encryption
    val isEncryptionActivated = SingleLiveEvent(false)
    val encryptionPassword: SingleLiveEvent<String> = SingleLiveEvent("")
    //endregion

    val isSendingAllowed = SingleLiveEvent(false)

    // Boolean: For toggleable actions, `false` if the formatting has been removed and `true` if the formatting has been applied.
    val editorAction = SingleLiveEvent<Pair<EditorAction, Boolean?>>()

    private val _isShimmering = MutableStateFlow(true)
    val isShimmering: StateFlow<Boolean> = _isShimmering

    //region Check mailbox existence
    private val exitSignal: CompletableJob = Job()

    private val mailboxRefFlow = MutableSharedFlow<MailboxRef>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _currentMailboxFlow: Flow<Mailbox> = mailboxRefFlow
        .mapLatest {
            val mailbox = mailboxController.getMailbox(it.userId, it.mailboxId)
            if (mailbox == null) {
                exitSignal.complete()
                awaitCancellation()
            }
            mailbox
        }
        .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    suspend fun currentMailbox() = _currentMailboxFlow.first()

    fun loadMailbox(userId: Int, mailboxId: Int) {
        mailboxRefFlow.tryEmit(MailboxRef(userId, mailboxId))
    }

    // ------------- !IMPORTANT! -------------
    /** Always call this before the first access to currentMailbox, otherwise the NewMessageActivity will just await indefinitely
     * in the cases where `mailboxController.getMailbox` returns null
     */
    suspend fun awaitNoMailboxSignal() = exitSignal.join()
    //endregion

    private val currentMailboxLive = _currentMailboxFlow.asLiveData(ioCoroutineContext)

    val featureFlagsLive = currentMailboxLive.map { it.featureFlags }

    val mergedContacts = liveData(ioCoroutineContext) {
        val mergedContacts = mergedContactController.getSortedMergedContacts().copyFromRealm()
        val addressBooks = addressBookController.getAllAddressBook().copyFromRealm()
        val contactGroups = contactGroupController.getGroup().copyFromRealm()
        val contactAutocompletables: List<ContactAutocompletable> = mergedContacts + addressBooks + contactGroups
        emit(contactAutocompletables to arrangeMergedContacts(mergedContacts))
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
    fun shouldLoadDistantResources() = shouldLoadDistantResources

    suspend fun hasMultiMailboxes() = withContext(ioDispatcher) {
        AccountUtils.getAllUsersSync().flatMap { user ->
            mailboxController.getMailboxes(user.id)
        }.size > 1
    }

    fun initDraftAndViewModel(intent: Intent): LiveData<Draft?> = liveData(ioCoroutineContext) {

        val realm = mailboxContentRealm()
        var signatures = emptyList<Signature>()

        val draft: Draft? = runCatching {

            signatures = currentMailbox().signatures
                .also { signaturesCount = it.count() }
                .toMutableList()
                .apply { add(index = 0, element = Signature.getDummySignature(appContext, email = currentMailbox().email)) }

            isNewMessage = !arrivedFromExistingDraft && draftLocalUuid == null
            if (isNewMessage) getNewDraft(signatures, intent, realm) else getExistingDraft(draftLocalUuid)
        }.cancellable().onFailure {
            SentryLog.e(TAG, "Caught exception during draft initialization", it)
        }.getOrNull()

        draft?.let {

            it.flagRecipientsAsAutomaticallyEntered()

            dismissNotification()
            markAsRead(currentMailbox(), realm)

            realm.write { DraftController.upsertDraftBlocking(it, realm = this) }
            it.saveSnapshot(initialBody.content)
            it.initLiveData(signatures)
            _isShimmering.emit(false)

            initResult.postValue(InitResult(it, signatures))
        }

        emit(draft)
    }

    private suspend fun getExistingDraft(localUuid: String?): Draft? {
        return getLocalOrRemoteDraft(localUuid)?.also { draft ->
            saveNavArgsToSavedState(draft.localUuid)
            if (draft.identityId.isNullOrBlank()) {
                draft.identityId = currentMailbox().getDefaultSignatureWithFallback().id.toString()
            }
            splitSignatureAndQuoteFromBody(draft)
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
                        val (fullMessage, hasFailedFetching) = DraftController.fetchHeavyDataIfNeeded(message, realm)
                        previousMessage = fullMessage

                        if (hasFailedFetching) return@let

                        setReplyForwardDraftValues(draft = this, fullMessage)
                    }
            }
        }

        with(draftInitManager) {
            val signature = chooseSignature(currentMailbox().email, signatures, draftMode, previousMessage)
            setSignatureIdentity(signature)
            if (signature.content.isNotEmpty()) {
                initialSignature = signatureUtils.encapsulateSignatureContentWithInfomaniakClass(signature.content)
            }
        }

        populateWithExternalMailDataIfNeeded(draft = this, intent)
    }

    private suspend fun setReplyForwardDraftValues(draft: Draft, fullMessage: Message) {

        with(draftInitManager) {
            draft.setPreviousMessage(draftMode = draftMode, previousMessage = fullMessage)
        }

        val quote = draftInitManager.createQuote(draftMode, fullMessage, draft.attachments)
        if (quote != null) initialQuote = quote

        if (fullMessage.body == null) {
            SentryLog.e(TAG, "The message we're trying to reply to has an unexpected null body") { scope ->
                scope.setExtra("Message resource", fullMessage.resource)
            }
        }

        val isAiEnabled = currentMailbox().featureFlags.contains(FeatureFlag.AI)
        if (isAiEnabled) parsePreviousMailToAnswerWithAi(fullMessage.body)

        val isEncryptionEnabled = currentMailbox().featureFlags.contains(FeatureFlag.ENCRYPTION)
        if (isEncryptionEnabled) draft.isEncrypted = fullMessage.isEncrypted
    }

    fun getAddressBookWithName(contactGroup: ContactGroup): AddressBook? {
        return addressBookController.getAddressBookWithGroup(contactGroup)
    }

    fun getMergedContactFromContactGroup(contactGroup: ContactGroup): List<MergedContact> {
        return mergedContactController.getMergedContactFromContactGroup(contactGroup)
    }

    fun getMergedContactFromAddressBook(addressBook: AddressBook): List<MergedContact> {
        return mergedContactController.getMergedContactFromAddressBook(addressBook)
    }

    private fun saveNavArgsToSavedState(localUuid: String) {
        savedStateHandle[NewMessageActivityArgs::draftLocalUuid.name] = localUuid

        // If the user put the app in background before we put the fetched Draft in Realm, and the system
        // kill the app, then we won't be able to fetch the Draft anymore as the `draftResource` will be null.
        savedStateHandle[NewMessageActivityArgs::draftResource.name] = draftResource
    }

    private fun splitSignatureAndQuoteFromBody(draft: Draft) {
        val remoteBody = draft.body
        if (remoteBody.isEmpty()) return

        val (body, signature, quote) = when (draft.mimeType) {
            Utils.TEXT_PLAIN -> BodyData(
                body = BodyContentPayload(remoteBody, BodyContentType.TEXT_PLAIN_WITHOUT_HTML),
                signature = null,
                quote = null
            )
            Utils.TEXT_HTML -> splitSignatureAndQuoteFromHtml(remoteBody)
            else -> error("Cannot load an email which is not of type text/plain or text/html")
        }

        initialBody = body
        initialSignature = signature
        initialQuote = quote
    }

    private fun splitSignatureAndQuoteFromHtml(draftBody: String): BodyData {

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

        val doc = jsoupParseWithLog(draftBody).also { it.outputSettings().prettyPrint(false) }

        val (bodyWithQuote, signature) = doc.split(MessageBodyUtils.INFOMANIAK_SIGNATURE_HTML_CLASS_NAME, draftBody)

        val replyPosition = draftBody.lastIndexOfOrMax(MessageBodyUtils.INFOMANIAK_REPLY_QUOTE_HTML_CLASS_NAME)
        val forwardPosition = draftBody.lastIndexOfOrMax(MessageBodyUtils.INFOMANIAK_FORWARD_QUOTE_HTML_CLASS_NAME)
        val (body, quote) = if (replyPosition < forwardPosition) {
            doc.split(MessageBodyUtils.INFOMANIAK_REPLY_QUOTE_HTML_CLASS_NAME, bodyWithQuote)
        } else {
            doc.split(MessageBodyUtils.INFOMANIAK_FORWARD_QUOTE_HTML_CLASS_NAME, bodyWithQuote)
        }

        return BodyData(BodyContentPayload(body, BodyContentType.HTML_UNSANITIZED), signature, quote)
    }

    private fun populateWithExternalMailDataIfNeeded(draft: Draft, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> handleSingleSendIntent(draft, intent)
            Intent.ACTION_SEND_MULTIPLE -> handleMultipleSendIntent(draft, intent)
            Intent.ACTION_VIEW, Intent.ACTION_SENDTO -> handleMailTo(draft, intent.data, intent)
        }

        if (mailToUri != null) handleMailTo(draft, mailToUri)

        SentryDebug.addDraftBreadcrumbs(draft, step = "populate Draft with external mail data")
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

    private fun Draft.saveSnapshot(uiBodyValue: String) {
        snapshot = DraftSnapshot(
            identityId = identityId,
            to = to.toSet(),
            cc = cc.toSet(),
            bcc = bcc.toSet(),
            subject = subject,
            uiBody = uiBodyValue,
            isEncrypted = isEncrypted,
            encryptionPassword = encryptionKey ?: "",
            attachmentsLocalUuids = attachments.mapTo(mutableSetOf()) { it.localUuid },
        )
    }

    private suspend fun Draft.initLiveData(signatures: List<Signature>) {
        val draftSignature = signatures.singleOrNull { it.id == identityId?.toInt() }

        encryptionPassword.postValue(encryptionKey)

        fromLiveData.postValue(
            UiFrom(
                signature = draftSignature ?: currentMailbox().getDefaultSignatureWithFallback(),
                shouldUpdateBodySignature = false,
            ),
        )

        toLiveData.postValue(UiRecipients(recipients = to, otherFieldsAreEmpty = cc.isEmpty() && bcc.isEmpty()))
        ccLiveData.postValue(UiRecipients(recipients = cc))
        bccLiveData.postValue(UiRecipients(recipients = bcc))

        attachmentsLiveData.postValue(attachments)

        editorBodyInitializer.postValue(initialBody)

        uiSignatureLiveData.postValue(initialSignature)
        uiQuoteLiveData.postValue(initialQuote)

        if (cc.isNotEmpty() || bcc.isNotEmpty()) {
            otherRecipientsFieldsAreEmpty.postValue(false)
            initializeFieldsAsOpen.postValue(true)
        }

        isEncryptionActivated.postValue(isEncrypted)
    }

    private suspend fun getLocalOrRemoteDraft(localUuid: String?): Draft? {

        @Suppress("UNUSED_PARAMETER")
        fun trackOpenLocal(draft: Draft) { // Unused but required to use references inside the `also` block, used for readability
            trackNewMessageEvent(MatomoName.OpenLocalDraft, TrackerAction.DATA, value = 1.0f)
        }

        @Suppress("UNUSED_PARAMETER")
        fun trackOpenRemote(draft: Draft) { // Unused but required to use references inside the `also` block, used for readability
            trackNewMessageEvent(MatomoName.OpenLocalDraft, TrackerAction.DATA, value = 0.0f)
        }

        return getLatestLocalDraft(localUuid)?.also(::trackOpenLocal) ?: fetchDraft()?.also(::trackOpenRemote)
    }

    private suspend fun getLatestLocalDraft(localUuid: String?): Draft? {
        return localUuid?.let { draftController.getDraft(it) }?.let { Dispatchers.IO { it.copyFromRealm() } }
    }

    private suspend fun fetchDraft(): Draft? {
        return ApiRepository.getDraft(draftResource!!).data?.also { draft ->

            /**
             * If we are opening for the 1st time an existing Draft created somewhere else (ex: webmail), we need to
             * set all of its Attachments to [AttachmentUploadStatus.UPLOADED], so we don't try to upload them again.
             */
            draft.attachments.forEach {
                it.setUploadStatus(AttachmentUploadStatus.UPLOADED, draft, "fetchDraft at NewMessage opening")
            }

            /**
             * If we are opening for the 1st time an existing Draft created somewhere else
             * (ex: webmail), we need to create the link between the Draft and its Message.
             * - The link in the Draft is added here, when creating the Draft.
             * - The link in the Message is added later, when saving the Draft.
             */
            draft.initLocalValues(messageUid!!)
        }
    }

    private suspend fun parsePreviousMailToAnswerWithAi(previousMessageBody: Body?) {
        if (draftMode == DraftMode.REPLY || draftMode == DraftMode.REPLY_ALL) {
            aiSharedData.previousMessageBodyPlainText = previousMessageBody?.asPlainText()
        }
    }

    private suspend fun Body.asPlainText(): String? {
        // TODO: When the API handles blank characters, remove ifBlank
        return when (type) {
            Utils.TEXT_HTML -> {
                val splitBodyContent = MessageBodyUtils.splitContentAndQuote(this).content
                val fullBody = MessageBodyUtils.mergeSplitBodyAndSubBodies(splitBodyContent, subBodies)
                fullBody.htmlToText()
            }
            else -> value
        }.ifBlank { null }
    }

    private fun handleSingleSendIntent(draft: Draft, intent: Intent) = with(intent) {

        if (hasExtra(Intent.EXTRA_EMAIL)) {
            handleMailTo(draft, intent.data, intent)
        } else if (hasExtra(Intent.EXTRA_TEXT)) {
            getStringExtra(Intent.EXTRA_SUBJECT)?.let { draft.subject = it }
            getStringExtra(Intent.EXTRA_TEXT)?.let { initialBody = BodyContentPayload(it, BodyContentType.TEXT_PLAIN_WITH_HTML) }
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

        /**
         * Each customer app is free to do what it wants, so we sometimes receive empty `mailTo` fields.
         * Instead of ignoring them, we return `null` so we can check the recipients inside the Intent.
         *
         * If the string obtained through the mailto uri is blank, this method needs to make sure to return null.
         */
        fun String.splitToRecipientList() = split(",", ";").mapNotNull {
            val email = it.trim()
            if (email.isEmail()) Recipient().initLocalValues(email, email) else parseEmailWithName(email)
        }.takeIf { it.isNotEmpty() }

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

            subject = mailToIntent?.subject?.takeIf(String::isNotEmpty) ?: intent?.getStringExtra(Intent.EXTRA_SUBJECT)

            val bodyContent = mailToIntent?.body?.takeIf(String::isNotEmpty) ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
            val mailToPayload = bodyContent?.let { BodyContentPayload(it, BodyContentType.TEXT_PLAIN_WITH_HTML) }
            initialBody = mailToPayload ?: BodyContentPayload.emptyBody()
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
            val (attachment, hasSizeLimitBeenReached) = importAttachment(uri, availableSpace)

            if (hasSizeLimitBeenReached) {
                result = ImportationResult.ATTACHMENTS_TOO_BIG
                return@forEach
            }

            attachment?.let {
                newAttachments.add(it)
                attachmentsSize += it.size
            }
        }

        importAttachmentsResult.postValue(result)

        return newAttachments
    }

    private fun importAttachment(uri: Uri, availableSpace: Long): Pair<Attachment?, Boolean> {

        val (fileName, fileSize) = appContext.getFileNameAndSize(uri) ?: return null to false
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
        } ?: (null to false)
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

        trackNewMessageEvent(MatomoName.DeleteRecipient)
        if (recipient.isDisplayedAsExternal) trackExternalEvent(MatomoName.DeleteRecipient)
    }

    fun deleteAttachment(position: Int) = viewModelScope.launch(ioCoroutineContext) {
        runCatching {
            val attachments = attachmentsLiveData.valueOrEmpty().toMutableList()
            val attachment = attachments[position]
            attachment.getUploadLocalFile()?.delete()
            LocalStorageUtils.deleteAttachmentUploadDir(appContext, draftLocalUuid!!, attachment.localUuid)

            mailboxContentRealm().write {
                DraftController.updateDraftBlocking(draftLocalUuid!!, realm = this) {
                    it.attachments.findSpecificAttachment(attachment)?.let(::delete)
                }
            }

            attachments.removeAt(position)
            attachmentsLiveData.postValue(attachments)
        }
    }

    fun updateIsSendingAllowed(
        attachments: List<Attachment> = attachmentsLiveData.valueOrEmpty(),
        type: FieldType? = null,
        recipients: List<Recipient> = emptyList(),
        isEncryptionValid: Boolean = true,
    ) {

        val allDraftRecipients = when (type) {
            FieldType.TO -> recipients + ccLiveData.valueOrEmpty() + bccLiveData.valueOrEmpty()
            FieldType.CC -> toLiveData.valueOrEmpty() + recipients + bccLiveData.valueOrEmpty()
            FieldType.BCC -> toLiveData.valueOrEmpty() + ccLiveData.valueOrEmpty() + recipients
            null -> allRecipients
        }

        isSendingAllowed.value = if (allDraftRecipients.isNotEmpty() && isEncryptionValid) {
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
        val realm = mailboxContentRealm()

        realm.write {
            DraftController.getDraftBlocking(localUuid, realm = this)?.also {
                it.updateDraftAttachmentsWithLiveData(
                    uiAttachments = uiAttachments,
                    step = "observeAttachments -> uploadAttachmentsToServer",
                )
            }
        } ?: return@launch

        runCatching {
            uploadAttachmentsWithMutex(localUuid, currentMailbox(), realm)
        }.cancellable().onFailure(Sentry::captureException)
    }

    fun setScheduleDate(date: Date?) = viewModelScope.launch(ioDispatcher) {
        val localUuid = draftLocalUuid ?: return@launch
        mailboxContentRealm().write {
            DraftController.getDraftBlocking(localUuid, realm = this)?.also { draft ->
                draft.scheduleDate = date?.format(FORMAT_ISO_8601_WITH_TIMEZONE_SEPARATOR)
            }
        }
    }

    fun resetScheduledDate() = setScheduleDate(date = null)

    fun storeBodyAndSubject(subject: String, html: String) {
        globalCoroutineScope.launch(ioDispatcher) {
            _subjectAndBodyChannel.send(SubjectAndBodyData(subject, html, channelExpirationIdTarget))
        }
    }

    fun discardOldBodyAndSubjectChannelMessages() {
        channelExpirationIdTarget++
    }

    fun waitForBodyAndSubjectToExecuteDraftAction(draftSaveConfiguration: DraftSaveConfiguration) {
        subjectAndBodyJob?.cancel()
        subjectAndBodyJob = globalCoroutineScope.launch(ioDispatcher) {
            val subject: String
            val body: String
            while (true) {
                val (receivedSubject, receivedBody, expirationId) = subjectAndBodyChannel.receive()
                if (expirationId == channelExpirationIdTarget) {
                    subject = receivedSubject
                    body = receivedBody
                    break
                }
            }

            draftSaveConfiguration.addSubjectAndBody(subject, body)
            executeDraftAction(draftSaveConfiguration)
        }
    }

    private suspend fun executeDraftAction(draftSaveConfiguration: DraftSaveConfiguration) = with(draftSaveConfiguration) {
        val localUuid = draftLocalUuid ?: return@with
        val subject = subjectValue.ifBlank { null }?.take(SUBJECT_MAX_LENGTH)

        if (action == DraftAction.SAVE && isSnapshotTheSame(subject, uiBodyValue)) {
            if (isFinishing && isNewMessage) removeDraftFromRealm(localUuid)
            return@with
        }

        val hasFailed = mailboxContentRealm().write {
            DraftController.getDraftBlocking(localUuid, realm = this)
                ?.updateDraftBeforeSavingRemotely(action, isFinishing, subject, uiBodyValue, realm = this@write)
                ?: return@write true
            return@write false
        }

        if (hasFailed) return@with

        showDraftToastToUser(action, isFinishing, isTaskRoot)
        startWorkerCallback()

        trackSendingDraftEvent(
            action = action,
            to = toLiveData.valueOrEmpty(),
            cc = ccLiveData.valueOrEmpty(),
            bcc = bccLiveData.valueOrEmpty(),
            externalMailFlagEnabled = currentMailbox().externalMailFlagEnabled,
        )
    }

    override fun onCleared() {
        draftLocalUuid?.let { LocalStorageUtils.deleteDraftUploadDir(appContext, draftLocalUuid = it) }
        super.onCleared()
    }

    private fun Draft.updateDraftBeforeSavingRemotely(
        draftAction: DraftAction,
        isFinishing: Boolean,
        subjectValue: String?,
        uiBodyValue: String,
        realm: MutableRealm,
    ) {

        action = draftAction
        isEncrypted = isEncryptionActivated.value ?: false
        encryptionKey = encryptionPassword.value
        identityId = fromLiveData.value?.signature?.id.toString()

        to = toLiveData.valueOrEmpty().toRealmList()
        cc = ccLiveData.valueOrEmpty().toRealmList()
        bcc = bccLiveData.valueOrEmpty().toRealmList()

        updateDraftAttachmentsWithLiveData(
            uiAttachments = attachmentsLiveData.valueOrEmpty(),
            step = "executeDraftActionWhenStopping (action = ${draftAction.name}) -> updateDraftFromLiveData",
        )

        subject = subjectValue

        body = uiBodyValue + (uiSignatureLiveData.value ?: "") + (uiQuoteLiveData.value ?: "")

        /**
         * If we are opening for the 1st time an existing Draft created somewhere else
         * (ex: webmail), we need to create the link between the Draft and its Message.
         * - The link in the Draft is already added, when creating the Draft.
         * - The link in the Message is added here, when saving the Draft.
         */
        messageUid?.let { MessageController.getMessageBlocking(uid = it, realm)?.draftLocalUuid = localUuid }

        // If we opened a text/plain draft, we will now convert it as text/html as we send it because we only support editing
        // text/html drafts.
        mimeType = Utils.TEXT_HTML

        // Only if `!isFinishing`, because if we are finishing, well… We're out of here so we don't care about all of that.
        if (!isFinishing) {
            copyFromRealm().saveSnapshot(uiBodyValue)
            isNewMessage = false
        }
    }

    private fun Draft.updateDraftAttachmentsWithLiveData(uiAttachments: List<Attachment>, step: String) {

        /**
         * If :
         * - we are in FORWARD mode,
         * - none got added (all Attachments are already uploaded, meaning they are all from the original forwarded Message),
         * - none got removed (their quantity is the same in UI and in Realm),
         * Then it means the Attachments list hasn't been edited by the user, so we have nothing to do here.
         */
        val isForwardingUneditedAttachmentsList = draftMode == DraftMode.FORWARD &&
                uiAttachments.all { it.attachmentUploadStatus == AttachmentUploadStatus.UPLOADED } &&
                uiAttachments.count() == attachments.count()
        if (isForwardingUneditedAttachmentsList) return

        val updatedAttachments = uiAttachments.map { uiAttachment ->

            val localAttachment = attachments.findSpecificAttachment(uiAttachment)

            /**
             * The DraftsActionWorker will possibly start uploading the Attachments beforehand, so there will possibly already
             * be some data for Attachments in Realm (for example, the `uuid`). If we don't take back the Realm version of the
             * Attachment, this data will be lost forever and we won't be able to save/send the Draft.
             */
            return@map if (localAttachment != null && localAttachment.attachmentUploadStatus == AttachmentUploadStatus.UPLOADED) {
                localAttachment.copyFromRealm()
            } else {
                uiAttachment
            }
        }

        attachments.apply {
            clear()
            addAll(updatedAttachments)
        }

        SentryDebug.addDraftBreadcrumbs(draft = this, step)
    }

    private fun isSnapshotTheSame(subjectValue: String?, uiBodyValue: String): Boolean {
        return snapshot?.let { draftSnapshot ->
            draftSnapshot.identityId == fromLiveData.value?.signature?.id?.toString() &&
                    draftSnapshot.to == toLiveData.valueOrEmpty().toSet() &&
                    draftSnapshot.cc == ccLiveData.valueOrEmpty().toSet() &&
                    draftSnapshot.bcc == bccLiveData.valueOrEmpty().toSet() &&
                    draftSnapshot.subject == subjectValue &&
                    draftSnapshot.uiBody == uiBodyValue &&
                    draftSnapshot.isEncrypted == isEncryptionActivated.value &&
                    draftSnapshot.encryptionPassword == encryptionPassword.value &&
                    draftSnapshot.attachmentsLocalUuids == attachmentsLiveData.valueOrEmpty()
                .mapTo(mutableSetOf()) { it.localUuid }
        } ?: false
    }

    private suspend fun removeDraftFromRealm(localUuid: String) {
        mailboxContentRealm().write {
            DraftController.getDraftBlocking(localUuid, realm = this)?.let(::delete)
        }
    }

    private suspend fun showDraftToastToUser(
        action: DraftAction,
        isFinishing: Boolean,
        isTaskRoot: Boolean,
    ) = withContext(mainDispatcher) {
        when (action) {
            DraftAction.SAVE -> showSaveToast(isFinishing, isTaskRoot)
            DraftAction.SEND, DraftAction.SEND_REACTION -> showSendToast(isTaskRoot)
            DraftAction.SCHEDULE -> showScheduleToast(isTaskRoot)
        }
    }

    private fun showSaveToast(isFinishing: Boolean, isTaskRoot: Boolean) {
        if (isFinishing) {
            if (isTaskRoot) appContext.showToast(R.string.snackbarDraftSaving)
        } else {
            appContext.showToast(R.string.snackbarDraftSaving)
        }
    }

    private fun showSendToast(isTaskRoot: Boolean) {
        if (isTaskRoot) appContext.showToast(R.string.snackbarEmailSending)
    }

    private fun showScheduleToast(isTaskRoot: Boolean) {
        if (isTaskRoot) appContext.showToast(R.string.snackbarScheduling)
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

    data class MailboxRef(val userId: Int, val mailboxId: Int)

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
        var isEncrypted: Boolean,
        var encryptionPassword: String,
        val attachmentsLocalUuids: Set<String>,
    )

    private data class SubjectAndBodyData(val subject: String, val body: String, val expirationId: Int)

    private data class BodyData(val body: BodyContentPayload, val signature: String?, val quote: String?)

    companion object {
        private val TAG = NewMessageViewModel::class.java.simpleName

        private const val ATTACHMENTS_MAX_SIZE = 25L * 1_024L * 1_024L // 25 MB
        private const val SUBJECT_MAX_LENGTH = 998
    }
}
