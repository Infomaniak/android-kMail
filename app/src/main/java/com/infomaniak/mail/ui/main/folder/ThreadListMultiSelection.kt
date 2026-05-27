/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2026 Infomaniak Network SA
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
import com.infomaniak.mail.ui.main.search.SearchViewModel
import com.infomaniak.mail.ui.main.thread.actions.ActionsViewModel
import com.infomaniak.mail.ui.main.thread.actions.multiselection.MultiSelectionHost
import com.infomaniak.mail.ui.main.thread.actions.multiselection.MultiselectionViewModel
import com.infomaniak.mail.utils.FolderRoleUtils
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.extensions.archiveWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.deleteWithConfirmationPopup
import kotlinx.coroutines.launch

class ThreadListMultiSelection {

    lateinit var mainViewModel: MainViewModel
    lateinit var multiselectionViewModel: MultiselectionViewModel
    lateinit var actionsViewModel: ActionsViewModel
    lateinit var searchViewModel: SearchViewModel

    lateinit var mainActivity: MainActivity
    private lateinit var host: MultiSelectionHost
    private lateinit var folderRoleUtils: FolderRoleUtils
    private var isFromSearch: Boolean = false
    lateinit var unlockSwipeActionsIfSet: () -> Unit
    lateinit var localSettings: LocalSettings
    private var shouldMultiselectRead: Boolean = false
    private var shouldMultiselectFavorite: Boolean = true

    fun initMultiSelection(
        mainViewModel: MainViewModel,
        multiselectionViewModel: MultiselectionViewModel,
        actionsViewModel: ActionsViewModel,
        activity: MainActivity,
        host: MultiSelectionHost,
        folderRoleUtils: FolderRoleUtils,
        unlockSwipeActionsIfSet: () -> Unit,
        localSettings: LocalSettings,
        isFromSearch: Boolean,
        searchViewModel: SearchViewModel?,
    ) {
        this.mainViewModel = mainViewModel
        this.multiselectionViewModel = multiselectionViewModel
        this.actionsViewModel = actionsViewModel
        this.mainActivity = activity
        this.host = host
        this.folderRoleUtils = folderRoleUtils
        this.unlockSwipeActionsIfSet = unlockSwipeActionsIfSet
        this.localSettings = localSettings
        this.isFromSearch = isFromSearch

        if (isFromSearch && searchViewModel != null) {
            this.searchViewModel = searchViewModel
        }

        setupMultiSelectionActions()
        observerMultiSelection()
    }

    private fun setupMultiSelectionActions() = with(multiselectionViewModel) {
        host.multiSelectionBinding.quickActionBar.setOnItemClickListener { menuId ->
            val selectedThreadsUids = selectedThreads.map { it.uid }
            val selectedThreadsCount = selectedThreadsUids.count()
            val currentMailBox = mainViewModel.currentMailbox.value ?: return@setOnItemClickListener

            when (menuId) {
                R.id.quickActionUnread -> {
                    trackMultiSelectActionEvent(MatomoName.MarkAsSeen, selectedThreadsCount)
                    actionsViewModel.toggleThreadsSeenStatus(
                        threadsUids = selectedThreadsUids,
                        shouldRead = shouldMultiselectRead,
                        currentFolderId = mainViewModel.currentFolderId,
                        mailbox = currentMailBox,
                    )
                    isMultiSelectOn = false
                }
                R.id.quickActionArchive -> host.lifecycleScope.launch {
                    host.descriptionDialog.archiveWithConfirmationPopup(
                        folderRole = host.folderRoleUtils.getThreadsActionFolderRole(selectedThreads),
                        count = selectedThreadsCount,
                    ) {
                        trackMultiSelectActionEvent(MatomoName.Archive, selectedThreadsCount)
                        actionsViewModel.archiveThreads(
                            threads = selectedThreads.toList(),
                            currentFolder = mainViewModel.currentFolder.value,
                            mailbox = currentMailBox,
                        )
                        isMultiSelectOn = false
                    }
                }
                R.id.quickActionFavorite -> {
                    trackMultiSelectActionEvent(MatomoName.Favorite, selectedThreadsCount)
                    actionsViewModel.toggleThreadsFavoriteStatus(
                        threadsUids = selectedThreadsUids,
                        mailbox = currentMailBox,
                        shouldFavorite = shouldMultiselectFavorite,
                    )
                    isMultiSelectOn = false
                }
                R.id.quickActionDelete -> host.lifecycleScope.launch {
                    val allMessages = selectedThreads.flatMap { it.messages }
                    host.descriptionDialog.deleteWithConfirmationPopup(
                        messagesFolderRoles = host.folderRoleUtils.getActionFolderRoles(allMessages),
                        currentFolderRole = mainViewModel.currentFolder.value?.role,
                        count = selectedThreadsCount,
                    ) {
                        trackMultiSelectActionEvent(MatomoName.Delete, selectedThreadsCount)
                        actionsViewModel.deleteThreads(selectedThreads.toList(), currentFolder.value, currentMailBox)
                        isMultiSelectOn = false
                    }
                }
                R.id.quickActionMenu -> {
                    trackMultiSelectActionEvent(MatomoName.OpenBottomSheet, selectedThreadsCount)
                    val direction = if (selectedThreadsCount == 1) {
                        host.directionToThreadActionsBottomSheetDialog(
                            threadUid = selectedThreadsUids.single(),
                            shouldLoadDistantResources = false,
                            shouldCloseMultiSelection = true,
                            isFromSearch = isFromSearch,
                        )
                    } else {
                        host.directionsToMultiSelectBottomSheetDialog(isFromSearch)
                    }
                    host.safeNavigation(direction)
                }
            }
        }
    }

    private fun observerMultiSelection() = with(host) {
        multiselectionViewModel.isMultiSelectOnLiveData.observe(multiSelectionLifecycleOwner) { isMultiSelectOn ->
            threadListAdapter.updateSelection()
            if (localSettings.threadDensity != ThreadDensity.LARGE) TransitionManager.beginDelayedTransition(host.multiSelectionBinding.threadsList)
            if (!isMultiSelectOn) multiselectionViewModel.selectedThreads.clear()

            displaySelectionToolbar(isMultiSelectOn)
            lockDrawerAndSwipe(isMultiSelectOn)
            if (multiSelectionBinding.unreadCountChip != null) hideUnreadChip(isMultiSelectOn)
            displayMultiSelectActions(isMultiSelectOn)
        }

        multiselectionViewModel.selectedThreadsLiveData.observe(multiSelectionLifecycleOwner) { selectedThreads ->
            if (selectedThreads.isEmpty()) {
                multiselectionViewModel.isMultiSelectOn = false
            } else {
                updateSelectedCount(selectedThreads)
                updateSelectAllLabel()
                updateMultiSelectActionsStatus(selectedThreads)
            }
        }
    }

    private fun displaySelectionToolbar(isMultiSelectOn: Boolean) = with(host.multiSelectionBinding) {
        val autoTransition = AutoTransition()
        autoTransition.duration = TOOLBAR_FADE_DURATION
        TransitionManager.beginDelayedTransition(multiselectToolbar.toolbar, autoTransition)

        toolbar.isGone = isMultiSelectOn
        multiselectToolbar.toolbar.isVisible = isMultiSelectOn
    }

    private fun lockDrawerAndSwipe(isMultiSelectOn: Boolean) = with(host) {
        mainActivity.setDrawerLockMode(isLocked = isMultiSelectOn)
        if (isMultiSelectOn) {
            multiSelectionBinding.threadsList.apply {
                disableSwipeDirection(DirectionFlag.LEFT)
                disableSwipeDirection(DirectionFlag.RIGHT)
            }
        } else {
            unlockSwipeActionsIfSet()
        }
    }

    private fun hideUnreadChip(isMultiSelectOn: Boolean) = runCatchingRealm {
        val thereAreUnread = mainViewModel.currentFolderLive.value?.let { it.unreadCountLocal > 0 } == true
        host.multiSelectionBinding.unreadCountChip?.isVisible = thereAreUnread && !isMultiSelectOn
    }

    private fun displayMultiSelectActions(isMultiSelectOn: Boolean) = with(host.multiSelectionBinding) {
        newMessageFab?.let { it.isGone = isMultiSelectOn }
        quickActionBar.isVisible = isMultiSelectOn
    }

    private fun updateSelectedCount(selectedThreads: Set<Thread>) {
        val threadCount = selectedThreads.count()
        host.multiSelectionBinding.multiselectToolbar.selectedCount.text = mainActivity.resources.getQuantityString(
            R.plurals.multipleSelectionCount,
            threadCount,
            threadCount
        )
    }

    private fun updateSelectAllLabel() {
        val currentThreadsSelected = mainViewModel.currentThreadsLive.value?.list?.count() ?: 0
        val isEverythingSelected = multiselectionViewModel.isEverythingSelected(currentThreadsSelected)
        val selectAllLabel =
            if (isEverythingSelected) R.string.buttonUnselectAll else R.string.buttonSelectAll
        host.multiSelectionBinding.multiselectToolbar.selectAll.setText(selectAllLabel)
    }

    private fun updateMultiSelectActionsStatus(selectedThreads: Set<Thread>) {
        computeReadFavoriteStatus(selectedThreads).let { (shouldRead, shouldFavorite) ->
            shouldMultiselectRead = shouldRead
            shouldMultiselectFavorite = shouldFavorite
        }

        host.multiSelectionBinding.quickActionBar.apply {
            val (readIcon, readText) = getReadIconAndShortText(shouldMultiselectRead)
            changeIcon(READ_UNREAD_INDEX, readIcon)
            changeText(READ_UNREAD_INDEX, readText)

            val favoriteIcon = if (shouldMultiselectFavorite) R.drawable.ic_star else R.drawable.ic_unstar
            changeIcon(FAVORITE_INDEX, favoriteIcon)

            val isSelectionEmpty = selectedThreads.isEmpty()

            host.lifecycleScope.launch {
                val isFromArchive = host.folderRoleUtils.getThreadsActionFolderRole(selectedThreads) == FolderRole.ARCHIVE
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
