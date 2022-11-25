/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.utils.DownloadManagerUtils
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.FragmentThreadBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.RealmChangesBinding.Companion.bindListChangeToAdapter
import com.infomaniak.mail.utils.Utils.getFormattedThreadSubject
import kotlin.math.roundToInt
import com.infomaniak.lib.core.R as RCore

class ThreadFragment : Fragment() {

    private lateinit var binding: FragmentThreadBinding
    private val navigationArgs: ThreadFragmentArgs by navArgs()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val threadViewModel: ThreadViewModel by viewModels()

    private var threadAdapter = ThreadAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentThreadBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUi()
        setupAdapter()
        threadViewModel.openThread(navigationArgs.threadUid)
        observeMessages()
        observeContacts()
    }

    private fun setupUi() = with(binding) {
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        threadSubject.text = navigationArgs.threadSubject.getFormattedThreadSubject(requireContext())
        iconFavorite.apply {
            setIconResource(if (navigationArgs.threadIsFavorite) R.drawable.ic_star_filled else R.drawable.ic_star)
            setIconTintResource(if (navigationArgs.threadIsFavorite) R.color.favoriteYellow else R.color.iconColor)
            setOnClickListener { notYetImplemented() }
        }

        quickActionBar.setOnItemClickListener { menuId ->
            val lastMessageUid = threadAdapter.messages.getLastMessageToExecuteAction().uid
            when (menuId) {
                R.id.quickActionReply -> safeNavigate(
                    ThreadFragmentDirections.actionThreadFragmentToReplyBottomSheetDialog(messageUid = lastMessageUid)
                )
                R.id.quickActionForward -> notYetImplemented()
                R.id.quickActionArchive -> notYetImplemented()
                R.id.quickActionDelete -> notYetImplemented()
                R.id.quickActionMenu -> {
                    safeNavigate(
                        ThreadFragmentDirections.actionThreadFragmentToThreadActionsBottomSheetDialog(
                            messageUid = lastMessageUid,
                            isFavorite = navigationArgs.threadIsFavorite,
                            unseenMessagesCount = navigationArgs.unseenMessagesCount,
                        )
                    )
                }
            }
        }

        toolbarSubject.text = navigationArgs.threadSubject.getFormattedThreadSubject(context)

        val defaultTextColor = context.getColor(R.color.primaryTextColor)
        appBar.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val total = appBarLayout.height * COLLAPSE_TITLE_THRESHOLD
            val removed = appBarLayout.height - total
            val progress = ((-verticalOffset.toFloat()) - removed).coerceAtLeast(0.0)
            val opacity = ((progress / total) * 255).roundToInt()

            val textColor = ColorUtils.setAlphaComponent(defaultTextColor, opacity)
            toolbarSubject.setTextColor(textColor)
        }
    }

    private fun setupAdapter() = with(binding) {
        messagesList.adapter = threadAdapter.apply {
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
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
                threadViewModel.deleteDraft(message)
            }
            onAttachmentClicked = { attachment ->
                notYetImplemented()
            }
            onDownloadAllClicked = { message ->
                downloadAllAttachments(message)
            }
            onReplyClicked = {
                safeNavigate(ThreadFragmentDirections.actionThreadFragmentToReplyBottomSheetDialog(messageUid = it.uid))
            }
            onMenuClicked = { message ->
                safeNavigate(
                    ThreadFragmentDirections.actionThreadFragmentToMessageActionBottomSheetDialog(
                        messageUid = message.uid,
                        isFavorite = message.isFavorite,
                        isSeen = message.seen,
                    )
                )
            }
        }
    }

    private fun downloadAllAttachments(message: Message) {
        val url = ApiRoutes.downloadAttachments(
            mailboxUuid = AccountUtils.currentMailboxUuid ?: return,
            folderId = MainViewModel.currentFolderId.value ?: return,
            messageId = message.shortUid,
        )
        val name = allAttachmentsFileName(message.subject?.substring(0..30) ?: "")
        DownloadManagerUtils.scheduleDownload(requireContext(), url, name)
    }

    private fun observeMessages() {
        threadViewModel.messages.bindListChangeToAdapter(viewLifecycleOwner, threadAdapter).apply {
            beforeUpdateAdapter = ::onMessagesUpdate
            afterUpdateAdapter = { binding.messagesList.scrollToPosition(threadAdapter.lastIndex()) }
        }
    }

    private fun onMessagesUpdate(messages: List<Message>) {
        Log.i("UI", "Received messages (${messages.size})")
        if (messages.isEmpty()) leaveThread()
        binding.messagesList.setBackgroundResource(if (messages.count() == 1) RCore.color.white else R.color.threadBackground)
    }

    private fun observeContacts() {
        mainViewModel.mergedContacts.observeNotNull(viewLifecycleOwner, threadAdapter::updateContacts)
    }

    private fun leaveThread() {
        threadViewModel.deleteThread(navigationArgs.threadUid)
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
