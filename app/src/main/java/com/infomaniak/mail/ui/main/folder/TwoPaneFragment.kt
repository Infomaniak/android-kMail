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
import androidx.annotation.FloatRange
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
import com.infomaniak.mail.utils.UiUtils
import com.infomaniak.mail.utils.safeNavigateToNewMessageActivity
import com.infomaniak.mail.utils.updateNavigationBarColor
import javax.inject.Inject

abstract class TwoPaneFragment : Fragment(), SlidingPaneLayout.PanelSlideListener {

    val mainViewModel: MainViewModel by activityViewModels()
    val twoPaneViewModel: TwoPaneViewModel by activityViewModels()

    protected abstract val slidingPaneLayout: SlidingPaneLayout

    // TODO: When we'll update DragDropSwipeRecyclerViewLib, we'll need to make the adapter nullable.
    //  For now it causes a memory leak, because we can't remove the strong reference
    //  between the ThreadList's RecyclerView and its Adapter as it throws an NPE.
    @Inject
    lateinit var threadListAdapter: ThreadListAdapter

    private val leftStatusBarColor: Int by lazy {
        requireContext().getColor(if (this is ThreadListFragment) R.color.backgroundHeaderColor else R.color.backgroundColor)
    }
    private val leftNavigationBarColor: Int by lazy { requireContext().getColor(R.color.backgroundColor) }
    private val rightStatusBarColor: Int by lazy { requireContext().getColor(R.color.backgroundColor) }
    private val rightNavigationBarColor: Int by lazy { requireContext().getColor(R.color.elevatedBackground) }

    abstract fun getAnchor(): View?
    open fun doAfterFolderChanged() = Unit

    fun isOnlyOneShown() = slidingPaneLayout.isSlideable
    fun areBothShown() = !isOnlyOneShown()
    fun isOnlyLeftShown() = isOnlyOneShown() && !slidingPaneLayout.isOpen
    fun isOnlyRightShown() = isOnlyOneShown() && slidingPaneLayout.isOpen

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSlidingPane()
        observeCurrentFolder()
        observeThreadUid()
        observeThreadNavigation()
    }

    private fun setupSlidingPane() = with(slidingPaneLayout) {
        lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED
        addPanelSlideListener(this@TwoPaneFragment)
    }

    private fun observeCurrentFolder() = with(twoPaneViewModel) {
        mainViewModel.currentFolder.observe(viewLifecycleOwner) { folder ->

            val (folderId, name) = if (this@TwoPaneFragment is SearchFragment) {
                FolderController.SEARCH_FOLDER_ID to getString(R.string.searchFolderName)
            } else {
                if (folder == null) return@observe
                folder.id to folder.getLocalizedName(requireContext())
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

    override fun onPanelOpened(panel: View) = Unit

    override fun onPanelClosed(panel: View) = Unit

    override fun onPanelSlide(panel: View, slideOffset: Float) {
        colorSystemBarsWithSlidingPane(slideOffset)
    }

    private fun colorSystemBarsWithSlidingPane(
        @FloatRange(0.0, 1.0) slideOffset: Float = UiUtils.FULLY_SLID,
    ): Unit = with(requireActivity().window) {
        if (slideOffset == UiUtils.FULLY_SLID) {
            statusBarColor = leftStatusBarColor
            updateNavigationBarColor(leftNavigationBarColor)
        } else {
            statusBarColor = UiUtils.pointBetweenColors(rightStatusBarColor, leftStatusBarColor, slideOffset)
            updateNavigationBarColor(UiUtils.pointBetweenColors(rightNavigationBarColor, leftNavigationBarColor, slideOffset))
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

    fun navigateToThread(thread: Thread) = with(twoPaneViewModel) {
        if (thread.isOnlyOneDraft) {
            trackNewMessageEvent(OPEN_FROM_DRAFT_NAME)
            openDraft(thread)
        } else {
            openThread(thread.uid)
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
