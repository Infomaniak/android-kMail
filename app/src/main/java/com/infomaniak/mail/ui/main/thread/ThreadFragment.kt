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
package com.infomaniak.mail.ui.main.thread

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.text.toSpannable
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import com.infomaniak.lib.core.utils.*
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
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.FragmentThreadBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivityArgs
import com.infomaniak.mail.ui.main.thread.ThreadViewModel.OpenThreadResult
import com.infomaniak.mail.ui.main.thread.actions.DownloadAttachmentProgressDialog
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.AlertDialogUtils.createInformationDialog
import com.infomaniak.mail.utils.AlertDialogUtils.showWithDescription
import com.infomaniak.mail.utils.ExternalUtils.findExternalRecipients
import com.infomaniak.mail.utils.UiUtils.dividerDrawable
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.roundToInt
import com.google.android.material.R as RMaterial

@AndroidEntryPoint
class ThreadFragment : Fragment() {

    @Inject
    lateinit var localSettings: LocalSettings

    private lateinit var binding: FragmentThreadBinding
    private val navigationArgs: ThreadFragmentArgs by navArgs()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val threadViewModel: ThreadViewModel by viewModels()

    private val threadAdapter by lazy { ThreadAdapter(shouldLoadDistantResources()) }
    private val permissionUtils by lazy { PermissionUtils(this) }
    private val isNotInSpam by lazy { mainViewModel.currentFolder.value?.role != FolderRole.SPAM }
    private val externalExpeditorInfoDialog by lazy {
        createInformationDialog(
            title = getString(R.string.externalDialogTitleExpeditor),
            confirmButtonText = R.string.externalDialogConfirmButton,
        )
    }

    private var isFavorite = false

    // When opening the Thread, we want to scroll to the last Message, but only once.
    private var isFirstVisit = AtomicBoolean(true)

    // TODO: Remove this when Realm doesn't broadcast twice when deleting a Thread anymore.
    private var isFirstTimeLeaving = AtomicBoolean(true)

    override fun onConfigurationChanged(newConfig: Configuration) {
        threadAdapter.reRenderMails()
        super.onConfigurationChanged(newConfig)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentThreadBinding.inflate(inflater, container, false).also {
            binding = it
            requireActivity().window.statusBarColor = requireContext().getColor(R.color.backgroundColor)
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeThreadLive()

        threadViewModel.openThread().observe(viewLifecycleOwner) { result ->

            if (result == null) {
                leaveThread()
                return@observe
            }

            setupUi(folderRole = mainViewModel.getActionFolderRole(result.thread))
            setupAdapter(result)

            observeMessagesLive()
            observeFailedMessages()
            observeContacts()
            observeQuickActionBarClicks()
            observeOpenAttachment()
            observerSubjectUpdateTriggers()
        }

        permissionUtils.registerDownloadManagerPermission()
        mainViewModel.toggleLightThemeForMessage.observe(viewLifecycleOwner, threadAdapter::toggleLightMode)
    }

    private fun observerSubjectUpdateTriggers() {
        threadViewModel.assembleSubjectData(mainViewModel.mergedContactsLive).observe(viewLifecycleOwner) { result ->
            result.thread?.let {
                setSubject(
                    thread = it,
                    emailDictionary = result.mergedContacts ?: emptyMap(),
                    aliases = result.mailbox?.aliases ?: emptyList(),
                    externalMailFlagEnabled = result.mailbox?.externalMailFlagEnabled ?: false,
                )
            }
        }
    }

    private fun setupUi(folderRole: FolderRole?) = with(binding) {

        toolbar.setNavigationOnClickListener { leaveThread() }

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

        changeToolbarColorOnScroll(toolbar, messagesListNestedScrollView) { color ->
            appBar.backgroundTintList = ColorStateList.valueOf(color)
        }

        iconFavorite.setOnClickListener {
            trackThreadActionsEvent(ACTION_FAVORITE_NAME, isFavorite)
            mainViewModel.toggleThreadFavoriteStatus(navigationArgs.threadUid)
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
                R.id.quickActionArchive -> with(mainViewModel) {
                    trackThreadActionsEvent(ACTION_ARCHIVE_NAME, isFromArchive)
                    archiveThread(navigationArgs.threadUid)
                }
                R.id.quickActionDelete -> {
                    deleteThreadDialog = deleteWithConfirmationPopup(folderRole, count = 1) {
                        trackThreadActionsEvent(ACTION_DELETE_NAME)
                        mainViewModel.deleteThread(navigationArgs.threadUid)
                    }
                }
                R.id.quickActionMenu -> {
                    trackThreadActionsEvent(OPEN_ACTION_BOTTOM_SHEET)
                    threadViewModel.clickOnQuickActionBar(menuId)
                }
            }
        }
    }

    private fun observeQuickActionBarClicks() {
        threadViewModel.quickActionBarClicks.observe(viewLifecycleOwner) { (lastMessageToReplyTo, menuId) ->
            when (menuId) {
                R.id.quickActionReply -> replyTo(lastMessageToReplyTo)
                R.id.quickActionForward -> {
                    safeNavigateToNewMessageActivity(
                        draftMode = DraftMode.FORWARD,
                        previousMessageUid = lastMessageToReplyTo.uid,
                        shouldLoadDistantResources = shouldLoadDistantResources(lastMessageToReplyTo.uid),
                    )
                }
                R.id.quickActionMenu -> {
                    safeNavigate(
                        ThreadFragmentDirections.actionThreadFragmentToThreadActionsBottomSheetDialog(
                            threadUid = navigationArgs.threadUid,
                            messageUidToReplyTo = lastMessageToReplyTo.uid,
                            shouldLoadDistantResources = shouldLoadDistantResources(lastMessageToReplyTo.uid),
                        )
                    )
                }
            }
        }
    }

    private fun shouldLoadDistantResources(messageUid: String): Boolean {
        val isMessageSpecificallyAllowed = threadAdapter.isMessageUidManuallyAllowed(messageUid)
        return (isMessageSpecificallyAllowed && isNotInSpam) || shouldLoadDistantResources()
    }

    private fun shouldLoadDistantResources(): Boolean = localSettings.externalContent == ExternalContent.ALWAYS && isNotInSpam

    private fun observeOpenAttachment() {
        getBackNavigationResult(DownloadAttachmentProgressDialog.OPEN_WITH, ::startActivity)
    }

    private fun setupAdapter(result: OpenThreadResult) = with(binding) {

        messagesList.addItemDecoration(DividerItemDecorator(InsetDrawable(dividerDrawable(context), 0)))
        messagesList.recycledViewPool.setMaxRecycledViews(0, 0)

        messagesList.adapter = threadAdapter.apply {

            isExpandedMap = result.isExpandedMap
            isThemeTheSameMap = result.isThemeTheSameMap

            stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
            contacts = mainViewModel.mergedContactsLive.value ?: emptyMap()

            onContactClicked = { contact ->
                safeNavigate(ThreadFragmentDirections.actionThreadFragmentToDetailedContactBottomSheetDialog(contact))
            }

            onDraftClicked = { message ->
                trackNewMessageEvent(OPEN_FROM_DRAFT_NAME)
                safeNavigateToNewMessageActivity(
                    NewMessageActivityArgs(
                        arrivedFromExistingDraft = true,
                        draftLocalUuid = message.draftLocalUuid,
                        draftResource = message.draftResource,
                        messageUid = message.uid,
                    ).toBundle(),
                )
            }

            onDeleteDraftClicked = { message ->
                trackMessageActionsEvent("deleteDraft")
                mainViewModel.currentMailbox.value?.let { mailbox -> threadViewModel.deleteDraft(message, mailbox) }
            }

            onAttachmentClicked = { attachment ->
                if (attachment.openWithIntent(requireContext()).hasSupportedApplications(requireContext())) {
                    trackAttachmentActionsEvent("open")
                    attachment.display()
                } else {
                    trackAttachmentActionsEvent("download")
                    mainViewModel.snackBarManager.setValue(getString(R.string.snackbarDownloadInProgress))
                    scheduleDownloadManager(attachment.downloadUrl, attachment.name)
                }
            }

            onDownloadAllClicked = { message ->
                trackAttachmentActionsEvent("downloadAll")
                mainViewModel.snackBarManager.setValue(getString(R.string.snackbarDownloadInProgress))
                downloadAllAttachments(message)
            }

            onReplyClicked = { message ->
                trackMessageActionsEvent(ACTION_REPLY_NAME)
                replyTo(message)
            }

            onMenuClicked = { message ->
                message.navigateToActionsBottomSheet()
            }

            navigateToNewMessageActivity = { uri ->
                safeNavigateToNewMessageActivity(NewMessageActivityArgs(mailToUri = uri).toBundle())
            }
        }
    }

    private fun Message.navigateToActionsBottomSheet() {
        safeNavigate(
            ThreadFragmentDirections.actionThreadFragmentToMessageActionsBottomSheetDialog(
                messageUid = uid,
                threadUid = navigationArgs.threadUid,
                isFavorite = isFavorite,
                isSeen = isSeen,
                isThemeTheSame = threadAdapter.isThemeTheSameMap[uid]!!,
                shouldLoadDistantResources = shouldLoadDistantResources(uid),
            )
        )
    }

    private fun scheduleDownloadManager(downloadUrl: String, filename: String) {

        fun scheduleDownloadManager() = threadViewModel.scheduleDownload(downloadUrl, filename)

        if (permissionUtils.hasDownloadManagerPermission) {
            scheduleDownloadManager()
        } else {
            permissionUtils.requestDownloadManagerPermission { scheduleDownloadManager() }
        }
    }

    private fun Attachment.display() {
        if (hasUsableCache(requireContext()) || isInlineCachedFile(requireContext())) {
            startActivity(openWithIntent(requireContext()))
        } else {
            safeNavigate(
                ThreadFragmentDirections.actionThreadFragmentToDownloadAttachmentProgressDialog(
                    attachmentResource = resource!!,
                    attachmentName = name,
                    attachmentType = getFileTypeFromMimeType(),
                )
            )
        }
    }

    private fun replyTo(message: Message) {
        if (message.getRecipientsForReplyTo(true).second.isEmpty()) {
            safeNavigateToNewMessageActivity(
                draftMode = DraftMode.REPLY,
                previousMessageUid = message.uid,
                shouldLoadDistantResources = shouldLoadDistantResources(message.uid),
            )
        } else {
            safeNavigate(
                ThreadFragmentDirections.actionThreadFragmentToReplyBottomSheetDialog(
                    messageUid = message.uid,
                    shouldLoadDistantResources = shouldLoadDistantResources(message.uid),
                )
            )
        }
    }

    private fun observeThreadLive() {
        threadViewModel.threadLive.observe(viewLifecycleOwner, ::onThreadUpdate)
    }

    private fun observeMessagesLive() {

        threadViewModel.messagesLive.observe(viewLifecycleOwner) { messages ->
            SentryLog.i("UI", "Received ${messages.count()} messages")

            if (messages.isEmpty()) {
                mainViewModel.deletedMessages.value = threadViewModel.deletedMessagesUids
                leaveThread()
                return@observe
            }

            threadViewModel.fetchMessagesHeavyData(messages)
            threadAdapter.submitList(messages)
            scrollToFirstUnseenMessage(messages.count())
        }
    }

    private fun observeFailedMessages() {
        threadViewModel.failedMessagesUids.observe(viewLifecycleOwner, threadAdapter::updateFailedMessages)
    }

    private fun scrollToFirstUnseenMessage(messagesCount: Int) {
        val shouldScrollToFirstUnseenMessage = isFirstVisit.compareAndSet(true, false) && messagesCount > 1
        if (shouldScrollToFirstUnseenMessage) onRecyclerViewLaidOut(::scrollToFirstUnseenMessage)
    }

    private fun onRecyclerViewLaidOut(callback: () -> Unit) = with(binding) {
        messagesList.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    callback()
                    messagesList.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        )
    }

    private fun scrollToFirstUnseenMessage() = with(binding) {
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

    private fun scrollToBottom() = with(binding) {
        messagesListNestedScrollView.scrollY = messagesListNestedScrollView.maxScrollAmount
    }

    private fun observeContacts() {
        mainViewModel.mergedContactsLive.observeNotNull(viewLifecycleOwner, threadAdapter::updateContacts)
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

    private fun onThreadUpdate(thread: Thread?) = with(binding) {

        if (thread == null) {
            leaveThread()
            return@with
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

        isFavorite = thread.isFavorite
    }

    private fun setSubject(
        thread: Thread,
        emailDictionary: MergedContactDictionary,
        aliases: List<String>,
        externalMailFlagEnabled: Boolean,
    ) = with(binding) {
        val (subject, spannedSubject) = computeSubject(thread, emailDictionary, aliases, externalMailFlagEnabled)
        threadSubject.text = spannedSubject
        toolbarSubject.text = subject
    }

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

        val externalPostfix = getString(R.string.externalTag)
        val postfixedSubject = "${subject}${EXTERNAL_TAG_SEPARATOR}${externalPostfix}"

        val spannedSubject = postfixedSubject.toSpannable().apply {
            val startIndex = subject.length + EXTERNAL_TAG_SEPARATOR.length
            val endIndex = startIndex + externalPostfix.length

            setExternalTagSpan(startIndex, endIndex)

            setClickableSpan(startIndex, endIndex) {
                trackExternalEvent("threadTag")

                val description = resources.getQuantityString(
                    R.plurals.externalDialogDescriptionExpeditor,
                    externalRecipientQuantity,
                    externalRecipientEmail,
                )

                externalExpeditorInfoDialog.showWithDescription(description)
            }
        }

        return subject to spannedSubject
    }

    private fun Spannable.setExternalTagSpan(startIndex: Int, endIndex: Int) = with(binding) {
        val backgroundColor = context.getColor(R.color.externalTagBackground)
        val textColor = context.getColor(R.color.externalTagOnBackground)
        val textTypeface = ResourcesCompat.getFont(context, R.font.external_tag_font)!!
        val textSize = resources.getDimensionPixelSize(R.dimen.externalTagTextSize).toFloat()
        setSpan(
            RoundedBackgroundSpan(
                backgroundColor = backgroundColor,
                textColor = textColor,
                textTypeface = textTypeface,
                fontSize = textSize,
            ),
            startIndex,
            endIndex,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }

    private fun Spannable.setClickableSpan(startIndex: Int, endIndex: Int, onClick: () -> Unit) {
        // TODO: Currently, the clickable zone extends beyond the span up to the edge of the textview.
        //  This is the same comportment that Gmail has.
        //  See if we can find a fix for this later.
        setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) = onClick()
            },
            startIndex,
            endIndex,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }

    private fun leaveThread() {
        // TODO: Realm broadcasts twice when the Thread is deleted.
        //  We don't know why.
        //  While it's not fixed, we do this quickfix of checking if we already left:
        if (isFirstTimeLeaving.compareAndSet(true, false)) {
            findNavController().popBackStack()
        }
    }

    enum class HeaderState {
        ELEVATED,
        LOWERED,
    }

    private companion object {
        const val COLLAPSE_TITLE_THRESHOLD = 0.5
        const val ARCHIVE_INDEX = 2
        const val EXTERNAL_TAG_SEPARATOR = " "

        fun allAttachmentsFileName(subject: String) = "infomaniak-mail-attachments-$subject.zip"
    }
}
