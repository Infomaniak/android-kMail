/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.annotation.ColorRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.utils.getBackNavigationResult
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.MatomoMail.OPEN_FROM_DRAFT_NAME
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.search.SearchFragment
import com.infomaniak.mail.ui.main.thread.ThreadFragment
import com.infomaniak.mail.utils.extensions.*
import javax.inject.Inject

abstract class TwoPaneFragment : Fragment() {

    val mainViewModel: MainViewModel by activityViewModels()
    protected val twoPaneViewModel: TwoPaneViewModel by activityViewModels()

    // TODO: When we'll update DragDropSwipeRecyclerViewLib, we'll need to make the adapter nullable.
    //  For now it causes a memory leak, because we can't remove the strong reference
    //  between the ThreadList's RecyclerView and its Adapter as it throws an NPE.
    @Inject
    lateinit var threadListAdapter: ThreadListAdapter

    @ColorRes
    abstract fun getStatusBarColor(): Int
    abstract fun getLeftPane(): View?
    abstract fun getRightPane(): FragmentContainerView?
    abstract fun getAnchor(): View?
    open fun doAfterFolderChanged() = Unit

    fun isOnlyOneShown(): Boolean = isPhone() || isTabletInPortrait()
    fun isOnlyLeftShown(): Boolean = isOnlyOneShown() && !twoPaneViewModel.isThreadOpen
    fun isOnlyRightShown(): Boolean = isOnlyOneShown() && twoPaneViewModel.isThreadOpen

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateTwoPaneVisibilities()
        observeCurrentFolder()
        observeThreadUid()
        observeThreadNavigation()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateTwoPaneVisibilities()
        updateDrawerLockMode()
        updateStatusBarColor()
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
                if (isThreadOpen && previousFolderId != null) closeThread()
                previousFolderId = folderId
            }

            doAfterFolderChanged()
        }
    }

    private fun observeThreadUid() {
        twoPaneViewModel.currentThreadUid.observe(viewLifecycleOwner) { threadUid ->
            updateTwoPaneVisibilities()
            updateDrawerLockMode()
            val isOpeningThread = threadUid != null
            if (isOpeningThread) {
                if (isOnlyRightShown()) setSystemBarsColors(statusBarColor = R.color.backgroundColor, navigationBarColor = null)
            } else {
                resetPanes()
            }
        }
    }

    private fun observeThreadNavigation() = with(twoPaneViewModel) {
        getBackNavigationResult(AttachmentExtensions.DOWNLOAD_ATTACHMENT_RESULT, ::startActivity)

        newMessageArgs.observe(viewLifecycleOwner) {
            safeNavigateToNewMessageActivity(args = it.toBundle())
        }

        navArgs.observe(viewLifecycleOwner) { (resId, args) ->
            safeNavigate(resId, args)
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

    private fun resetPanes() {

        if (isOnlyLeftShown()) {
            setSystemBarsColors(statusBarColor = getStatusBarColor(), navigationBarColor = R.color.backgroundColor)
        }

        threadListAdapter.selectNewThread(newPosition = null, threadUid = null)

        childFragmentManager.beginTransaction().replace(R.id.threadHostFragment, ThreadFragment()).commit()
    }

    private fun updateTwoPaneVisibilities() {

        val (leftWidth, rightWidth) = computeTwoPaneWidths(
            widthPixels = requireActivity().application.resources.displayMetrics.widthPixels,
            isThreadOpen = twoPaneViewModel.isThreadOpen,
        )

        getLeftPane()?.let { leftPane ->
            if (leftWidth == 0) {
                leftPane.isGone = true
            } else {
                if (leftPane.width != leftWidth) leftPane.layoutParams?.width = leftWidth
                leftPane.isVisible = true
            }
        }

        getRightPane()?.let { rightPane ->
            if (rightWidth == 0) {
                rightPane.isGone = true
            } else {
                if (rightPane.width != rightWidth) rightPane.layoutParams?.width = rightWidth
                rightPane.isVisible = true
            }
        }
    }

    private fun computeTwoPaneWidths(widthPixels: Int, isThreadOpen: Boolean): Pair<Int, Int> = with(twoPaneViewModel) {

        val leftPaneWidthRatio = ResourcesCompat.getFloat(resources, R.dimen.leftPaneWidthRatio)
        val rightPaneWidthRatio = ResourcesCompat.getFloat(resources, R.dimen.rightPaneWidthRatio)

        return if (isTabletInLandscape()) {
            (leftPaneWidthRatio * widthPixels).toInt() to (rightPaneWidthRatio * widthPixels).toInt()
        } else {
            if (isThreadOpen) 0 to widthPixels else widthPixels to 0
        }
    }

    // TODO: When we'll add the feature of swiping between Threads, we'll need to check if this function is still needed.
    private fun updateDrawerLockMode() {
        if (this is ThreadListFragment) {
            (requireActivity() as MainActivity).setDrawerLockMode(isLocked = isOnlyRightShown())
        }
    }

    private fun updateStatusBarColor() {

        val statusBarColor = if (isOnlyRightShown()) { // Thread (in Phone mode)
            if (getRightPane()?.getFragment<ThreadFragment?>()?.isScrolledToTheTop() == true) {
                R.color.toolbarLoweredColor
            } else {
                R.color.toolbarElevatedColor
            }
        } else { // ThreadList or Search
            getStatusBarColor()
        }

        setSystemBarsColors(statusBarColor = statusBarColor, navigationBarColor = null)
    }
}
