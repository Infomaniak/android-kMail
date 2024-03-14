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
import androidx.core.widget.doAfterTextChanged
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
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.alertDialogs.InformationAlertDialog
import com.infomaniak.mail.ui.main.thread.AttachmentAdapter
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel.ImportationResult
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.WebViewUtils.Companion.destroyAndClearHistory
import com.infomaniak.mail.utils.WebViewUtils.Companion.setupNewMessageWebViewSettings
import com.infomaniak.mail.utils.extensions.*
import com.infomaniak.mail.workers.DraftsActionsWorker
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.util.UUID
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

    @Inject
    lateinit var aiManager: NewMessageAiManager

    @Inject
    lateinit var externalsManager: NewMessageExternalsManager

    @Inject
    lateinit var editorManager: NewMessageEditorManager

    @Inject
    lateinit var recipientFieldsManager: NewMessageRecipientFieldsManager

    private var addressListPopupWindow: ListPopupWindow? = null

    private var quoteWebView: WebView? = null
    private var signatureWebView: WebView? = null

    private val attachmentAdapter inline get() = binding.attachmentsRecyclerView.adapter as AttachmentAdapter

    private val newMessageActivity by lazy { requireActivity() as NewMessageActivity }
    private val webViewUtils by lazy { WebViewUtils(requireContext()) }

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
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
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
        initDraftAndViewModel()

        handleOnBackPressed()

        recipientFieldsManager.setOnFocusChangedListeners()

        doAfterSubjectChange()
        doAfterBodyChange()

        recipientFieldsManager.observeContacts()
        recipientFieldsManager.observeCcAndBccVisibility()
        editorManager.observeEditorActions()
        observeNewAttachments()
        observeDraftWorkerResults()
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

    private fun initDraftAndViewModel() = with(newMessageViewModel) {

        if (initResult.value == null) initDraftAndViewModel(intent = requireActivity().intent)

        initResult.observeNotNull(viewLifecycleOwner) { (isSuccess, signatures) ->

            if (!isSuccess) requireActivity().apply {
                showToast(R.string.failToOpenDraft)
                finish()
                return@observeNotNull
            }

            showKeyboardInCorrectView()
            hideLoader()
            populateUiWithViewModel()
            setupFromField(signatures)
            editorManager.setupEditorActions()
            editorManager.setupEditorFormatActionsToggle()
            externalsManager.observeExternals(arrivedFromExistingDraft())
            aiManager.observeEverything()
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

    private fun showKeyboardInCorrectView() {
        when (newMessageViewModel.draftMode()) {
            DraftMode.REPLY,
            DraftMode.REPLY_ALL -> recipientFieldsManager.focusBodyField()
            DraftMode.FORWARD -> recipientFieldsManager.focusToField()
            DraftMode.NEW_MAIL -> {
                if (newMessageViewModel.recipient() == null && newMessageViewModel.draft.to.isEmpty()) {
                    recipientFieldsManager.focusToField()
                } else {
                    recipientFieldsManager.focusBodyField()
                }
            }
        }
    }

    private fun populateUiWithViewModel() = with(binding) {
        val draft = newMessageViewModel.draft
        recipientFieldsManager.initRecipients(draft)

        newMessageViewModel.updateIsSendingAllowed()

        subjectTextField.setText(draft.subject)

        attachmentAdapter.addAll(draft.attachments)
        attachmentsRecyclerView.isGone = attachmentAdapter.itemCount == 0

        bodyText.setText(draft.uiBody)

        val alwaysShowExternalContent = localSettings.externalContent == ExternalContent.ALWAYS

        draft.uiSignature?.let { html ->
            signatureWebView.apply {
                settings.setupNewMessageWebViewSettings()
                loadSignatureContent(html, signatureGroup)
                initWebViewClientAndBridge(
                    attachments = emptyList(),
                    messageUid = "SIGNATURE-${draft.messageUid}",
                    shouldLoadDistantResources = true,
                    navigateToNewMessageActivity = null,
                )
            }
            removeSignature.setOnClickListener {
                trackNewMessageEvent("deleteSignature")
                draft.uiSignature = null
                signatureGroup.isGone = true
            }
        }

        draft.uiQuote?.let { html ->
            quoteWebView.apply {
                settings.setupNewMessageWebViewSettings()
                loadContent(html, quoteGroup)
                initWebViewClientAndBridge(
                    attachments = draft.attachments,
                    messageUid = "QUOTE-${draft.messageUid}",
                    shouldLoadDistantResources = alwaysShowExternalContent || newMessageViewModel.shouldLoadDistantResources(),
                    navigateToNewMessageActivity = null,
                )
            }
            removeQuote.setOnClickListener {
                trackNewMessageEvent("deleteQuote")
                draft.uiQuote = null
                quoteGroup.isGone = true
            }
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

        val selectedSignatureId = newMessageViewModel.draft.identityId?.toInt() ?: -1
        val selectedSignature = with(signatures) {
            find { it.id == selectedSignatureId } ?: find { it.isDefault }!!
        }
        updateSelectedSignatureFromField(signatures.count(), selectedSignature)

        val adapter = SignatureAdapter(
            signatures = signatures,
            selectedSignatureId = selectedSignatureId,
            onClickListener = { newSelectedSignature ->
                trackNewMessageEvent("switchIdentity")

                updateSelectedSignatureFromField(signatures.count(), newSelectedSignature)
                updateBodySignature(newSelectedSignature.content)

                newMessageViewModel.draft.identityId = newSelectedSignature.id.toString()

                addressListPopupWindow?.dismiss()
            },
        )

        fromMailAddress.post {
            runCatching {
                addressListPopupWindow?.width = fromMailAddress.width
            }.onFailure {
                Sentry.captureMessage("Binding null in post(), this is not normal", SentryLevel.WARNING)
            }
        }

        addressListPopupWindow?.apply {
            setAdapter(adapter)
            isModal = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            anchorView = fromMailAddress
        }

        if (signatures.count() > 1) {
            fromMailAddress.apply {
                icon = AppCompatResources.getDrawable(context, R.drawable.ic_chevron_down)
                setOnClickListener { _ -> addressListPopupWindow?.show() }
            }
        }
    }

    private fun updateBodySignature(signatureContent: String) = with(binding) {
        newMessageViewModel.draft.uiSignature = signatureUtils.encapsulateSignatureContentWithInfomaniakClass(signatureContent)
        signatureWebView.loadSignatureContent(signatureContent, signatureGroup)
    }

    private fun updateSelectedSignatureFromField(signaturesCount: Int, signature: Signature) {
        val formattedExpeditor = if (signaturesCount > 1) {
            "${signature.senderName} <${signature.senderEmailIdn}> (${signature.name})"
        } else {
            signature.senderEmailIdn
        }
        binding.fromMailAddress.text = formattedExpeditor
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

    private fun observeNewAttachments() = with(newMessageViewModel) {
        importedAttachments.observe(viewLifecycleOwner) { (attachments, importationResult) ->

            attachmentAdapter.addAll(attachments)
            draft.attachments.addAll(attachments)

            binding.attachmentsRecyclerView.isGone = draft.attachments.isEmpty()

            if (importationResult == ImportationResult.FILE_SIZE_TOO_BIG) showSnackbar(R.string.attachmentFileLimitReached)
        }
    }

    override fun onStop() = with(newMessageViewModel) {

        val action = if (shouldSendInsteadOfSave) DraftAction.SEND else DraftAction.SAVE
        val isFinishing = requireActivity().isFinishing
        val isTaskRoot = requireActivity().isTaskRoot

        executeDraftActionWhenStopping(
            action = action,
            isFinishing = isFinishing,
            isTaskRoot = isTaskRoot,
            startWorkerCallback = ::startWorker,
        )

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

    private fun onDeleteAttachment(position: Int, itemCountLeft: Int) = with(binding) {

        trackAttachmentActionsEvent("delete")
        val draft = newMessageViewModel.draft

        if (itemCountLeft == 0) {
            TransitionManager.beginDelayedTransition(binding.root)
            attachmentsRecyclerView.isGone = true
        }

        runCatching {
            draft.attachments[position].getUploadLocalFile()?.delete()
            draft.attachments.removeAt(position)
        }.onFailure { exception ->
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

        if (newMessageViewModel.draft.subject.isNullOrBlank()) {
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

    companion object {
        private val TAG = NewMessageFragment::class.java.simpleName
    }
}
