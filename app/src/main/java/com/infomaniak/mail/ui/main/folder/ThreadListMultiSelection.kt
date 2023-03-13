/*
 * Infomaniak kMail - Android
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

import android.transition.AutoTransition
import android.transition.TransitionManager
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView.ListOrientation.DirectionFlag
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.ThreadDensity
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.thread.SelectedThread
import com.infomaniak.mail.databinding.FragmentThreadListBinding
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.context

class ThreadListMultiSelection {

    lateinit var binding: FragmentThreadListBinding
    lateinit var mainViewModel: MainViewModel
    lateinit var threadListFragment: ThreadListFragment
    lateinit var threadListAdapter: ThreadListAdapter
    lateinit var unlockSwipeActionsIfSet: () -> Unit
    lateinit var localSettings: LocalSettings

    private var shouldMultiselectRead: Boolean = false
    private var shouldMultiselectFavorite: Boolean = true

    fun initMultiSelection(
        binding: FragmentThreadListBinding,
        mainViewModel: MainViewModel,
        threadListFragment: ThreadListFragment,
        threadListAdapter: ThreadListAdapter,
        unlockSwipeActionsIfSet: () -> Unit,
        localSettings: LocalSettings,
    ) {
        this.binding = binding
        this.mainViewModel = mainViewModel
        this.threadListFragment = threadListFragment
        this.threadListAdapter = threadListAdapter
        this.unlockSwipeActionsIfSet = unlockSwipeActionsIfSet
        this.localSettings = localSettings

        setupMultiSelectionActions()

        observerMultiSelection()
    }

    private fun setupMultiSelectionActions() = with(mainViewModel) {
        binding.quickActionBar.setOnItemClickListener { menuId ->
            val selectedThreadsUids = selectedThreads.map { it.uid }
            when (menuId) {
                R.id.quickActionUnread -> {
                    toggleThreadsSeenStatus(selectedThreadsUids, shouldMultiselectRead)
                    isMultiSelectOn = false
                }
                R.id.quickActionArchive -> {
                    archiveThreads(selectedThreadsUids)
                    isMultiSelectOn = false
                }
                R.id.quickActionFavorite -> {
                    toggleThreadsFavoriteStatus(selectedThreadsUids, shouldMultiselectFavorite)
                    isMultiSelectOn = false
                }
                R.id.quickActionDelete -> {
                    deleteThreads(selectedThreadsUids)
                    isMultiSelectOn = false
                }
                R.id.quickActionMenu -> {
                    val direction = if (selectedThreadsUids.count() == 1) {
                        isMultiSelectOn = false
                        ThreadListFragmentDirections.actionThreadListFragmentToThreadActionsBottomSheetDialog(selectedThreadsUids.single())
                    } else {
                        ThreadListFragmentDirections.actionThreadListFragmentToMultiSelectBottomSheetDialog()
                    }
                    threadListFragment.safeNavigate(direction)
                }
            }
        }
    }

    private fun observerMultiSelection() = with(binding) {
        mainViewModel.isMultiSelectOnLiveData.observe(threadListFragment.viewLifecycleOwner) { isMultiSelectOn ->
            threadListAdapter.updateSelection()
            if (localSettings.threadDensity != ThreadDensity.LARGE) TransitionManager.beginDelayedTransition(threadsList)
            if (!isMultiSelectOn) mainViewModel.selectedThreads.clear()

            displaySelectionToolbar(isMultiSelectOn)
            lockDrawerAndSwipe(isMultiSelectOn)
            hideUnreadChip(isMultiSelectOn)
            displayMultiSelectActions(isMultiSelectOn)
        }

        mainViewModel.selectedThreadsLiveData.observe(threadListFragment.viewLifecycleOwner) { selectedThreads ->
            updateSelectedCount(selectedThreads)
            updateSelectAllLabel()
            updateMultiSelectActionsStatus(selectedThreads)
        }
    }

    private fun displaySelectionToolbar(isMultiSelectOn: Boolean) = with(binding) {
        val autoTransition = AutoTransition()
        autoTransition.duration = TOOLBAR_FADE_DURATION
        TransitionManager.beginDelayedTransition(toolbarLayout, autoTransition)

        toolbar.isGone = isMultiSelectOn
        toolbarSelection.isVisible = isMultiSelectOn
    }

    private fun lockDrawerAndSwipe(isMultiSelectOn: Boolean) = with(binding) {
        (threadListFragment.activity as MainActivity).setDrawerLockMode(!isMultiSelectOn)
        if (isMultiSelectOn) {
            threadsList.apply {
                disableSwipeDirection(DirectionFlag.LEFT)
                disableSwipeDirection(DirectionFlag.RIGHT)
            }
        } else {
            unlockSwipeActionsIfSet()
        }
    }

    private fun hideUnreadChip(isMultiSelectOn: Boolean) {
        val noUnread = mainViewModel.currentFolderLive.value?.let { it.unreadCount == 0 } == true
        binding.unreadCountChip.isGone = isMultiSelectOn || noUnread
    }

    private fun displayMultiSelectActions(isMultiSelectOn: Boolean) = with(binding) {
        newMessageFab.isGone = isMultiSelectOn
        quickActionBar.isVisible = isMultiSelectOn
        val navBarColor = context.getColor(if (isMultiSelectOn) R.color.elevatedBackground else R.color.backgroundColor)
        threadListFragment.requireActivity().window.navigationBarColor = navBarColor
    }

    private fun updateSelectedCount(selectedThreads: MutableSet<SelectedThread>) {
        val threadCount = selectedThreads.count()
        binding.selectedCount.text = threadListFragment.resources.getQuantityString(
            R.plurals.multipleSelectionCount,
            threadCount,
            threadCount
        )
    }

    private fun updateSelectAllLabel() {
        val selectAllLabel = if (mainViewModel.isEverythingSelected) R.string.buttonUnselectAll else R.string.buttonSelectAll
        binding.selectAll.setText(selectAllLabel)
    }

    private fun updateMultiSelectActionsStatus(selectedThreads: MutableSet<SelectedThread>) {
        computeReadFavoriteStatus(selectedThreads).let { (shouldRead, shouldFavorite) ->
            shouldMultiselectRead = shouldRead
            shouldMultiselectFavorite = shouldFavorite
        }

        binding.quickActionBar.apply {
            changeIcon(0, if (shouldMultiselectRead) R.drawable.ic_envelope_open else R.drawable.ic_envelope)
            changeText(0, if (shouldMultiselectRead) R.string.actionShortMarkAsRead else R.string.actionShortMarkAsUnread)

            changeIcon(2, if (shouldMultiselectFavorite) R.drawable.ic_star else R.drawable.ic_unstar)

            val isSelectionEmpty = selectedThreads.isEmpty()
            val isInsideArchive = mainViewModel.isCurrentFolderRole(FolderRole.ARCHIVE)
            for (index in 0 until getButtonCount()) {
                val shouldDisable = isSelectionEmpty || (isInsideArchive && index == 1)
                if (shouldDisable) disable(index) else enable(index)
            }
        }
    }

    private fun computeReadFavoriteStatus(selectedThreads: MutableSet<SelectedThread>): Pair<Boolean, Boolean> {
        var shouldUnRead = true
        var shouldUnFavorite = selectedThreads.isNotEmpty()

        for (thread in selectedThreads) {
            shouldUnRead = shouldUnRead && thread.unseenMessagesCount == 0
            shouldUnFavorite = shouldUnFavorite && thread.isFavorite

            if (!shouldUnRead && !shouldUnFavorite) break
        }
        return !shouldUnRead to !shouldUnFavorite
    }

    companion object {
        const val TOOLBAR_FADE_DURATION = 150L
    }
}
