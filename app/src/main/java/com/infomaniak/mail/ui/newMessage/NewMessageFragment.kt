/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipDescription
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ListPopupWindow
import android.widget.PopupWindow
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.Group
import androidx.core.view.forEach
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.richhtmleditor.StatusCommand.*
import com.infomaniak.mail.MatomoMail.OPEN_FROM_DRAFT_NAME
import com.infomaniak.mail.MatomoMail.trackAttachmentActionsEvent
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.ExternalContent
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.AttachmentDisposition
import com.infomaniak.mail.data.models.FeatureFlag
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.alertDialogs.InformationAlertDialog
import com.infomaniak.mail.ui.alertDialogs.SelectDateAndTimeForScheduledDraftDialog
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleSendBottomSheetDialog.Companion.OPEN_DATE_AND_TIME_SCHEDULE_DIALOG
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleSendBottomSheetDialog.Companion.SCHEDULE_DRAFT_RESULT
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleSendBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.thread.AttachmentAdapter
import com.infomaniak.mail.ui.newMessage.NewMessageRecipientFieldsManager.FieldType
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel.ImportationResult
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel.UiFrom
import com.infomaniak.mail.utils.HtmlUtils.processCids
import com.infomaniak.mail.utils.JsoupParserUtil.jsoupParseWithLog
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.SignatureUtils
import com.infomaniak.mail.utils.UiUtils.PRIMARY_COLOR_CODE
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.WebViewUtils
import com.infomaniak.mail.utils.WebViewUtils.Companion.destroyAndClearHistory
import com.infomaniak.mail.utils.WebViewUtils.Companion.setupNewMessageWebViewSettings
import com.infomaniak.mail.utils.extensions.*
import com.infomaniak.mail.utils.extensions.AttachmentExtensions.openAttachment
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import com.google.android.material.R as RMaterial

@AndroidEntryPoint
class NewMessageFragment : Fragment() {

    private var _binding: FragmentNewMessageBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView
    private val newMessageActivityArgs by lazy {
        // When opening this fragment via deeplink, it can happen that the navigation
        // extras aren't yet initialized, so we don't use the `navArgs` here.
        requireActivity().intent?.extras?.let(NewMessageActivityArgs::fromBundle) ?: NewMessageActivityArgs()
    }
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()
    private val aiViewModel: AiViewModel by activityViewModels()

    private val filePicker = FilePicker(fragment = this).apply {
        initCallback { uris -> newMessageViewModel.importAttachmentsLiveData.value = uris }
    }

    private var addressListPopupWindow: ListPopupWindow? = null

    private var quoteWebView: WebView? = null
    private var signatureWebView: WebView? = null

    private val signatureAdapter = SignatureAdapter(::onSignatureClicked)
    private val attachmentAdapter inline get() = binding.attachmentsRecyclerView.adapter as AttachmentAdapter

    private val newMessageActivity by lazy { requireActivity() as NewMessageActivity }
    private val webViewUtils by lazy { WebViewUtils(requireContext()) }

    @Inject
    lateinit var editorContentManager: EditorContentManager

    @Inject
    lateinit var aiManager: NewMessageAiManager

    @Inject
    lateinit var externalsManager: NewMessageExternalsManager

    @Inject
    lateinit var editorManager: NewMessageEditorManager

    @Inject
    lateinit var recipientFieldsManager: NewMessageRecipientFieldsManager

    @Inject
    lateinit var localSettings: LocalSettings

    @Inject
    lateinit var informationDialog: InformationAlertDialog

    @Inject
    lateinit var signatureUtils: SignatureUtils

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    @Inject
    lateinit var snackbarManager: SnackbarManager

    @Inject
    lateinit var dateAndTimeScheduleDialog: SelectDateAndTimeForScheduledDraftDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentNewMessageBinding.inflate(inflater, container, false).also { _binding = it }.root
    }

    // This `SuppressLint` seems useless, but it's for the CI. Don't remove it.
    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setSystemBarsColors(statusBarColor = R.color.newMessageBackgroundColor)

        SentryDebug.addNavigationBreadcrumb(
            name = findNavController().currentDestination?.displayName ?: "newMessageFragment",
            arguments = newMessageActivityArgs.toBundle(),
        )

        initManagers()

        bindAlertToViewLifecycle(descriptionDialog)

        setWebViewReference()
        initUi()
        initializeDraft()

        handleOnBackPressed()

        observeInitResult()
        observeFromData()
        observeRecipients()
        observeAttachments()
        observeImportAttachmentsResult()
        observeBodyLoader()
        observeUiSignature()
        observeUiQuote()
        observeShimmering()

        setupBackActionHandler()

        with(editorManager) {
            observeEditorFormatActions()
            observeEditorStatus()
        }

        externalsManager.observeExternals(newMessageViewModel.arrivedFromExistingDraft())

        with(aiManager) {
            observeAiOutput()
            observeAiPromptStatus()
            observeAiFeatureFlagUpdates()
        }

        with(recipientFieldsManager) {
            setOnFocusChangedListeners()
            observeContacts()
            observeCcAndBccVisibility()
        }

        observeScheduledDraftsFeatureFlagUpdates()
    }

    private fun setupBackActionHandler() {

        fun scheduleDraft(timestamp: Long) {
            newMessageViewModel.setScheduleDate(Date(timestamp))
            tryToSendEmail(scheduled = true)
        }

        getBackNavigationResult(OPEN_DATE_AND_TIME_SCHEDULE_DIALOG) { _: Boolean ->
            dateAndTimeScheduleDialog.show(
                onDateSelected = { timestamp ->
                    localSettings.lastSelectedScheduleEpoch = timestamp
                    scheduleDraft(timestamp)
                },
                onAbort = ::navigateToScheduleSendBottomSheet,
            )
        }

        getBackNavigationResult(SCHEDULE_DRAFT_RESULT, ::scheduleDraft)

        getBackNavigationResult(AttachmentExtensions.DOWNLOAD_ATTACHMENT_RESULT, ::startActivity)
    }

    private fun setShimmerVisibility(isShimmering: Boolean) = with(binding) {
        fromMailAddress.isGone = isShimmering
        subjectTextField.isGone = isShimmering
        editorWebView.isGone = isShimmering

        fromLoader.isVisible = isShimmering
        subjectLoader.isVisible = isShimmering
        bodyLoader.isVisible = isShimmering

        toField.setShimmerVisibility(isShimmering)
        ccField.setShimmerVisibility(isShimmering)
        bccField.setShimmerVisibility(isShimmering)
    }

    private fun initManagers() {
        aiManager.initValues(
            newMessageViewModel = newMessageViewModel,
            binding = binding,
            fragment = this@NewMessageFragment,
            aiViewModel = aiViewModel,
        )

        externalsManager.initValues(
            newMessageViewModel = newMessageViewModel,
            binding = binding,
            fragment = this@NewMessageFragment,
            informationDialog = informationDialog,
        )

        editorManager.initValues(
            newMessageViewModel = newMessageViewModel,
            binding = binding,
            fragment = this@NewMessageFragment,
            aiManager = aiManager,
            openFilePicker = filePicker::open,
        )

        recipientFieldsManager.initValues(
            newMessageViewModel = newMessageViewModel,
            binding = binding,
            fragment = this@NewMessageFragment,
            externalsManager = externalsManager,
        )
    }

    private fun setWebViewReference() {
        quoteWebView = binding.quoteWebView
        signatureWebView = binding.signatureWebView
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        newMessageViewModel.uiSignatureLiveData.value?.let { _ ->
            binding.signatureWebView.reload()
        }
        newMessageViewModel.uiQuoteLiveData.value?.let { _ ->
            binding.quoteWebView.reload()
        }
        super.onConfigurationChanged(newConfig)
    }

    override fun onDestroyView() {
        // This block of code is needed in order to keep and reload the content of the editor across configuration changes.
        binding.editorWebView.exportHtml { html ->
            newMessageViewModel.editorBodyInitializer.postValue(BodyContentPayload(html, BodyContentType.HTML_SANITIZED))
        }

        addressListPopupWindow = null
        quoteWebView?.destroyAndClearHistory()
        quoteWebView = null
        signatureWebView?.destroyAndClearHistory()
        signatureWebView = null
        TransitionManager.endTransitions(binding.root)
        super.onDestroyView()
        _binding = null
    }

    private fun handleOnBackPressed() {
        newMessageActivity.onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            when {
                aiViewModel.aiPromptOpeningStatus.value?.isOpened == true -> aiManager.closeAiPrompt()
                newMessageViewModel.isAutoCompletionOpened -> recipientFieldsManager.closeAutoCompletion()
                else -> newMessageActivity.finishAppAndRemoveTaskIfNeeded()
            }
        }
    }

    private fun initUi() = with(binding) {
        addressListPopupWindow = ListPopupWindow(binding.root.context)

        toolbar.setNavigationOnClickListener { activity?.onBackPressedDispatcher?.onBackPressed() }
        changeToolbarColorOnScroll(toolbar, compositionNestedScrollView)

        signatureWebView.enableAlgorithmicDarkening(true)
        quoteWebView.enableAlgorithmicDarkening(true)

        attachmentsRecyclerView.adapter = AttachmentAdapter(
            shouldDisplayCloseButton = true,
            onDelete = ::onDeleteAttachment,
            onAttachmentClicked = {
                if (it !is Attachment) return@AttachmentAdapter

                trackAttachmentActionsEvent(OPEN_FROM_DRAFT_NAME)
                it.openAttachment(
                    context = requireContext(),
                    navigateToDownloadProgressDialog = { attachment, attachmentIntentType ->
                        navigateToDownloadProgressDialog(
                            attachment,
                            attachmentIntentType,
                            NewMessageFragment::class.java.name,
                        )
                    },
                    snackbarManager = snackbarManager,
                )
            },
        )

        recipientFieldsManager.setupAutoCompletionFields()

        subjectTextField.filters = arrayOf<InputFilter>(object : InputFilter {
            override fun filter(source: CharSequence?, s: Int, e: Int, d: Spanned?, dS: Int, dE: Int): CharSequence? {
                source?.toString()?.let { if (it.contains("\n")) return it.replace("\n", "") }
                return null
            }
        })

        initEditorUi()

        setupSendButtons()
        externalsManager.setupExternalBanner()

        scrim.setOnClickListener {
            scrim.isClickable = false
            aiManager.closeAiPrompt()
        }
    }

    private fun initEditorUi() {
        binding.editorWebView.subscribeToStates(setOf(BOLD, ITALIC, UNDERLINE, STRIKE_THROUGH, UNORDERED_LIST, CREATE_LINK))
        setEditorStyle()
        handleEditorPlaceholderVisibility()

        setToolbarEnabledStatus(false)
        disableButtonsWhenFocusIsLost()
    }

    private fun setEditorStyle() = with(binding.editorWebView) {
        enableAlgorithmicDarkening(isEnabled = true)
        if (context.isNightModeEnabled()) addCss(context.loadCss(R.raw.custom_dark_mode))

        val customColors = listOf(PRIMARY_COLOR_CODE to context.getAttributeColor(RMaterial.attr.colorPrimary))
        addCss(context.loadCss(R.raw.style, customColors))
        addCss(context.loadCss(R.raw.editor_style, customColors))
    }

    private fun handleEditorPlaceholderVisibility() {
        val isPlaceholderVisible = combine(
            binding.editorWebView.isEmptyFlow.filterNotNull(),
            newMessageViewModel.isShimmering,
        ) { isEditorEmpty, isShimmering -> isEditorEmpty && !isShimmering }

        isPlaceholderVisible
            .onEach { isVisible -> binding.newMessagePlaceholder.isVisible = isVisible }
            .launchIn(lifecycleScope)
    }

    private fun disableButtonsWhenFocusIsLost() {
        newMessageViewModel.isEditorWebViewFocusedLiveData.observe(viewLifecycleOwner, ::setToolbarEnabledStatus)
    }

    private fun setToolbarEnabledStatus(isEnabled: Boolean) {
        binding.formatOptionsLayout.forEach { view -> view.isEnabled = isEnabled }
    }

    private fun initializeDraft() = with(newMessageViewModel) {
        if (initResult.value == null) {
            initDraftAndViewModel(intent = requireActivity().intent).observe(viewLifecycleOwner) { draft ->
                if (draft != null) {
                    showKeyboardInCorrectView(isToFieldEmpty = draft.to.isEmpty())
                    binding.subjectTextField.setText(draft.subject)
                } else {
                    requireActivity().apply {
                        showToast(R.string.failToOpenDraft)
                        finish()
                    }
                }
            }
        }
    }

    private fun showKeyboardInCorrectView(isToFieldEmpty: Boolean) = with(recipientFieldsManager) {
        when (newMessageViewModel.draftMode()) {
            DraftMode.REPLY,
            DraftMode.REPLY_ALL -> focusBodyField()
            DraftMode.FORWARD -> focusToField()
            DraftMode.NEW_MAIL -> if (isToFieldEmpty) focusToField() else focusBodyField()
        }
    }

    private fun configureUiWithDraftData(draft: Draft) = with(binding) {

        // Signature
        signatureWebView.apply {
            settings.setupNewMessageWebViewSettings()
            initWebViewClientAndBridge(
                attachments = emptyList(),
                messageUid = "SIGNATURE-${draft.messageUid}",
                shouldLoadDistantResources = true,
                navigateToNewMessageActivity = null,
            )
        }
        removeSignature.setOnClickListener {
            trackNewMessageEvent("deleteSignature")
            newMessageViewModel.uiSignatureLiveData.value = null
        }

        // Quote
        quoteWebView.apply {
            settings.setupNewMessageWebViewSettings()
            val alwaysShowExternalContent = localSettings.externalContent == ExternalContent.ALWAYS
            initWebViewClientAndBridge(
                attachments = draft.attachments,
                messageUid = "QUOTE-${draft.messageUid}",
                shouldLoadDistantResources = alwaysShowExternalContent || newMessageViewModel.shouldLoadDistantResources(),
                navigateToNewMessageActivity = null,
            )
        }
        removeQuote.setOnClickListener {
            trackNewMessageEvent("deleteQuote")
            removeInlineAttachmentsUsedInQuote()
            newMessageViewModel.uiQuoteLiveData.value = null
        }
    }

    private fun removeInlineAttachmentsUsedInQuote() = with(newMessageViewModel) {
        uiQuoteLiveData.value?.let { html ->
            attachmentsLiveData.value?.filterOutHtmlCids(html)?.let { attachmentsLiveData.value = it }
        }
    }

    private fun List<Attachment>.filterOutHtmlCids(html: String): List<Attachment> {
        return buildList {
            addAll(this@filterOutHtmlCids)

            jsoupParseWithLog(html).processCids(
                attachments = this@filterOutHtmlCids,
                associateDataToCid = { it },
                onCidImageFound = { attachment, _ ->
                    remove(attachment)
                }
            )
        }
    }

    private fun WebView.loadSignatureContent(html: String, webViewGroup: Group) {
        val processedHtml = webViewUtils.processSignatureHtmlForDisplay(html, context.isNightModeEnabled())
        loadProcessedContent(processedHtml, webViewGroup)
    }

    private fun WebView.loadContent(html: String, webViewGroup: Group) {
        val processedHtml = webViewUtils.processHtmlForDisplay(html = html, isDisplayedInDarkMode = context.isNightModeEnabled())
        loadProcessedContent(processedHtml, webViewGroup)
    }

    private fun WebView.loadProcessedContent(processedHtml: String, webViewGroup: Group) {
        webViewGroup.isVisible = processedHtml.isNotBlank()
        loadDataWithBaseURL("", processedHtml, ClipDescription.MIMETYPE_TEXT_HTML, Utils.UTF_8, "")
    }

    private fun setupFromField(signatures: List<Signature>) = with(binding) {

        signatureAdapter.setList(signatures)

        fromMailAddress.post {
            runCatching {
                addressListPopupWindow?.width = fromMailAddress.width
            }.onFailure {
                Sentry.captureMessage("Binding null in post(), this is not normal", SentryLevel.WARNING)
            }
        }

        addressListPopupWindow?.apply {
            setAdapter(signatureAdapter)
            isModal = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            anchorView = fromMailAddress
        }

        if (newMessageViewModel.signaturesCount > 1) {
            fromMailAddress.apply {
                icon = AppCompatResources.getDrawable(context, R.drawable.ic_chevron_down)
                setOnClickListener { _ -> addressListPopupWindow?.show() }

                // Set `isFocusable` here instead of in XML file because setting it in the
                // XML doesn't trigger the overridden `setFocusable(boolean)` in AvatarView.
                isFocusable = true
            }
        }
    }

    private fun onSignatureClicked(signature: Signature) {
        trackNewMessageEvent("switchIdentity")
        newMessageViewModel.fromLiveData.value = UiFrom(signature)
        addressListPopupWindow?.dismiss()
    }

    private fun updateSelectedSignatureInFromField(signature: Signature) {

        val defaultFormat = if (signature.senderName.isBlank()) {
            signature.senderEmailIdn
        } else {
            "${signature.senderName} <${signature.senderEmailIdn}>"
        }

        val formattedExpeditor = when {
            signature.isDummy -> signature.senderEmailIdn
            newMessageViewModel.signaturesCount > 1 -> "$defaultFormat (${signature.name})"
            else -> defaultFormat
        }

        binding.fromMailAddress.text = formattedExpeditor
    }

    private fun observeInitResult() {
        newMessageViewModel.initResult.observe(viewLifecycleOwner) { (draft, signatures) ->
            configureUiWithDraftData(draft)
            setupFromField(signatures)
            editorManager.setupEditorFormatActions()
            editorManager.setupEditorFormatActionsToggle()
        }
    }

    private fun observeFromData() = with(newMessageViewModel) {
        fromLiveData.observe(viewLifecycleOwner) { (signature, shouldUpdateBodySignature) ->
            updateSelectedSignatureInFromField(signature)
            if (shouldUpdateBodySignature) updateBodySignature(signature)
            signatureAdapter.updateSelectedSignature(signature.id)
        }
    }

    private fun observeRecipients() = with(newMessageViewModel) {

        var shouldInitToField = true
        var shouldInitCcField = true
        var shouldInitBccField = true

        toLiveData.observe(viewLifecycleOwner) {
            if (shouldInitToField) {
                shouldInitToField = false
                binding.toField.initRecipients(it.recipients, it.otherFieldsAreEmpty)
            }
            updateIsSendingAllowed(type = FieldType.TO, recipients = it.recipients)
        }

        ccLiveData.observe(viewLifecycleOwner) {
            if (shouldInitCcField) {
                shouldInitCcField = false
                binding.ccField.initRecipients(it.recipients)
            }
            updateIsSendingAllowed(type = FieldType.CC, recipients = it.recipients)
            updateOtherRecipientsFieldsAreEmpty(cc = it.recipients, bcc = bccLiveData.valueOrEmpty())
        }

        bccLiveData.observe(viewLifecycleOwner) {
            if (shouldInitBccField) {
                shouldInitBccField = false
                binding.bccField.initRecipients(it.recipients)
            }
            updateIsSendingAllowed(type = FieldType.BCC, recipients = it.recipients)
            updateOtherRecipientsFieldsAreEmpty(cc = ccLiveData.valueOrEmpty(), bcc = it.recipients)
        }
    }

    private fun observeAttachments() = with(newMessageViewModel) {

        var isFirstTime = true

        attachmentsLiveData.observe(viewLifecycleOwner) { attachments ->

            if (isFirstTime) {
                isFirstTime = false
                observeImportAttachments()
            } else if (attachments.count() > attachmentAdapter.itemCount) {
                // If we are adding Attachments, directly upload them to save time when sending/saving the Draft.
                newMessageViewModel.uploadAttachmentsToServer(attachments)
            }

            /*
             * When removing an Attachment, both counts will be the same, because the Adapter is already notified.
             * We don't want to notify it again, because it will cancel the nice animation.
             */
            if (attachments.count() != attachmentAdapter.itemCount) {
                attachmentAdapter.submitList(attachments.filterNot { it.disposition == AttachmentDisposition.INLINE })
            }

            if (attachments.isEmpty()) TransitionManager.beginDelayedTransition(binding.root)
            binding.attachmentsRecyclerView.isVisible = attachments.isNotEmpty()

            updateIsSendingAllowed(attachments)
        }
    }

    private fun observeImportAttachments() = with(newMessageViewModel) {
        importAttachmentsLiveData.observe(viewLifecycleOwner) { uris ->
            val currentAttachments = attachmentsLiveData.valueOrEmpty()
            importNewAttachments(currentAttachments, uris) { newAttachments ->
                attachmentsLiveData.postValue(currentAttachments + newAttachments)
            }
        }
    }

    private fun observeImportAttachmentsResult() = with(newMessageViewModel) {
        importAttachmentsResult.observe(viewLifecycleOwner) { result ->
            if (result == ImportationResult.ATTACHMENTS_TOO_BIG) showSnackbar(R.string.attachmentFileLimitReached)
        }
    }

    private fun observeBodyLoader() {
        newMessageViewModel.editorBodyInitializer.observe(viewLifecycleOwner) { body ->
            editorContentManager.setContent(binding.editorWebView, body)
        }
    }

    private fun observeUiSignature() = with(binding) {
        newMessageViewModel.uiSignatureLiveData.observe(viewLifecycleOwner) { signature ->
            if (signature == null) {
                signatureGroup.isGone = true
            } else {
                signatureWebView.loadSignatureContent(signature, signatureGroup)
            }
        }
    }

    private fun observeUiQuote() = with(binding) {
        newMessageViewModel.uiQuoteLiveData.observe(viewLifecycleOwner) { quote ->
            if (quote == null) {
                quoteGroup.isGone = true
            } else {
                quoteWebView.loadContent(quote, quoteGroup)
            }
        }
    }

    private fun observeShimmering() = lifecycleScope.launch {
        newMessageViewModel.isShimmering.collect(::setShimmerVisibility)
    }

    private fun observeScheduledDraftsFeatureFlagUpdates() {
        newMessageViewModel.currentMailboxLive.observeNotNull(viewLifecycleOwner) { mailbox ->
            val isScheduledDraftsEnabled = mailbox.featureFlags.contains(FeatureFlag.SCHEDULE_DRAFTS)
            binding.scheduleButton.isVisible = isScheduledDraftsEnabled
        }
    }

    override fun onStart() {
        super.onStart()
        newMessageViewModel.discardOldBodyAndSubjectChannelMessages()
    }

    override fun onStop() {
        // When the Activity is being stopped, we save the Draft. To do this, we need to know the subject and body values but
        // these values might not be accessible anymore by then. We store them right now before potentially loosing access to them
        // in case we need to save the Draft when they're inaccessible.
        val subject = binding.subjectTextField.text.toString()
        binding.editorWebView.exportHtml { html ->
            newMessageViewModel.storeBodyAndSubject(subject, html)
        }

        super.onStop()
    }

    private fun onDeleteAttachment(position: Int) {
        trackAttachmentActionsEvent("delete")
        newMessageViewModel.deleteAttachment(position)
    }

    private fun setupSendButtons() = with(binding) {
        newMessageViewModel.isSendingAllowed.observe(viewLifecycleOwner) {
            scheduleButton.isEnabled = it
            sendButton.isEnabled = it
        }

        scheduleButton.setOnClickListener { if (checkMailboxStorage()) navigateToScheduleSendBottomSheet() }

        sendButton.setOnClickListener { if (checkMailboxStorage()) tryToSendEmail() }
    }

    private fun navigateToScheduleSendBottomSheet() {
        safeNavigate(
            resId = R.id.scheduleSendBottomSheetDialog,
            args = ScheduleSendBottomSheetDialogArgs(
                lastSelectedScheduleEpoch = localSettings.lastSelectedScheduleEpoch ?: 0L,
                isCurrentMailboxFree = newMessageViewModel.currentMailbox.isFreeMailbox,
            ).toBundle(),
        )
    }

    private fun tryToSendEmail(scheduled: Boolean = false) {

        fun setSnackbarActivityResult() {
            val resultIntent = Intent()
            resultIntent.putExtra(
                MainActivity.DRAFT_ACTION_KEY,
                if (scheduled) DraftAction.SCHEDULE.name else DraftAction.SEND.name,
            )
            requireActivity().setResult(AppCompatActivity.RESULT_OK, resultIntent)
        }

        fun sendEmail() {
            newMessageViewModel.draftAction = if (scheduled) DraftAction.SCHEDULE else DraftAction.SEND
            setSnackbarActivityResult()
            requireActivity().finishAppAndRemoveTaskIfNeeded()
        }

        if (isSubjectBlank()) {
            trackNewMessageEvent("sendWithoutSubject")
            descriptionDialog.show(
                title = getString(R.string.emailWithoutSubjectTitle),
                description = getString(R.string.emailWithoutSubjectDescription),
                displayLoader = false,
                positiveButtonText = R.string.buttonContinue,
                onPositiveButtonClicked = {
                    trackNewMessageEvent("sendWithoutSubjectConfirm")
                    sendEmail()
                },
                onCancel = { if (scheduled) newMessageViewModel.resetScheduledDate() },
            )
        } else {
            sendEmail()
        }
    }

    private fun checkMailboxStorage(): Boolean {
        val isMailboxFull = newMessageViewModel.currentMailbox.quotas?.isFull == true
        if (isMailboxFull) {
            trackNewMessageEvent("trySendingWithMailboxFull")
            showSnackbar(R.string.myKSuiteSpaceFullAlert)
        }

        return !isMailboxFull
    }

    private fun Activity.finishAppAndRemoveTaskIfNeeded() {
        if (isTaskRoot) finishAndRemoveTask() else finish()
    }

    fun navigateToPropositionFragment() = aiManager.navigateToPropositionFragment()

    fun closeAiPrompt() = aiManager.closeAiPrompt()

    fun isSubjectBlank() = binding.subjectTextField.text?.isBlank() == true
}
