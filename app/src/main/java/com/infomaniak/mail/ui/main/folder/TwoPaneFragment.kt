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
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.search.SearchFragment
import com.infomaniak.mail.ui.main.thread.ThreadFragment
import com.infomaniak.mail.ui.main.thread.actions.DownloadAttachmentProgressDialog
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.safeNavigateToNewMessageActivity

abstract class TwoPaneFragment : Fragment() {

    val mainViewModel: MainViewModel by activityViewModels()

    private val slidingPaneLayout: SlidingPaneLayout
        get() = when (this) {
            is ThreadListFragment -> binding.threadListSlidingPaneLayout
            is SearchFragment -> binding.searchSlidingPaneLayout
            else -> throw IllegalStateException()
        }

    abstract fun getAnchor(): View?

    fun areBothShown() = !slidingPaneLayout.isSlideable
    fun isOnlyLeftShown() = slidingPaneLayout.let { it.isSlideable && !it.isOpen }
    fun isOnlyRightShown() = slidingPaneLayout.let { it.isSlideable && it.isOpen }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSlidingPane()
        observeCurrentFolder()
        observeThreadEvents()
    }

    private fun setupSlidingPane() {
        slidingPaneLayout.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED
    }

    private fun observeCurrentFolder() = with(mainViewModel) {
        currentFolder.observe(viewLifecycleOwner) { folder ->

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
        }
    }

    private fun observeThreadEvents() = with(mainViewModel) {

        val threadListAdapter = when (this@TwoPaneFragment) {
            is ThreadListFragment -> this@TwoPaneFragment.threadListAdapter
            is SearchFragment -> this@TwoPaneFragment.threadListAdapter
            else -> null
        }

        // Reset selected Thread UI when closing Thread
        currentThreadUid.observe(viewLifecycleOwner) { threadUid ->
            if (threadUid == null) threadListAdapter?.selectNewThread(newPosition = null, threadUid = null)
        }

        closeThreadTrigger.observe(viewLifecycleOwner) { resetPanes() }

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
            isOnlyRightShown() -> resetPanes()
            this is ThreadListFragment -> requireActivity().finish()
            else -> findNavController().popBackStack()
        }
    }

    fun openThread(uid: String) {

        mainViewModel.currentThreadUid.value = uid

        val isOpening = slidingPaneLayout.openPane()

        if (isOpening) requireActivity().window.statusBarColor = requireContext().getColor(R.color.backgroundColor)
    }

    private fun resetPanes() {

        val isClosing = slidingPaneLayout.closePane()

        if (isClosing && this is ThreadListFragment) {
            requireActivity().window.statusBarColor = requireContext().getColor(R.color.backgroundHeaderColor)
        }

        mainViewModel.currentThreadUid.value = null

        // TODO: We can see that the ThreadFragment's content is changing, while the pane is closing.
        //  Maybe we need to delay the transaction? Or better: start it when the pane is fully closed?
        childFragmentManager.beginTransaction().replace(R.id.threadHostFragment, ThreadFragment()).commit()
    }
}
