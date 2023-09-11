/*
 * Infomaniak Mail - Android
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
import android.app.Activity
import android.content.ClipDescription
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.*
import android.view.WindowManager
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
import com.google.android.material.button.MaterialButton
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.mail.MatomoMail
import com.infomaniak.mail.MatomoMail.trackAttachmentActionsEvent
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.MatomoMail.trackExternalEvent
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
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.alertDialogs.InformationAlertDialog
import com.infomaniak.mail.ui.main.newMessage.NewMessageFragment.FieldType.*
import com.infomaniak.mail.ui.main.newMessage.NewMessageViewModel.ImportationResult
import com.infomaniak.mail.ui.main.thread.AttachmentAdapter
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.ExternalUtils.findExternalRecipient
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.WebViewUtils.Companion.setupNewMessageWebViewSettings
import com.infomaniak.mail.workers.DraftsActionsWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.UUID
import javax.inject.Inject
import com.google.android.material.R as RMaterial

@AndroidEntryPoint
class NewMessageFragment : Fragment() {

    private lateinit var binding: FragmentNewMessageBinding
    private val newMessageActivityArgs by lazy {
        // When opening this fragment via deeplink, it can happen that the navigation
        // extras aren't yet initialized, so we don't use the `navArgs` here.
        requireActivity().intent?.extras?.let(NewMessageActivityArgs::fromBundle) ?: NewMessageActivityArgs()
    }
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()

    private lateinit var addressListPopupWindow: ListPopupWindow
    private lateinit var filePicker: FilePicker

    private val attachmentAdapter = AttachmentAdapter(shouldDisplayCloseButton = true, onDelete = ::onDeleteAttachment)

    private val newMessageActivity by lazy { requireActivity() as NewMessageActivity }
    private val webViewUtils by lazy { WebViewUtils(requireContext()) }

    private var lastFieldToTakeFocus: FieldType? = TO

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

        filePicker = FilePicker(this@NewMessageFragment)

        initUi()

        if (newMessageViewModel.initResult.value == null) initDraftAndViewModel()

        handleOnBackPressed()

        setOnFocusChangedListeners()

        doAfterSubjectChange()
        doAfterBodyChange()

        observeContacts()
        observeEditorActions()
        observeNewAttachments()
        observeCcAndBccVisibility()
        observeDraftWorkerResults()
        observeInitResult()
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

    private fun handleOnBackPressed() = with(newMessageViewModel) {
        newMessageActivity.onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (isAutoCompletionOpened) closeAutoCompletion() else newMessageActivity.finishAppAndRemoveTaskIfNeeded()
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

        setupSendButton()
        setupExternalBanner()

        aiPrompt.initListeners(::closeAiPrompt)
    }

    private fun initDraftAndViewModel() {
        newMessageViewModel.initDraftAndViewModel(
            intent = requireActivity().intent,
            navArgs = newMessageActivityArgs,
        ).observe(viewLifecycleOwner) { isSuccess ->
            if (isSuccess) {
                showKeyboardInCorrectView()
            } else {
                requireActivity().apply {
                    showToast(R.string.failToOpenDraft)
                    finish()
                }
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
            DraftMode.FORWARD -> focusToField()
            DraftMode.NEW_MAIL -> {
                if (newMessageActivityArgs.recipient == null && newMessageViewModel.draft.to.isEmpty()) {
                    focusToField()
                } else {
                    focusBodyField()
                }
            }
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
            onContactRemovedCallback = { recipient -> recipient.removeInViewModelAndUpdateBannerVisibility(TO) },
            onCopyContactAddressCallback = ::copyRecipientEmailToClipboard,
            gotFocusCallback = { fieldGotFocus(TO) },
            onToggleEverythingCallback = ::openAdvancedFields,
            setSnackBarCallback = ::setSnackBar,
        )

        ccField.initRecipientField(
            autoComplete = autoCompleteCc,
            onAutoCompletionToggledCallback = { hasOpened -> toggleAutoCompletion(CC, hasOpened) },
            onContactAddedCallback = { newMessageViewModel.addRecipientToField(it, CC) },
            onContactRemovedCallback = { recipient -> recipient.removeInViewModelAndUpdateBannerVisibility(CC) },
            onCopyContactAddressCallback = ::copyRecipientEmailToClipboard,
            gotFocusCallback = { fieldGotFocus(CC) },
            setSnackBarCallback = ::setSnackBar,
        )

        bccField.initRecipientField(
            autoComplete = autoCompleteBcc,
            onAutoCompletionToggledCallback = { hasOpened -> toggleAutoCompletion(BCC, hasOpened) },
            onContactAddedCallback = { newMessageViewModel.addRecipientToField(it, BCC) },
            onContactRemovedCallback = { recipient -> recipient.removeInViewModelAndUpdateBannerVisibility(BCC) },
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
        contactMap: MergedContactDictionary,
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

    private fun Recipient.removeInViewModelAndUpdateBannerVisibility(type: FieldType) {
        newMessageViewModel.removeRecipientFromField(recipient = this, type)
        updateBannerVisibility()
    }

    private fun populateUiWithViewModel() = with(binding) {
        val draftMode = newMessageActivityArgs.draftMode
        val draft = newMessageViewModel.draft
        val aliases = newMessageViewModel.currentMailbox.aliases

        val externalMailFlagEnabled = newMessageViewModel.currentMailbox.externalMailFlagEnabled
        val shouldWarnForExternal = externalMailFlagEnabled && (draftMode == DraftMode.REPLY || draftMode == DraftMode.REPLY_ALL)

        val ccAndBccFieldsAreEmpty = draft.cc.isEmpty() && draft.bcc.isEmpty()
        val emailDictionary = newMessageViewModel.mergedContacts.value?.second ?: emptyMap()
        toField.initRecipients(draft.to, shouldWarnForExternal, emailDictionary, aliases, ccAndBccFieldsAreEmpty)
        ccField.initRecipients(draft.cc, shouldWarnForExternal, emailDictionary, aliases)
        bccField.initRecipients(draft.bcc, shouldWarnForExternal, emailDictionary, aliases)

        if (shouldWarnForExternal) {
            val (externalRecipientEmail, externalRecipientQuantity) = draft.findExternalRecipient(aliases, emailDictionary)
            newMessageViewModel.externalRecipientCount.value = externalRecipientEmail to externalRecipientQuantity
        }

        newMessageViewModel.updateIsSendingAllowed()

        subjectTextField.setText(draft.subject)

        attachmentAdapter.addAll(draft.attachments.filterNot { it.disposition == INLINE })
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
                    shouldLoadDistantResources = alwaysShowExternalContent || newMessageActivityArgs.shouldLoadDistantResources,
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

    private fun updateBannerVisibility() = with(binding) {
        var externalRecipientEmail: String? = null
        var externalRecipientQuantity = 0

        listOf(toField, ccField, bccField).forEach { field ->
            val (singleEmail, quantityForThisField) = field.findAlreadyExistingExternalRecipientsInFields()
            externalRecipientQuantity += quantityForThisField

            if (externalRecipientQuantity > 1) {
                newMessageViewModel.externalRecipientCount.value = null to 2
                return
            }

            if (quantityForThisField == 1) externalRecipientEmail = singleEmail
        }

        newMessageViewModel.externalRecipientCount.value = externalRecipientEmail to externalRecipientQuantity
    }

    private fun WebView.loadSignatureContent(html: String, webViewGroup: Group) {
        val processedHtml = webViewUtils.processSignatureHtmlForDisplay(html, context.isNightModeEnabled())
        loadProcessedContent(processedHtml, webViewGroup)
    }

    private fun WebView.loadContent(html: String, webViewGroup: Group) {
        val processedHtml = webViewUtils.processHtmlForDisplay(html, context.isNightModeEnabled())
        loadProcessedContent(processedHtml, webViewGroup)
    }

    private fun WebView.loadProcessedContent(processedHtml: String, webViewGroup: Group) {
        webViewGroup.isVisible = processedHtml.isNotBlank()
        loadDataWithBaseURL("", processedHtml, ClipDescription.MIMETYPE_TEXT_HTML, Utils.UTF_8, "")
    }

    private fun setupFromField(signatures: List<Signature>) = with(binding) {
        val selectedSignature = signatures.find { it.id == newMessageViewModel.selectedSignatureId }!!
        updateSelectedSignatureFromField(signatures.count(), selectedSignature)

        val adapter = SignatureAdapter(signatures, newMessageViewModel.selectedSignatureId) { newSelectedSignature ->
            updateSelectedSignatureFromField(signatures.count(), newSelectedSignature)
            updateBodySignature(newSelectedSignature.content)

            newMessageViewModel.apply {
                selectedSignatureId = newSelectedSignature.id
                draft.identityId = newSelectedSignature.id.toString()
                saveDraftDebouncing()
            }

            addressListPopupWindow.dismiss()
        }

        fromMailAddress.post { addressListPopupWindow.width = fromMailAddress.width }

        addressListPopupWindow.apply {
            setAdapter(adapter)
            isModal = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            anchorView = fromMailAddress
        }

        if (signatures.count() > 1) {
            fromMailAddress.apply {
                icon = AppCompatResources.getDrawable(context, R.drawable.ic_chevron_down)
                setOnClickListener { _ -> addressListPopupWindow.show() }
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

    private fun observeEditorActions() {
        newMessageViewModel.editorAction.observe(viewLifecycleOwner) { (editorAction, /*isToggled*/ _) ->
            when (editorAction) {
                EditorAction.ATTACHMENT -> {
                    filePicker.open { uris ->
                        requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                        newMessageViewModel.importAttachmentsToCurrentDraft(uris)
                    }
                }
                EditorAction.CAMERA -> notYetImplemented()
                EditorAction.LINK -> notYetImplemented()
                EditorAction.CLOCK -> notYetImplemented()
                EditorAction.AI -> openAiPrompt()
            }
        }
    }

    private fun openAiPrompt() {
        setAiPromptVisibility(true)
        binding.aiPrompt.focusPrompt()
        binding.newMessageConstraintLayout.descendantFocusability = FOCUS_BLOCK_DESCENDANTS
    }

    private fun closeAiPrompt() {
        binding.aiPrompt.hideKeyboard()
        setAiPromptVisibility(false)
        binding.newMessageConstraintLayout.descendantFocusability = FOCUS_BEFORE_DESCENDANTS
    }

    private fun setAiPromptVisibility(isVisible: Boolean) {
        fun updateStatusBarColor(isVisible: Boolean) {
            val backgroundColor = requireContext().getColor(R.color.backgroundColor)
            requireActivity().window.statusBarColor = if (isVisible) {
                // TODO : Extract to resources
                UiUtils.pointBetweenColors(backgroundColor, Color.parseColor("#FF000000"), 80f / 255)
            } else {
                backgroundColor
            }
        }

        fun updateNavigationBarColor(isVisible: Boolean) {
            val backgroundColorRes = if (isVisible) R.color.backgroundColorSecondary else R.color.backgroundColor
            requireActivity().window.navigationBarColor = requireContext().getColor(backgroundColorRes)
        }

        binding.aiPromptGroup.isVisible = isVisible
        updateStatusBarColor(isVisible)
        updateNavigationBarColor(isVisible)
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
            executeDraftActionWhenStopping(
                action = action,
                isFinishing = isFinishing,
                isTaskRoot = isTaskRoot,
                startWorkerCallback = ::startWorker,
            )
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

    private fun closeAutoCompletion() = with(binding) {
        toField.clearField()
        ccField.clearField()
        bccField.clearField()
    }

    private fun onDeleteAttachment(position: Int, itemCountLeft: Int) = with(binding) {
        trackAttachmentActionsEvent("delete")
        val draft = newMessageViewModel.draft

        if (itemCountLeft == 0) {
            TransitionManager.beginDelayedTransition(binding.root)
            attachmentsRecyclerView.isGone = true
        }

        draft.attachments[position].getUploadLocalFile(requireContext(), draft.localUuid).delete()
        draft.attachments.removeAt(position)
        newMessageViewModel.saveDraftWithoutDebouncing()
    }

    private fun setupExternalBanner() = with(binding) {
        var manuallyClosed = false

        var externalRecipientEmail: String? = null
        var externalRecipientQuantity = 0

        closeButton.setOnClickListener {
            trackExternalEvent("bannerManuallyClosed")
            manuallyClosed = true
            externalBanner.isGone = true
        }

        informationButton.setOnClickListener {
            trackExternalEvent("bannerInfo")

            val description = resources.getQuantityString(
                R.plurals.externalDialogDescriptionRecipient,
                externalRecipientQuantity,
                externalRecipientEmail,
            )

            informationDialog.show(
                title = R.string.externalDialogTitleRecipient,
                description = description,
                confirmButtonText = R.string.externalDialogConfirmButton,
            )
        }

        newMessageViewModel.externalRecipientCount.observe(viewLifecycleOwner) { (email, externalQuantity) ->
            externalBanner.isGone = manuallyClosed || externalQuantity == 0
            externalRecipientEmail = email
            externalRecipientQuantity = externalQuantity
        }
    }

    private fun setupSendButton() = with(binding) {
        newMessageViewModel.isSendingAllowed.observe(viewLifecycleOwner) {
            sendButton.isEnabled = it
        }

        sendButton.setOnClickListener { tryToSendEmail() }
    }

    private fun tryToSendEmail() {

        fun setSnackBarActivityResult() {
            val resultIntent = Intent()
            resultIntent.putExtra(MainActivity.DRAFT_ACTION_KEY, DraftAction.SEND.name)
            requireActivity().setResult(AppCompatActivity.RESULT_OK, resultIntent)
        }

        fun sendEmail() {
            newMessageViewModel.shouldSendInsteadOfSave = true
            setSnackBarActivityResult()
            requireActivity().finishAppAndRemoveTaskIfNeeded()
        }

        if (newMessageViewModel.draft.subject.isNullOrBlank()) {
            trackNewMessageEvent("sendWithoutSubject")
            descriptionDialog.show(
                title = getString(R.string.emailWithoutSubjectTitle),
                description = getString(R.string.emailWithoutSubjectDescription),
                confirmButtonText = R.string.buttonContinue,
                displayLoader = false,
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

    private fun observeInitResult() {
        newMessageViewModel.initResult.observe(viewLifecycleOwner) { signatures ->
            hideLoader()
            populateUiWithViewModel()
            setupFromField(signatures)
            setupEditorActions()
            setupEditorFormatActionsToggle()
        }
    }

    private fun setupEditorActions() = with(binding) {

        fun linkEditor(view: MaterialButton, action: EditorAction) {
            view.setOnClickListener {
                // TODO: Don't forget to add in this `if` all actions that make the app go to background.
                if (action == EditorAction.ATTACHMENT) newMessageViewModel.shouldExecuteDraftActionWhenStopping = false
                trackEvent("editorActions", action.matomoValue)
                newMessageViewModel.editorAction.value = action to null
            }
        }

        linkEditor(editorAttachment, EditorAction.ATTACHMENT)
        linkEditor(editorCamera, EditorAction.CAMERA)
        linkEditor(editorLink, EditorAction.LINK)
        linkEditor(editorClock, EditorAction.CLOCK)
        linkEditor(editorAi, EditorAction.AI)
    }

    private fun setupEditorFormatActionsToggle() = with(binding) {
        editorTextOptions.setOnClickListener {
            newMessageViewModel.isEditorExpanded = !newMessageViewModel.isEditorExpanded
            updateEditorVisibility(newMessageViewModel.isEditorExpanded)
        }
    }

    private fun updateEditorVisibility(isEditorExpanded: Boolean) = with(binding) {
        val color = if (isEditorExpanded) {
            context.getAttributeColor(RMaterial.attr.colorPrimary)
        } else {
            context.getColor(R.color.iconColor)
        }
        val resId = if (isEditorExpanded) R.string.buttonTextOptionsClose else R.string.buttonTextOptionsOpen

        editorTextOptions.apply {
            iconTint = ColorStateList.valueOf(color)
            contentDescription = getString(resId)
        }

        editorActions.isGone = isEditorExpanded
        textEditing.isVisible = isEditorExpanded
    }

    enum class EditorAction(val matomoValue: String) {
        ATTACHMENT("importFile"),
        CAMERA("importFromCamera"),
        LINK("addLink"),
        CLOCK(MatomoMail.ACTION_POSTPONE_NAME),
        AI("ai"),
        // BOLD("bold"),
        // ITALIC("italic"),
        // UNDERLINE("underline"),
        // STRIKE_THROUGH("strikeThrough"),
        // UNORDERED_LIST("unorderedList"),
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
