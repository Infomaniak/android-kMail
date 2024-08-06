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
package com.infomaniak.mail.ui.main.thread

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.InsetDrawable
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
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.views.DividerItemDecorator
import com.infomaniak.mail.MatomoMail.ACTION_ARCHIVE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_DELETE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_FAVORITE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_FORWARD_NAME
import com.infomaniak.mail.MatomoMail.ACTION_OPEN_NAME
import com.infomaniak.mail.MatomoMail.ACTION_REPLY_NAME
import com.infomaniak.mail.MatomoMail.OPEN_ACTION_BOTTOM_SHEET
import com.infomaniak.mail.MatomoMail.OPEN_FROM_DRAFT_NAME
import com.infomaniak.mail.MatomoMail.trackAttachmentActionsEvent
import com.infomaniak.mail.MatomoMail.trackMessageActionsEvent
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.MatomoMail.trackThreadActionsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.AutoAdvanceMode
import com.infomaniak.mail.data.LocalSettings.ExternalContent
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.data.models.Attachable
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.SwissTransferFile
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.FragmentThreadBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.alertDialogs.EmailContextualMenuAlertDialog
import com.infomaniak.mail.ui.alertDialogs.InformationAlertDialog
import com.infomaniak.mail.ui.alertDialogs.LinkContextualMenuAlertDialog
import com.infomaniak.mail.ui.alertDialogs.PhoneContextualMenuAlertDialog
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.folder.TwoPaneFragment
import com.infomaniak.mail.ui.main.folder.TwoPaneViewModel
import com.infomaniak.mail.ui.main.folder.TwoPaneViewModel.NavData
import com.infomaniak.mail.ui.main.thread.SubjectFormatter.SubjectData
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.ContextMenuType
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.ThreadAdapterCallbacks
import com.infomaniak.mail.ui.main.thread.actions.AttachmentActionsBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.actions.MessageActionsBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.actions.ReplyBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.actions.ThreadActionsBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.calendar.AttendeesBottomSheetDialogArgs
import com.infomaniak.mail.utils.PermissionUtils
import com.infomaniak.mail.utils.UiUtils
import com.infomaniak.mail.utils.UiUtils.dividerDrawable
import com.infomaniak.mail.utils.extensions.AttachmentExtensions.openAttachment
import com.infomaniak.mail.utils.extensions.bindAlertToViewLifecycle
import com.infomaniak.mail.utils.extensions.changeToolbarColorOnScroll
import com.infomaniak.mail.utils.extensions.copyStringToClipboard
import com.infomaniak.mail.utils.extensions.deleteWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.getAttributeColor
import com.infomaniak.mail.utils.extensions.isAtTheTop
import com.infomaniak.mail.utils.extensions.isTabletInLandscape
import com.infomaniak.mail.utils.extensions.navigateToDownloadProgressDialog
import com.infomaniak.mail.utils.extensions.observeNotNull
import com.infomaniak.mail.utils.extensions.updateNavigationBarColor
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Sentry
import io.sentry.SentryLevel
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.roundToInt
import com.google.android.material.R as RMaterial

@AndroidEntryPoint
class ThreadFragment : Fragment() {

    @Inject
    lateinit var localSettings: LocalSettings

    @Inject
    lateinit var informationDialog: InformationAlertDialog

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    @Inject
    lateinit var linkContextualMenuAlertDialog: LinkContextualMenuAlertDialog

    @Inject
    lateinit var emailContextualMenuAlertDialog: EmailContextualMenuAlertDialog

    @Inject
    lateinit var phoneContextualMenuAlertDialog: PhoneContextualMenuAlertDialog

    @Inject
    lateinit var permissionUtils: PermissionUtils

    @Inject
    lateinit var subjectFormatter: SubjectFormatter

    @Inject
    lateinit var snackbarManager: SnackbarManager

    private var _binding: FragmentThreadBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView

    private val mainViewModel: MainViewModel by activityViewModels()
    private val twoPaneViewModel: TwoPaneViewModel by activityViewModels()
    private val threadViewModel: ThreadViewModel by viewModels()

    private val twoPaneFragment inline get() = parentFragment as TwoPaneFragment
    private val threadAdapter inline get() = binding.messagesList.adapter as ThreadAdapter
    private val isNotInSpam by lazy { mainViewModel.currentFolder.value?.role != FolderRole.SPAM }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentThreadBinding.inflate(inflater, container, false).also { _binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUi()
        setupAdapter()
        setupDialogs()
        permissionUtils.registerDownloadManagerPermission(fragment = this)

        observeLightThemeToggle()
        observeThreadLive()
        observeMessagesLive()
        observeFailedMessages()
        observeQuickActionBarClicks()
        observeSubjectUpdateTriggers()
        observeCurrentFolderName()

        observeThreadOpening()
        observeAutoAdvance()
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
    }

    private fun setupUi() = with(binding) {

        threadSubject.movementMethod = LinkMovementMethod.getInstance()

        updateNavigationIcon()
        toolbar.setNavigationOnClickListener { twoPaneViewModel.closeThread() }

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
            toolbar,
            messagesListNestedScrollView,
            shouldUpdateStatusBar = twoPaneFragment::isOnlyRightShown,
            otherUpdates = { color -> appBar.backgroundTintList = ColorStateList.valueOf(color) },
        )
    }

    private fun updateNavigationIcon() {
        binding.toolbar.apply {
            if (isTabletInLandscape()) {
                if (navigationIcon != null) navigationIcon = null
            } else {
                if (navigationIcon == null) setNavigationIcon(R.drawable.ic_navigation_default)
            }
        }
    }

    private fun setupAdapter() = with(threadViewModel) {

        binding.messagesList.adapter = ThreadAdapter(
            shouldLoadDistantResources = shouldLoadDistantResources(),
            threadAdapterState = object : ThreadAdapterState {
                override val isExpandedMap by threadState::isExpandedMap
                override val isThemeTheSameMap by threadState::isThemeTheSameMap
                override val hasSuperCollapsedBlockBeenClicked by threadState::hasSuperCollapsedBlockBeenClicked
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
                onDraftClicked = { message ->
                    trackNewMessageEvent(OPEN_FROM_DRAFT_NAME)
                    twoPaneViewModel.navigateToNewMessage(
                        arrivedFromExistingDraft = true,
                        draftLocalUuid = message.draftLocalUuid,
                        draftResource = message.draftResource,
                        messageUid = message.uid,
                    )
                },
                onDeleteDraftClicked = { message ->
                    trackMessageActionsEvent("deleteDraft")
                    mainViewModel.currentMailbox.value?.let { mailbox -> deleteDraft(message, mailbox) }
                },
                onAttachmentClicked = ::onAttachmentClicked,
                onAttachmentOptionsClicked = {
                    safeNavigate(
                        resId = R.id.attachmentActionsBottomSheetDialog,
                        args = AttachmentActionsBottomSheetDialogArgs(it.localUuid, it is SwissTransferFile).toBundle(),
                    )
                },
                onDownloadAllClicked = { message ->
                    trackAttachmentActionsEvent("downloadAll")
                    downloadAllAttachments(message)
                },
                onReplyClicked = { message ->
                    trackMessageActionsEvent(ACTION_REPLY_NAME)
                    replyTo(message)
                },
                onMenuClicked = { message -> message.navigateToActionsBottomSheet() },
                onAllExpandedMessagesLoaded = ::scrollToFirstUnseenMessage,
                onSuperCollapsedBlockClicked = ::expandSuperCollapsedBlock,
                navigateToAttendeeBottomSheet = { attendees ->
                    safeNavigate(
                        resId = R.id.attendeesBottomSheetDialog,
                        args = AttendeesBottomSheetDialogArgs(attendees.toTypedArray()).toBundle(),
                    )
                },
                navigateToNewMessageActivity = { twoPaneViewModel.navigateToNewMessage(mailToUri = it) },
                navigateToDownloadProgressDialog = { attachment, attachmentIntentType ->
                    navigateToDownloadProgressDialog(attachment, attachmentIntentType, ThreadFragment::class.java.name)
                },
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
            ),
        )

        binding.messagesList.addItemDecoration(
            DividerItemDecorator(
                divider = InsetDrawable(dividerDrawable(requireContext()), 0),
                shouldIgnoreView = { view -> view.tag == UiUtils.IGNORE_DIVIDER_TAG },
            ),
        )

        binding.messagesList.recycledViewPool.setMaxRecycledViews(0, 0)
        threadAdapter.stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    private fun onAttachmentClicked(attachable: Attachable) {
        when (attachable) {
            is Attachment -> {
                trackAttachmentActionsEvent(ACTION_OPEN_NAME)
                attachable.openAttachment(
                    context = requireContext(),
                    navigateToDownloadProgressDialog = { attachment, attachmentIntentType ->
                        navigateToDownloadProgressDialog(
                            attachment,
                            attachmentIntentType,
                            ThreadFragment::class.java.name,
                        )
                    },
                    snackbarManager = snackbarManager,
                )
            }
            is SwissTransferFile -> {
                trackAttachmentActionsEvent("openSwissTransfer")
                downloadSwissTransferFile(swissTransferFile = attachable)
            }
        }
    }

    private fun setupDialogs() {
        bindAlertToViewLifecycle(descriptionDialog)
        linkContextualMenuAlertDialog.initValues(snackbarManager)
        emailContextualMenuAlertDialog.initValues(snackbarManager)
        phoneContextualMenuAlertDialog.initValues(snackbarManager)
    }

    private fun observeThreadOpening() = with(threadViewModel) {

        twoPaneViewModel.currentThreadUid.distinctUntilChanged().observeNotNull(viewLifecycleOwner) { threadUid ->

            displayThreadView()

            openThread(threadUid).observe(viewLifecycleOwner) { thread ->

                if (thread == null) {
                    twoPaneViewModel.closeThread()
                    return@observe
                }

                initUi(threadUid, folderRole = mainViewModel.getActionFolderRole(thread))

                reassignThreadLive(threadUid)
                reassignMessagesLive(threadUid)
            }
        }
    }

    private fun observeLightThemeToggle() {
        mainViewModel.toggleLightThemeForMessage.observe(viewLifecycleOwner, threadAdapter::toggleLightMode)
    }

    private fun observeThreadLive() {

        threadViewModel.threadLive.observe(viewLifecycleOwner) { thread ->

            if (thread == null) {
                twoPaneViewModel.closeThread()
                return@observe
            }

            binding.iconFavorite.apply {
                setIconResource(if (thread.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star)
                val color = if (thread.isFavorite) {
                    context.getColor(R.color.favoriteYellow)
                } else {
                    context.getAttributeColor(RMaterial.attr.colorPrimary)
                }
                iconTint = ColorStateList.valueOf(color)
            }
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

    private fun observeFailedMessages() {
        threadViewModel.failedMessagesUids.observe(viewLifecycleOwner, threadAdapter::updateFailedMessages)
    }

    private fun observeQuickActionBarClicks() {
        threadViewModel.quickActionBarClicks.observe(viewLifecycleOwner) { (threadUid, lastMessageToReplyTo, menuId) ->
            when (menuId) {
                R.id.quickActionReply -> replyTo(lastMessageToReplyTo)
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
                            messageUidToReplyTo = lastMessageToReplyTo.uid,
                        ).toBundle(),
                    )
                }
            }
        }
    }

    private fun observeSubjectUpdateTriggers() = with(binding) {
        threadViewModel.assembleSubjectData(mainViewModel.mergedContactsLive).observe(viewLifecycleOwner) { result ->

            val (subjectWithoutTags, subjectWithTags) = subjectFormatter.generateSubjectContent(
                subjectData = SubjectData(
                    thread = result.thread ?: return@observe,
                    emailDictionary = result.mergedContacts ?: emptyMap(),
                    aliases = result.mailbox?.aliases ?: emptyList(),
                    externalMailFlagEnabled = result.mailbox?.externalMailFlagEnabled ?: false,
                    trustedDomains = result.mailbox?.trustedDomains ?: emptyList(),
                ),
            ) { description ->
                informationDialog.show(
                    title = R.string.externalDialogTitleExpeditor,
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

    private fun observeAutoAdvance() {
        mainViewModel.autoAdvanceThreadsUids.observe(viewLifecycleOwner, ::tryToAutoAdvance)
    }

    private fun displayThreadView() = with(binding) {
        emptyView.isGone = true
        threadView.isVisible = true
    }

    private fun initUi(threadUid: String, folderRole: FolderRole?) = with(binding) {

        if (twoPaneFragment.isOnlyOneShown()) {
            requireActivity().window.updateNavigationBarColor(context.getColor(R.color.elevatedBackground))
        }

        iconFavorite.setOnClickListener {
            trackThreadActionsEvent(ACTION_FAVORITE_NAME, threadViewModel.threadLive.value!!.isFavorite)
            mainViewModel.toggleThreadFavoriteStatus(threadUid)
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
                    trackThreadActionsEvent(ACTION_REPLY_NAME)
                    threadViewModel.clickOnQuickActionBar(menuId)
                }
                R.id.quickActionForward -> {
                    trackThreadActionsEvent(ACTION_FORWARD_NAME)
                    threadViewModel.clickOnQuickActionBar(menuId)
                }
                R.id.quickActionArchive -> {
                    trackThreadActionsEvent(ACTION_ARCHIVE_NAME, isFromArchive)
                    mainViewModel.archiveThread(threadUid)
                }
                R.id.quickActionDelete -> {
                    descriptionDialog.deleteWithConfirmationPopup(folderRole, count = 1) {
                        trackThreadActionsEvent(ACTION_DELETE_NAME)
                        mainViewModel.deleteThread(threadUid)
                    }
                }
                R.id.quickActionMenu -> {
                    trackThreadActionsEvent(OPEN_ACTION_BOTTOM_SHEET)
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
            ).toBundle(),
        )
    }

    private fun scrollToFirstUnseenMessage() = with(threadViewModel) {

        fun getBottomY(): Int = binding.messagesListNestedScrollView.maxScrollAmount

        val scrollY = threadState.verticalScroll ?: run {

            val indexToScroll = threadAdapter.items.indexOfFirst { it is Message && threadState.isExpandedMap[it.uid] == true }

            // If no Message is expanded (e.g. the last Message of the Thread is a Draft),
            // we want to automatically scroll to the very bottom.
            if (indexToScroll == -1) {
                getBottomY()
            } else {
                val targetChild = binding.messagesList.getChildAt(indexToScroll)
                if (targetChild == null) {
                    Sentry.withScope { scope ->
                        scope.setExtra("indexToScroll", indexToScroll.toString())
                        scope.setExtra("messageCount", threadAdapter.items.count().toString())
                        scope.setExtra("isExpandedMap", threadState.isExpandedMap.toString())
                        scope.setExtra("isLastMessageDraft", (threadAdapter.items.lastOrNull() as Message?)?.isDraft.toString())
                        Sentry.captureMessage(
                            "Target child for scroll in ThreadFragment is null. Fallback to scrolling to bottom",
                            SentryLevel.ERROR,
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

    private fun expandSuperCollapsedBlock() = with(threadViewModel) {
        threadState.hasSuperCollapsedBlockBeenClicked = true
        reassignMessagesLive(twoPaneViewModel.currentThreadUid.value!!)
    }

    private fun shouldLoadDistantResources(messageUid: String): Boolean {
        val isMessageSpecificallyAllowed = threadAdapter.isMessageUidManuallyAllowed(messageUid)
        return (isMessageSpecificallyAllowed && isNotInSpam) || shouldLoadDistantResources()
    }

    private fun shouldLoadDistantResources(): Boolean = localSettings.externalContent == ExternalContent.ALWAYS && isNotInSpam

    fun getAnchor(): View? = _binding?.quickActionBar

    fun isScrolledToTheTop(): Boolean? = _binding?.messagesListNestedScrollView?.isAtTheTop()

    private fun safeNavigate(@IdRes resId: Int, args: Bundle) {
        twoPaneViewModel.navArgs.value = NavData(resId, args)
    }

    private fun tryToAutoAdvance(listThreadUids: List<String>) = with(twoPaneFragment.threadListAdapter) {
        if (!listThreadUids.contains(openedThreadUid)) return@with

        openedThreadPosition?.let {
            val data = getNextThreadToOpenByPosition(it)

            data?.let { (nextThread, index) ->
                if (nextThread.uid != openedThreadUid && !nextThread.isOnlyOneDraft) {
                    selectNewThread(newPosition = index, nextThread.uid)
                }

                twoPaneFragment.navigateToThread(nextThread)
            } ?: run {
                twoPaneViewModel.closeThread()
            }
        }
    }

    private fun getNextThreadToOpenByPosition(
        startingThreadIndex: Int,
    ): Pair<Thread, Int>? = with(twoPaneFragment.threadListAdapter) {

        val direction = when (localSettings.autoAdvanceMode) {
            AutoAdvanceMode.PREVIOUS_THREAD -> PREVIOUS_CHRONOLOGICAL_THREAD
            AutoAdvanceMode.FOLLOWING_THREAD -> NEXT_CHRONOLOGICAL_THREAD
            AutoAdvanceMode.THREADS_LIST -> null
            AutoAdvanceMode.NATURAL_THREAD -> {
                if (localSettings.autoAdvanceNaturalThread == AutoAdvanceMode.PREVIOUS_THREAD) {
                    PREVIOUS_CHRONOLOGICAL_THREAD
                } else {
                    NEXT_CHRONOLOGICAL_THREAD
                }
            }
        }

        return direction?.let { getNextThread(startingThreadIndex, direction) }
    }

    enum class HeaderState {
        ELEVATED,
        LOWERED,
    }

    companion object {
        private const val COLLAPSE_TITLE_THRESHOLD = 0.5
        private const val ARCHIVE_INDEX = 2

        private const val PREVIOUS_CHRONOLOGICAL_THREAD = -1
        private const val NEXT_CHRONOLOGICAL_THREAD = 1

        private const val MAXIMUM_SUBJECT_LENGTH = 30

        private fun allAttachmentsFileName(subject: String) = "infomaniak-mail-attachments-$subject.zip"
        private fun allSwissTransferFilesName(subject: String) = "infomaniak-mail-swisstransfer-$subject.zip"
    }
}
