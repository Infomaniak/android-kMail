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
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.FragmentThreadBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.*
import com.infomaniak.mail.ui.main.thread.ThreadViewModel.OpenThreadResult
import com.infomaniak.mail.ui.main.thread.actions.DownloadAttachmentProgressDialog
import com.infomaniak.mail.ui.newMessage.NewMessageActivityArgs
import com.infomaniak.mail.utils.*
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

    private var _binding: FragmentThreadBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView

    private val navigationArgs: ThreadFragmentArgs by navArgs()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val threadViewModel: ThreadViewModel by activityViewModels()

    private val threadAdapter inline get() = binding.messagesList.adapter as ThreadAdapter
    private val permissionUtils by lazy { PermissionUtils(this) }
    private val isNotInSpam by lazy { mainViewModel.currentFolder.value?.role != FolderRole.SPAM }

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

    private var isFavorite = false

    // TODO: Remove this when Realm doesn't broadcast twice when deleting a Thread anymore.
    private var isFirstTimeLeaving = AtomicBoolean(true)

    override fun onConfigurationChanged(newConfig: Configuration) {
        threadAdapter.reRenderMails()
        super.onConfigurationChanged(newConfig)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentThreadBinding.inflate(inflater, container, false).also {
            _binding = it
            requireActivity().window.statusBarColor = requireContext().getColor(R.color.backgroundColor)
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Avoid crashing the app when rotating.
        if (!isTwoPaneLayout()) {
            runCatching { navigationArgs.threadUid }.onFailure { if (it is IllegalStateException) return }
        }

        initAdapter()
        observeThreadLive()

        setupDialogs()
        permissionUtils.registerDownloadManagerPermission()

        mainViewModel.toggleLightThemeForMessage.observe(viewLifecycleOwner, threadAdapter::toggleLightMode)

        setupUi()
        setupAdapter()

        observeMessagesLive()
        observeFailedMessages()
        observeContacts()
        observeQuickActionBarClicks()
        if (!isTwoPaneLayout()) observeOpenAttachment()
        observeSubjectUpdateTriggers()

        observeThreadOpening()
    }

    override fun onDestroyView() {

        // Don't replace with `threadAdapter` variable, the cast will fail.
        (binding.messagesList.adapter as ThreadAdapter?)?.resetCallbacks()

        super.onDestroyView()
        _binding = null
    }

    private fun observeThreadOpening() {

        threadViewModel.threadUid.observe(viewLifecycleOwner, ::handleThreadUid)

        if (isTwoPaneLayout()) {
            observeFolderChange()
        } else {
            threadViewModel.threadUid.value = navigationArgs.threadUid
        }
    }

    private fun observeFolderChange() {

        threadViewModel.goToSearch.observeNotNull(viewLifecycleOwner) { isGoing ->
            val folder = if (isGoing) {
                Folder().apply { name = getString(R.string.searchFolderName) }
            } else {
                mainViewModel.currentFolder.value
            }
            folder?.let(::reactToFolderChange)
            threadViewModel.goToSearch.value = null
        }

        mainViewModel.currentFolder.observeNotNull(viewLifecycleOwner, ::reactToFolderChange)
    }

    private fun reactToFolderChange(folder: Folder) {
        binding.emptyViewFolderName.text = getString(R.string.noConversationSelected, folder.getLocalizedName(requireContext()))
        threadViewModel.threadUid.value = null
    }

    private fun handleThreadUid(threadUid: String?) {
        if (threadUid == null) {
            threadViewModel.closeThread()
            displayEmptyView()
        } else {
            if (isTwoPaneLayout()) displayThreadView()
            openThread(threadUid)
        }
    }

    private fun displayEmptyView() = with(binding) {
        threadView.isGone = true
        emptyView.isVisible = true
    }

    private fun displayThreadView() = with(binding) {
        emptyView.isGone = true
        threadView.isVisible = true
    }

    private fun openThread(threadUid: String) = with(threadViewModel) {

        reassignThreadLive(threadUid)
        reassignMessagesLive(threadUid)

        openThread(threadUid).observe(viewLifecycleOwner) { result ->

            if (result == null) {
                leaveThread()
                return@observe
            }

            updateUi(threadUid, folderRole = mainViewModel.getActionFolderRole(result.thread))
            updateAdapter(result)
        }
    }

    private fun observeSubjectUpdateTriggers() {
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

    private fun setupUi() = with(binding) {

        // TODO: Use a different layout in normal mode & table mode instead of doing that.
        if (isTwoPaneLayout()) {
            toolbar.navigationIcon?.alpha = 0
        } else {
            toolbar.setNavigationOnClickListener { leaveThread() }
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

        changeToolbarColorOnScroll(toolbar, messagesListNestedScrollView) { color ->
            appBar.backgroundTintList = ColorStateList.valueOf(color)
        }
    }

    private fun updateUi(threadUid: String, folderRole: FolderRole?) = with(binding) {

        iconFavorite.setOnClickListener {
            trackThreadActionsEvent(ACTION_FAVORITE_NAME, isFavorite)
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

    private fun observeQuickActionBarClicks() {
        threadViewModel.quickActionBarClicks.observe(viewLifecycleOwner) { (threadUid, lastMessageToReplyTo, menuId) ->
            when (menuId) {
                R.id.quickActionReply -> replyTo(lastMessageToReplyTo)
                R.id.quickActionForward -> {
                    goToNewMessageActivity(
                        draftMode = DraftMode.FORWARD,
                        previousMessageUid = lastMessageToReplyTo.uid,
                        shouldLoadDistantResources = shouldLoadDistantResources(lastMessageToReplyTo.uid),
                    )
                }
                R.id.quickActionMenu -> {
                    val shouldLoadDistantResources = shouldLoadDistantResources(lastMessageToReplyTo.uid)
                    if (isTwoPaneLayout()) {
                        mainViewModel.threadActionsBottomSheetArgs.value = Triple(
                            threadUid,
                            lastMessageToReplyTo.uid,
                            shouldLoadDistantResources,
                        )
                    } else {
                        safeNavigate(
                            ThreadFragmentDirections.actionThreadFragmentToThreadActionsBottomSheetDialog(
                                threadUid = threadUid,
                                messageUidToReplyTo = lastMessageToReplyTo.uid,
                                shouldLoadDistantResources = shouldLoadDistantResources,
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

    private fun shouldLoadDistantResources(): Boolean = localSettings.externalContent == ExternalContent.ALWAYS && isNotInSpam

    private fun observeOpenAttachment() {
        getBackNavigationResult(DownloadAttachmentProgressDialog.OPEN_WITH, ::startActivity)
    }

    private fun initAdapter() {
        binding.messagesList.adapter = ThreadAdapter(
            shouldLoadDistantResources = shouldLoadDistantResources(),
            onContactClicked = { contact ->
                if (isTwoPaneLayout()) {
                    mainViewModel.detailedContactArgs.value = contact
                } else {
                    safeNavigate(ThreadFragmentDirections.actionThreadFragmentToDetailedContactBottomSheetDialog(contact))
                }
            },
            onDraftClicked = { message ->
                trackNewMessageEvent(OPEN_FROM_DRAFT_NAME)
                goToNewMessageActivity(
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
                if (attachment.openWithIntent(requireContext()).hasSupportedApplications(requireContext())) {
                    trackAttachmentActionsEvent("open")
                    attachment.display()
                } else {
                    trackAttachmentActionsEvent("download")
                    mainViewModel.snackBarManager.setValue(getString(R.string.snackbarDownloadInProgress))
                    scheduleDownloadManager(attachment.downloadUrl, attachment.name)
                }
            },
            onDownloadAllClicked = { message ->
                trackAttachmentActionsEvent("downloadAll")
                mainViewModel.snackBarManager.setValue(getString(R.string.snackbarDownloadInProgress))
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
            navigateToNewMessageActivity = { uri ->
                goToNewMessageActivity(mailToUri = uri)
            },
            promptLink = { data, type ->
                // When adding a phone number to contacts, Google decodes this value in case it's url-encoded. But I could not
                // reproduce this issue when manually creating a url-encoded href. If this is triggered, fix it by also
                // decoding it at that step.
                if (type == ThreadAdapter.ContextMenuType.PHONE && data.contains('%')) Sentry.withScope { scope ->
                    scope.level = SentryLevel.ERROR
                    Sentry.captureMessage("Google was right, phone numbers can be url-encoded. Needs to be fixed")
                }

                when (type) {
                    ThreadAdapter.ContextMenuType.LINK -> linkContextualMenuAlertDialog.show(data)
                    ThreadAdapter.ContextMenuType.EMAIL -> emailContextualMenuAlertDialog.show(data)
                    ThreadAdapter.ContextMenuType.PHONE -> phoneContextualMenuAlertDialog.show(data)
                }
            }
        )
    }

    private fun setupAdapter() = with(binding.messagesList) {
        addItemDecoration(DividerItemDecorator(InsetDrawable(dividerDrawable(context), 0)))
        recycledViewPool.setMaxRecycledViews(0, 0)
        threadAdapter.stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    private fun setupDialogs() = with(mainViewModel) {
        bindAlertToViewLifecycle(descriptionDialog)
        linkContextualMenuAlertDialog.initValues(snackBarManager)
        emailContextualMenuAlertDialog.initValues(snackBarManager)
        phoneContextualMenuAlertDialog.initValues(snackBarManager)
    }

    private fun updateAdapter(result: OpenThreadResult) = with(binding.messagesList) {

        threadAdapter.apply {
            isExpandedMap = result.isExpandedMap
            initialSetOfExpandedMessagesUids = result.initialSetOfExpandedMessagesUids
            isThemeTheSameMap = result.isThemeTheSameMap

            contacts = mainViewModel.mergedContactsLive.value ?: emptyMap()
        }
    }

    private fun Message.navigateToActionsBottomSheet() {
        val threadUid = threadViewModel.threadUid.value ?: return
        val isThemeTheSame = threadAdapter.isThemeTheSameMap[uid] ?: return
        val shouldLoadDistantResources = shouldLoadDistantResources(uid)

        if (isTwoPaneLayout()) {
            mainViewModel.messageActionsBottomSheetArgs.value = MessageActionsArgs(
                messageUid = uid,
                threadUid = threadUid,
                isThemeTheSame = isThemeTheSame,
                shouldLoadDistantResources = shouldLoadDistantResources,
            )
        } else {
            safeNavigate(
                ThreadFragmentDirections.actionThreadFragmentToMessageActionsBottomSheetDialog(
                    messageUid = uid,
                    threadUid = threadUid,
                    isThemeTheSame = isThemeTheSame,
                    shouldLoadDistantResources = shouldLoadDistantResources,
                )
            )
        }
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
            val fileType = getFileTypeFromMimeType()
            if (isTwoPaneLayout()) {
                mainViewModel.downloadAttachmentsArgs.value = Triple(resource!!, name, fileType)
            } else {
                safeNavigate(
                    ThreadFragmentDirections.actionThreadFragmentToDownloadAttachmentProgressDialog(
                        attachmentResource = resource!!,
                        attachmentName = name,
                        attachmentType = fileType,
                    )
                )
            }
        }
    }

    private fun replyTo(message: Message) {

        val shouldLoadDistantResources = shouldLoadDistantResources(message.uid)

        if (message.getRecipientsForReplyTo(replyAll = true).second.isEmpty()) {
            goToNewMessageActivity(
                draftMode = DraftMode.REPLY,
                previousMessageUid = message.uid,
                shouldLoadDistantResources = shouldLoadDistantResources,
                arrivedFromExistingDraft = false,
            )
        } else {
            if (isTwoPaneLayout()) {
                mainViewModel.replyBottomSheetArgs.value = message.uid to shouldLoadDistantResources
            } else {
                safeNavigate(
                    ThreadFragmentDirections.actionThreadFragmentToReplyBottomSheetDialog(
                        messageUid = message.uid,
                        shouldLoadDistantResources = shouldLoadDistantResources,
                    )
                )
            }
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
        }
    }

    private fun observeFailedMessages() {
        threadViewModel.failedMessagesUids.observe(viewLifecycleOwner, threadAdapter::updateFailedMessages)
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

        threadSubject.setOnLongClickListener {
            context.copyStringToClipboard(subject, R.string.snackbarSubjectCopiedToClipboard, mainViewModel.snackBarManager)
            true
        }
        toolbarSubject.setOnLongClickListener {
            context.copyStringToClipboard(subject, R.string.snackbarSubjectCopiedToClipboard, mainViewModel.snackBarManager)
            true
        }
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

    private fun leaveThread() {
        if (isTwoPaneLayout()) {
            threadViewModel.threadUid.value = null
        } else {
            // TODO: Realm broadcasts twice when the Thread is deleted.
            //  We don't know why.
            //  While it's not fixed, we do this quickfix of checking if we already left:
            if (isFirstTimeLeaving.compareAndSet(true, false)) {
                findNavController().popBackStack()
            }
        }
    }

    private fun goToNewMessageActivity(
        draftMode: DraftMode = DraftMode.NEW_MAIL,
        previousMessageUid: String? = null,
        shouldLoadDistantResources: Boolean = false,
        arrivedFromExistingDraft: Boolean = false,
        draftLocalUuid: String? = null,
        draftResource: String? = null,
        messageUid: String? = null,
        mailToUri: Uri? = null,
    ) {

        val args = NewMessageActivityArgs(
            draftMode = draftMode,
            previousMessageUid = previousMessageUid,
            shouldLoadDistantResources = shouldLoadDistantResources,
            arrivedFromExistingDraft = arrivedFromExistingDraft,
            draftLocalUuid = draftLocalUuid,
            draftResource = draftResource,
            messageUid = messageUid,
            mailToUri = mailToUri,
        )

        if (isTwoPaneLayout()) {
            mainViewModel.newMessageArgs.value = args
        } else {
            safeNavigateToNewMessageActivity(args = args.toBundle())
        }
    }

    data class MessageActionsArgs(
        val messageUid: String,
        val threadUid: String,
        val isThemeTheSame: Boolean,
        val shouldLoadDistantResources: Boolean,
    )

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
