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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.transition.TransitionManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListPopupWindow
import android.widget.PopupWindow
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.forEach
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.common.extensions.isNightModeEnabled
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.ksuite.ui.utils.MatomoKSuite
import com.infomaniak.core.legacy.utils.FilePicker
import com.infomaniak.core.legacy.utils.SnackbarUtils.showSnackbar
import com.infomaniak.core.legacy.utils.getBackNavigationResult
import com.infomaniak.core.legacy.utils.setMargins
import com.infomaniak.core.ui.showToast
import com.infomaniak.lib.richhtmleditor.StatusCommand.BOLD
import com.infomaniak.lib.richhtmleditor.StatusCommand.CREATE_LINK
import com.infomaniak.lib.richhtmleditor.StatusCommand.ITALIC
import com.infomaniak.lib.richhtmleditor.StatusCommand.STRIKE_THROUGH
import com.infomaniak.lib.richhtmleditor.StatusCommand.UNDERLINE
import com.infomaniak.lib.richhtmleditor.StatusCommand.UNORDERED_LIST
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackAttachmentActionsEvent
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.MatomoMail.trackScheduleSendEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.AttachmentDisposition
import com.infomaniak.mail.data.models.FeatureFlag
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.alertDialogs.InformationAlertDialog
import com.infomaniak.mail.ui.alertDialogs.SelectDateAndTimeForScheduledDraftDialog
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleSendBottomSheetDialog.Companion.OPEN_SCHEDULE_DRAFT_DATE_AND_TIME_PICKER
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleSendBottomSheetDialog.Companion.SCHEDULE_DRAFT_RESULT
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleSendBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.thread.AttachmentAdapter
import com.infomaniak.mail.ui.newMessage.NewMessageRecipientFieldsManager.FieldType
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel.ImportationResult
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel.UiFrom
import com.infomaniak.mail.ui.newMessage.encryption.EncryptionMessageManager
import com.infomaniak.mail.ui.newMessage.encryption.EncryptionViewModel
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.MessageBodyUtils
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.SignatureUtils
import com.infomaniak.mail.utils.UiUtils.PRIMARY_COLOR_CODE
import com.infomaniak.mail.utils.extensions.AttachmentExt
import com.infomaniak.mail.utils.extensions.AttachmentExt.openAttachment
import com.infomaniak.mail.utils.extensions.applySideAndBottomSystemInsets
import com.infomaniak.mail.utils.extensions.applyStatusBarInsets
import com.infomaniak.mail.utils.extensions.applyWindowInsetsListener
import com.infomaniak.mail.utils.extensions.bindAlertToViewLifecycle
import com.infomaniak.mail.utils.extensions.changeToolbarColorOnScroll
import com.infomaniak.mail.utils.extensions.enableAlgorithmicDarkening
import com.infomaniak.mail.utils.extensions.getAttributeColor
import com.infomaniak.mail.utils.extensions.ime
import com.infomaniak.mail.utils.extensions.loadCss
import com.infomaniak.mail.utils.extensions.navigateToDownloadProgressDialog
import com.infomaniak.mail.utils.extensions.readRawResource
import com.infomaniak.mail.utils.extensions.systemBars
import com.infomaniak.mail.utils.extensions.valueOrEmpty
import com.infomaniak.mail.utils.openKSuiteProBottomSheet
import com.infomaniak.mail.utils.openMailPremiumBottomSheet
import com.infomaniak.mail.utils.openMyKSuiteUpgradeBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import splitties.experimental.ExperimentalSplittiesApi
import java.util.Date
import javax.inject.Inject
import androidx.appcompat.R as RAndroid

@AndroidEntryPoint
class NewMessageFragment : Fragment() {

    private var _binding: FragmentNewMessageBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView
    private val newMessageActivityArgs by lazy {
        // When opening this fragment via deeplink, it can happen that the navigation
        // extras aren't yet initialized, so we don't use the `navArgs` here.
        requireActivity().intent?.extras?.let(NewMessageActivityArgs::fromBundle) ?: NewMessageActivityArgs()
    }
    private val newMessageFragmentArgs: NewMessageFragmentArgs by navArgs()
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()
    private val aiViewModel: AiViewModel by activityViewModels()
    private val encryptionViewModel: EncryptionViewModel by activityViewModels()
    private var hasPlaceholder = true

    private val filePicker = FilePicker(fragment = this).apply {
        initCallback { uris -> newMessageViewModel.importAttachmentsLiveData.value = uris }
    }

    private var addressListPopupWindow: ListPopupWindow? = null

    private val signatureAdapter = SignatureAdapter(::onSignatureClicked)
    private val attachmentAdapter inline get() = binding.attachmentsRecyclerView.adapter as AttachmentAdapter

    private val newMessageActivity by lazy { requireActivity() as NewMessageActivity }

    @Inject
    lateinit var editorContentManager: EditorContentManager

    @Inject
    lateinit var aiManager: NewMessageAiManager

    @Inject
    lateinit var encryptionMessageManager: EncryptionMessageManager

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
        handleEdgeToEdge()

        SentryDebug.addNavigationBreadcrumb(
            name = findNavController().currentDestination?.displayName ?: "newMessageFragment",
            arguments = newMessageActivityArgs.toBundle(),
        )

        initManagers()

        bindAlertToViewLifecycle(descriptionDialog)

        initMailbox()
        initUi()
        initializeDraft()

        handleOnBackPressed()

        observeInitResult()
        observeFromData()
        observeRecipients()
        observeAttachments()
        observeImportAttachmentsResult()
        observeBodyLoader()
        observeShimmering()

        setupBackActionHandler()

        with(editorManager) {
            observeEditorFormatActions()
            observeEditorStatus()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            externalsManager.observeExternals(newMessageViewModel.arrivedFromExistingDraft())
        }

        with(aiManager) {
            observeAiOutput()
            observeAiPromptStatus()
            observeAiFeatureFlagUpdates()
        }

        with(encryptionMessageManager) {
            observeEncryptionFeatureFlagUpdates()
            observeEncryptionData()
            observeEncryptionActivation()
            observeUnencryptableRecipients()
        }

        with(recipientFieldsManager) {
            setOnFocusChangedListeners()
            observeContacts()
            observeCcAndBccVisibility()
        }

        observeScheduledDraftsFeatureFlagUpdates()
    }

    private fun handleEdgeToEdge() = with(binding) {
        applyWindowInsetsListener(shouldConsume = false) { _, insets ->
            appBarLayout.applyStatusBarInsets(insets)
            compositionNestedScrollView.applySideAndBottomSystemInsets(insets, withBottom = false)
            externalBannerContent.applySideAndBottomSystemInsets(insets, withBottom = false)
            editorActionsLayout.applySideAndBottomSystemInsets(insets, withBottom = false)

            val imeBottomInset = insets.ime().bottom
            newMessageConstraintLayout.updatePadding(
                bottom = if (imeBottomInset == 0) insets.systemBars().bottom else 0,
            )
            editorToolbar.setMargins(bottom = imeBottomInset)
        }
    }

    private fun setupBackActionHandler() {

        fun scheduleDraft(timestamp: Long) {
            newMessageViewModel.setScheduleDate(Date(timestamp))
            tryToSendEmail(scheduled = true)
        }

        getBackNavigationResult(OPEN_SCHEDULE_DRAFT_DATE_AND_TIME_PICKER) { _: Boolean ->
            dateAndTimeScheduleDialog.show(
                onDateSelected = { timestamp ->
                    trackScheduleSendEvent(MatomoName.CustomSchedule)
                    localSettings.lastSelectedScheduleEpochMillis = timestamp
                    scheduleDraft(timestamp)
                },
                onAbort = ::navigateToScheduleSendBottomSheet,
            )
        }

        getBackNavigationResult(SCHEDULE_DRAFT_RESULT, ::scheduleDraft)

        getBackNavigationResult(AttachmentExt.DOWNLOAD_ATTACHMENT_RESULT, ::startActivity)
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
            encryptionManager = encryptionMessageManager,
            openFilePicker = filePicker::open,
        )

        encryptionMessageManager.init(
            newMessageViewModel = newMessageViewModel,
            binding = binding,
            fragment = this@NewMessageFragment,
            encryptionViewModel = encryptionViewModel,
        )

        recipientFieldsManager.initValues(
            newMessageViewModel = newMessageViewModel,
            binding = binding,
            fragment = this@NewMessageFragment,
            externalsManager = externalsManager,
            encryptionMessageManager = encryptionMessageManager,
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        //TODO: CHECK IF WE SHOULD ADD A RELOD HERE
        super.onConfigurationChanged(newConfig)
    }

    override fun onDestroyView() {
        // This block of code is needed in order to keep and reload the content of the editor across configuration changes.
        binding.editorWebView.exportHtml { html ->
            newMessageViewModel.editorBodyInitializer.postValue(BodyContentPayload(html, BodyContentType.HTML_SANITIZED))
        }

        addressListPopupWindow = null
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

    private fun initMailbox() {
        val userId = if (newMessageFragmentArgs.userId == -1) AccountUtils.currentUserId else newMessageFragmentArgs.userId
        val mailboxId = if (newMessageFragmentArgs.mailboxId == -1) {
            AccountUtils.currentMailboxId
        } else {
            newMessageFragmentArgs.mailboxId
        }
        newMessageViewModel.loadMailbox(userId, mailboxId)
    }

    private fun initUi() = with(binding) {
        addressListPopupWindow = ListPopupWindow(binding.root.context)

        toolbar.setNavigationOnClickListener { activity?.onBackPressedDispatcher?.onBackPressed() }
        changeToolbarColorOnScroll(appBarLayout, compositionNestedScrollView)

        attachmentsRecyclerView.adapter = AttachmentAdapter(
            shouldDisplayCloseButton = true,
            onDelete = ::onDeleteAttachment,
            onAttachmentClicked = {
                if (it !is Attachment) return@AttachmentAdapter

                trackAttachmentActionsEvent(MatomoName.OpenFromDraft)
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

        viewLifecycleOwner.lifecycleScope.launch { setupSendButtons() }
        externalsManager.setupExternalBanner()

        scrim.setOnClickListener {
            scrim.isClickable = false
            aiManager.closeAiPrompt()
        }
    }

    private fun initEditorUi() = with(binding) {
        editorWebView.subscribeToStates(setOf(BOLD, ITALIC, UNDERLINE, STRIKE_THROUGH, UNORDERED_LIST, CREATE_LINK))
        setEditorStyle()
        editorAiAnimation.setAnimation(R.raw.euria)
        setToolbarEnabledStatus(false)
        handleFocusChanges()
    }

    fun editorHasPlaceholder() = with(binding.editorWebView) {
        exportHtml { html ->
            hasPlaceholder = newMessageViewModel.bodyHasPlaceholder(html)
        }
    }

    private fun setEditorStyle() = with(binding.editorWebView) {
        enableAlgorithmicDarkening(isEnabled = true)
        if (context.isNightModeEnabled()) addCss(context.loadCss(R.raw.custom_dark_mode))

        val customColors = listOf(PRIMARY_COLOR_CODE to context.getAttributeColor(RAndroid.attr.colorPrimary))
        addCss(context.loadCss(R.raw.style, customColors))
        addCss(context.loadCss(R.raw.editor_style, customColors))
    }

    private fun removePlaceholder() = with(binding.editorWebView) {
        exportHtml { html ->
            val doc: Document = Jsoup.parseBodyFragment(html).apply {
                getElementsByClass("placeholder").first()?.text("")?.removeClass("placeholder")
            }

            newMessageViewModel.editorBodyInitializer.postValue(
                BodyContentPayload(
                    doc.html(),
                    BodyContentType.HTML_SANITIZED
                )
            )
        }
    }

    private fun handleFocusChanges() {
        newMessageViewModel.isEditorWebViewFocusedLiveData.observe(viewLifecycleOwner) { isFocused ->
            setToolbarEnabledStatus(isFocused)
            if (isFocused && hasPlaceholder) {
                removePlaceholder()
            }
        }
    }

    private fun setToolbarEnabledStatus(isEnabled: Boolean) {
        binding.formatOptionsLayout.forEach { view -> view.isEnabled = isEnabled }
    }

    private fun initializeDraft() = with(newMessageViewModel) {
        if (initResult.value == null) {
            initDraftAndViewModel(intent = requireActivity().intent).observe(viewLifecycleOwner) { draft ->
                if (draft != null) {
                    val isBodyEmpty = newMessageViewModel.bodyHasPlaceholder(draft.body)
                    showKeyboardInCorrectView(isToFieldEmpty = draft.to.isEmpty(), isBodyEmpty = isBodyEmpty)
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

    private fun showKeyboardInCorrectView(isToFieldEmpty: Boolean, isBodyEmpty: Boolean) = with(recipientFieldsManager) {
        when (newMessageViewModel.draftMode()) {
            DraftMode.REPLY,
            DraftMode.REPLY_ALL -> if (isBodyEmpty) focusBodyField()
            DraftMode.FORWARD -> focusToField()
            DraftMode.NEW_MAIL -> if (isToFieldEmpty) focusToField() else focusBodyField()
        }
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
        trackNewMessageEvent(MatomoName.SwitchIdentity)

        binding.editorWebView.exportHtml { html ->
            Log.d("HTML", html)
            val body = Jsoup.parse(html).body()
            val oldSignature = newMessageViewModel.fromLiveData.value?.signature?.content

            val bodyHtml = if (!oldSignature.isNullOrEmpty()) {
                removeSignature(body.html())
            } else {
                body.html()
            }

            newMessageViewModel.fromLiveData.value = UiFrom(signature)
            addressListPopupWindow?.dismiss()

            // Get the New Signature HTML
            val newSignatureHtml = signature.content
            val wrappedNewSignature =
                if (signature.isDummy) "" else signatureUtils.encapsulateSignatureContentWithInfomaniakClass(newSignatureHtml)

            // Combine: New Body + New Signature
            val finalHtml = addInsideBody(bodyHtml, wrappedNewSignature)

            // Update the Editor
            newMessageViewModel.editorBodyInitializer.postValue(BodyContentPayload(finalHtml, BodyContentType.HTML_SANITIZED))
        }
    }

    private fun addInsideBody(bodyHtml: String, element: String): String {
        if (element.isEmpty()) return bodyHtml

        val doc = Jsoup.parseBodyFragment(bodyHtml)
        doc.body().append(element)
        return doc.body().html()
    }

    private fun removeSignature(html: String): String {
        val doc: Document = Jsoup.parseBodyFragment(html).apply {
            getElementById(MessageBodyUtils.INFOMANIAK_SIGNATURE_HTML_ID)?.remove()
        }

        return doc.html()
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
        newMessageViewModel.initResult.observe(viewLifecycleOwner) { (_, signatures) ->
            setupFromField(signatures)
            editorManager.setupEditorFormatActions()
            editorManager.setupEditorFormatActionsToggle()
        }
    }

    private fun observeFromData() = with(newMessageViewModel) {
        fromLiveData.observe(viewLifecycleOwner) { (signature) ->
            updateSelectedSignatureInFromField(signature)
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
            updateIsSendingAllowed(
                type = FieldType.TO,
                recipients = it.recipients,
                isEncryptionValid = encryptionMessageManager.checkEncryptionCanBeSend(),
            )
        }

        ccLiveData.observe(viewLifecycleOwner) {
            if (shouldInitCcField) {
                shouldInitCcField = false
                binding.ccField.initRecipients(it.recipients)
            }
            updateIsSendingAllowed(
                type = FieldType.CC,
                recipients = it.recipients,
                isEncryptionValid = encryptionMessageManager.checkEncryptionCanBeSend(),
            )
            updateOtherRecipientsFieldsAreEmpty(cc = it.recipients, bcc = bccLiveData.valueOrEmpty())
        }

        bccLiveData.observe(viewLifecycleOwner) {
            if (shouldInitBccField) {
                shouldInitBccField = false
                binding.bccField.initRecipients(it.recipients)
            }
            updateIsSendingAllowed(
                type = FieldType.BCC,
                recipients = it.recipients,
                isEncryptionValid = encryptionMessageManager.checkEncryptionCanBeSend(),
            )
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

            updateIsSendingAllowed(attachments, isEncryptionValid = encryptionMessageManager.checkEncryptionCanBeSend())
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
            val script = context?.readRawResource(R.raw.toggle_quote_visibility_script)
            script?.let { binding.editorWebView.addScript(script) }
            editorHasPlaceholder()
        }
    }

    private fun observeShimmering() = lifecycleScope.launch {
        newMessageViewModel.isShimmering.collect(::setShimmerVisibility)
    }

    private fun observeScheduledDraftsFeatureFlagUpdates() {
        newMessageViewModel.featureFlagsLive.observe(viewLifecycleOwner) { featureFlags ->
            val isScheduledDraftsEnabled = featureFlags.contains(FeatureFlag.SCHEDULE_DRAFTS)
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
        trackAttachmentActionsEvent(MatomoName.Delete)
        newMessageViewModel.deleteAttachment(position)
    }

    private suspend fun setupSendButtons() = with(binding) {

        val mailbox = newMessageViewModel.currentMailbox()

        newMessageViewModel.isSendingAllowed.observe(viewLifecycleOwner) {
            scheduleButton.isEnabled = it
            sendButton.isEnabled = it
        }

        scheduleButton.setOnClickListener {
            if (checkMailboxStorage(mailbox)) {
                if (newMessageViewModel.isEncryptionActivated.value == true) {
                    snackbarManager.postValue(getString(R.string.encryptedMessageSnackbarScheduledUnavailable))
                } else {
                    navigateToScheduleSendBottomSheet()
                }
            }
        }

        sendButton.setOnClickListener { if (checkMailboxStorage(mailbox)) tryToSendEmail() }
    }

    private fun navigateToScheduleSendBottomSheet(): Job = viewLifecycleOwner.lifecycleScope.launch {
        val mailbox = newMessageViewModel.currentMailbox()
        safelyNavigate(
            resId = R.id.scheduleSendBottomSheetDialog,
            args = ScheduleSendBottomSheetDialogArgs(
                lastSelectedScheduleEpochMillis = localSettings.lastSelectedScheduleEpochMillis ?: 0L,
                currentKSuite = mailbox.kSuite,
                isAdmin = mailbox.isAdmin,
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
            trackNewMessageEvent(MatomoName.SendWithoutSubject)
            descriptionDialog.show(
                title = getString(R.string.emailWithoutSubjectTitle),
                description = getString(R.string.emailWithoutSubjectDescription),
                displayLoader = false,
                positiveButtonText = R.string.buttonContinue,
                onPositiveButtonClicked = {
                    trackNewMessageEvent(MatomoName.SendWithoutSubjectConfirm)
                    sendEmail()
                },
                onCancel = { if (scheduled) newMessageViewModel.resetScheduledDate() },
            )
        } else {
            sendEmail()
        }
    }

    private fun checkMailboxStorage(mailbox: Mailbox): Boolean {

        val isMailboxFull = mailbox.quotas?.isFull == true

        if (isMailboxFull) {
            trackNewMessageEvent(MatomoName.TrySendingWithMailboxFull)

            val kSuite = mailbox.kSuite
            val matomoName = MatomoKSuite.NOT_ENOUGH_STORAGE_UPGRADE_NAME
            val onActionClicked: (() -> Unit)? = when (kSuite) {
                KSuite.Perso.Free -> fun() = openMyKSuiteUpgradeBottomSheet(matomoName)
                KSuite.Pro.Free -> fun() = openKSuiteProBottomSheet(kSuite, mailbox.isAdmin, matomoName)
                KSuite.StarterPack -> fun() = openMailPremiumBottomSheet(matomoName)
                else -> null
            }

            showSnackbar(
                R.string.myKSuiteSpaceFullAlert,
                actionButtonTitle = R.string.buttonUpgrade,
                onActionClicked = onActionClicked,
            )
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
