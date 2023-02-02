/*
 * Infomaniak kMail - Android
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

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import com.infomaniak.lib.core.utils.DownloadManagerUtils
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.data.models.Thread
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.FragmentThreadBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.RealmChangesBinding.Companion.bindResultsChangeToAdapter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.roundToInt

class ThreadFragment : Fragment() {

    private lateinit var binding: FragmentThreadBinding
    private val navigationArgs: ThreadFragmentArgs by navArgs()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val threadViewModel: ThreadViewModel by viewModels()

    private val threadAdapter by lazy { ThreadAdapter() }

    // When opening the Thread, we want to scroll to the last Message, but only once.
    private var shouldScrollToBottom = AtomicBoolean(true)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentThreadBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeThreadLive()

        threadViewModel.openThread(navigationArgs.threadUid).observe(viewLifecycleOwner) { result ->

            if (result == null) {
                findNavController().popBackStack()
                return@observe
            }

            val threadUid = result.first.uid
            setupUi(threadUid)
            setupAdapter(threadUid)
            threadAdapter.expandedMap = result.second
            observeMessagesLive()
            observeContacts()
            observeQuickActionBarClicks()
        }
    }

    private fun setupUi(threadUid: String) = with(binding) {

        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val defaultTextColor = context.getColor(R.color.primaryTextColor)
        appBar.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val total = appBarLayout.height * COLLAPSE_TITLE_THRESHOLD
            val removed = appBarLayout.height - total
            val progress = ((-verticalOffset.toFloat()) - removed).coerceAtLeast(0.0)
            val opacity = ((progress / total) * 255).roundToInt()

            val textColor = ColorUtils.setAlphaComponent(defaultTextColor, opacity)
            toolbarSubject.setTextColor(textColor)
        }

        iconFavorite.setOnClickListener { mainViewModel.toggleFavoriteStatus(threadUid) }

        quickActionBar.setOnItemClickListener { menuId ->
            when (menuId) {
                R.id.quickActionReply -> threadViewModel.clickOnQuickActionBar(threadUid, menuId)
                R.id.quickActionForward -> notYetImplemented()
                R.id.quickActionArchive -> mainViewModel.archiveThreadOrMessage(threadUid)
                R.id.quickActionDelete -> mainViewModel.deleteThreadOrMessage(threadUid)
                R.id.quickActionMenu -> threadViewModel.clickOnQuickActionBar(threadUid, menuId)
            }
        }
    }

    private fun observeQuickActionBarClicks() {
        threadViewModel.quickActionBarClicks.observe(viewLifecycleOwner) { (lastMessageToReplyTo, menuId) ->
            when (menuId) {
                R.id.quickActionReply -> replyTo(lastMessageToReplyTo)
                R.id.quickActionMenu -> safeNavigate(
                    ThreadFragmentDirections.actionThreadFragmentToThreadActionsBottomSheetDialog(
                        threadUid = navigationArgs.threadUid,
                        messageUidToReplyTo = lastMessageToReplyTo.uid,
                    )
                )
            }
        }
    }

    private fun setupAdapter(threadUid: String) = with(binding) {
        messagesList.adapter = threadAdapter.apply {
            stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
            contacts = mainViewModel.mergedContacts.value ?: emptyMap()
            onContactClicked = { contact ->
                safeNavigate(ThreadFragmentDirections.actionThreadFragmentToDetailedContactBottomSheetDialog(contact))
            }
            onDraftClicked = { message ->
                safeNavigate(
                    ThreadFragmentDirections.actionThreadFragmentToNewMessageActivity(
                        draftExists = true,
                        draftLocalUuid = message.draftLocalUuid,
                        draftResource = message.draftResource,
                        messageUid = message.uid,
                    )
                )
            }
            onDeleteDraftClicked = { message ->
                mainViewModel.currentMailbox.value?.let { mailbox ->
                    threadViewModel.deleteDraft(message, threadUid, mailbox)
                }
            }
            onAttachmentClicked = { attachment ->
                notYetImplemented()
            }
            onDownloadAllClicked = { message ->
                downloadAllAttachments(message)
            }
            onReplyClicked = { message ->
                replyTo(message)
            }
            onMenuClicked = { message ->
                safeNavigate(
                    ThreadFragmentDirections.actionThreadFragmentToMessageActionBottomSheetDialog(
                        messageUid = message.uid,
                        threadUid = navigationArgs.threadUid,
                        isFavorite = message.isFavorite,
                        isSeen = message.isSeen,
                    )
                )
            }
        }
    }

    private fun replyTo(message: Message) {
        if (message.getRecipientForReplyTo(true).second.isEmpty()) {
            safeNavigateToNewMessageActivity(DraftMode.REPLY, message.uid)
        } else {
            safeNavigate(ThreadFragmentDirections.actionThreadFragmentToReplyBottomSheetDialog(messageUid = message.uid))
        }
    }

    private fun observeThreadLive() {
        threadViewModel.threadLive(navigationArgs.threadUid).refreshObserve(viewLifecycleOwner, ::onThreadUpdate)
    }

    private fun observeMessagesLive() {
        threadViewModel
            .messagesLive(navigationArgs.threadUid)
            .bindResultsChangeToAdapter(viewLifecycleOwner, threadAdapter)
            .apply {
                beforeUpdateAdapter = ::onMessagesUpdate
                afterUpdateAdapter = {
                    if (shouldScrollToBottom.compareAndSet(true, false)) {
                        val indexToScroll = threadAdapter.messages.indexOfFirst { threadAdapter.expandedMap[it.uid] == true }
                        binding.messagesList.scrollToPosition(indexToScroll)
                    }
                }
            }
    }

    private fun observeContacts() {
        mainViewModel.mergedContacts.observeNotNull(viewLifecycleOwner, threadAdapter::updateContacts)
    }

    private fun downloadAllAttachments(message: Message) {
        val url = ApiRoutes.downloadAttachments(
            mailboxUuid = mainViewModel.currentMailbox.value?.uuid ?: return,
            folderId = mainViewModel.currentFolder.value?.id ?: return,
            messageId = message.shortUid,
        )
        val truncatedSubject = message.subject?.let { it.substring(0..min(30, it.lastIndex)) }
        val name = allAttachmentsFileName(truncatedSubject ?: "")
        DownloadManagerUtils.scheduleDownload(requireContext(), url, name)
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
            setIconTintResource(if (thread.isFavorite) R.color.favoriteYellow else R.color.iconColor)
        }
    }

    private fun onMessagesUpdate(messages: List<Message>) {
        Log.i("UI", "Received messages (${messages.size})")
        threadViewModel.fetchIncompleteMessages(messages)
        binding.messagesList.setBackgroundResource(if (messages.count() == 1) R.color.backgroundColor else R.color.threadBackground)
    }

    private fun leaveThread() {
        // TODO: The day we'll have the Notifications, this `popBackStack` will probably fail to execute correctly.
        // TODO: When opening a Thread via a Notification, the action of leaving this fragment
        // TODO: (either via a classic Back button, or via this `popBackStack`) will probably
        // TODO: do nothing instead of going back to the ThreadList fragment (as it should be).
        findNavController().popBackStack()
    }

    private companion object {
        const val COLLAPSE_TITLE_THRESHOLD = 0.5

        fun allAttachmentsFileName(subject: String) = "kMail-attachments-$subject.zip"
    }
}
