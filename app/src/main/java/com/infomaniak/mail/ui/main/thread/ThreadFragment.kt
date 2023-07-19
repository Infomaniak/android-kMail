/*
 * Infomaniak ikMail - Android
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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
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
import com.infomaniak.mail.ui.main.thread.actions.DownloadAttachmentProgressDialog
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.RealmChangesBinding.Companion.bindResultsChangeToAdapter
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.roundToInt
import com.google.android.material.R as RMaterial

@AndroidEntryPoint
class ThreadFragment : Fragment() {

    private lateinit var binding: FragmentThreadBinding
    private val navigationArgs: ThreadFragmentArgs by navArgs()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val threadViewModel: ThreadViewModel by viewModels()

    private val alwaysShowExternalContent by lazy {
        LocalSettings.getInstance(requireContext()).externalContent == ExternalContent.ALWAYS
    }

    private val threadAdapter by lazy { ThreadAdapter(shouldLoadDistantResources()) }
    private val permissionUtils by lazy { PermissionUtils(this) }
    private val isNotInSpam by lazy { mainViewModel.currentFolder.value?.role != FolderRole.SPAM }

    private var isFavorite = false

    // When opening the Thread, we want to scroll to the last Message, but only once.
    private var isFirstVisit = AtomicBoolean(true)

    // TODO: Remove this when Realm doesn't broadcast twice when deleting a Thread anymore.
    private var isFirstTimeLeaving = AtomicBoolean(true)

    override fun onConfigurationChanged(newConfig: Configuration) {
        threadAdapter.rerenderMails()
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

        threadViewModel.openThread(navigationArgs.threadUid).observe(viewLifecycleOwner) { result ->

            if (result == null) {
                leaveThread()
                return@observe
            }

            val threadUid = result.first.uid
            setupUi(threadUid)
            setupAdapter(threadUid)
            threadAdapter.isExpandedMap = result.second
            threadAdapter.isThemeTheSameMap = result.third
            observeMessagesLive()
            observeContacts()
            observeQuickActionBarClicks()
            observeOpenAttachment()
        }

        permissionUtils.registerDownloadManagerPermission()
        mainViewModel.toggleLightThemeForMessage.observe(viewLifecycleOwner, threadAdapter::toggleLightMode)
    }

    private fun setupUi(threadUid: String) = with(binding) {
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
            mainViewModel.toggleThreadFavoriteStatus(threadUid)
        }

        if (mainViewModel.isCurrentFolderRole(FolderRole.ARCHIVE)) {
            quickActionBar.disable(ARCHIVE_INDEX)
        } else {
            quickActionBar.enable(ARCHIVE_INDEX)
        }

        quickActionBar.setOnItemClickListener { menuId ->
            when (menuId) {
                R.id.quickActionReply -> {
                    trackThreadActionsEvent(ACTION_REPLY_NAME)
                    threadViewModel.clickOnQuickActionBar(threadUid, menuId)
                }
                R.id.quickActionForward -> {
                    trackThreadActionsEvent(ACTION_FORWARD_NAME)
                    threadViewModel.clickOnQuickActionBar(threadUid, menuId)
                }
                R.id.quickActionArchive -> with(mainViewModel) {
                    trackThreadActionsEvent(ACTION_ARCHIVE_NAME, isCurrentFolderRole(FolderRole.ARCHIVE))
                    archiveThread(threadUid)
                }
                R.id.quickActionDelete -> {
                    trackThreadActionsEvent(ACTION_DELETE_NAME)
                    mainViewModel.deleteThread(threadUid)
                }
                R.id.quickActionMenu -> {
                    trackThreadActionsEvent(OPEN_ACTION_BOTTOM_SHEET)
                    threadViewModel.clickOnQuickActionBar(threadUid, menuId)
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
                    if (threadAdapter.messages.count() == 1) {
                        threadAdapter.messages.single().navigateToActionBottomsheet()
                    } else {
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
    }

    private fun shouldLoadDistantResources(messageUid: String): Boolean {
        val isMessageSpecificallyAllowed = threadAdapter.isMessageUidManuallyAllowed(messageUid)
        return (isMessageSpecificallyAllowed && isNotInSpam) || shouldLoadDistantResources()
    }

    private fun shouldLoadDistantResources(): Boolean = alwaysShowExternalContent && isNotInSpam

    private fun observeOpenAttachment() {
        getBackNavigationResult(DownloadAttachmentProgressDialog.OPEN_WITH, ::startActivity)
    }

    private fun setupAdapter(threadUid: String) = with(binding) {
        AppCompatResources.getDrawable(context, R.drawable.divider)?.let {
            messagesList.addItemDecoration(DividerItemDecorator(InsetDrawable(it, 0)))
        }

        messagesList.recycledViewPool.setMaxRecycledViews(0, 0)

        messagesList.adapter = threadAdapter.apply {
            stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
            contacts = mainViewModel.mergedContacts.value ?: emptyMap()
            onContactClicked = { contact ->
                safeNavigate(ThreadFragmentDirections.actionThreadFragmentToDetailedContactBottomSheetDialog(contact))
            }
            onDraftClicked = { message ->
                trackNewMessageEvent(OPEN_FROM_DRAFT_NAME)
                safeNavigate(
                    ThreadFragmentDirections.actionThreadFragmentToNewMessageActivity(
                        arrivedFromExistingDraft = true,
                        draftLocalUuid = message.draftLocalUuid,
                        draftResource = message.draftResource,
                        messageUid = message.uid,
                    )
                )
            }
            onDeleteDraftClicked = { message ->
                trackMessageActionsEvent("deleteDraft")
                mainViewModel.currentMailbox.value?.let { mailbox ->
                    threadViewModel.deleteDraft(message, threadUid, mailbox)
                }
            }
            onAttachmentClicked = { attachment ->
                if (attachment.openWithIntent(requireContext()).hasSupportedApplications(requireContext())) {
                    trackAttachmentActionsEvent("open")
                    attachment.display()
                } else {
                    trackAttachmentActionsEvent("download")
                    scheduleDownloadManager(attachment.downloadUrl, attachment.name)
                }
            }
            onDownloadAllClicked = { message ->
                trackAttachmentActionsEvent("downloadAll")
                downloadAllAttachments(message)
            }
            onReplyClicked = { message ->
                trackMessageActionsEvent(ACTION_REPLY_NAME)
                replyTo(message)
            }
            onMenuClicked = { message -> message.navigateToActionBottomsheet() }
        }
    }

    private fun Message.navigateToActionBottomsheet() {
        safeNavigate(
            ThreadFragmentDirections.actionThreadFragmentToMessageActionBottomSheetDialog(
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

        fun scheduleDownloadManager() = DownloadManagerUtils.scheduleDownload(requireContext(), downloadUrl, filename)

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
        threadViewModel.threadLive(navigationArgs.threadUid).observe(viewLifecycleOwner, ::onThreadUpdate)
    }

    private fun observeMessagesLive() {
        threadViewModel
            .messagesLive(navigationArgs.threadUid)
            .bindResultsChangeToAdapter(viewLifecycleOwner, threadAdapter)
            .apply {
                beforeUpdateAdapter = ::onMessagesUpdate
                afterUpdateAdapter = {
                    val shouldScrollToFirstUnseenMessage = isFirstVisit.compareAndSet(true, false) && it.count() > 1
                    if (shouldScrollToFirstUnseenMessage) onRecyclerViewLaidOut(::scrollToFirstUnseenMessage)
                }
            }
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

        // If no message is expanded (e.g. the last message of the thread is a draft) we want to automatically scroll to the very
        // bottom.
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
        mainViewModel.mergedContacts.observeNotNull(viewLifecycleOwner, threadAdapter::updateContacts)
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

        val subject = context.formatSubject(thread.subject)
        threadSubject.text = subject
        toolbarSubject.text = subject

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

    private fun onMessagesUpdate(messages: List<Message>) {
        Log.i("UI", "Received ${messages.size} messages")
        threadViewModel.fetchIncompleteMessages(messages)
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

        fun allAttachmentsFileName(subject: String) = "ikMail-attachments-$subject.zip"
    }
}
