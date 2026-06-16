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
package com.infomaniak.mail.ui.main.thread

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.InsetDrawable
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.core.graphics.ColorUtils
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.work.Data
import com.infomaniak.core.common.observe
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.legacy.utils.context
import com.infomaniak.core.legacy.utils.getBackNavigationResult
import com.infomaniak.core.legacy.views.DividerItemDecorator
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackAttachmentActionsEvent
import com.infomaniak.mail.MatomoMail.trackBlockUserAction
import com.infomaniak.mail.MatomoMail.trackEmojiReactionsEvent
import com.infomaniak.mail.MatomoMail.trackMessageActionsEvent
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.MatomoMail.trackScheduleSendEvent
import com.infomaniak.mail.MatomoMail.trackSnoozeEvent
import com.infomaniak.mail.MatomoMail.trackThreadActionsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.AutoAdvanceMode
import com.infomaniak.mail.data.LocalSettings.ExternalContent
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.data.models.Attachable
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.FolderRole
import com.infomaniak.mail.data.models.SwissTransferFile
import com.infomaniak.mail.data.models.calendar.Attendee
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.extensions.containsOnlyScheduledDrafts
import com.infomaniak.mail.data.models.extensions.downloadUrl
import com.infomaniak.mail.data.models.extensions.folder
import com.infomaniak.mail.data.models.extensions.getRecipientsForReplyTo
import com.infomaniak.mail.data.models.extensions.kSuite
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.snooze.BatchSnoozeResult
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.ThreadFilter
import com.infomaniak.mail.databinding.FragmentThreadBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.ConfirmScheduledDraftModificationDialog
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.alertDialogs.EmailContextualMenuAlertDialog
import com.infomaniak.mail.ui.alertDialogs.InformationAlertDialog
import com.infomaniak.mail.ui.alertDialogs.LinkContextualMenuAlertDialog
import com.infomaniak.mail.ui.alertDialogs.PhoneContextualMenuAlertDialog
import com.infomaniak.mail.ui.alertDialogs.SelectDateAndTimeForScheduledDraftDialog
import com.infomaniak.mail.ui.alertDialogs.SelectDateAndTimeForSnoozeDialog
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleSendBottomSheetDialog.Companion.OPEN_SCHEDULE_DRAFT_DATE_AND_TIME_PICKER
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleSendBottomSheetDialog.Companion.SCHEDULE_DRAFT_RESULT
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleSendBottomSheetDialogArgs
import com.infomaniak.mail.ui.bottomSheetDialogs.SnoozeBottomSheetDialog.Companion.OPEN_SNOOZE_DATE_AND_TIME_PICKER
import com.infomaniak.mail.ui.bottomSheetDialogs.SnoozeBottomSheetDialog.Companion.SNOOZE_RESULT
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.emojiPicker.EmojiPickerBottomSheetDialog.EmojiPickerObserverTarget
import com.infomaniak.mail.ui.main.emojiPicker.EmojiPickerBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.emojiPicker.PickedEmojiPayload
import com.infomaniak.mail.ui.main.emojiPicker.PickerEmojiObserver
import com.infomaniak.mail.ui.main.folder.ThreadListItem
import com.infomaniak.mail.ui.main.folder.TwoPaneFragment
import com.infomaniak.mail.ui.main.folder.TwoPaneViewModel
import com.infomaniak.mail.ui.main.search.SearchFragment
import com.infomaniak.mail.ui.main.search.SearchViewModel
import com.infomaniak.mail.ui.main.thread.SubjectFormatter.SubjectData
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.ContextMenuType
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.DisplayType
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.NotifyType
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.ThreadAdapterCallbacks
import com.infomaniak.mail.ui.main.thread.ThreadViewModel.SnoozeScheduleType
import com.infomaniak.mail.ui.main.thread.ThreadViewModel.ThreadHeaderVisibility
import com.infomaniak.mail.ui.main.thread.actions.ActionsViewModel
import com.infomaniak.mail.ui.main.thread.actions.AiActionsViewModel
import com.infomaniak.mail.ui.main.thread.actions.AiActionsViewModel.AiAction
import com.infomaniak.mail.ui.main.thread.actions.AiActionsViewModel.AiBodyUpdate
import com.infomaniak.mail.ui.main.thread.actions.AskEuriaBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.actions.AttachmentActionsBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.actions.ConfirmationToBlockUserDialog
import com.infomaniak.mail.ui.main.thread.actions.EmojiReactionsViewModel
import com.infomaniak.mail.ui.main.thread.actions.JunkMessagesViewModel
import com.infomaniak.mail.ui.main.thread.actions.MessageActionsBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.actions.ReplyBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.actions.ThreadActionsBottomSheetDialog.Companion.OPEN_SNOOZE_BOTTOM_SHEET
import com.infomaniak.mail.ui.main.thread.actions.ThreadActionsBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.calendar.AttendeesBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.encryption.UnencryptableRecipientsBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.models.MessageUi
import com.infomaniak.mail.utils.FolderRoleUtils
import com.infomaniak.mail.utils.PermissionUtils
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.UiUtils
import com.infomaniak.mail.utils.UiUtils.dividerDrawable
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.WorkerUtils
import com.infomaniak.mail.utils.date.MailDateFormatUtils.formatDayOfWeekAdaptiveYear
import com.infomaniak.mail.utils.extensions.AttachmentExt.openAttachment
import com.infomaniak.mail.utils.extensions.applySideAndBottomSystemInsets
import com.infomaniak.mail.utils.extensions.applyStatusBarInsets
import com.infomaniak.mail.utils.extensions.applyWindowInsetsListener
import com.infomaniak.mail.utils.extensions.archiveWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.bindAlertToViewLifecycle
import com.infomaniak.mail.utils.extensions.changeToolbarColorOnScroll
import com.infomaniak.mail.utils.extensions.copyStringToClipboard
import com.infomaniak.mail.utils.extensions.deleteWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.getAttributeColor
import com.infomaniak.mail.utils.extensions.isTabletOrFoldable
import com.infomaniak.mail.utils.extensions.navigateToDownloadProgressDialog
import com.infomaniak.mail.utils.extensions.observeNotNull
import com.infomaniak.mail.utils.extensions.replyWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.toDate
import com.infomaniak.mail.workers.DraftsActionsWorker
import com.infomaniak.mail.workers.DraftsActionsWorker.Companion.ALL_EMOJI_SENT_STATUS
import com.infomaniak.mail.workers.DraftsActionsWorker.Companion.EMOJI_SENT_STATUS
import com.infomaniak.mail.workers.MailActionsManager
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.appcompat.R as RAndroid
import com.infomaniak.core.common.R as RCore

@AndroidEntryPoint
class ThreadFragment : Fragment(), PickerEmojiObserver {

    @Inject
    lateinit var confirmScheduledDraftModificationDialog: ConfirmScheduledDraftModificationDialog

    @Inject
    lateinit var confirmationToBlockUserDialog: ConfirmationToBlockUserDialog

    @Inject
    lateinit var dateAndTimeScheduleDialog: SelectDateAndTimeForScheduledDraftDialog

    @Inject
    lateinit var dateAndTimeSnoozeDialog: SelectDateAndTimeForSnoozeDialog

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    @Inject
    lateinit var draftsActionsWorkerScheduler: DraftsActionsWorker.Scheduler

    @Inject
    lateinit var emailContextualMenuAlertDialog: EmailContextualMenuAlertDialog

    @Inject
    lateinit var folderRoleUtils: FolderRoleUtils

    @Inject
    lateinit var informationDialog: InformationAlertDialog

    @Inject
    lateinit var linkContextualMenuAlertDialog: LinkContextualMenuAlertDialog

    @Inject
    lateinit var localSettings: LocalSettings

    @Inject
    lateinit var permissionUtils: PermissionUtils

    @Inject
    lateinit var phoneContextualMenuAlertDialog: PhoneContextualMenuAlertDialog

    @Inject
    lateinit var snackbarManager: SnackbarManager

    @Inject
    lateinit var subjectFormatter: SubjectFormatter

    private var _binding: FragmentThreadBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView

    private val mainViewModel: MainViewModel by activityViewModels()
    private val junkMessagesViewModel: JunkMessagesViewModel by activityViewModels()
    private val twoPaneViewModel: TwoPaneViewModel by activityViewModels()
    private val threadViewModel: ThreadViewModel by viewModels()
    private val aiActionsViewModel: AiActionsViewModel by viewModels()
    private val actionsViewModel: ActionsViewModel by activityViewModels()
    private val emojiReactionsViewModel: EmojiReactionsViewModel by viewModels()
    private val searchViewModel: SearchViewModel by activityViewModels()

    private val twoPaneFragment inline get() = parentFragment as TwoPaneFragment
    private val threadAdapter inline get() = binding.messagesList.adapter as ThreadAdapter
    private val isNotInSpam: Boolean
        get() = runCatchingRealm { mainViewModel.currentFolder.value?.role != FolderRole.SPAM }.getOrDefault(true)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentThreadBinding.inflate(inflater, container, false).also { _binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        handleEdgeToEdge()

        setupUi()
        setupAdapter()
        setupDialogs()
        permissionUtils.registerDownloadManagerPermission(fragment = this)

        observeLightThemeToggle()
        observeThreadLive()
        observeMessagesLive()
        observeMessagesAreCollapsibles()
        observeFailedMessages()
        observeQuickActionBarClicks()
        observeSubjectUpdateTriggers()
        observeCurrentFolderName()
        observeSnoozeHeaderVisibility()

        observePickedEmoji()

        observeDraftWorkerResults()

        observeThreadOpening()
        observeCalculateThreadPosition()
        observeAutoAdvance()

        setupBackActionHandler()

        observeReportDisplayProblemResult()

        observeMessageOfUserToBlock()

        observeCanSendEmails()

        observeAiActionEvents()
    }

    private fun handleEdgeToEdge() = with(binding) {
        applyWindowInsetsListener(shouldConsume = true) { _, insets ->
            mainAppBar.applyStatusBarInsets(insets)
            appBar.applySideAndBottomSystemInsets(insets, withBottom = false)
            messagesListNestedScrollView.applySideAndBottomSystemInsets(insets, withBottom = false)
            quickActionBar.getRoot().applySideAndBottomSystemInsets(insets)
        }
    }

    private fun observeReportDisplayProblemResult() {
        mainViewModel.reportDisplayProblemTrigger.observe(viewLifecycleOwner) { descriptionDialog.resetLoadingAndDismiss() }
    }

    private fun observeMessageOfUserToBlock() = with(confirmationToBlockUserDialog) {
        junkMessagesViewModel.messageOfUserToBlock.observe(viewLifecycleOwner) { messageOfUserToBlock ->
            setPositiveButtonCallback { message ->
                trackBlockUserAction(MatomoName.ConfirmSelectedUser)
                actionsViewModel.blockUser(message.folderId, message.shortUid, mainViewModel.currentMailbox.value!!)
            }
            show(messageOfUserToBlock)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        threadAdapter.reRenderMails()
        super.onConfigurationChanged(newConfig)
        updateNavigationIcon()
    }

    override fun onStop() {
        threadViewModel.threadState.verticalScroll = binding.messagesListNestedScrollView.scrollY
        super.onStop()
    }

    override fun onDestroyView() {

        threadAdapter.resetCallbacks()
        super.onDestroyView()
        _binding = null
    }

    fun resetThreadState() {
        threadViewModel.threadState.reset()
        aiActionsViewModel.reset()
    }

    private fun setupUi() = with(binding) {

        threadSubject.movementMethod = LinkMovementMethod.getInstance()

        updateNavigationIcon()
        toolbar.setNavigationOnClickListener {
            if (SDK_INT >= 29) requireActivity().window.isNavigationBarContrastEnforced = true
            twoPaneViewModel.closeThread()
        }

        val defaultTextColor = context.getColor(R.color.primaryTextColor)
        appBar.addOnOffsetChangedListener { appBarLayout, verticalOffset ->

            val subjectHeight = appBarLayout.height
            val impactingHeight = subjectHeight * COLLAPSE_TITLE_THRESHOLD
            val nonImpactingHeight = subjectHeight - impactingHeight

            val absoluteProgress = verticalOffset.absoluteValue - nonImpactingHeight
            val relativeProgress = (absoluteProgress / impactingHeight).coerceIn(0.0, 1.0) // Between 0 and 1
            val opacity = (relativeProgress * 255.0).roundToInt()
            val textColor = ColorUtils.setAlphaComponent(defaultTextColor, opacity)

            toolbarSubject.setTextColor(textColor)
        }

        changeToolbarColorOnScroll(
            mainAppBar,
            messagesListNestedScrollView,
            otherUpdates = { color ->
                // Duplicated line so both the mainAppBar and the appBar change color simultaneously
                mainAppBar.backgroundTintList = ColorStateList.valueOf(color)
                appBar.backgroundTintList = ColorStateList.valueOf(color)
            },
        )
    }

    private fun updateNavigationIcon() {
        binding.toolbar.apply {
            if (isTabletOrFoldable()) {
                if (navigationIcon != null) navigationIcon = null
            } else {
                if (navigationIcon == null) setNavigationIcon(R.drawable.ic_navigation_default)
            }
        }
    }

    private fun setupAdapter() = with(threadViewModel) {

        binding.messagesList.adapter = ThreadAdapter(
            shouldLoadDistantResources = shouldLoadDistantResources(),
            isSpamFilterActivated = { mainViewModel.currentMailbox.value?.isSpamFiltered ?: false },
            areMessagesCollapsibles = { threadViewModel.messagesAreCollapsiblesFlow.value },
            senderRestrictions = { mainViewModel.currentMailbox.value?.sendersRestrictions },
            aliases = { mainViewModel.currentMailbox.value?.aliases?.toList() ?: emptyList() },
            threadAdapterState = object : ThreadAdapterState {
                override val isExpandedMap by threadState::isExpandedMap
                override val isThemeTheSameMap by threadState::isThemeTheSameMap
                override val verticalScroll by threadState::verticalScroll
                override val isCalendarEventExpandedMap by threadState::isCalendarEventExpandedMap
            },
            threadAdapterCallbacks = ThreadAdapterCallbacks(
                onContactClicked = { recipient, bimi ->
                    safeNavigate(
                        resId = R.id.detailedContactBottomSheetDialog,
                        args = DetailedContactBottomSheetDialogArgs(recipient, bimi).toBundle(),
                    )
                },
                onDraftClicked = ::openDraft,
                onDeleteDraftClicked = ::deleteDraft,
                onAttachmentClicked = ::onAttachableClicked,
                onAttachmentOptionsClicked = ::navigateToAttachmentActions,
                onDownloadAllClicked = ::downloadAllAttachments,
                onReplyClicked = { message ->
                    val hasNoReplyRecipients = SharedUtils.hasNoReplyRecipients(message, isReplyAll = false)
                    descriptionDialog.replyWithConfirmationPopup(
                        hasNoReplyRecipients = hasNoReplyRecipients,
                        onPositiveButtonClicked = {
                            trackMessageActionsEvent(MatomoName.Reply)
                            replyTo(message)
                        }
                    )
                },
                onMenuClicked = { message -> message.navigateToActionsBottomSheet() },
                onAllExpandedMessagesLoaded = ::scrollToFirstUnseenMessage,
                onSuperCollapsedBlockClicked = ::expandSuperCollapsedBlock,
                navigateToAttendeeBottomSheet = ::navigateToAttendees,
                navigateToNewMessageActivity = { twoPaneViewModel.navigateToNewMessage(mailToUri = it) },
                navigateToDownloadProgressDialog = { attachment, attachmentIntentType ->
                    navigateToDownloadProgressDialog(attachment, attachmentIntentType, ThreadFragment::class.java.name)
                },
                onUnsubscribeClicked = threadViewModel::unsubscribeMessage,
                onAcknowledgeClicked = threadViewModel::acknowledgeMessage,
                onMessageExpanded = threadViewModel::refreshMessageIfNeeded,
                moveMessageToSpam = { messageUid ->
                    actionsViewModel.moveToSpamFolder(
                        messagesUid = listOf(messageUid),
                        parentFolderId = mainViewModel.currentFolderId,
                        mailbox = mainViewModel.currentMailbox.value!!,
                    )
                },
                activateSpamFilter = { actionsViewModel.activateSpamFilter(mainViewModel.currentMailbox.value!!) },
                unblockMail = { actionsViewModel.unblockMail(it, mainViewModel.currentMailbox.value!!) },
                replyToCalendarEvent = { attendanceState, message ->
                    replyToCalendarEvent(
                        attendanceState,
                        message,
                    ).observe(viewLifecycleOwner) { successfullyUpdated ->
                        if (successfullyUpdated) {
                            snackbarManager.setValue(getString(R.string.snackbarCalendarChoiceSent))
                            fetchCalendarEvents(listOf(message), forceFetch = true)
                        } else {
                            snackbarManager.setValue(getString(R.string.errorCalendarChoiceCouldNotBeSent))
                            threadAdapter.undoUserAttendanceClick(message)
                        }
                    }
                },
                promptLink = { data, type ->
                    // When adding a phone number to contacts, Google decodes this value in case it's url-encoded. But I could not
                    // reproduce this issue when manually creating a url-encoded href. If this is triggered, fix it by also
                    // decoding it at that step.
                    if (type == ContextMenuType.PHONE && data.contains('%')) {
                        Sentry.captureMessage(
                            "Google was right, phone numbers can be url-encoded. Needs to be fixed",
                            SentryLevel.ERROR,
                        )
                    }

                    when (type) {
                        ContextMenuType.LINK -> linkContextualMenuAlertDialog.show(data)
                        ContextMenuType.EMAIL -> emailContextualMenuAlertDialog.show(data)
                        ContextMenuType.PHONE -> phoneContextualMenuAlertDialog.show(data)
                    }
                },
                onRescheduleClicked = ::rescheduleDraft,
                onModifyScheduledClicked = ::modifyScheduledDraft,
                onEncryptionSeeConcernedRecipients = ::navigateToUnencryptableRecipients,
                onAddReaction = {
                    trackEmojiReactionsEvent(MatomoName.OpenEmojiPicker)
                    navigateToEmojiPicker(it.uid, EmojiPickerObserverTarget.Thread)
                },
                onAddEmoji = { emoji, messageUid ->
                    val reactions = threadViewModel.getLocalEmojiReactionsFor(messageUid) ?: return@ThreadAdapterCallbacks

                    if (reactions[emoji]?.hasReacted == true) {
                        trackEmojiReactionsEvent(MatomoName.AlreadyUsedReaction)
                    } else {
                        trackEmojiReactionsEvent(MatomoName.AddReactionFromChip)
                    }

                    emojiReactionsViewModel.trySendEmojiReply(
                        emoji = emoji,
                        messageUid = messageUid,
                        reactions = reactions,
                        mailbox = mainViewModel.currentMailbox.value!!,
                        onAllowed = {
                            threadViewModel.fakeEmojiReply(emoji, messageUid)
                        },
                    )
                },
                showEmojiDetails = { messageUid, emoji ->
                    trackEmojiReactionsEvent(MatomoName.ShowReactionsBottomSheet)
                    lifecycleScope.launch {
                        val emojiDetails = getLocalEmojiReactionsDetailsFor(messageUid) ?: return@launch
                        binding.emojiReactionDetailsBottomSheet.showBottomSheetFor(emojiDetails, preselectedEmojiTab = emoji)
                    }
                },
                onAiBannerRetry = { messageUid, aiAction -> aiActionsViewModel.doAiAction(messageUid, aiAction) },
                onAiBannerClose = { messageUid, aiAction -> aiActionsViewModel.dismissAiAction(messageUid, aiAction) },
                onShowOriginal = { messageUid -> aiActionsViewModel.dismissAiAction(messageUid, AiAction.TRANSLATE) },
                getAiState = { aiActionsViewModel.aiStateMap.value },
            ),
        )

        binding.messagesList.apply {
            addItemDecoration(
                DividerItemDecorator(
                    divider = InsetDrawable(dividerDrawable(requireContext()), 0),
                    shouldIgnoreView = { view -> view.tag == UiUtils.IGNORE_DIVIDER_TAG },
                ),
            )
            recycledViewPool.setMaxRecycledViews(DisplayType.MAIL.layout, 0)

            // Try to fix IllegalArgumentException occurring when a tmp detached view is recycled during its removing animation ends
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        }

        threadAdapter.stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    private fun openDraft(message: Message) {
        trackNewMessageEvent(MatomoName.OpenFromDraft)
        twoPaneViewModel.navigateToNewMessage(
            arrivedFromExistingDraft = true,
            draftLocalUuid = message.draftLocalUuid,
            draftResource = message.draftResource,
            messageUid = message.uid,
        )
    }

    private fun deleteDraft(message: Message) {
        trackMessageActionsEvent(MatomoName.DeleteDraft)
        mainViewModel.currentMailbox.value?.let { mailbox -> threadViewModel.deleteDraft(message, mailbox) }
    }

    private fun onAttachableClicked(attachable: Attachable) {
        when (attachable) {
            is Attachment -> {
                trackAttachmentActionsEvent(MatomoName.Open)
                attachable.openAttachment(
                    context = requireContext(),
                    navigateToDownloadProgressDialog = { attachment, attachmentIntentType ->
                        navigateToDownloadProgressDialog(
                            attachment = attachment,
                            attachmentIntentType = attachmentIntentType,
                            currentClassName = ThreadFragment::class.java.name,
                        )
                    },
                    snackbarManager = snackbarManager,
                )
            }
            is SwissTransferFile -> {
                trackAttachmentActionsEvent(MatomoName.OpenSwissTransfer)
                downloadSwissTransferFile(swissTransferFile = attachable)
            }
        }
    }

    private fun navigateToAttachmentActions(attachable: Attachable) {
        safeNavigate(
            resId = R.id.attachmentActionsBottomSheetDialog,
            args = AttachmentActionsBottomSheetDialogArgs(attachable.localUuid, attachable is SwissTransferFile).toBundle(),
        )
    }

    private fun setupDialogs() {
        bindAlertToViewLifecycle(descriptionDialog)
        linkContextualMenuAlertDialog.initValues(snackbarManager)
        emailContextualMenuAlertDialog.initValues(snackbarManager)
        phoneContextualMenuAlertDialog.initValues(snackbarManager)
    }

    private fun observeThreadOpening() {
        twoPaneViewModel.currentThreadUid.distinctUntilChanged().observeNotNull(viewLifecycleOwner) { threadUid ->
            aiActionsViewModel.reset()
            displayThreadView()
            threadViewModel.updateCurrentThreadUid(threadViewModel.AllMessages(threadUid))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            threadViewModel.threadFlow.collect { thread ->
                if (thread == null) {
                    twoPaneViewModel.closeThread()
                    return@collect
                }

                val allFolderRoles = folderRoleUtils.getActionFolderRoles(thread.messages)
                initUi(
                    thread.uid,
                    folderRole = folderRoleUtils.getThreadActionFolderRole(thread),
                    messagesFolderRoles = allFolderRoles
                )
            }
        }
    }

    private fun observeLightThemeToggle() {
        mainViewModel.toggleLightThemeForMessage.observe(viewLifecycleOwner, threadAdapter::toggleLightMode)
    }

    private fun observeCanSendEmails() {
        mainViewModel.canSendEmailsFlow.observe(viewLifecycleOwner) { canSend ->
            threadAdapter.updateEmailsPermission(canSend)
            updateQuickActionBarSendingState(canSend)
        }
    }

    private fun updateQuickActionBarSendingState(canSend: Boolean) = with(binding.quickActionBar) {
        setEnableByMenuId(R.id.quickActionReply, enabled = canSend)
        setEnableByMenuId(R.id.quickActionForward, enabled = canSend)
    }

    private fun observeThreadLive() {
        threadViewModel.threadLive.observe(viewLifecycleOwner) { thread ->
            if (thread == null) {
                twoPaneViewModel.closeThread()
                return@observe
            }

            updateFavoriteIcon(thread.isFavorite)
            setupQuickActionBar(thread)
            setupSnoozeAlert(thread)
        }
    }

    private fun updateFavoriteIcon(isFavorite: Boolean) = with(binding.iconFavorite) {
        val (iconRes, color) = if (isFavorite) {
            R.drawable.ic_star_filled to context.getColor(R.color.favoriteYellow)
        } else {
            R.drawable.ic_star to context.getAttributeColor(RAndroid.attr.colorPrimary)
        }

        setIconResource(iconRes)
        iconTint = ColorStateList.valueOf(color)
    }

    private fun setupQuickActionBar(thread: Thread) = with(binding.quickActionBar) {
        val shouldDisplayScheduledDraftActions = thread.containsOnlyScheduledDrafts(
            mainViewModel.featureFlagsLive.value,
            localSettings,
        )

        init(if (shouldDisplayScheduledDraftActions) R.menu.scheduled_draft_menu else R.menu.message_menu)

        if (!mainViewModel.canSendEmailsFlow.value) {
            setEnableByMenuId(R.id.quickActionReply, enabled = false)
            setEnableByMenuId(R.id.quickActionForward, enabled = false)
        }
    }

    private fun setupSnoozeAlert(thread: Thread) = with(binding.snoozeAlert) {
        thread.snoozeEndDate?.let { snoozeEndDate ->
            val formattedDate = context.formatDayOfWeekAdaptiveYear(snoozeEndDate.toDate())
            setDescription(getString(R.string.snoozeAlertTitle, formattedDate))
        }

        onAction1 {
            trackSnoozeEvent(MatomoName.ModifySnooze)
            navigateToSnoozeBottomSheet(SnoozeScheduleType.Modify(thread.uid))
        }

        onAction2 {
            trackSnoozeEvent(MatomoName.CancelSnooze)
            unsnoozeThread(thread)
        }
    }

    private fun observeMessagesLive() = with(threadViewModel) {

        messagesLive.observe(viewLifecycleOwner) { (items, messagesToFetch) ->
            SentryLog.i("UI", "Received ${items.count()} messages")

            if (items.isEmpty()) {
                mainViewModel.deletedMessages.value = deletedMessagesUids
                twoPaneViewModel.closeThread()
                return@observe
            }

            threadAdapter.submitList(items)

            if (messagesToFetch.isNotEmpty()) fetchMessagesHeavyData(messagesToFetch)

            fetchCalendarEvents(items)
        }
    }

    private fun observeMessagesAreCollapsibles() {
        viewLifecycleOwner.lifecycleScope.launch {
            threadViewModel.messagesAreCollapsiblesFlow.collect(threadAdapter::messagesCollapseStateChange)
        }
    }

    private fun observeFailedMessages() {
        threadViewModel.failedMessagesUids.observe(viewLifecycleOwner, threadAdapter::updateFailedMessages)
    }

    private fun observeQuickActionBarClicks() {
        threadViewModel.quickActionBarClicks.observe(viewLifecycleOwner) { (threadUid, lastMessageToReplyTo, menuId) ->
            when (menuId) {
                R.id.quickActionReply -> {
                    val hasNoReplyRecipients = SharedUtils.hasNoReplyRecipients(lastMessageToReplyTo, isReplyAll = false)
                    descriptionDialog.replyWithConfirmationPopup(
                        hasNoReplyRecipients = hasNoReplyRecipients,
                        onPositiveButtonClicked = { replyTo(lastMessageToReplyTo) }
                    )
                }
                R.id.quickActionForward -> {
                    twoPaneViewModel.navigateToNewMessage(
                        draftMode = DraftMode.FORWARD,
                        previousMessageUid = lastMessageToReplyTo.uid,
                        shouldLoadDistantResources = shouldLoadDistantResources(lastMessageToReplyTo.uid),
                    )
                }
                R.id.quickActionMenu -> {
                    safeNavigate(
                        resId = R.id.threadActionsBottomSheetDialog,
                        args = ThreadActionsBottomSheetDialogArgs(
                            threadUid = threadUid,
                            shouldLoadDistantResources = shouldLoadDistantResources(lastMessageToReplyTo.uid),
                            isFromSearch = parentFragment is SearchFragment
                        ).toBundle(),
                    )
                }
            }
        }
    }

    private fun observeSubjectUpdateTriggers() = with(binding) {
        threadViewModel.assembleSubjectData(mainViewModel.mergedContactsLive).observe(viewLifecycleOwner) { result ->
            val mailbox = result.mailbox ?: return@observe
            val (subjectWithoutTags, subjectWithTags) = subjectFormatter.generateSubjectContent(
                subjectData = SubjectData(
                    thread = result.thread ?: return@observe,
                    emailDictionary = result.mergedContacts ?: emptyMap(),
                    aliases = mailbox.aliases,
                    hasOrganisation = mailbox.kSuite is KSuite.Pro,
                    externalMailFlagEnabled = mailbox.externalMailFlagEnabled,
                    trustedDomains = mailbox.trustedDomains,
                ),
            ) { title, description ->
                informationDialog.show(
                    title = title,
                    description = description,
                    confirmButtonText = R.string.externalDialogConfirmButton,
                )
            }

            toolbarSubject.text = subjectWithoutTags
            threadSubject.text = subjectWithTags

            threadSubject.setOnLongClickListener {
                context.copyStringToClipboard(subjectWithoutTags, R.string.snackbarSubjectCopiedToClipboard, snackbarManager)
                true
            }
            toolbarSubject.setOnLongClickListener {
                context.copyStringToClipboard(subjectWithoutTags, R.string.snackbarSubjectCopiedToClipboard, snackbarManager)
                true
            }
        }
    }

    private fun observeCurrentFolderName() {
        twoPaneViewModel.rightPaneFolderName.observe(viewLifecycleOwner) {
            binding.emptyView.title = getString(R.string.noConversationSelected, it)
        }
    }

    private fun observeSnoozeHeaderVisibility() = with(binding) {
        threadViewModel.isThreadSnoozeHeaderVisible.observe(viewLifecycleOwner) {
            threadAlertsLayout.isGone = it == ThreadHeaderVisibility.NONE
            snoozeAlert.setActionsVisibility(it == ThreadHeaderVisibility.MESSAGE_AND_ACTIONS)
        }
    }

    override fun observePickedEmoji() {
        getBackNavigationResult<PickedEmojiPayload>(EmojiPickerObserverTarget.Thread.name) { (emoji, messageUid) ->
            trackEmojiReactionsEvent(MatomoName.AddReactionFromEmojiPicker)
            val reactions = threadViewModel.getLocalEmojiReactionsFor(messageUid) ?: return@getBackNavigationResult
            emojiReactionsViewModel.trySendEmojiReply(
                emoji = emoji,
                messageUid = messageUid,
                reactions = reactions,
                mailbox = mainViewModel.currentMailbox.value!!,
                onAllowed = {
                    threadViewModel.fakeEmojiReply(emoji, messageUid)
                },
            )
        }
    }

    private fun observeDraftWorkerResults() {
        WorkerUtils.flushWorkersBefore(context = requireContext(), lifecycleOwner = viewLifecycleOwner) {

            // Listening to progress of the worker only lets us react quickly if the user had to upload multiple drafts. This
            // approach may skip intermediate progress updates if we stop listening and then restart, as it only captures the most
            // recent state.
            val runningWorkInfoLiveData = draftsActionsWorkerScheduler.getRunningWorkInfoLiveData()
            runningWorkInfoLiveData.observe(viewLifecycleOwner) {
                it.forEach { workInfo ->
                    val emojiSendResult = workInfo.progress
                        .getSerializable<MailActionsManager.EmojiSendResult>(EMOJI_SENT_STATUS) ?: return@forEach

                    undoFakeEmojiReplyIfNeeded(emojiSendResult)
                }
            }

            // Listening to the draft results ensures we will get all of the possible emoji results and not miss any unlike when
            // we listen to the worker's progress.
            val treatedWorkInfoUuids = mutableSetOf<UUID>()
            draftsActionsWorkerScheduler.getCompletedInfoLiveData().observe(viewLifecycleOwner) {
                it.forEach { workInfo ->
                    if (!treatedWorkInfoUuids.add(workInfo.id)) return@forEach

                    val emojiSendResults = workInfo.outputData
                        .getSerializable<MailActionsManager.EmojiSendResults>(ALL_EMOJI_SENT_STATUS) ?: return@forEach

                    emojiSendResults.results.forEach { emojiSendResult ->
                        undoFakeEmojiReplyIfNeeded(emojiSendResult)
                    }
                }
            }
        }
    }

    private fun undoFakeEmojiReplyIfNeeded(emojiSendResult: MailActionsManager.EmojiSendResult) {
        if (emojiSendResult.isSuccess.not()) {
            threadViewModel.undoFakeEmojiReply(emojiSendResult.emoji, emojiSendResult.previousMessageUid)
        }
    }

    private fun observeCalculateThreadPosition() {
        actionsViewModel.calculateCurrentThreadPosition.observe(viewLifecycleOwner) { calculateThreadPosition() }
    }

    private fun observeAutoAdvance() {
        actionsViewModel.tryToAutoAdvance.observe(viewLifecycleOwner) { tryToAutoAdvance() }
    }

    private fun setupBackActionHandler() {
        getBackNavigationResult(OPEN_SCHEDULE_DRAFT_DATE_AND_TIME_PICKER) { _: Boolean ->
            val mailbox = mainViewModel.currentMailbox.value
            if (mailbox == null) {
                snackbarManager.postValue(requireContext().getString(RCore.string.anErrorHasOccurred))
                return@getBackNavigationResult
            }
            dateAndTimeScheduleDialog.show(
                positiveButtonResId = R.string.buttonModify,
                onDateSelected = { timestamp ->
                    trackScheduleSendEvent(MatomoName.CustomScheduleConfirm)
                    localSettings.lastSelectedScheduleEpochMillis = timestamp
                    actionsViewModel.rescheduleDraft(Date(timestamp), mailbox)
                },
                onAbort = ::navigateToScheduleSendBottomSheet,
            )
        }

        getBackNavigationResult(SCHEDULE_DRAFT_RESULT) { selectedScheduleEpoch: Long ->
            val mailbox = mainViewModel.currentMailbox.value
            if (mailbox == null) {
                snackbarManager.postValue(requireContext().getString(RCore.string.anErrorHasOccurred))
                return@getBackNavigationResult
            }
            actionsViewModel.rescheduleDraft(Date(selectedScheduleEpoch), mailbox)
        }

        getBackNavigationResult(OPEN_SNOOZE_BOTTOM_SHEET) { snoozeScheduleType: SnoozeScheduleType ->
            navigateToSnoozeBottomSheet(snoozeScheduleType)
        }

        getBackNavigationResult(OPEN_SNOOZE_DATE_AND_TIME_PICKER) { _: Boolean ->
            dateAndTimeSnoozeDialog.show(
                positiveButtonResId = twoPaneViewModel.snoozeScheduleType?.positiveButtonResId,
                onDateSelected = { timestamp ->
                    trackSnoozeEvent(MatomoName.CustomScheduleConfirm)
                    localSettings.lastSelectedSnoozeEpochMillis = timestamp
                    executeSavedSnoozeScheduleType(timestamp)
                },
                onAbort = { navigateToSnoozeBottomSheet(twoPaneViewModel.snoozeScheduleType) },
            )
        }

        getBackNavigationResult(OPEN_REACTION_BOTTOM_SHEET) { messageUid: String ->
            navigateToEmojiPicker(
                messageUid,
                emojiPickerObserverTarget = if (twoPaneViewModel.isThreadOpen) {
                    EmojiPickerObserverTarget.Thread
                } else {
                    EmojiPickerObserverTarget.ThreadList
                }
            )
        }

        getBackNavigationResult(OPEN_AI_ACTIONS_BOTTOM_SHEET) { messageUid: String ->
            navigateToAskEuriaBottomSheet(messageUid)
        }

        getBackNavigationResult(SNOOZE_RESULT) { selectedScheduleEpoch: Long ->
            executeSavedSnoozeScheduleType(selectedScheduleEpoch)
        }

        getBackNavigationResult<AiActionNavigationResult>(OPEN_AI_SUMMARY_BOTTOM_SHEET) { (messageUid, isAlreadySummarized) ->
            if (isAlreadySummarized) {
                snackbarManager.setValue(getString(R.string.messageAlreadySummarized))
            } else {
                aiActionsViewModel.doAiAction(messageUid, AiAction.SUMMARY)
            }
        }

        getBackNavigationResult<AiActionNavigationResult>(OPEN_AI_TRANSLATE_BOTTOM_SHEET) { (messageUid, isAlreadyTranslated) ->
            if (isAlreadyTranslated) {
                snackbarManager.setValue(getString(R.string.messageAlreadyTranslated))
            } else {
                aiActionsViewModel.doAiAction(messageUid, AiAction.TRANSLATE)
            }
        }
    }

    private fun executeSavedSnoozeScheduleType(timestamp: Long) {
        when (val type = twoPaneViewModel.snoozeScheduleType) {
            is SnoozeScheduleType.Snooze -> snoozeThreads(timestamp, type.threadUids)
            is SnoozeScheduleType.Modify -> rescheduleSnoozedThreads(timestamp, type.threadUids)
            null -> SentryLog.e(TAG, "Tried to execute snooze api call but there's no saved schedule type to handle")
        }
    }

    private fun snoozeThreads(timestamp: Long, threadUids: List<String>) {
        lifecycleScope.launch {
            val isSuccess = actionsViewModel.snoozeThreads(
                date = Date(timestamp),
                threadUids = threadUids,
                parentFolderId = mainViewModel.currentFolderId,
                mailbox = mainViewModel.currentMailbox.value!!,
            )
            if (isSuccess) twoPaneViewModel.closeThread()
        }
    }

    private fun rescheduleSnoozedThreads(timestamp: Long, threadUids: List<String>) {
        lifecycleScope.launch {
            binding.snoozeAlert.showAction1Progress()

            val result = actionsViewModel.rescheduleSnoozedThreads(
                date = Date(timestamp),
                threadUids = threadUids,
                mailbox = mainViewModel.currentMailbox.value!!,
            )
            binding.snoozeAlert.hideAction1Progress(R.string.buttonModify)

            if (result is BatchSnoozeResult.Success) twoPaneViewModel.closeThread()
        }
    }

    private fun displayThreadView() = with(binding) {
        emptyView.isGone = true
        threadView.isVisible = true
    }

    private fun initUi(threadUid: String, folderRole: FolderRole?, messagesFolderRoles: List<FolderRole>) = with(binding) {
        iconFavorite.setOnClickListener {
            trackThreadActionsEvent(MatomoName.Favorite, threadViewModel.threadLive.value!!.isFavorite)
            actionsViewModel.toggleThreadsFavoriteStatus(
                threadsUids = listOf(threadUid),
                mailbox = mainViewModel.currentMailbox.value!!,
                shouldRefreshSearch = searchViewModel.currentFilters.contains(ThreadFilter.STARRED),
            )
        }

        val isFromArchive = folderRole == FolderRole.ARCHIVE

        if (isFromArchive) {
            quickActionBar.disable(ARCHIVE_INDEX)
        } else {
            quickActionBar.enable(ARCHIVE_INDEX)
        }

        quickActionBar.setOnItemClickListener { menuId ->
            when (menuId) {
                R.id.quickActionReply -> {
                    trackThreadActionsEvent(MatomoName.Reply)
                    threadViewModel.clickOnQuickActionBar(menuId)
                }
                R.id.quickActionForward -> {
                    trackThreadActionsEvent(MatomoName.Forward)
                    threadViewModel.clickOnQuickActionBar(menuId)
                }
                R.id.quickActionArchive -> {
                    descriptionDialog.archiveWithConfirmationPopup(folderRole, count = 1) {
                        trackThreadActionsEvent(MatomoName.Archive, isFromArchive)
                        val thread = threadViewModel.threadLive.value ?: return@archiveWithConfirmationPopup
                        actionsViewModel.archiveThreads(
                            threads = listOf(thread),
                            parentFolderId = thread.folderId,
                            mailbox = mainViewModel.currentMailbox.value!!,
                        )
                    }
                }
                R.id.quickActionDelete -> {
                    val thread = threadViewModel.threadLive.value ?: return@setOnItemClickListener
                    descriptionDialog.deleteWithConfirmationPopup(
                        messagesFolderRoles,
                        currentFolderRole = thread.folder.role,
                        count = 1
                    ) {
                        trackThreadActionsEvent(MatomoName.Delete)
                        actionsViewModel.deleteThreads(
                            threads = listOf(thread),
                            parentFolderId = thread.folderId,
                            mailbox = mainViewModel.currentMailbox.value!!,
                        )

                    }
                }
                R.id.quickActionMenu -> {
                    trackThreadActionsEvent(MatomoName.OpenBottomSheet)
                    threadViewModel.clickOnQuickActionBar(menuId)
                }
            }
        }
    }

    private fun scheduleDownloadManager(downloadUrl: String, filename: String) {

        fun scheduleDownloadManager() = mainViewModel.scheduleDownload(downloadUrl, filename)

        if (permissionUtils.hasDownloadManagerPermission) {
            scheduleDownloadManager()
        } else {
            permissionUtils.requestDownloadManagerPermission { scheduleDownloadManager() }
        }
    }

    private fun downloadAllAttachments(message: Message) {
        trackAttachmentActionsEvent(MatomoName.DownloadAll)

        val truncatedSubject = message.subject?.let { it.substring(0..min(MAXIMUM_SUBJECT_LENGTH, it.lastIndex)) }

        if (message.hasAttachments) downloadAttachments(message, allAttachmentsFileName(truncatedSubject ?: ""))

        message.swissTransferUuid?.let { containerUuid ->
            downloadSwissTransferFiles(
                containerUuid = containerUuid,
                name = allSwissTransferFilesName(truncatedSubject ?: ""),
            )
        }
    }

    private fun downloadAttachments(message: Message, name: String) {
        val url = ApiRoutes.downloadAttachments(
            mailboxUuid = mainViewModel.currentMailbox.value?.uuid ?: return,
            folderId = message.folderId,
            shortUid = message.shortUid,
        )
        scheduleDownloadManager(url, name)
    }

    private fun downloadSwissTransferFile(swissTransferFile: SwissTransferFile) {
        scheduleDownloadManager(swissTransferFile.downloadUrl, swissTransferFile.name)
    }

    private fun downloadSwissTransferFiles(containerUuid: String, name: String) {
        scheduleDownloadManager(ApiRoutes.swissTransferContainerDownloadUrl(containerUuid), name)
    }

    private fun replyTo(message: Message) {

        val shouldLoadDistantResources = shouldLoadDistantResources(message.uid)

        if (message.getRecipientsForReplyTo(replyAll = true).second.isEmpty()) {
            twoPaneViewModel.navigateToNewMessage(
                draftMode = DraftMode.REPLY,
                previousMessageUid = message.uid,
                shouldLoadDistantResources = shouldLoadDistantResources,
            )
        } else {
            safeNavigate(
                resId = R.id.replyBottomSheetDialog,
                args = ReplyBottomSheetDialogArgs(message.uid, shouldLoadDistantResources).toBundle(),
            )
        }
    }

    private fun Message.navigateToActionsBottomSheet() {
        safeNavigate(
            resId = R.id.messageActionsBottomSheetDialog,
            args = MessageActionsBottomSheetDialogArgs(
                messageUid = uid,
                threadUid = twoPaneViewModel.currentThreadUid.value ?: return,
                isThemeTheSame = threadViewModel.threadState.isThemeTheSameMap[uid] ?: return,
                shouldLoadDistantResources = shouldLoadDistantResources(uid),
                isFromSearch = parentFragment is SearchFragment
            ).toBundle(),
        )
    }

    private fun canStartAiProcess(isDoneInBody: Boolean?, state: AiProcessState?): Boolean = when (state) {
        is AiProcessState.Loading, is AiProcessState.Success, is AiProcessState.Retrying -> false
        is AiProcessState.Dismissed -> true
        else -> isDoneInBody != true
    }

    private fun scrollToFirstUnseenMessage() = with(threadViewModel) {

        fun getBottomY(): Int = binding.messagesListNestedScrollView.maxScrollAmount

        val scrollY = threadState.verticalScroll ?: run {

            val indexToScroll = threadAdapter.items.indexOfFirst {
                it is MessageUi && threadState.isExpandedMap[it.message.uid] == true
            }

            // If no Message is expanded (e.g. the last Message of the Thread is a Draft),
            // we want to automatically scroll to the very bottom.
            if (indexToScroll == -1) {
                getBottomY()
            } else {
                val targetChild = binding.messagesList.getChildAt(indexToScroll)
                if (targetChild == null) {
                    Sentry.captureMessage(
                        "Target child for scroll in ThreadFragment is null. Fallback to scrolling to bottom",
                        SentryLevel.ERROR,
                    ) { scope ->
                        scope.setExtra("indexToScroll", indexToScroll.toString())
                        scope.setExtra("messageCount", threadAdapter.items.count().toString())
                        scope.setExtra("isExpandedMap", threadState.isExpandedMap.toString())
                        scope.setExtra(
                            "isLastMessageDraft",
                            (threadAdapter.items.lastOrNull() as MessageUi?)?.message?.isDraft.toString()
                        )
                    }
                    getBottomY()
                } else {
                    targetChild.top
                }
            }
        }

        binding.messagesListNestedScrollView.scrollY = scrollY
    }

    private fun expandSuperCollapsedBlock() {
        threadViewModel.threadState.clickSuperCollapsedBlock()
    }

    private fun navigateToAttendees(attendees: List<Attendee>) {
        safeNavigate(
            resId = R.id.attendeesBottomSheetDialog,
            args = AttendeesBottomSheetDialogArgs(attendees.toTypedArray()).toBundle(),
        )
    }

    private fun navigateToUnencryptableRecipients(recipients: List<Recipient>) {
        safeNavigate(
            resId = R.id.unencryptableRecipientsBottomSheetDialog,
            args = UnencryptableRecipientsBottomSheetDialogArgs(recipients.toTypedArray()).toBundle(),
        )
    }

    private fun observeAiActionEvents() {
        aiActionsViewModel.aiActionEvents.observe(viewLifecycleOwner) { (messageUid, aiAction, bodyUpdate) ->
            val aiState = aiActionsViewModel.aiStateMap.value

            val processState = if (aiAction == AiAction.SUMMARY) {
                aiState.summaryStateMap[messageUid]
            } else {
                aiState.translateStateMap[messageUid]
            }

            if (processState is AiProcessState.Error &&
                processState.canRetry &&
                processState.hasAlreadyRetried &&
                !processState.wasLoaderShown
            ) {
                val errorMessage = if (aiAction == AiAction.SUMMARY) {
                    R.string.messageSummaryError
                } else {
                    R.string.messageTranslateError
                }
                snackbarManager.setValue(getString(errorMessage))
            }

            when (bodyUpdate) {
                AiBodyUpdate.SHOW_TRANSLATED -> reloadMessageInAdapter(messageUid)
                AiBodyUpdate.SHOW_ORIGINAL -> showOriginalMessage(messageUid)
                AiBodyUpdate.NONE -> Unit
            }
            notifyAiStateChanged(messageUid, aiAction)
        }
    }

    private fun notifyAiStateChanged(messageUid: String, aiAction: AiAction) {
        val index = threadAdapter.currentList.indexOfFirst { it is MessageUi && it.message.uid == messageUid }
        if (index < 0) return

        val notifyType = when (aiAction) {
            AiAction.SUMMARY -> NotifyType.AiSummaryStateChanged
            AiAction.TRANSLATE -> NotifyType.AiTranslateStateChanged
        }
        threadAdapter.notifyItemChanged(index, notifyType)
    }

    private fun showOriginalMessage(messageUid: String) {
        val originalSplitBody = threadViewModel.threadState.cachedSplitBodies[messageUid]
        val message = threadAdapter.currentList
            .filterIsInstance<MessageUi>()
            .firstOrNull { it.message.uid == messageUid }
            ?.message

        message?.splitBody = originalSplitBody
        reloadMessageInAdapter(messageUid)
    }

    private fun reloadMessageInAdapter(messageUid: String) {
        val index = threadAdapter.currentList.indexOfFirst { it is MessageUi && it.message.uid == messageUid }
        if (index >= 0) {
            threadAdapter.notifyItemChanged(index)
        }
    }

    private fun navigateToAskEuriaBottomSheet(messageUid: String) {
        val message = threadAdapter.currentList
            .filterIsInstance<MessageUi>()
            .firstOrNull { it.message.uid == messageUid }
            ?.message ?: return

        val aiState = aiActionsViewModel.aiStateMap.value
        val isAlreadyTranslated = !canStartAiProcess(message.body?.isTranslated, aiState.translateStateMap[messageUid])
        val isAlreadySummarized = !canStartAiProcess(message.body?.hasSummary, aiState.summaryStateMap[messageUid])

        safeNavigate(
            resId = R.id.askEuriaBottomSheetDialog,
            args = AskEuriaBottomSheetDialogArgs(
                messageUid = messageUid,
                isAlreadyTranslated = isAlreadyTranslated,
                isAlreadySummarized = isAlreadySummarized,
            ).toBundle(),
        )
    }

    private fun rescheduleDraft(draftResource: String, currentScheduledEpochMillis: Long?) {
        actionsViewModel.draftResource = draftResource
        threadViewModel.reschedulingCurrentlyScheduledEpochMillis = currentScheduledEpochMillis
        navigateToScheduleSendBottomSheet()
    }

    private fun navigateToScheduleSendBottomSheet() {
        val mailbox = mainViewModel.currentMailbox.value ?: return
        safeNavigate(
            resId = R.id.scheduleSendBottomSheetDialog,
            args = ScheduleSendBottomSheetDialogArgs(
                lastSelectedScheduleEpochMillis = localSettings.lastSelectedScheduleEpochMillis ?: 0L,
                currentlyScheduledEpochMillis = threadViewModel.reschedulingCurrentlyScheduledEpochMillis ?: 0L,
                currentKSuite = mailbox.kSuite,
                isAdmin = mailbox.isAdmin,
            ).toBundle(),
        )
    }

    private fun navigateToSnoozeBottomSheet(snoozeScheduleType: SnoozeScheduleType?) {
        twoPaneFragment.navigateToSnoozeBottomSheet(snoozeScheduleType, threadViewModel.threadLive.value?.snoozeEndDate)
    }

    private fun unsnoozeThread(thread: Thread): Unit = with(binding) {
        lifecycleScope.launch {
            snoozeAlert.showAction2Progress()

            val result = actionsViewModel.unsnoozeThreads(listOf(thread), mainViewModel.currentMailbox.value)
            snoozeAlert.hideAction2Progress(R.string.buttonCancelReminder)

            if (result is BatchSnoozeResult.Success) twoPaneViewModel.closeThread()
        }
    }

    private fun modifyScheduledDraft(message: Message) {
        confirmScheduledDraftModificationDialog.show(
            title = getString(R.string.editSendTitle),
            description = getString(R.string.editSendDescription),
            onPositiveButtonClicked = {
                val unscheduleDraftUrl = message.unscheduleDraftUrl
                val draftResource = message.draftResource

                if (unscheduleDraftUrl != null && draftResource != null) {
                    actionsViewModel.modifyScheduledDraft(
                        unscheduleDraftUrl = unscheduleDraftUrl,
                        onSuccess = {
                            trackNewMessageEvent(MatomoName.OpenFromDraft)
                            twoPaneViewModel.navigateToNewMessage(
                                arrivedFromExistingDraft = true,
                                draftResource = draftResource,
                                messageUid = message.uid,
                            )
                        },
                        mailbox = mainViewModel.currentMailbox.value!!,
                    )
                }
            },
        )
    }

    private fun shouldLoadDistantResources(messageUid: String): Boolean {
        val isMessageSpecificallyAllowed = threadAdapter.isMessageUidManuallyAllowed(messageUid)
        return (isMessageSpecificallyAllowed && isNotInSpam) || shouldLoadDistantResources()
    }

    private fun shouldLoadDistantResources(): Boolean = localSettings.externalContent == ExternalContent.ALWAYS && isNotInSpam

    fun getAnchor(): View? = _binding?.quickActionBar

    private fun safeNavigate(@IdRes resId: Int, args: Bundle) {
        twoPaneViewModel.safelyNavigate(resId, args)
    }

    private fun calculateThreadPosition() = viewLifecycleOwner.lifecycleScope.launch {
        val currentThread = threadViewModel.threadLive.value
        currentThread?.let {
            val currentThreadPosition =
                twoPaneFragment.threadListAdapter.dataSet
                    .indexOfFirst { it is ThreadListItem.Content && it.thread.uid == currentThread.uid }
            if (currentThreadPosition >= 0) {
                actionsViewModel.updateCurrentThreadPosition(currentThreadPosition, currentThread.uid)
            }
        }
    }

    private fun tryToAutoAdvance() = with(twoPaneFragment.threadListAdapter) {
        viewLifecycleOwner.lifecycleScope.launch {
            actionsViewModel.currentThread?.let { (position, uid) ->
                val data = getNextThreadToOpenByPosition(position)

                data?.let { (nextThread, index) ->
                    if (nextThread.uid != uid && !nextThread.isOnlyOneDraft) {
                        selectNewThread(newPosition = index, nextThread.uid)
                    }

                    twoPaneFragment.navigateToThread(nextThread)
                } ?: run {
                    twoPaneViewModel.closeThread()
                }
            }
        }
    }

    private fun getNextThreadToOpenByPosition(
        startingThreadIndex: Int,
    ): Pair<Thread, Int>? = with(twoPaneFragment.threadListAdapter) {

        val direction = when (localSettings.autoAdvanceMode) {
            AutoAdvanceMode.PREVIOUS_THREAD -> NextThreadTarget.PREVIOUS_CHRONOLOGICAL_THREAD
            AutoAdvanceMode.FOLLOWING_THREAD -> NextThreadTarget.NEXT_CHRONOLOGICAL_THREAD
            AutoAdvanceMode.THREADS_LIST -> null
            AutoAdvanceMode.NATURAL_THREAD -> {
                if (localSettings.autoAdvanceNaturalThread == AutoAdvanceMode.PREVIOUS_THREAD) {
                    NextThreadTarget.PREVIOUS_CHRONOLOGICAL_THREAD
                } else {
                    NextThreadTarget.NEXT_CHRONOLOGICAL_THREAD
                }
            }
        }

        return direction?.let { getNextThread(startingThreadIndex, direction) }
    }

    private fun Fragment.navigateToEmojiPicker(messageUid: String, emojiPickerObserverTarget: EmojiPickerObserverTarget) {
        safelyNavigate(
            resId = R.id.emojiPickerBottomSheetDialog,
            args = EmojiPickerBottomSheetDialogArgs(messageUid, emojiPickerObserverTarget).toBundle(),
            substituteClassName = twoPaneFragment.substituteClassName,
        )
    }

    enum class HeaderState {
        ELEVATED,
        LOWERED,
    }

    enum class NextThreadTarget {
        PREVIOUS_CHRONOLOGICAL_THREAD,
        NEXT_CHRONOLOGICAL_THREAD,
    }

    companion object {
        private val TAG = ThreadFragment::class.java.simpleName

        private const val COLLAPSE_TITLE_THRESHOLD = 0.5
        private const val ARCHIVE_INDEX = 2

        private const val MAXIMUM_SUBJECT_LENGTH = 30

        const val OPEN_REACTION_BOTTOM_SHEET = "openReactionBottomSheet"
        const val OPEN_AI_ACTIONS_BOTTOM_SHEET = "openAiActionsBottomSheet"
        const val OPEN_AI_SUMMARY_BOTTOM_SHEET = "openAiSummaryBottomSheet"
        const val OPEN_AI_TRANSLATE_BOTTOM_SHEET = "openAiTranslateBottomSheet"

        private fun allAttachmentsFileName(subject: String) = "infomaniak-mail-attachments-$subject.zip"
        private fun allSwissTransferFilesName(subject: String) = "infomaniak-mail-swisstransfer-$subject.zip"
    }
}

private inline fun <reified T> Data.getSerializable(key: String): T? = getString(key)?.let { Json.decodeFromString(it) }
