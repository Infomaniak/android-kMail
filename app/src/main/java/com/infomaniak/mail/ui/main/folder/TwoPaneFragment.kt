/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.folder

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.infomaniak.lib.core.utils.getBackNavigationResult
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.MatomoMail.OPEN_FROM_DRAFT_NAME
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.search.SearchFragment
import com.infomaniak.mail.ui.main.thread.ThreadFragment
import com.infomaniak.mail.ui.main.thread.actions.DownloadAttachmentProgressDialog
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.safeNavigateToNewMessageActivity
import com.infomaniak.mail.utils.updateNavigationBarColor

abstract class TwoPaneFragment : Fragment() {

    val mainViewModel: MainViewModel by activityViewModels()
    val twoPaneViewModel: TwoPaneViewModel by activityViewModels()

    protected abstract val slidingPaneLayout: SlidingPaneLayout

    abstract fun getAnchor(): View?
    open fun doAfterFolderChanged() {}

    fun isOnlyOneShown() = slidingPaneLayout.isSlideable
    fun areBothShown() = !slidingPaneLayout.isSlideable
    fun isOnlyLeftShown() = isOnlyOneShown() && !slidingPaneLayout.isOpen
    fun isOnlyRightShown() = isOnlyOneShown() && slidingPaneLayout.isOpen

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSlidingPane()
        observeCurrentFolder()
        observeThreadUid()
        observeThreadNavigation()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val isLeftShown = areBothShown() || isOnlyLeftShown() // TODO: Only works on Phone, and not on Tablet.
        val statusBarColor = if (isLeftShown && this is ThreadListFragment) {
            R.color.backgroundHeaderColor
        } else {
            R.color.backgroundColor
        }
        requireActivity().window.statusBarColor = requireContext().getColor(statusBarColor)
    }

    private fun setupSlidingPane() {
        slidingPaneLayout.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED
    }

    private fun observeCurrentFolder() = with(twoPaneViewModel) {
        mainViewModel.currentFolder.observe(viewLifecycleOwner) { folder ->

            val (folderId, name) = if (this@TwoPaneFragment is SearchFragment) {
                FolderController.SEARCH_FOLDER_ID to getString(R.string.searchFolderName)
            } else {
                if (folder == null) return@observe
                folder.id to folder.getLocalizedName(context)
            }

            rightPaneFolderName.value = name

            if (folderId != previousFolderId) {
                previousFolderId = folderId
                if (isThreadOpen) closeThread()
            }

            doAfterFolderChanged()
        }
    }

    private fun observeThreadUid() {

        val threadListAdapter = when (this) {
            is ThreadListFragment -> this.threadListAdapter
            is SearchFragment -> this.threadListAdapter
            else -> null
        }

        twoPaneViewModel.currentThreadUid.observe(viewLifecycleOwner) { threadUid ->
            val isOpeningThread = threadUid != null
            if (isOpeningThread) {
                val hasPaneOpened = slidingPaneLayout.openPane()
                if (hasPaneOpened) requireActivity().window.statusBarColor = requireContext().getColor(R.color.backgroundColor)
            } else {
                resetPanes(threadListAdapter)
            }
        }
    }

    private fun observeThreadNavigation() = with(twoPaneViewModel) {

        getBackNavigationResult(DownloadAttachmentProgressDialog.OPEN_WITH, ::startActivity)

        downloadAttachmentArgs.observe(viewLifecycleOwner) {
            safeNavigate(resId = R.id.downloadAttachmentProgressDialog, args = it.toBundle())
        }

        newMessageArgs.observe(viewLifecycleOwner) {
            safeNavigateToNewMessageActivity(args = it.toBundle())
        }

        replyBottomSheetArgs.observe(viewLifecycleOwner) {
            safeNavigate(resId = R.id.replyBottomSheetDialog, args = it.toBundle())
        }

        threadActionsArgs.observe(viewLifecycleOwner) {
            safeNavigate(resId = R.id.threadActionsBottomSheetDialog, args = it.toBundle())
        }

        messageActionsArgs.observe(viewLifecycleOwner) {
            safeNavigate(resId = R.id.messageActionsBottomSheetDialog, args = it.toBundle())
        }

        detailedContactArgs.observe(viewLifecycleOwner) {
            safeNavigate(resId = R.id.detailedContactBottomSheetDialog, args = it.toBundle())
        }
    }

    fun handleOnBackPressed() {
        when {
            isOnlyRightShown() -> twoPaneViewModel.closeThread()
            this is ThreadListFragment -> requireActivity().finish()
            else -> findNavController().popBackStack()
        }
    }

    fun navigateToThread(thread: Thread) {
        if (thread.isOnlyOneDraft) {
            trackNewMessageEvent(OPEN_FROM_DRAFT_NAME)
            openDraft(thread)
        } else {
            twoPaneViewModel.openThread(thread.uid)
        }
    }

    private fun openDraft(thread: Thread) = runCatchingRealm {
        twoPaneViewModel.navigateToSelectedDraft(thread.messages.first()).observe(viewLifecycleOwner) {
            safeNavigateToNewMessageActivity(it.toBundle())
        }
    }

    private fun resetPanes(threadListAdapter: ThreadListAdapter?) = with(requireActivity()) {

        val isClosing = slidingPaneLayout.closePane()

        if (isClosing) {
            if (this@TwoPaneFragment is ThreadListFragment) window.statusBarColor = getColor(R.color.backgroundHeaderColor)
            window.updateNavigationBarColor(getColor(R.color.backgroundColor))
        }

        threadListAdapter?.selectNewThread(newPosition = null, threadUid = null)

        // TODO: We can see that the ThreadFragment's content is changing, while the pane is closing.
        //  Maybe we need to delay the transaction? Or better: start it when the pane is fully closed?
        childFragmentManager.beginTransaction().replace(R.id.threadHostFragment, ThreadFragment()).commit()
    }
}
