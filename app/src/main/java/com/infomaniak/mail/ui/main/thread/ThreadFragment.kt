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

import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.views.DividerItemDecorator
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.FragmentThreadBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.ModelsUtils.getFormattedThreadSubject
import com.infomaniak.mail.utils.RealmChangesBinding.Companion.bindListChangeToAdapter
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.notYetImplemented
import kotlin.math.roundToInt

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
        mainViewModel.openThread(navigationArgs.threadUid)
        bindMessages()
        listenToContacts()
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
            when (menuId) {
                R.id.quickActionReply -> safeNavigate(ThreadFragmentDirections.actionThreadFragmentToReplyBottomSheetDialog())
                R.id.quickActionForward -> notYetImplemented()
                R.id.quickActionArchive -> notYetImplemented()
                R.id.quickActionDelete -> notYetImplemented()
                R.id.quickActionMenu -> safeNavigate(
                    ThreadFragmentDirections.actionThreadFragmentToThreadActionsBottomSheetDialog(
                        isFavorite = navigationArgs.threadIsFavorite,
                        unseenMessagesCount = navigationArgs.unseenMessagesCount,
                    )
                )
            }
        }

        AppCompatResources.getDrawable(context, R.drawable.mail_divider)?.let {
            messagesList.addItemDecoration(DividerItemDecorator(InsetDrawable(it, 0)))
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
                        isOpeningExistingDraft = true,
                        isExistingDraftAlreadyDownloaded = true,
                        draftUuid = message.draftUuid,
                    )
                )
            }
            onDeleteDraftClicked = { message ->
                mainViewModel.deleteDraft(message)
            }
            onAttachmentClicked = { attachment ->
                notYetImplemented()
            }
            onDownloadAllClicked = {
                notYetImplemented()
            }
            onReplyClicked = {
                safeNavigate(ThreadFragmentDirections.actionThreadFragmentToReplyBottomSheetDialog())
            }
            onMenuClicked = { message ->
                safeNavigate(
                    ThreadFragmentDirections.actionThreadFragmentToMessageActionBottomSheetDialog(
                        isFavorite = message.isFavorite,
                        isSeen = message.seen,
                    )
                )
            }
        }
    }

    private fun bindMessages() {
        threadViewModel.messages(navigationArgs.threadUid).bindListChangeToAdapter(viewLifecycleOwner, threadAdapter).apply {
            beforeUpdateAdapter = ::onMessagesUpdate
            afterUpdateAdapter = { binding.messagesList.scrollToPosition(threadAdapter.lastIndex()) }
        }
    }

    private fun onMessagesUpdate(messages: List<Message>) {
        Log.i("UI", "Received messages (${messages.size})")
        if (messages.isEmpty()) leaveThread()
    }

    private fun listenToContacts() {
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
    }
}
