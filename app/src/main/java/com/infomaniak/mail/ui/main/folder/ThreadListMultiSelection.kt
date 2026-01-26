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

// import com.infomaniak.mail.ui.main.folder.`<no name provided>`.computeReadFavoriteStatus
import android.transition.AutoTransition
import android.transition.TransitionManager
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
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
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.extensions.archiveWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.deleteWithConfirmationPopup
import kotlinx.coroutines.launch

interface MultiSelectionHost : LifecycleOwner {
    val multiSelectionBinding: MultiSelectionBinding
    val folderRoleUtils: com.infomaniak.mail.utils.FolderRoleUtils
    val descriptionDialog: com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
    val threadListAdapter: ThreadListAdapter
    fun safeNavigation(directions: NavDirections)
    fun disableSwipeDirection(direction: DirectionFlag)
    fun unlockSwipeActionsIfSet()
    fun directionToThreadActionsBottomSheetDialog(
        threadUid: String,
        shouldLoadDistantResources: Boolean,
        shouldCloseMultiSelection: Boolean
    ): NavDirections

    fun directionsToMultiSelectBottomSheetDialog(): NavDirections
}

interface MultiSelectionBinding {
    val quickActionBar: com.infomaniak.mail.views.BottomQuickActionBarView
    val multiselectToolbar: com.infomaniak.mail.databinding.ViewMultiselectionInfoToolbarBinding
    val toolbarLayout: android.view.View
    val toolbar: android.view.View
    val threadsList: android.view.ViewGroup
    val newMessageFab: android.view.View?
    val unreadCountChip: android.view.View?
}

class ThreadListMultiSelection {

    lateinit var mainViewModel: MainViewModel
    lateinit var mainActivity: MainActivity
    lateinit var searchViewModel: SearchViewModel
    private lateinit var host: MultiSelectionHost
    lateinit var unlockSwipeActionsIfSet: () -> Unit
    lateinit var localSettings: LocalSettings

    private var shouldMultiselectRead: Boolean = false
    private var shouldMultiselectFavorite: Boolean = true

    fun initMultiSelection(
        mainViewModel: MainViewModel,
        activity: MainActivity,
        host: MultiSelectionHost,
        searchViewModel: SearchViewModel?,
        unlockSwipeActionsIfSet: () -> Unit,
        localSettings: LocalSettings,
    ) {
        this.mainViewModel = mainViewModel
        this.mainActivity = activity
        this.host = host
        this.unlockSwipeActionsIfSet = unlockSwipeActionsIfSet
        this.localSettings = localSettings

        if (searchViewModel != null) {
            this.searchViewModel = searchViewModel
            setupMessageMultiSelectionActions()
        } else setupMultiSelectionActions()

        observerMultiSelection()
    }

    private fun setupMessageMultiSelectionActions() = with(searchViewModel) {
        host.multiSelectionBinding.quickActionBar.setOnItemClickListener { menuId ->
            val selectedMessagesUids = selectedMessages.map { it.uid }
            val selectedMessagesCount = selectedMessagesUids.count()

            when (menuId) {
                R.id.quickActionUnread -> {
                    trackMultiSelectActionEvent(MatomoName.MarkAsSeen, selectedMessagesCount)
                    mainViewModel.toggleMessageSeenStatus("", selectedMessages.toList())
                    mainViewModel.isMultiSelectOn = false
                }
                // R.id.quickActionArchive -> host.lifecycleScope.launch {
                //     trackMultiSelectActionEvent(MatomoName.Archive, selectedMessagesCount)
                //     mainViewModel.archiveThreads(selectedMessagesUids)
                //     isMultiSelectOn = false
                // }
                // R.id.quickActionFavorite -> {
                //     trackMultiSelectActionEvent(MatomoName.Favorite, selectedThreadsCount)
                //     toggleThreadsFavoriteStatus(selectedThreadsUids, shouldMultiselectFavorite)
                //     isMultiSelectOn = false
                // }
                // R.id.quickActionDelete -> host.lifecycleScope.launch {
                //     host.descriptionDialog.deleteWithConfirmationPopup(
                //         folderRole = host.folderRoleUtils.getActionFolderRole(selectedThreads),
                //         count = selectedThreadsCount,
                //     ) {
                //         trackMultiSelectActionEvent(MatomoName.Delete, selectedThreadsCount)
                //         deleteThreads(selectedThreadsUids)
                //         isMultiSelectOn = false
                //     }
                // }
                // R.id.quickActionMenu -> {
                //     trackMultiSelectActionEvent(MatomoName.OpenBottomSheet, selectedThreadsCount)
                //     val direction = if (selectedThreadsCount == 1) {
                //         host.directionToThreadActionsBottomSheetDialog(
                //             threadUid = selectedThreadsUids.single(),
                //             shouldLoadDistantResources = false,
                //             shouldCloseMultiSelection = true,
                //         )
                //     } else {
                //         host.directionsToMultiSelectBottomSheetDialog()
                //     }
                //     host.safeNavigation(direction)
                // }
            }
        }
    }

    private fun setupMultiSelectionActions() = with(mainViewModel) {
        host.multiSelectionBinding.quickActionBar.setOnItemClickListener { menuId ->
            val selectedThreadsUids = selectedThreads.map { it.uid }
            val selectedThreadsCount = selectedThreadsUids.count()

            when (menuId) {
                R.id.quickActionUnread -> {
                    trackMultiSelectActionEvent(MatomoName.MarkAsSeen, selectedThreadsCount)
                    toggleThreadsSeenStatus(selectedThreadsUids, shouldMultiselectRead)
                    isMultiSelectOn = false
                }
                R.id.quickActionArchive -> host.lifecycleScope.launch {
                    host.descriptionDialog.archiveWithConfirmationPopup(
                        folderRole = host.folderRoleUtils.getActionFolderRole(selectedThreads),
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
                R.id.quickActionDelete -> host.lifecycleScope.launch {
                    host.descriptionDialog.deleteWithConfirmationPopup(
                        folderRole = host.folderRoleUtils.getActionFolderRole(selectedThreads),
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
                        host.directionToThreadActionsBottomSheetDialog(
                            threadUid = selectedThreadsUids.single(),
                            shouldLoadDistantResources = false,
                            shouldCloseMultiSelection = true,
                        )
                    } else {
                        host.directionsToMultiSelectBottomSheetDialog()
                    }
                    host.safeNavigation(direction)
                }
            }
        }
    }

    private fun observerMultiSelection() = with(host) {
        mainViewModel.isMultiSelectOnLiveData.observe(host) { isMultiSelectOn ->
            threadListAdapter.updateSelection()
            if (localSettings.threadDensity != ThreadDensity.LARGE) TransitionManager.beginDelayedTransition(host.multiSelectionBinding.threadsList)
            if (!isMultiSelectOn) mainViewModel.selectedThreads.clear()

            displaySelectionToolbar(isMultiSelectOn)
            lockDrawerAndSwipe(isMultiSelectOn)
            if (multiSelectionBinding.unreadCountChip != null) hideUnreadChip(isMultiSelectOn)
            displayMultiSelectActions(isMultiSelectOn)
        }

        mainViewModel.selectedThreadsLiveData.observe(host) { selectedThreads ->
            if (selectedThreads.isEmpty()) {
                mainViewModel.isMultiSelectOn = false
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
        val selectAllLabel = if (mainViewModel.isEverythingSelected) R.string.buttonUnselectAll else R.string.buttonSelectAll
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
                val isFromArchive = host.folderRoleUtils.getActionFolderRole(selectedThreads) == FolderRole.ARCHIVE
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
    }
}
