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

import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import com.infomaniak.lib.core.utils.DownloadManagerUtils
import com.infomaniak.lib.core.utils.getBackNavigationResult
import com.infomaniak.lib.core.utils.hasSupportedApplications
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.views.DividerItemDecorator
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.FragmentThreadBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.thread.actions.DownloadAttachmentProgressDialog
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.RealmChangesBinding.Companion.bindResultsChangeToAdapter
import com.infomaniak.mail.utils.UiUtils.animateColorChange
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.roundToInt
import com.google.android.material.R as RMaterial

class ThreadFragment : Fragment() {

    private lateinit var binding: FragmentThreadBinding
    private val navigationArgs: ThreadFragmentArgs by navArgs()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val threadViewModel: ThreadViewModel by viewModels()

    private val threadAdapter by lazy { ThreadAdapter() }

    private var valueAnimator: ValueAnimator? = null

    // When opening the Thread, we want to scroll to the last Message, but only once.
    private var shouldScrollToBottom = AtomicBoolean(true)

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

        mainViewModel.toggleLightThemeForMessage.observe(viewLifecycleOwner, threadAdapter::toggleLightMode)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { leaveThread() }
    }

    private fun setupUi(threadUid: String) = with(binding) {
        toolbar.setNavigationOnClickListener { leaveThread() }

        val defaultTextColor = context.getColor(R.color.primaryTextColor)
        appBar.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val total = appBarLayout.height * COLLAPSE_TITLE_THRESHOLD
            val removed = appBarLayout.height - total
            val progress = (((-verticalOffset.toFloat()) - removed).coerceAtLeast(0.0) / total).toFloat() // Between 0 and 1
            val opacity = (progress * 255).roundToInt()

            val textColor = ColorUtils.setAlphaComponent(defaultTextColor, opacity)
            toolbarSubject.setTextColor(textColor)
        }

        var headerColorState = HeaderState.LOWERED
        messagesList.addOnScrollListener(object : OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val isAtTheTop = !recyclerView.canScrollVertically(-1)
                if (headerColorState == HeaderState.ELEVATED && !isAtTheTop) return

                val newColor = context.getColor(if (isAtTheTop) R.color.backgroundColor else R.color.elevatedBackground)
                headerColorState = if (isAtTheTop) HeaderState.LOWERED else HeaderState.ELEVATED

                val oldColor = appBar.backgroundTintList!!.defaultColor
                if (oldColor == newColor) return

                valueAnimator?.cancel()
                valueAnimator = animateColorChange(oldColor, newColor, animate = true) { color ->
                    toolbar.setBackgroundColor(color)
                    appBar.backgroundTintList = ColorStateList.valueOf(color)
                    activity?.window?.statusBarColor = color
                }
            }
        })

        iconFavorite.setOnClickListener { mainViewModel.toggleFavoriteStatus(threadUid) }

        quickActionBar.setOnItemClickListener { menuId ->
            when (menuId) {
                R.id.quickActionReply -> threadViewModel.clickOnQuickActionBar(threadUid, menuId)
                R.id.quickActionForward -> threadViewModel.forward(threadUid) { messageUid ->
                    safeNavigateToNewMessageActivity(DraftMode.FORWARD, messageUid)
                }
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

    private fun observeOpenAttachment() {
        getBackNavigationResult<Intent>(DownloadAttachmentProgressDialog.OPEN_WITH, ::startActivity)
    }

    private fun setupAdapter(threadUid: String) = with(binding) {
        AppCompatResources.getDrawable(context, R.drawable.divider)?.let {
            messagesList.addItemDecoration(DividerItemDecorator(InsetDrawable(it, 0)))
        }
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
                if (attachment.openWithIntent(requireContext()).hasSupportedApplications(requireContext())) {
                    attachment.display()
                } else {
                    Toast.makeText(requireContext(), R.string.webViewCantHandleAction, Toast.LENGTH_SHORT).show()
                }
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
                        isThemeTheSame = threadAdapter.isThemeTheSameMap[message.uid]!!,
                    )
                )
            }
        }
    }

    private fun Attachment.display() {
        if (hasUsableCache(requireContext()) || isInlineCachedFile(requireContext())) {
            startActivity(openWithIntent(requireContext()))
        } else {
            findNavController().navigate(
                ThreadFragmentDirections.actionThreadFragmentToDownloadAttachmentProgressDialog(
                    attachmentResource = resource!!,
                    attachmentName = name,
                    attachmentType = getFileTypeFromExtension(),
                )
            )
        }
    }

    private fun replyTo(message: Message) {
        if (message.getRecipientsForReplyTo(true).second.isEmpty()) {
            safeNavigateToNewMessageActivity(DraftMode.REPLY, message.uid, ThreadFragment::class.java.name)
        } else {
            safeNavigate(ThreadFragmentDirections.actionThreadFragmentToReplyBottomSheetDialog(messageUid = message.uid))
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
                    if (shouldScrollToBottom.compareAndSet(true, false)) {
                        val indexToScroll = threadAdapter.messages.indexOfFirst { threadAdapter.isExpandedMap[it.uid] == true }
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
            folderId = message.folderId,
            shortUid = message.shortUid,
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
            val color = if (thread.isFavorite) {
                context.getColor(R.color.favoriteYellow)
            } else {
                context.getAttributeColor(RMaterial.attr.colorPrimary)
            }
            iconTint = ColorStateList.valueOf(color)
        }
    }

    private fun onMessagesUpdate(messages: List<Message>) {
        Log.i("UI", "Received ${messages.size} messages")
        threadViewModel.fetchIncompleteMessages(messages)
    }

    private fun leaveThread() {
        // TODO: The day we'll have the Notifications, this `popBackStack` will probably fail to execute correctly.
        // TODO: When opening a Thread via a Notification, the action of leaving this fragment
        // TODO: (either via a classic Back button, or via this `popBackStack`) will probably
        // TODO: do nothing instead of going back to the ThreadList fragment (as it should be).
        valueAnimator?.cancel()
        findNavController().popBackStack()
    }

    enum class HeaderState {
        ELEVATED,
        LOWERED,
    }

    private companion object {
        const val COLLAPSE_TITLE_THRESHOLD = 0.5

        fun allAttachmentsFileName(subject: String) = "kMail-attachments-$subject.zip"
    }
}
