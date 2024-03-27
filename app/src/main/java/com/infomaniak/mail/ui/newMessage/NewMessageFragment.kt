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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.isNightModeEnabled
import com.infomaniak.lib.core.utils.showToast
import com.infomaniak.mail.MatomoMail.trackAttachmentActionsEvent
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.ExternalContent
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.alertDialogs.InformationAlertDialog
import com.infomaniak.mail.ui.main.thread.AttachmentAdapter
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel.ImportationResult
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel.UiFrom
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.WebViewUtils.Companion.destroyAndClearHistory
import com.infomaniak.mail.utils.WebViewUtils.Companion.setupNewMessageWebViewSettings
import com.infomaniak.mail.utils.extensions.bindAlertToViewLifecycle
import com.infomaniak.mail.utils.extensions.changeToolbarColorOnScroll
import com.infomaniak.mail.utils.extensions.initWebViewClientAndBridge
import com.infomaniak.mail.utils.extensions.setSystemBarsColors
import com.infomaniak.mail.workers.DraftsActionsWorker
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Sentry
import io.sentry.SentryLevel
import javax.inject.Inject

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

    private var addressListPopupWindow: ListPopupWindow? = null

    private var quoteWebView: WebView? = null
    private var signatureWebView: WebView? = null

    private val signatureAdapter = SignatureAdapter(::onSignatureClicked)
    private val attachmentAdapter inline get() = binding.attachmentsRecyclerView.adapter as AttachmentAdapter

    private val newMessageActivity by lazy { requireActivity() as NewMessageActivity }
    private val webViewUtils by lazy { WebViewUtils(requireContext()) }

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
    lateinit var draftsActionsWorkerScheduler: DraftsActionsWorker.Scheduler

    @Inject
    lateinit var informationDialog: InformationAlertDialog

    @Inject
    lateinit var signatureUtils: SignatureUtils

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

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
        observeAttachments()
        observeImportAttachmentsResult()
        observeUiSignature()
        observeUiQuote()
        editorManager.observeEditorActions()
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

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(signatureWebView.settings, true)
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(quoteWebView.settings, true)
        }

        attachmentsRecyclerView.adapter = AttachmentAdapter(shouldDisplayCloseButton = true, onDelete = ::onDeleteAttachment)

        recipientFieldsManager.setupAutoCompletionFields()

        subjectTextField.filters = arrayOf<InputFilter>(object : InputFilter {
            override fun filter(source: CharSequence?, s: Int, e: Int, d: Spanned?, dS: Int, dE: Int): CharSequence? {
                source?.toString()?.let { if (it.contains("\n")) return it.replace("\n", "") }
                return null
            }
        })

        setupSendButton()
        externalsManager.setupExternalBanner()

        scrim.setOnClickListener {
            scrim.isClickable = false
            aiManager.closeAiPrompt()
        }
    }

    private fun initializeDraft() = with(newMessageViewModel) {
        if (initResult.value == null) {
            initDraftAndViewModel(intent = requireActivity().intent).observe(viewLifecycleOwner) { draft ->
                if (draft != null) {
                    showKeyboardInCorrectView()
                    binding.subjectTextField.setText(draft.subject)
                    binding.bodyTextField.setText(draft.uiBody)
                } else {
                    requireActivity().apply {
                        showToast(R.string.failToOpenDraft)
                        finish()
                    }
                }
            }
        }
    }

    private fun hideLoader() = with(binding) {

        fromMailAddress.isVisible = true
        subjectTextField.isVisible = true
        bodyTextField.isVisible = true

        fromLoader.isGone = true
        subjectLoader.isGone = true
        bodyLoader.isGone = true

        toField.hideLoader()
        ccField.hideLoader()
        bccField.hideLoader()
    }

    private fun showKeyboardInCorrectView() = with(recipientFieldsManager) {
        when (newMessageViewModel.draftMode()) {
            DraftMode.REPLY,
            DraftMode.REPLY_ALL -> focusBodyField()
            DraftMode.FORWARD -> focusToField()
            DraftMode.NEW_MAIL -> {
                if (newMessageViewModel.recipient() == null && newMessageViewModel.draftInRAM.to.isEmpty()) {
                    focusToField()
                } else {
                    focusBodyField()
                }
            }
        }
    }

    private fun configureUiWithDraftData(draft: Draft) = with(binding) {
        recipientFieldsManager.initRecipients(draft)

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
            newMessageViewModel.uiQuoteLiveData.value = null
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
            }
        }
    }

    private fun onSignatureClicked(signature: Signature) {
        trackNewMessageEvent("switchIdentity")
        newMessageViewModel.fromLiveData.value = UiFrom(signature)
        addressListPopupWindow?.dismiss()
    }

    private fun updateSelectedSignatureInFromField(signature: Signature) {
        val formattedExpeditor = if (newMessageViewModel.signaturesCount > 1) {
            "${signature.senderName} <${signature.senderEmailIdn}> (${signature.name})"
        } else {
            signature.senderEmailIdn
        }
        binding.fromMailAddress.text = formattedExpeditor
    }

    private fun observeInitResult() {
        newMessageViewModel.initResult.observe(viewLifecycleOwner) { (draft, signatures) ->
            hideLoader()
            configureUiWithDraftData(draft)
            setupFromField(signatures)
            editorManager.setupEditorActions()
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

    private fun observeAttachments() {
        newMessageViewModel.attachmentsLiveData.observe(viewLifecycleOwner) {

            val shouldTransition = attachmentAdapter.itemCount != 0 && it.isEmpty()

            attachmentAdapter.submitList(it)

            binding.attachmentsRecyclerView.isVisible = if (it.isEmpty()) {
                if (shouldTransition) TransitionManager.beginDelayedTransition(binding.root)
                false
            } else {
                true
            }

            newMessageViewModel.updateIsSendingAllowed(attachments = it)
        }
    }

    private fun observeImportAttachmentsResult() = with(newMessageViewModel) {
        importAttachmentsResult.observe(viewLifecycleOwner) { result ->
            if (result == ImportationResult.ATTACHMENTS_TOO_BIG) showSnackbar(R.string.attachmentFileLimitReached)
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

    override fun onStop() = with(newMessageViewModel) {

        executeDraftActionWhenStopping(
            action = if (shouldSendInsteadOfSave) DraftAction.SEND else DraftAction.SAVE,
            isFinishing = requireActivity().isFinishing,
            isTaskRoot = requireActivity().isTaskRoot,
            subjectValue = binding.subjectTextField.text.toString(),
            uiBodyValue = binding.bodyTextField.text.toString(),
            startWorkerCallback = ::startWorker,
        )

        super.onStop()
    }

    private fun startWorker() {
        draftsActionsWorkerScheduler.scheduleWork(newMessageViewModel.draftLocalUuid())
    }

    private fun onDeleteAttachment(position: Int) = with(newMessageViewModel) {

        trackAttachmentActionsEvent("delete")

        runCatching {
            val attachments = attachmentsLiveData.value?.toMutableList() ?: mutableListOf()
            val attachment = attachments[position]
            attachment.getUploadLocalFile()?.delete()
            LocalStorageUtils.deleteAttachmentUploadDir(requireContext(), draftLocalUuid()!!, attachment.localUuid)
            attachments.removeAt(position)
            attachmentsLiveData.value = attachments
        }.onFailure { exception ->
            // TODO: If we don't see this Sentry after May 2024, we can remove it.
            SentryLog.e(TAG, " Attachment $position doesn't exist", exception)
        }
    }

    private fun setupSendButton() = with(binding) {
        newMessageViewModel.isSendingAllowed.observe(viewLifecycleOwner) {
            sendButton.isEnabled = it
        }

        sendButton.setOnClickListener { tryToSendEmail() }
    }

    private fun tryToSendEmail() {

        fun setSnackbarActivityResult() {
            val resultIntent = Intent()
            resultIntent.putExtra(MainActivity.DRAFT_ACTION_KEY, DraftAction.SEND.name)
            requireActivity().setResult(AppCompatActivity.RESULT_OK, resultIntent)
        }

        fun sendEmail() {
            newMessageViewModel.shouldSendInsteadOfSave = true
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
            )
        } else {
            sendEmail()
        }
    }

    private fun Activity.finishAppAndRemoveTaskIfNeeded() {
        if (isTaskRoot) finishAndRemoveTask() else finish()
    }

    fun navigateToPropositionFragment() = aiManager.navigateToPropositionFragment()

    fun closeAiPrompt() = aiManager.closeAiPrompt()

    fun isSubjectBlank() = binding.subjectTextField.text?.isBlank() == true

    companion object {
        private val TAG = NewMessageFragment::class.java.simpleName
    }
}
