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

import android.annotation.SuppressLint
import android.content.ClipDescription
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.transition.TransitionManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.ListPopupWindow
import androidx.core.net.MailTo
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.*
import com.infomaniak.mail.data.models.Attachment.AttachmentDisposition.INLINE
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft.*
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivity.EditorAction
import com.infomaniak.mail.ui.main.newMessage.NewMessageFragment.FieldType.*
import com.infomaniak.mail.ui.main.newMessage.NewMessageViewModel.ImportationResult
import com.infomaniak.mail.ui.main.thread.AttachmentAdapter
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.WebViewUtils.Companion.setupNewMessageWebViewSettings
import com.infomaniak.mail.workers.DraftsActionsWorker
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class NewMessageFragment : Fragment() {

    private lateinit var binding: FragmentNewMessageBinding
    private val newMessageActivityArgs by lazy {
        // When opening this fragment via deeplink, it can happen that the navigation
        // extras aren't yet initialized, so we don't use the `navArgs` here.
        requireActivity().intent?.extras?.let(NewMessageActivityArgs::fromBundle) ?: NewMessageActivityArgs()
    }
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()

    private val addressListPopupWindow by lazy { ListPopupWindow(binding.root.context) }
    private lateinit var filePicker: FilePicker

    private val attachmentAdapter = AttachmentAdapter(shouldDisplayCloseButton = true, onDelete = ::onDeleteAttachment)
    private lateinit var signatureAdapter: SignatureAdapter

    private val webViewUtils by lazy { WebViewUtils(requireContext()) }

    private var selectedMailboxIndex = 0
    private var lastFieldToTakeFocus: FieldType? = TO
    var shouldSendInsteadOfSave: Boolean = false

    private val localSettings by lazy { LocalSettings.getInstance(requireContext()) }

    @Inject
    lateinit var draftsActionsWorkerScheduler: DraftsActionsWorker.Scheduler

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentNewMessageBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    // This `SuppressLint` seems useless, but it's for the CI. Don't remove it.
    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        SentryDebug.addNavigationBreadcrumb(
            name = findNavController().currentDestination?.displayName ?: "newMessageFragment",
            arguments = newMessageActivityArgs.toBundle(),
        )

        updateCreationStatus()

        filePicker = FilePicker(this@NewMessageFragment)

        initUi()
        initDraftAndViewModel()

        setOnFocusChangedListeners()

        doAfterSubjectChange()
        doAfterBodyChange()

        observeContacts()
        // observeMailboxes()
        observeEditorActions()
        observeNewAttachments()
        observeCcAndBccVisibility()
        observeDraftWorkerResults()
        observeInitSuccess()
    }

    private fun observeInitSuccess() {
        newMessageViewModel.isInitSuccess.observe(viewLifecycleOwner) { isSuccess ->
            Log.e("gibran", "observeInitSuccess: FRAGMENT COLLECTED IT");
            if (isSuccess) {
                val (signatures, signatureMap) = newMessageViewModel.signatures
                setupFromField(signatures, signatureMap)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        newMessageViewModel.updateDraftInLocalIfRemoteHasChanged()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        newMessageViewModel.draft.uiSignature?.let { _ ->
            binding.signatureWebView.reload()
        }
        newMessageViewModel.draft.uiQuote?.let { _ ->
            binding.quoteWebView.reload()
        }
        super.onConfigurationChanged(newConfig)
    }

    private fun updateCreationStatus() = with(newMessageViewModel) {
        activityCreationStatus.next()?.let {
            activityCreationStatus = it
        }
    }

    private fun initUi() = with(binding) {
        toolbar.setNavigationOnClickListener { activity?.onBackPressedDispatcher?.onBackPressed() }
        changeToolbarColorOnScroll(toolbar, compositionNestedScrollView)

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(signatureWebView.settings, true)
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(quoteWebView.settings, true)
        }

        attachmentsRecyclerView.adapter = attachmentAdapter

        setupAutoCompletionFields()

        subjectTextField.apply {
            // Enables having imeOptions="actionNext" and inputType="textMultiLine" at the same time
            setRawInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)

            filters = arrayOf<InputFilter>(object : InputFilter {
                override fun filter(source: CharSequence?, s: Int, e: Int, d: Spanned?, dS: Int, dE: Int): CharSequence? {
                    source?.toString()?.let { if (it.contains("\n")) return it.replace("\n", "") }
                    return null
                }
            })
        }
    }

    private fun initDraftAndViewModel() = with(newMessageActivityArgs) {
        newMessageViewModel.initDraftAndViewModel(
            arrivedFromExistingDraft,
            draftLocalUuid,
            draftResource,
            messageUid,
            draftMode,
            previousMessageUid,
            recipient,
        ).observe(viewLifecycleOwner) { isSuccess ->
            if (isSuccess) {
                hideLoader()
                showKeyboardInCorrectView()
                populateViewModelWithExternalMailData()
                populateUiWithViewModel()
            } else requireActivity().apply {
                showToast(R.string.failToOpenDraft)
                finish()
            }
        }
    }

    private fun hideLoader() = with(binding) {

        fromMailAddress.isVisible = true
        subjectTextField.isVisible = true
        bodyText.isVisible = true

        fromLoader.isGone = true
        subjectLoader.isGone = true
        bodyLoader.isGone = true

        toField.hideLoader()
        ccField.hideLoader()
        bccField.hideLoader()
    }

    private fun showKeyboardInCorrectView() = with(binding) {
        when (newMessageActivityArgs.draftMode) {
            DraftMode.REPLY,
            DraftMode.REPLY_ALL -> focusBodyField()
            DraftMode.NEW_MAIL -> if (newMessageActivityArgs.recipient == null) focusToField() else focusBodyField()
            DraftMode.FORWARD -> focusToField()
        }
    }

    private fun FragmentNewMessageBinding.focusBodyField() {
        bodyText.showKeyboard()
    }

    private fun FragmentNewMessageBinding.focusToField() {
        toField.showKeyboardInTextInput()
    }

    private fun setOnFocusChangedListeners() = with(binding) {
        val listener = View.OnFocusChangeListener { _, hasFocus -> if (hasFocus) fieldGotFocus(null) }
        subjectTextField.onFocusChangeListener = listener
        bodyText.onFocusChangeListener = listener
    }

    private fun setupAutoCompletionFields() = with(binding) {
        toField.initRecipientField(
            autoComplete = autoCompleteTo,
            onAutoCompletionToggledCallback = { hasOpened -> toggleAutoCompletion(TO, hasOpened) },
            onContactAddedCallback = { newMessageViewModel.addRecipientToField(it, TO) },
            onContactRemovedCallback = { newMessageViewModel.removeRecipientFromField(it, TO) },
            onCopyContactAddressCallback = ::copyRecipientEmailToClipboard,
            gotFocusCallback = { fieldGotFocus(TO) },
            onToggleEverythingCallback = ::openAdvancedFields,
            setSnackBarCallback = ::setSnackBar,
        )

        ccField.initRecipientField(
            autoComplete = autoCompleteCc,
            onAutoCompletionToggledCallback = { hasOpened -> toggleAutoCompletion(CC, hasOpened) },
            onContactAddedCallback = { newMessageViewModel.addRecipientToField(it, CC) },
            onContactRemovedCallback = { newMessageViewModel.removeRecipientFromField(it, CC) },
            onCopyContactAddressCallback = ::copyRecipientEmailToClipboard,
            gotFocusCallback = { fieldGotFocus(CC) },
            setSnackBarCallback = ::setSnackBar,
        )

        bccField.initRecipientField(
            autoComplete = autoCompleteBcc,
            onAutoCompletionToggledCallback = { hasOpened -> toggleAutoCompletion(BCC, hasOpened) },
            onContactAddedCallback = { newMessageViewModel.addRecipientToField(it, BCC) },
            onContactRemovedCallback = { newMessageViewModel.removeRecipientFromField(it, BCC) },
            onCopyContactAddressCallback = ::copyRecipientEmailToClipboard,
            gotFocusCallback = { fieldGotFocus(BCC) },
            setSnackBarCallback = ::setSnackBar,
        )
    }

    private fun fieldGotFocus(field: FieldType?) = with(binding) {
        if (lastFieldToTakeFocus == field) return

        if (field == null && newMessageViewModel.otherFieldsAreAllEmpty.value == true) {
            toField.collapseEverything()
        } else {
            if (field != TO) toField.collapse()
            if (field != CC) ccField.collapse()
            if (field != BCC) bccField.collapse()
        }

        lastFieldToTakeFocus = field
    }

    private fun openAdvancedFields(isCollapsed: Boolean) = with(binding) {
        cc.isGone = isCollapsed
        bcc.isGone = isCollapsed
    }

    private fun setSnackBar(titleRes: Int) {
        newMessageViewModel.snackBarManager.setValue(getString(titleRes))
    }

    private fun observeContacts() {
        newMessageViewModel.mergedContacts.observe(viewLifecycleOwner) { (sortedContactList, contactMap) ->
            updateRecipientFieldsContacts(sortedContactList, contactMap)
        }
    }

    private fun updateRecipientFieldsContacts(
        sortedContactList: List<MergedContact>,
        contactMap: Map<String, Map<String, MergedContact>>,
    ) = with(binding) {
        toField.updateContacts(sortedContactList, contactMap)
        ccField.updateContacts(sortedContactList, contactMap)
        bccField.updateContacts(sortedContactList, contactMap)
    }

    private fun toggleAutoCompletion(field: FieldType? = null, isAutoCompletionOpened: Boolean) = with(binding) {
        preFields.isGone = isAutoCompletionOpened
        to.isVisible = !isAutoCompletionOpened || field == TO
        cc.isVisible = !isAutoCompletionOpened || field == CC
        bcc.isVisible = !isAutoCompletionOpened || field == BCC
        postFields.isGone = isAutoCompletionOpened

        newMessageViewModel.isAutoCompletionOpened = isAutoCompletionOpened
    }

    private fun populateUiWithViewModel() = with(binding) {
        val draft = newMessageViewModel.draft

        val ccAndBccFieldsAreEmpty = draft.cc.isEmpty() && draft.bcc.isEmpty()
        toField.initRecipients(draft.to, ccAndBccFieldsAreEmpty)
        ccField.initRecipients(draft.cc)
        bccField.initRecipients(draft.bcc)

        newMessageViewModel.updateIsSendingAllowed()

        subjectTextField.setText(draft.subject)

        attachmentAdapter.addAll(draft.attachments.filterNot { it.disposition == INLINE })
        attachmentsRecyclerView.isGone = attachmentAdapter.itemCount == 0

        bodyText.setText(draft.uiBody)

        val alwaysShowExternalContent = localSettings.externalContent == ExternalContent.ALWAYS

        draft.uiSignature?.let { html ->
            signatureWebView.apply {
                loadContent(html)
                initWebViewClientAndBridge(
                    attachments = emptyList(),
                    messageUid = "SIGNATURE-${draft.messageUid}",
                    shouldLoadDistantResources = true,
                )
            }
            removeSignature.setOnClickListener {
                trackNewMessageEvent("deleteSignature")
                draft.uiSignature = null
                signatureGroup.isGone = true
            }
            signatureGroup.isVisible = true
        }

        draft.uiQuote?.let { html ->
            quoteWebView.apply {
                loadContent(html)
                initWebViewClientAndBridge(
                    attachments = draft.attachments,
                    messageUid = "QUOTE-${draft.messageUid}",
                    shouldLoadDistantResources = alwaysShowExternalContent || newMessageActivityArgs.shouldLoadDistantResources,
                )
            }
            removeQuote.setOnClickListener {
                trackNewMessageEvent("deleteQuote")
                draft.uiQuote = null
                quoteGroup.isGone = true
            }
            quoteGroup.isVisible = true
        }
    }

    private fun WebView.loadContent(html: String) {
        settings.setupNewMessageWebViewSettings()

        val processedHtml = webViewUtils.processHtmlForDisplay(html, context.isNightModeEnabled())
        loadDataWithBaseURL("", processedHtml, ClipDescription.MIMETYPE_TEXT_HTML, Utils.UTF_8, "")
    }

    private fun populateViewModelWithExternalMailData() {
        when (requireActivity().intent?.action) {
            Intent.ACTION_SEND -> handleSingleSendIntent()
            Intent.ACTION_SEND_MULTIPLE -> handleMultipleSendIntent()
            Intent.ACTION_VIEW, Intent.ACTION_SENDTO -> handleMailTo()
        }
    }

    /**
     * Handle `MailTo` from [Intent.ACTION_VIEW] or [Intent.ACTION_SENDTO]
     * Get [Intent.ACTION_VIEW] data with [MailTo] and [Intent.ACTION_SENDTO] with [Intent]
     */
    private fun handleMailTo() = with(newMessageViewModel) {

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

        val intent = requireActivity().intent
        intent?.data?.let { uri ->
            if (!MailTo.isMailTo(uri)) return@with

            val mailToIntent = MailTo.parse(uri)
            val to = mailToIntent.to?.splitToRecipientList()
                ?: emptyList()
            val cc = mailToIntent.cc?.splitToRecipientList()
                ?: intent.getStringArrayExtra(Intent.EXTRA_CC)?.map { Recipient().initLocalValues(it, it) }
                ?: emptyList()
            val bcc = mailToIntent.bcc?.splitToRecipientList()
                ?: intent.getStringArrayExtra(Intent.EXTRA_BCC)?.map { Recipient().initLocalValues(it, it) }
                ?: emptyList()

            draft.to.addAll(to)
            draft.cc.addAll(cc)
            draft.bcc.addAll(bcc)

            draft.subject = mailToIntent.subject ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            draft.uiBody = mailToIntent.body ?: intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""

            saveDraftDebouncing()
        }
    }

    private fun handleSingleSendIntent() = with(requireActivity().intent) {
        if (hasExtra(Intent.EXTRA_TEXT)) {
            getStringExtra(Intent.EXTRA_SUBJECT)?.let { newMessageViewModel.draft.subject = it }
            getStringExtra(Intent.EXTRA_TEXT)?.let { newMessageViewModel.draft.uiBody = it }
        }

        if (hasExtra(Intent.EXTRA_STREAM)) {
            (parcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uri ->
                newMessageViewModel.importAttachments(listOf(uri))
            }
        }
    }

    private fun handleMultipleSendIntent() {
        requireActivity().intent
            .parcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
            ?.filterIsInstance<Uri>()
            ?.let(newMessageViewModel::importAttachments)
    }

    private fun doAfterSubjectChange() {
        binding.subjectTextField.doAfterTextChanged { editable ->
            editable?.toString()?.let { newMessageViewModel.updateMailSubject(it.ifBlank { null }) }
        }
    }

    private fun doAfterBodyChange() {
        binding.bodyText.doAfterTextChanged { editable ->
            editable?.toString()?.let(newMessageViewModel::updateMailBody)
        }
    }

    // private fun observeMailboxes() {
    //     newMessageViewModel.mailboxes.observe(viewLifecycleOwner) {
    //         setupFromField(it.first, it.second)
    //     }
    // }

    private fun setupFromField(signatures: List<Signature>, signatureMap: Map<Int, Signature>) = with(binding) {
        // fromMailAddress.text = signatureMap[newMessageViewModel.selectedSignatureId]!!.senderIdn

        // val adapter = SignatureAdapter(signatures)
        // addressListPopupWindow.apply {
        //     setAdapter(adapter)
        //     isModal = true
        //     inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        //     anchorView = fromMailAddress
        //     width = fromMailAddress.width
        //     setOnItemClickListener { _, _, position, _ ->
        //         fromMailAddress.text = mails[position]
        //         selectedMailboxIndex = position
        //         dismiss()
        //     }
        // }
        //
        // if (signatures.count() > 1) {
        //     fromMailAddress.apply {
        //         setOnClickListener { _ -> addressListPopupWindow.show() }
        //         isClickable = true
        //         isFocusable = true
        //     }
        // }
    }

    private fun observeEditorActions() {
        newMessageViewModel.editorAction.observe(requireActivity()) { (editorAction, /*isToggled*/ _) ->
            when (editorAction) {
                EditorAction.ATTACHMENT -> {
                    filePicker.open { uris ->
                        requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                        newMessageViewModel.importAttachments(uris)
                    }
                }
                EditorAction.CAMERA -> notYetImplemented()
                EditorAction.LINK -> notYetImplemented()
                EditorAction.CLOCK -> notYetImplemented()
                else -> Log.wtf("SelectedText", "Impossible action got triggered: $editorAction")
            }
        }
    }

    private fun observeNewAttachments() = with(binding) {
        newMessageViewModel.importedAttachments.observe(requireActivity()) { (attachments, importationResult) ->
            attachmentAdapter.addAll(attachments)
            attachmentsRecyclerView.isGone = attachmentAdapter.itemCount == 0

            if (importationResult == ImportationResult.FILE_SIZE_TOO_BIG) showSnackbar(R.string.attachmentFileLimitReached)
        }
    }

    private fun observeCcAndBccVisibility() = with(newMessageViewModel) {
        otherFieldsAreAllEmpty.observe(viewLifecycleOwner, binding.toField::updateOtherFieldsVisibility)
        initializeFieldsAsOpen.observe(viewLifecycleOwner) { openAdvancedFields(!it) }
    }

    override fun onStop() = with(newMessageViewModel) {

        // TODO:
        //  Currently, we don't handle the possibility to leave the App during Draft composition.
        //  If it happens and we do anything in Realm about that, it will desynchronize the UI &
        //  Realm, and we'll lost some Draft data. A quick fix to get rid of the current bugs is
        //  to wait the end of Draft composition before starting DraftsActionsWorker.
        if (shouldExecuteDraftActionWhenStopping) {
            val isFinishing = requireActivity().isFinishing
            val isTaskRoot = requireActivity().isTaskRoot
            val action = if (shouldSendInsteadOfSave) DraftAction.SEND else DraftAction.SAVE
            executeDraftActionWhenStopping(action, isFinishing, isTaskRoot) {
                startWorker()
            }
        } else {
            shouldExecuteDraftActionWhenStopping = true
        }

        super.onStop()
    }

    private fun startWorker() {
        draftsActionsWorkerScheduler.scheduleWork(newMessageViewModel.draft.localUuid)
    }

    private fun observeDraftWorkerResults() {
        WorkerUtils.flushWorkersBefore(requireContext(), viewLifecycleOwner) {

            val treatedWorkInfoUuids = mutableSetOf<UUID>()

            draftsActionsWorkerScheduler.getCompletedAndFailedInfoLiveData().observe(viewLifecycleOwner) {
                it.forEach { workInfo ->
                    if (!treatedWorkInfoUuids.add(workInfo.id)) return@forEach
                    newMessageViewModel.synchronizeViewModelDraftFromRealm()
                }
            }
        }
    }

    fun closeAutoCompletion() = with(binding) {
        toField.clearField()
        ccField.clearField()
        bccField.clearField()
    }

    private fun onDeleteAttachment(position: Int, itemCountLeft: Int) = with(binding) {
        val draft = newMessageViewModel.draft

        if (itemCountLeft == 0) {
            TransitionManager.beginDelayedTransition(binding.root)
            attachmentsRecyclerView.isGone = true
        }

        draft.attachments[position].getUploadLocalFile(requireContext(), draft.localUuid).delete()
        draft.attachments.removeAt(position)
    }

    enum class FieldType {
        TO,
        CC,
        BCC,
    }

    enum class CreationStatus {
        NOT_YET_CREATED,
        CREATED,
        RECREATED;

        fun next(): CreationStatus? {
            return when (this) {
                NOT_YET_CREATED -> CREATED
                CREATED -> RECREATED
                RECREATED -> null
            }
        }
    }
}
