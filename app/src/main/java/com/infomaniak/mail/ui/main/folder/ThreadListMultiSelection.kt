/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
import androidx.lifecycle.lifecycleScope
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.dragdropswiperecyclerview.DragDropSwipeRecyclerView.ListOrientation.DirectionFlag
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackMultiSelectActionEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.ThreadDensity
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.extensions.archiveWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.deleteWithConfirmationPopup
import kotlinx.coroutines.launch

class ThreadListMultiSelection {

    lateinit var mainViewModel: MainViewModel
    private lateinit var threadListFragment: ThreadListFragment
    lateinit var unlockSwipeActionsIfSet: () -> Unit
    lateinit var localSettings: LocalSettings

    private var shouldMultiselectRead: Boolean = false
    private var shouldMultiselectFavorite: Boolean = true

    fun initMultiSelection(
        mainViewModel: MainViewModel,
        threadListFragment: ThreadListFragment,
        unlockSwipeActionsIfSet: () -> Unit,
        localSettings: LocalSettings,
    ) {
        this.mainViewModel = mainViewModel
        this.threadListFragment = threadListFragment
        this.unlockSwipeActionsIfSet = unlockSwipeActionsIfSet
        this.localSettings = localSettings

        setupMultiSelectionActions()

        observerMultiSelection()
    }

    private fun setupMultiSelectionActions() = with(mainViewModel) {
        threadListFragment.binding.quickActionBar.setOnItemClickListener { menuId ->
            val selectedThreadsUids = selectedThreads.map { it.uid }
            val selectedThreadsCount = selectedThreadsUids.count()

            when (menuId) {
                R.id.quickActionUnread -> {
                    trackMultiSelectActionEvent(MatomoName.MarkAsSeen, selectedThreadsCount)
                    toggleThreadsSeenStatus(selectedThreadsUids, shouldMultiselectRead)
                    isMultiSelectOn = false
                }
                R.id.quickActionArchive -> threadListFragment.lifecycleScope.launch {
                    threadListFragment.descriptionDialog.archiveWithConfirmationPopup(
                        folderRole = threadListFragment.folderRoleUtils.getActionFolderRole(selectedThreads),
                        count = selectedThreadsCount,
                    ) {
                        trackMultiSelectActionEvent(MatomoName.Archive, selectedThreadsCount)
                        archiveThreads(selectedThreadsUids)
                        isMultiSelectOn = false
                    }
                }
                R.id.quickActionFavorite -> {
                    trackMultiSelectActionEvent(MatomoName.Favorite, selectedThreadsCount)
                    toggleThreadsFavoriteStatus(selectedThreadsUids, shouldMultiselectFavorite)
                    isMultiSelectOn = false
                }
                R.id.quickActionDelete -> threadListFragment.lifecycleScope.launch {
                    threadListFragment.descriptionDialog.deleteWithConfirmationPopup(
                        folderRole = threadListFragment.folderRoleUtils.getActionFolderRole(selectedThreads),
                        count = selectedThreadsCount,
                    ) {
                        trackMultiSelectActionEvent(MatomoName.Delete, selectedThreadsCount)
                        deleteThreads(selectedThreadsUids)
                        isMultiSelectOn = false
                    }
                }
                R.id.quickActionMenu -> {
                    trackMultiSelectActionEvent(MatomoName.OpenBottomSheet, selectedThreadsCount)
                    val direction = if (selectedThreadsCount == 1) {
                        ThreadListFragmentDirections.actionThreadListFragmentToThreadActionsBottomSheetDialog(
                            threadUid = selectedThreadsUids.single(),
                            shouldLoadDistantResources = false,
                            shouldCloseMultiSelection = true,
                        )
                    } else {
                        ThreadListFragmentDirections.actionThreadListFragmentToMultiSelectBottomSheetDialog()
                    }
                    threadListFragment.safelyNavigate(direction)
                }
            }
        }
    }

    private fun observerMultiSelection() = with(threadListFragment) {
        mainViewModel.isMultiSelectOnLiveData.observe(viewLifecycleOwner) { isMultiSelectOn ->
            threadListAdapter.updateSelection()
            if (localSettings.threadDensity != ThreadDensity.LARGE) TransitionManager.beginDelayedTransition(binding.threadsList)
            if (!isMultiSelectOn) mainViewModel.selectedThreads.clear()

            displaySelectionToolbar(isMultiSelectOn)
            lockDrawerAndSwipe(isMultiSelectOn)
            hideUnreadChip(isMultiSelectOn)
            displayMultiSelectActions(isMultiSelectOn)
        }

        mainViewModel.selectedThreadsLiveData.observe(viewLifecycleOwner) { selectedThreads ->
            if (selectedThreads.isEmpty()) {
                mainViewModel.isMultiSelectOn = false
            } else {
                updateSelectedCount(selectedThreads)
                updateSelectAllLabel()
                updateMultiSelectActionsStatus(selectedThreads)
            }
        }
    }

    private fun displaySelectionToolbar(isMultiSelectOn: Boolean) = with(threadListFragment.binding) {
        val autoTransition = AutoTransition()
        autoTransition.duration = TOOLBAR_FADE_DURATION
        TransitionManager.beginDelayedTransition(toolbarLayout, autoTransition)

        toolbar.isGone = isMultiSelectOn
        toolbarSelection.isVisible = isMultiSelectOn
    }

    private fun lockDrawerAndSwipe(isMultiSelectOn: Boolean) = with(threadListFragment) {
        (requireActivity() as MainActivity).setDrawerLockMode(isLocked = isMultiSelectOn)
        if (isMultiSelectOn) {
            binding.threadsList.apply {
                disableSwipeDirection(DirectionFlag.LEFT)
                disableSwipeDirection(DirectionFlag.RIGHT)
            }
        } else {
            unlockSwipeActionsIfSet()
        }
    }

    private fun hideUnreadChip(isMultiSelectOn: Boolean) = runCatchingRealm {
        val thereAreUnread = mainViewModel.currentFolderLive.value?.let { it.unreadCountLocal > 0 } == true
        threadListFragment.binding.unreadCountChip.isVisible = thereAreUnread && !isMultiSelectOn
    }

    private fun displayMultiSelectActions(isMultiSelectOn: Boolean) = with(threadListFragment.binding) {
        newMessageFab.isGone = isMultiSelectOn
        quickActionBar.isVisible = isMultiSelectOn
    }

    private fun updateSelectedCount(selectedThreads: Set<Thread>) {
        val threadCount = selectedThreads.count()
        threadListFragment.binding.selectedCount.text = threadListFragment.resources.getQuantityString(
            R.plurals.multipleSelectionCount,
            threadCount,
            threadCount
        )
    }

    private fun updateSelectAllLabel() {
        val selectAllLabel = if (mainViewModel.isEverythingSelected) R.string.buttonUnselectAll else R.string.buttonSelectAll
        threadListFragment.binding.selectAll.setText(selectAllLabel)
    }

    private fun updateMultiSelectActionsStatus(selectedThreads: Set<Thread>) {
        computeReadFavoriteStatus(selectedThreads).let { (shouldRead, shouldFavorite) ->
            shouldMultiselectRead = shouldRead
            shouldMultiselectFavorite = shouldFavorite
        }

        threadListFragment.binding.quickActionBar.apply {
            val (readIcon, readText) = getReadIconAndShortText(shouldMultiselectRead)
            changeIcon(READ_UNREAD_INDEX, readIcon)
            changeText(READ_UNREAD_INDEX, readText)

            val favoriteIcon = if (shouldMultiselectFavorite) R.drawable.ic_star else R.drawable.ic_unstar
            changeIcon(FAVORITE_INDEX, favoriteIcon)

            val isSelectionEmpty = selectedThreads.isEmpty()
            threadListFragment.viewLifecycleOwner.lifecycleScope.launch {
                val isFromArchive = threadListFragment.folderRoleUtils.getActionFolderRole(selectedThreads) == FolderRole.ARCHIVE
                for (index in 0 until getButtonCount()) {
                    val shouldDisable = isSelectionEmpty || (isFromArchive && index == ARCHIVE_INDEX)
                    if (shouldDisable) disable(index) else enable(index)
                }
            }
        }
    }

    companion object {
        private const val TOOLBAR_FADE_DURATION = 150L

        private const val READ_UNREAD_INDEX = 0
        private const val ARCHIVE_INDEX = 1
        private const val FAVORITE_INDEX = 2

        fun computeReadFavoriteStatus(selectedThreads: Set<Thread>): Pair<Boolean, Boolean> {
            var shouldUnread = true
            var shouldUnfavorite = selectedThreads.isNotEmpty()

            for (thread in selectedThreads) {
                shouldUnread = shouldUnread && thread.isSeen
                shouldUnfavorite = shouldUnfavorite && thread.isFavorite

                if (!shouldUnread && !shouldUnfavorite) break
            }
            return !shouldUnread to !shouldUnfavorite
        }

        fun getReadIconAndShortText(shouldRead: Boolean): Pair<Int, Int> {
            return if (shouldRead) {
                R.drawable.ic_envelope_open to R.string.actionShortMarkAsRead
            } else {
                R.drawable.ic_envelope to R.string.actionShortMarkAsUnread
            }
        }

        fun getArchiveIconAndShortText(isFromArchive: Boolean): Pair<Int, Int> {
            return if (isFromArchive) {
                R.drawable.ic_drawer_inbox to R.string.inboxFolder
            } else {
                R.drawable.ic_archive_folder to R.string.actionArchive
            }
        }

        fun getFavoriteIconAndShortText(shouldFavorite: Boolean): Pair<Int, Int> {
            return if (shouldFavorite) {
                R.drawable.ic_star to R.string.actionStar
            } else {
                R.drawable.ic_unstar to R.string.actionUnstar
            }
        }
    }
}
