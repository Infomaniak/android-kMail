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
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.transition.TransitionManager
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
import com.infomaniak.core.common.observe
import com.infomaniak.core.common.extensions.isNightModeEnabled
import com.infomaniak.core.common.utils.FORMAT_DATE_DAY_FULL_MONTH_YEAR_WITH_TIME
import com.infomaniak.core.common.utils.format
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.ksuite.ui.utils.MatomoKSuite
import com.infomaniak.core.legacy.utils.FilePicker
import com.infomaniak.core.legacy.utils.getBackNavigationResult
import com.infomaniak.core.ui.showToast
import com.infomaniak.core.ui.view.extension.setMargins
import com.infomaniak.core.ui.view.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.richhtmleditor.StatusCommand.BOLD
import com.infomaniak.lib.richhtmleditor.StatusCommand.CREATE_LINK
import com.infomaniak.lib.richhtmleditor.StatusCommand.ITALIC
import com.infomaniak.lib.richhtmleditor.StatusCommand.STRIKE_THROUGH
import com.infomaniak.lib.richhtmleditor.StatusCommand.UNDERLINE
import com.infomaniak.lib.richhtmleditor.StatusCommand.UNORDERED_LIST
import com.infomaniak.lib.richhtmleditor.executor.JsExecutableMethod
import com.infomaniak.lib.richhtmleditor.looselyEscapeAsStringLiteralForJs
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackAttachmentActionsEvent
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.MatomoMail.trackScheduleSendEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.AttachmentDisposition
import com.infomaniak.mail.data.models.FeatureFlag
import com.infomaniak.mail.data.models.correspondent.ContactAutocompletable
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.draft.DraftAction
import com.infomaniak.mail.data.models.extensions.kSuite
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.alertDialogs.InformationAlertDialog
import com.infomaniak.mail.ui.alertDialogs.SelectDateAndTimeDialog.Companion.ONE_HOUR_IN_MILLIS
import com.infomaniak.mail.ui.alertDialogs.SelectDateAndTimeForScheduledDraftDialog
import com.infomaniak.mail.ui.newMessage.sendOptions.DraftSendOptionsFragmentArgs
import com.infomaniak.mail.ui.bottomSheetDialogs.RescheduleDraftBottomSheetDialog.Companion.OPEN_SCHEDULE_DRAFT_DATE_AND_TIME_PICKER
import com.infomaniak.mail.ui.bottomSheetDialogs.RescheduleDraftBottomSheetDialog.Companion.SCHEDULE_DRAFT_RESULT
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.thread.AttachmentAdapter
import com.infomaniak.mail.ui.newMessage.NewMessageRecipientFieldsManager.FieldType
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel.ImportationResult
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel.UiFrom
import com.infomaniak.mail.ui.newMessage.encryption.EncryptionMessageManager
import com.infomaniak.mail.ui.newMessage.encryption.EncryptionViewModel
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getCommonMentionsCodeScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getCustomEditorStyle
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getCustomStyle
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getDeletedInlineImagesObserverScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getEditorBodyScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getEditorJsBridgeScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getEditorMentionClickHandlerScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getEditorMentionsDetectorScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getFixStyleScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getIncludeQuotesScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getInsertMentionScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getMentionDeletionObserverScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getMentionsStyle
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getRemoveElementsByIdScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getReplaceSignatureScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getSetAiContentScript
import com.infomaniak.mail.utils.MessageBodyUtils.EDITOR_LOCAL_SIGNATURE_ID
import com.infomaniak.mail.utils.MessageBodyUtils.INFOMANIAK_FORWARD_QUOTE_HTML_CLASS_NAME
import com.infomaniak.mail.utils.MessageBodyUtils.INFOMANIAK_REPLY_QUOTE_HTML_CLASS_NAME
import com.infomaniak.mail.utils.MessageBodyUtils.INFOMANIAK_SIGNATURE_HTML_CLASS_NAME
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.SignatureUtils
import com.infomaniak.mail.utils.WebViewUtils.Companion.evaluateJs
import com.infomaniak.mail.utils.WebViewUtils.Companion.setupNewMessageWebViewSettings
import com.infomaniak.mail.utils.date.DateFormatUtils.formatDelayText
import com.infomaniak.mail.utils.extensions.AttachmentExt
import com.infomaniak.mail.utils.extensions.AttachmentExt.openAttachment
import com.infomaniak.mail.utils.extensions.applySideAndBottomSystemInsets
import com.infomaniak.mail.utils.extensions.applyStatusBarInsets
import com.infomaniak.mail.utils.extensions.applyWindowInsetsListener
import com.infomaniak.mail.utils.extensions.bindAlertToViewLifecycle
import com.infomaniak.mail.utils.extensions.changeToolbarColorOnScroll
import com.infomaniak.mail.utils.extensions.enableAlgorithmicDarkening
import com.infomaniak.mail.utils.extensions.ime
import com.infomaniak.mail.utils.extensions.initEditorWebviewBridge
import com.infomaniak.mail.utils.extensions.initEditorWebviewClient
import com.infomaniak.mail.utils.extensions.navigateToDownloadProgressDialog
import com.infomaniak.mail.utils.extensions.systemBars
import com.infomaniak.mail.utils.extensions.valueOrEmpty
import com.infomaniak.mail.utils.openKSuiteProBottomSheet
import com.infomaniak.mail.utils.openMailPremiumBottomSheet
import com.infomaniak.mail.utils.openMyKSuiteUpgradeBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.launch
import splitties.experimental.ExperimentalSplittiesApi
import java.util.Date
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@AndroidEntryPoint
class NewMessageFragment : Fragment() {

    private var _binding: FragmentNewMessageBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView
    private val newMessageActivityArgs by lazy {
        // When opening this fragment via deeplink, it can happen that the navigation
        // extras aren't yet initialized, so we don't use the `navArgs` here.
        requireActivity().intent?.extras?.let(NewMessageActivityArgs::fromBundle) ?: NewMessageActivityArgs()
    }
    private val replaceSignatureScript by lazy { requireContext().getReplaceSignatureScript() }
    private val includeQuotesScript by lazy { requireContext().getIncludeQuotesScript() }
    private val deletedInlineImagesObserverScript by lazy { requireContext().getDeletedInlineImagesObserverScript() }
    private val commonMentionsCodeScript by lazy { requireContext().getCommonMentionsCodeScript() }
    private val mentionsObserverScript by lazy { requireContext().getEditorMentionsDetectorScript() }
    private val mentionDeletionObserverScript by lazy { requireContext().getMentionDeletionObserverScript() }
    private val editorJsBridgeScript by lazy { requireContext().getEditorJsBridgeScript() }
    private val fixStyle by lazy { requireContext().getFixStyleScript() }
    private val setAiContentScript by lazy { requireContext().getSetAiContentScript() }
    private val getEditorBodyScript by lazy { requireContext().getEditorBodyScript() }
    private val insertMentionScript by lazy { requireContext().getInsertMentionScript() }
    private val mentionClickHandlerScript by lazy { requireContext().getEditorMentionClickHandlerScript() }
    private val removeElementsByIdScript by lazy { requireContext().getRemoveElementsByIdScript() }

    private val newMessageFragmentArgs: NewMessageFragmentArgs by navArgs()
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()
    private val aiViewModel: AiViewModel by activityViewModels()
    private val encryptionViewModel: EncryptionViewModel by activityViewModels()

    private val filePicker = FilePicker(fragment = this).apply {
        initCallback { uris -> newMessageViewModel.importAttachmentsLiveData.value = uris }
    }

    private var addressListPopupWindow: ListPopupWindow? = null

    private val signatureAdapter = SignatureAdapter(::onSignatureClicked)
    private val attachmentAdapter inline get() = binding.attachmentsRecyclerView.adapter as AttachmentAdapter

    private val mentionContactAdapter by lazy {
        ContactAdapter(
            usedEmails = mutableSetOf(),
            onContactClicked = ::onMentionContactClicked,
            onAddUnrecognizedContact = ::onAddUnrecognizedContactClicked,
            snackbarManager = snackbarManager,
            getAddressBookWithGroup = null,
            isForMentions = true,
        )
    }

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

    private val isScheduled: Boolean
        get() = newMessageViewModel.scheduleConfig.value is ScheduleConfig.Scheduled

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
        observePlaceholderVisibility()
        observeQuotesButtonVisibility()
        observeQuotesInclusion()

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
        observeScheduleAndReminder()
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
            tryToSendEmail(isScheduled = true)
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

        scheduleAlert.apply {
            onAction1 { navigateToScheduleSendBottomSheet() }
            onAction2 {
                newMessageViewModel.scheduleConfig.value = ScheduleConfig.None
                newMessageViewModel.setScheduleDate(null)
            }
        }

        reminderAlert.apply {
            onAction1 { navigateToScheduleSendBottomSheet() }
            onAction2 { newMessageViewModel.reminderConfig.value = ReminderConfig.None }
        }

        recipientFieldsManager.setupAutoCompletionFields()

        subjectTextField.filters = arrayOf<InputFilter>(object : InputFilter {
            override fun filter(source: CharSequence?, s: Int, e: Int, d: Spanned?, dS: Int, dE: Int): CharSequence? {
                source?.toString()?.let { if (it.contains("\n")) return it.replace("\n", "") }
                return null
            }
        })

        initEditorUi()
        setupMentionAutocomplete()
        setupShowQuotesButton()

        viewLifecycleOwner.lifecycleScope.launch {
            val mailbox = newMessageViewModel.currentMailbox()
            setupSendButtons(mailbox)
            externalsManager.setupExternalBanner(hasOrganisation = mailbox.kSuite is KSuite.Pro)
        }

        scrim.setOnClickListener {
            scrim.isClickable = false
            aiManager.closeAiPrompt()
        }
    }

    private fun observeScheduleAndReminder() {
        newMessageViewModel.scheduleConfig.observe(viewLifecycleOwner) { config ->
            when (config) {
                is ScheduleConfig.Scheduled -> {
                    val date = Date(config.epochMillis).format(FORMAT_DATE_DAY_FULL_MONTH_YEAR_WITH_TIME)
                    binding.scheduleAlert.apply {
                        setDescription(getString(R.string.scheduledEmailHeader, date))
                        isVisible = true
                    }
                    binding.divider6.isVisible = true
                }
                ScheduleConfig.None -> {
                    binding.scheduleAlert.isVisible = false
                    binding.divider6.isVisible = false
                }
            }
        }

        newMessageViewModel.reminderConfig.observe(viewLifecycleOwner) { config ->
            when (config) {
                is ReminderConfig.Preset -> {
                    val hours = config.delayHours.hours
                    val dateText = if (hours % HOURS_IN_A_DAY == 0 && hours > HOURS_IN_A_DAY) {
                        resources.getQuantityString(R.plurals.daysBeforeSendingReminder, hours / HOURS_IN_A_DAY, hours / HOURS_IN_A_DAY)
                    } else {
                        resources.getQuantityString(R.plurals.hoursBeforeSendingReminder, hours, hours)
                    }
                    binding.reminderAlert.apply {
                        setDescription(getString(R.string.callIfNoResponseHeaderTitle, dateText))
                        isVisible = true
                    }
                    binding.divider7.isVisible = true
                }
                is ReminderConfig.Custom -> {
                    val delayText = requireContext().formatDelayText(config.delayMillis)
                    binding.reminderAlert.apply {
                        setDescription(getString(R.string.callIfNoResponseHeaderTitle, delayText))
                        isVisible = true
                    }
                    binding.divider7.isVisible = true
                }
                ReminderConfig.None -> {
                    binding.reminderAlert.isVisible = false
                    binding.divider7.isVisible = false
                }
            }
        }
    }

    private fun initEditorUi() = with(binding) {
        editorWebView.settings.setupNewMessageWebViewSettings()
        editorWebView.initEditorWebviewBridge(
            onInlineImagesDeleted = newMessageViewModel::deleteInlineAttachments,
            onMentionQueryChanged = newMessageViewModel::updateMentionQuery,
            onMentionsDeleted = newMessageViewModel::removeMentions,
        )
        editorWebView.subscribeToStates(setOf(BOLD, ITALIC, UNDERLINE, STRIKE_THROUGH, UNORDERED_LIST, CREATE_LINK))
        setEditorStyle()
        setEditorScript()
        editorAiAnimation.setAnimation(R.raw.euria)
        setToolbarEnabledStatus(false)
        removeAllProperties()
        handleFocusChanges()
    }

    private fun setEditorStyle() = with(binding.editorWebView) {
        enableAlgorithmicDarkening(isEnabled = true)
        addCss(context.getCustomStyle())
        addCss(context.getCustomEditorStyle())
    }

    private fun addMentionsStyle() {
        viewLifecycleOwner.lifecycleScope.launch {
            val selfEmails = newMessageViewModel.currentMailbox().aliases
            val formatMentionsStyle = requireContext().getMentionsStyle(selfEmails)
            binding.editorWebView.addCss(formatMentionsStyle, MENTIONS_STYLE)
        }
    }

    private fun setEditorScript() = with(binding.editorWebView) {
        addScript(fixStyle)
        addScript(editorJsBridgeScript)
        addScript(deletedInlineImagesObserverScript)
        addScript(mentionClickHandlerScript)
        addScript(removeElementsByIdScript)

        val formattedAiContentScript = setAiContentScript.format(
            INFOMANIAK_SIGNATURE_HTML_CLASS_NAME,
            INFOMANIAK_REPLY_QUOTE_HTML_CLASS_NAME,
            INFOMANIAK_FORWARD_QUOTE_HTML_CLASS_NAME
        )
        addScript(formattedAiContentScript)

        val formattedGetEditorBodyScript = getEditorBodyScript.format(
            INFOMANIAK_SIGNATURE_HTML_CLASS_NAME,
            INFOMANIAK_REPLY_QUOTE_HTML_CLASS_NAME,
            INFOMANIAK_FORWARD_QUOTE_HTML_CLASS_NAME
        )
        addScript(formattedGetEditorBodyScript)
    }

    private fun removeAllProperties() = viewLifecycleOwner.lifecycleScope.launch {
        binding.editorWebView.executeJsMethodWhenEditorIsSetup(JsExecutableMethod("removeAllProperties"))
    }

    private fun handleFocusChanges() = with(newMessageViewModel) {
        isEditorWebViewFocusedLiveData.observe(viewLifecycleOwner) { isFocused ->
            setToolbarEnabledStatus(isFocused)
            if (isFocused && isPlaceHolderVisible.value) {
                setPlaceholderVisibility(isVisible = false)
            }
        }
    }

    private fun onAddUnrecognizedContactClicked() {
        addMention(newMessageViewModel.mentionQuery.value, null)
    }

    private fun onMentionContactClicked(contact: ContactAutocompletable) {
        val mergedContact = contact as? MergedContact ?: return
        addMention(mergedContact.email, mergedContact.name)
    }

    private fun addMention(email: String, name: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.editorWebView.executeJsMethodWhenEditorIsSetup(
                JsExecutableMethod(
                    "insertMention",
                    email,
                    name,
                    newMessageViewModel.mentionQuery.value
                ),
            )
        }

        binding.toField.apply {
            addRecipient(email, name)
            updateCollapsedChipValues(isCollapsed)
        }

        newMessageViewModel.addMention(email)
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
            DraftMode.REPLY_ALL,
            DraftMode.FOLLOW_UP -> focusBodyField()
            DraftMode.FORWARD -> focusToField()
            DraftMode.NEW_MAIL -> if (isToFieldEmpty) focusToField() else focusBodyField()
        }
    }

    private fun configureUiWithDraftData(draft: Draft) = with(binding) {
        val alwaysShowExternalContent = localSettings.externalContent == LocalSettings.ExternalContent.ALWAYS
        editorWebView.initEditorWebviewClient(
            attachments = draft.attachments,
            shouldLoadDistantResources = alwaysShowExternalContent || newMessageViewModel.shouldLoadDistantResources(),
            onPageFinished = { editorWebView.notifyPageHasLoaded() },
        )
    }

    private fun observeQuotesButtonVisibility() = viewLifecycleOwner.lifecycleScope.launch {
        combine(
            newMessageViewModel.isQuotesButtonVisible,
            newMessageViewModel.isShimmering.filterNot { it }
        ) { isQuoteVisible, _ ->
            isQuoteVisible
        }.collect { isQuoteButtonVisible ->
            binding.showQuotesButton.isVisible = isQuoteButtonVisible
        }
    }

    private fun observePlaceholderVisibility() = viewLifecycleOwner.lifecycleScope.launch {
        newMessageViewModel.isPlaceHolderVisible.collect { isPlaceholderVisible ->
            binding.newMessagePlaceholder.isVisible = isPlaceholderVisible
        }
    }

    private fun observeQuotesInclusion() = viewLifecycleOwner.lifecycleScope.launch {
        for (quote in newMessageViewModel.quotesToIncludeChannel) {
            val escapedQuote = looselyEscapeAsStringLiteralForJs(quote)
            val includeQuoteScript = includeQuotesScript.format(escapedQuote)
            binding.editorWebView.evaluateJavascript(includeQuoteScript, null)
        }
    }

    private fun setupShowQuotesButton() {
        binding.showQuotesButton.setOnClickListener {
            trackNewMessageEvent(MatomoName.ShowQuote)
            newMessageViewModel.setQuotesButtonVisibility(isVisible = false)
            newMessageViewModel.includeQuotes()
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
        newMessageViewModel.fromLiveData.value = UiFrom(signature)
        addressListPopupWindow?.dismiss()
    }

    private fun updateBodySignature(signature: Signature) {
        val selectedSignature = if (signature.isDummy) {
            "''" // This will represent an empty string in js.
        } else {
            val signatureWithClass = signatureUtils.encapsulateSignatureContentWithInfomaniakClass(signature.content)
            looselyEscapeAsStringLiteralForJs(signatureWithClass)
        }

        val quotesSelector = ".${INFOMANIAK_REPLY_QUOTE_HTML_CLASS_NAME},.${INFOMANIAK_FORWARD_QUOTE_HTML_CLASS_NAME}"
        val replaceSignatureScript = replaceSignatureScript.format(
            EDITOR_LOCAL_SIGNATURE_ID,
            selectedSignature,
            quotesSelector
        )

        binding.editorWebView.evaluateJavascript(replaceSignatureScript, null)
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
        }
    }

    private fun observeShimmering() = lifecycleScope.launch {
        newMessageViewModel.isShimmering.collect(::setShimmerVisibility)
    }

    private fun observeScheduledDraftsFeatureFlagUpdates() {
        newMessageViewModel.featureFlagsLive.observe(viewLifecycleOwner) { featureFlags ->
            val isScheduledDraftsEnabled = featureFlags.contains(FeatureFlag.SCHEDULE_DRAFTS)
            binding.sendOptionsButton.isVisible = isScheduledDraftsEnabled

            val areMentionsAvailable = featureFlags.contains(FeatureFlag.MENTIONS)

            binding.editorWebView.apply {
                if (areMentionsAvailable) {
                    addScript(commonMentionsCodeScript, COMMON_MENTIONS_SCRIPT)
                    addScript(mentionsObserverScript, MENTION_OBSERVER_SCRIPT)
                    addScript(insertMentionScript, INSERT_MENTION_SCRIPT)
                    addScript(mentionDeletionObserverScript, MENTION_DELETION_OBSERVER_SCRIPT)
                    addMentionsStyle()
                } else {
                    viewLifecycleOwner.lifecycleScope.launch {
                        executeJsMethodWhenEditorIsSetup(
                            JsExecutableMethod(
                                "removeElementsById",
                                COMMON_MENTIONS_SCRIPT,
                                MENTION_OBSERVER_SCRIPT,
                                INSERT_MENTION_SCRIPT,
                                MENTION_DELETION_OBSERVER_SCRIPT,
                                MENTIONS_STYLE,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun setupMentionAutocomplete() = with(binding) {
        mentionAutoComplete.adapter = mentionContactAdapter
        newMessageViewModel.mergedContacts.observe(viewLifecycleOwner) { (contacts, _) ->
            mentionContactAdapter.updateContacts(contacts.filterIsInstance<MergedContact>())
        }

        newMessageViewModel.mentionQuery.observe(viewLifecycleOwner) { query ->
            if (query.isBlank()) {
                mentionAutoComplete.isVisible = false
                mentionContactAdapter.clear()
            } else {
                mentionContactAdapter.searchContacts(query, isForRecipients = false)
                mentionAutoComplete.isVisible = mentionContactAdapter.itemCount > 0
                updateMentionAutocompleteHeight()
            }
        }
    }

    private fun updateMentionAutocompleteHeight() {
        with(binding.mentionAutoComplete) {
            if (!isVisible) return
            post {
                val itemHeight = getChildAt(0)?.height ?: return@post
                val visibleRows = minOf(mentionContactAdapter.itemCount, 3)
                val targetHeight = itemHeight * visibleRows + paddingTop + paddingBottom
                if (layoutParams.height != targetHeight) {
                    layoutParams = layoutParams.apply { height = targetHeight }
                }
            }
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
        newMessageViewModel.updateMentionQuery("")

        super.onStop()
    }

    private fun onDeleteAttachment(position: Int) {
        trackAttachmentActionsEvent(MatomoName.Delete)
        newMessageViewModel.deleteAttachment(position)
    }

    private fun setupSendButtons(mailbox: Mailbox) = with(binding) {
        newMessageViewModel.isSendingAllowed.observe(viewLifecycleOwner) {
            sendOptionsButton.isEnabled = it
            sendButton.isEnabled = it
        }

        sendOptionsButton.setOnClickListener {
            if (checkMailboxStorage(mailbox)) {
                if (newMessageViewModel.isEncryptionActivated.value == true) {
                    snackbarManager.postValue(getString(R.string.encryptedMessageSnackbarScheduledUnavailable))
                } else {
                    navigateToScheduleSendBottomSheet()
                }
            }
        }

        sendButton.setOnClickListener {
            if (!checkMailboxStorage(mailbox)) return@setOnClickListener

            val scheduleConfig = newMessageViewModel.scheduleConfig.value
            val isSendingWithScheduled = if (scheduleConfig is ScheduleConfig.Scheduled) {
                if (scheduleConfig.epochMillis - MIN_SELECTABLE_DATE_MINUTES.minutes.inWholeMilliseconds > System.currentTimeMillis()) {
                    newMessageViewModel.setScheduleDate(Date(scheduleConfig.epochMillis))
                    true
                } else {
                    newMessageViewModel.scheduleConfig.value = ScheduleConfig.None
                    newMessageViewModel.resetScheduledDate()
                    false
                }
            } else {
                false
            }

            val reminderConfig = newMessageViewModel.reminderConfig.value
            val isSendingWithReminder = if (reminderConfig !is ReminderConfig.None) {
                val delayMillis = when (reminderConfig) {
                    is ReminderConfig.Preset -> reminderConfig.delayHours.hours * ONE_HOUR_IN_MILLIS
                    is ReminderConfig.Custom -> reminderConfig.delayMillis
                    else -> 0L
                }
                newMessageViewModel.setReminderDelay((delayMillis / 1_000L).toInt())
                true
            } else {
                newMessageViewModel.setReminderDelay(0)
                false
            }

            tryToSendEmail(isSendingWithScheduled, isSendingWithReminder)
        }
    }

    private fun navigateToScheduleSendBottomSheet(): Job = viewLifecycleOwner.lifecycleScope.launch {
        val mailbox = newMessageViewModel.currentMailbox()
        safelyNavigate(
            resId = R.id.sendOptionsBottomSheetDialog,
            args = DraftSendOptionsFragmentArgs(
                lastSelectedScheduleEpochMillis = localSettings.lastSelectedScheduleEpochMillis ?: 0L,
                currentKSuite = mailbox.kSuite,
                isAdmin = mailbox.isAdmin,
            ).toBundle(),
        )
    }

    private fun tryToSendEmail(isScheduled: Boolean = false, isReminder: Boolean = false) {

        fun setSnackbarActivityResult() {
            val resultIntent = Intent()
            resultIntent.putExtra(
                MainActivity.DRAFT_ACTION_KEY,
                when {
                    isScheduled -> DraftAction.SCHEDULE.name
                    isReminder -> DraftAction.REMINDER.name
                    else -> DraftAction.SEND.name
                },
            )
            requireActivity().setResult(AppCompatActivity.RESULT_OK, resultIntent)
        }

        fun sendEmail() {
            newMessageViewModel.draftAction = when { // TODO: add an other DraftAction if it's reminder + schedule ?
                isScheduled -> DraftAction.SCHEDULE
                isReminder -> DraftAction.REMINDER
                else -> DraftAction.SEND
            }
            setSnackbarActivityResult()
            requireActivity().finishAppAndRemoveTaskIfNeeded()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            if (isSubjectBlank() && showSubjectDialog(isScheduled)) return@launch

            val body = binding.editorWebView.evaluateJs("getEditorBody()").removeSurrounding("\"")
            val shouldShowAttachmentReminder = newMessageViewModel.shouldShowAttachmentReminder(body)

            if (shouldShowAttachmentReminder && showAttachmentDialog(isScheduled)) return@launch
            sendEmail()
        }
    }

    private suspend fun showConfirmationDialog(
        titleRes: Int,
        descriptionRes: Int,
        trackEvent: MatomoName,
        trackConfirmEvent: MatomoName,
        isScheduled: Boolean,
    ): Boolean {
        trackNewMessageEvent(trackEvent)

        // This flag lets us wait for the dialog to be fully dismissed before deciding
        // whether to continue. Without it, the next dialog could open too early and stay stucked in loading state.
        var hasConfirmed = false
        val isSendingCanceled = CompletableDeferred<Boolean>()

        descriptionDialog.show(
            title = getString(titleRes),
            description = getString(descriptionRes),
            positiveButtonText = R.string.buttonContinue,
            displayLoader = false,
            onPositiveButtonClicked = {
                trackNewMessageEvent(trackConfirmEvent)
                hasConfirmed = true
            },
            onCancel = { if (isScheduled) newMessageViewModel.resetScheduledDate() },
            onDismiss = { isSendingCanceled.complete(!hasConfirmed) },
        )

        return isSendingCanceled.await()
    }

    private suspend fun showSubjectDialog(isScheduled: Boolean) = showConfirmationDialog(
        titleRes = R.string.emailWithoutSubjectTitle,
        descriptionRes = R.string.emailWithoutSubjectDescription,
        trackEvent = MatomoName.SendWithoutSubject,
        trackConfirmEvent = MatomoName.SendWithoutSubjectConfirm,
        isScheduled = isScheduled,
    )

    private suspend fun showAttachmentDialog(isScheduled: Boolean) = showConfirmationDialog(
        titleRes = R.string.attachmentsReminderTitle,
        descriptionRes = R.string.attachmentsReminderDescription,
        trackEvent = MatomoName.SendWithoutAttachment,
        trackConfirmEvent = MatomoName.SendWithoutAttachmentConfirm,
        isScheduled = isScheduled,
    )

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

    suspend fun navigateToPropositionFragment() = aiManager.navigateToPropositionFragment()

    fun closeAiPrompt() = aiManager.closeAiPrompt()

    fun isSubjectBlank() = binding.subjectTextField.text?.isBlank() == true

    companion object {
        const val COMMON_MENTIONS_SCRIPT = "common_mentions_code_script"
        const val MENTION_OBSERVER_SCRIPT = "mention_observer_script"
        const val INSERT_MENTION_SCRIPT = "insert_mention_script"
        const val MENTION_DELETION_OBSERVER_SCRIPT = "mention_deletion_observer_script"
        const val MENTIONS_STYLE = "mentions_style"
    }
}
