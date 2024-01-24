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
import com.infomaniak.mail.MatomoMail.ACTION_REPLY_NAME
import com.infomaniak.mail.MatomoMail.OPEN_ACTION_BOTTOM_SHEET
import com.infomaniak.mail.MatomoMail.OPEN_FROM_DRAFT_NAME
import com.infomaniak.mail.MatomoMail.trackAttachmentActionsEvent
import com.infomaniak.mail.MatomoMail.trackExternalEvent
import com.infomaniak.mail.MatomoMail.trackMessageActionsEvent
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.MatomoMail.trackThreadActionsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.ExternalContent
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.FragmentThreadBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.*
import com.infomaniak.mail.ui.main.SnackBarManager
import com.infomaniak.mail.ui.main.folder.TwoPaneFragment
import com.infomaniak.mail.ui.main.folder.TwoPaneViewModel
import com.infomaniak.mail.ui.main.folder.TwoPaneViewModel.NavData
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.ContextMenuType
import com.infomaniak.mail.ui.main.thread.ThreadViewModel.OpenThreadResult
import com.infomaniak.mail.ui.main.thread.actions.AttachmentActionsBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.actions.MessageActionsBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.actions.ReplyBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.actions.ThreadActionsBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.calendar.AttendeesBottomSheetDialogArgs
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.ExternalUtils.findExternalRecipients
import com.infomaniak.mail.utils.UiUtils.dividerDrawable
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
    lateinit var snackBarManager: SnackBarManager

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
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        threadAdapter.reRenderMails()
        super.onConfigurationChanged(newConfig)
        updateNavigationIcon()
    }

    override fun onDestroyView() {
        threadAdapter.resetCallbacks()
        super.onDestroyView()
        _binding = null
    }

    private fun setupUi() = with(binding) {

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
            if (canDisplayBothPanes()) {
                if (navigationIcon != null) navigationIcon = null
            } else {
                if (navigationIcon == null) setNavigationIcon(R.drawable.ic_navigation_default)
            }
        }
    }

    private fun setupAdapter() = with(binding.messagesList) {
        adapter = ThreadAdapter(
            shouldLoadDistantResources = shouldLoadDistantResources(),
            onContactClicked = {
                safeNavigate(
                    resId = R.id.detailedContactBottomSheetDialog,
                    args = DetailedContactBottomSheetDialogArgs(it).toBundle(),
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
                mainViewModel.currentMailbox.value?.let { mailbox -> threadViewModel.deleteDraft(message, mailbox) }
            },
            onAttachmentClicked = { attachment ->
                attachment.resource?.let { resource ->
                    safeNavigate(
                        resId = R.id.attachmentActionsBottomSheetDialog,
                        args = AttachmentActionsBottomSheetDialogArgs(resource).toBundle(),
                    )
                }
            },
            onDownloadAllClicked = { message ->
                trackAttachmentActionsEvent("downloadAll")
                downloadAllAttachments(message)
            },
            onReplyClicked = { message ->
                trackMessageActionsEvent(ACTION_REPLY_NAME)
                replyTo(message)
            },
            onMenuClicked = { message ->
                message.navigateToActionsBottomSheet()
            },
            onAllExpandedMessagesLoaded = ::scrollToFirstUnseenMessage,
            navigateToAttendeeBottomSheet = { attendees ->
                safeNavigate(
                    resId = R.id.attendeesBottomSheetDialog,
                    args = AttendeesBottomSheetDialogArgs(attendees.toTypedArray()).toBundle(),
                )
            },
            navigateToNewMessageActivity = { twoPaneViewModel.navigateToNewMessage(mailToUri = it) },
            promptLink = { data, type ->
                // When adding a phone number to contacts, Google decodes this value in case it's url-encoded. But I could not
                // reproduce this issue when manually creating a url-encoded href. If this is triggered, fix it by also
                // decoding it at that step.
                if (type == ContextMenuType.PHONE && data.contains('%')) Sentry.withScope { scope ->
                    scope.level = SentryLevel.ERROR
                    Sentry.captureMessage("Google was right, phone numbers can be url-encoded. Needs to be fixed")
                }

                when (type) {
                    ContextMenuType.LINK -> linkContextualMenuAlertDialog.show(data)
                    ContextMenuType.EMAIL -> emailContextualMenuAlertDialog.show(data)
                    ContextMenuType.PHONE -> phoneContextualMenuAlertDialog.show(data)
                }
            },
        )

        addItemDecoration(DividerItemDecorator(InsetDrawable(dividerDrawable(context), 0)))
        recycledViewPool.setMaxRecycledViews(0, 0)
        threadAdapter.stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    private fun setupDialogs() {
        bindAlertToViewLifecycle(descriptionDialog)
        linkContextualMenuAlertDialog.initValues(snackBarManager)
        emailContextualMenuAlertDialog.initValues(snackBarManager)
        phoneContextualMenuAlertDialog.initValues(snackBarManager)
    }

    private fun observeThreadOpening() = with(threadViewModel) {

        twoPaneViewModel.currentThreadUid.distinctUntilChanged().observeNotNull(viewLifecycleOwner) { threadUid ->

            displayThreadView()

            reassignThreadLive(threadUid)
            reassignMessagesLive(threadUid)

            openThread(threadUid).observe(viewLifecycleOwner) { result ->

                if (result == null) {
                    twoPaneViewModel.closeThread()
                    return@observe
                }

                initUi(threadUid, folderRole = mainViewModel.getActionFolderRole(result.thread))
                initAdapter(result)
            }
        }
    }

    private fun observeLightThemeToggle() {
        mainViewModel.toggleLightThemeForMessage.observe(viewLifecycleOwner, threadAdapter::toggleLightMode)
    }

    private fun observeThreadLive() = with(binding) {

        threadViewModel.threadLive.observe(viewLifecycleOwner) { thread ->

            if (thread == null) {
                twoPaneViewModel.closeThread()
                return@observe
            }

            threadSubject.movementMethod = LinkMovementMethod.getInstance()

            iconFavorite.apply {
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

        messagesLive.observe(viewLifecycleOwner) { messages ->

            SentryLog.i("UI", "Received ${messages.count()} messages")

            if (messages.isEmpty()) {
                mainViewModel.deletedMessages.value = deletedMessagesUids
                twoPaneViewModel.closeThread()
                return@observe
            }

            fetchMessagesHeavyData(messages)
            threadAdapter.submitList(messages)
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

            val (subject, spannedSubject) = computeSubject(
                thread = result.thread ?: return@observe,
                emailDictionary = result.mergedContacts ?: emptyMap(),
                aliases = result.mailbox?.aliases ?: emptyList(),
                externalMailFlagEnabled = result.mailbox?.externalMailFlagEnabled ?: false,
            )

            threadSubject.text = spannedSubject
            toolbarSubject.text = subject

            threadSubject.setOnLongClickListener {
                context.copyStringToClipboard(subject, R.string.snackbarSubjectCopiedToClipboard, snackBarManager)
                true
            }
            toolbarSubject.setOnLongClickListener {
                context.copyStringToClipboard(subject, R.string.snackbarSubjectCopiedToClipboard, snackBarManager)
                true
            }
        }
    }

    private fun observeCurrentFolderName() {
        twoPaneViewModel.rightPaneFolderName.observe(viewLifecycleOwner) {
            binding.emptyView.title = getString(R.string.noConversationSelected, it)
        }
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

    private fun initAdapter(result: OpenThreadResult) {
        threadAdapter.apply {
            isExpandedMap = result.isExpandedMap
            initialSetOfExpandedMessagesUids = result.initialSetOfExpandedMessagesUids
            isThemeTheSameMap = result.isThemeTheSameMap
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
        val url = ApiRoutes.downloadAttachments(
            mailboxUuid = mainViewModel.currentMailbox.value?.uuid ?: return,
            folderId = message.folderId,
            shortUid = message.shortUid,
        )
        val truncatedSubject = message.subject?.let { it.substring(0..min(30, it.lastIndex)) }
        val name = allAttachmentsFileName(truncatedSubject ?: "")
        scheduleDownloadManager(url, name)
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
                isThemeTheSame = threadAdapter.isThemeTheSameMap[uid] ?: return,
                shouldLoadDistantResources = shouldLoadDistantResources(uid),
            ).toBundle(),
        )
    }

    private fun scrollToFirstUnseenMessage() = with(binding) {

        fun scrollToBottom() {
            messagesListNestedScrollView.scrollY = messagesListNestedScrollView.maxScrollAmount
        }

        val indexToScroll = threadAdapter.messages.indexOfFirst { threadAdapter.isExpandedMap[it.uid] == true }

        // If no Message is expanded (e.g. the last Message of the Thread is a Draft),
        // we want to automatically scroll to the very bottom.
        if (indexToScroll == -1) {
            scrollToBottom()
        } else {
            val targetChild = messagesList.getChildAt(indexToScroll)
            if (targetChild == null) {
                Sentry.withScope { scope ->
                    scope.level = SentryLevel.WARNING
                    scope.setExtra("indexToScroll", indexToScroll.toString())
                    scope.setExtra("messageCount", threadAdapter.messages.count().toString())
                    scope.setExtra("isExpandedMap", threadAdapter.isExpandedMap.toString())
                    scope.setExtra("isLastMessageDraft", threadAdapter.messages.lastOrNull()?.isDraft.toString())
                    Sentry.captureMessage("Target child for scroll in ThreadFragment is null. Fallback to scrolling to bottom")
                }
                scrollToBottom()
            } else {
                messagesListNestedScrollView.scrollY = targetChild.top
            }
        }
    }

    private fun shouldLoadDistantResources(messageUid: String): Boolean {
        val isMessageSpecificallyAllowed = threadAdapter.isMessageUidManuallyAllowed(messageUid)
        return (isMessageSpecificallyAllowed && isNotInSpam) || shouldLoadDistantResources()
    }

    private fun shouldLoadDistantResources(): Boolean = localSettings.externalContent == ExternalContent.ALWAYS && isNotInSpam

    private fun computeSubject(
        thread: Thread,
        emailDictionary: MergedContactDictionary,
        aliases: List<String>,
        externalMailFlagEnabled: Boolean,
    ): Pair<String, CharSequence> = with(binding) {
        val subject = context.formatSubject(thread.subject)
        if (!externalMailFlagEnabled) return subject to subject

        val (externalRecipientEmail, externalRecipientQuantity) = thread.findExternalRecipients(emailDictionary, aliases)
        if (externalRecipientQuantity == 0) return subject to subject

        val spannedSubject = requireContext().postfixWithTag(
            subject,
            R.string.externalTag,
            R.color.externalTagBackground,
            R.color.externalTagOnBackground,
        ) {
            trackExternalEvent("threadTag")

            val description = resources.getQuantityString(
                R.plurals.externalDialogDescriptionExpeditor,
                externalRecipientQuantity,
                externalRecipientEmail,
            )

            informationDialog.show(
                title = R.string.externalDialogTitleExpeditor,
                description = description,
                confirmButtonText = R.string.externalDialogConfirmButton,
            )
        }

        return subject to spannedSubject
    }

    fun getAnchor(): View? = _binding?.quickActionBar

    private fun safeNavigate(@IdRes resId: Int, args: Bundle) {
        twoPaneViewModel.navArgs.value = NavData(resId, args)
    }

    enum class HeaderState {
        ELEVATED,
        LOWERED,
    }

    companion object {
        private const val COLLAPSE_TITLE_THRESHOLD = 0.5
        private const val ARCHIVE_INDEX = 2

        private fun allAttachmentsFileName(subject: String) = "infomaniak-mail-attachments-$subject.zip"
    }
}
