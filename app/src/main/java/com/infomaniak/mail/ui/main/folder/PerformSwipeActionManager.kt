/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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

import androidx.navigation.fragment.findNavController
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.matomo.Matomo.TrackerAction
import com.infomaniak.mail.MatomoMail.MatomoCategory
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.SwipeAction
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.ui.main.folderPicker.FolderPickerAction
import com.infomaniak.mail.ui.main.settings.appearance.swipe.SwipeActionsSettingsFragment
import com.infomaniak.mail.ui.main.thread.ThreadViewModel.SnoozeScheduleType
import com.infomaniak.mail.utils.extensions.animatedNavigation
import com.infomaniak.mail.utils.extensions.archiveWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.deleteWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.getAnimatedNavOptions
import com.infomaniak.mail.utils.extensions.moveWithConfirmationPopup

object PerformSwipeActionManager {

    /**
     * The boolean return value is used to know if we should keep the Thread in
     * the RecyclerView (true), or remove it when the swipe is done (false).
     */
    fun ThreadListFragment.performSwipeAction(
        swipeAction: SwipeAction,
        thread: Thread,
        position: Int,
        isPermanentDeleteFolder: Boolean,
    ): Boolean {
        val folderRole = thread.folder.role
        if (!swipeAction.canDisplay(folderRole, mainViewModel.featureFlagsLive.value, localSettings)) {
            snackbarManager.setValue(getString(R.string.snackbarSwipeActionIncompatible))
            return true
        }

        trackEvent(MatomoCategory.SwipeActions, swipeAction.matomoName, TrackerAction.DRAG)

        val shouldKeepItemBecauseOfAction = performSwipeAction(
            swipeAction = swipeAction,
            folderRole = folderRole,
            thread = thread,
            position = position,
            isPermanentDeleteFolder = isPermanentDeleteFolder,
        )

        val shouldKeepItemBecauseOfNoConnection = !mainViewModel.hasNetwork
        return shouldKeepItemBecauseOfAction || shouldKeepItemBecauseOfNoConnection
    }

    private fun ThreadListFragment.performSwipeAction(
        swipeAction: SwipeAction,
        folderRole: FolderRole?,
        thread: Thread,
        position: Int,
        isPermanentDeleteFolder: Boolean
    ) = when (swipeAction) {
        SwipeAction.TUTORIAL -> {
            localSettings.setDefaultSwipeActions()
            safelyNavigate(ThreadListFragmentDirections.actionThreadListFragmentToSettingsFragment())
            findNavController().navigate(R.id.swipeActionsSettingsFragment, args = null, getAnimatedNavOptions())
            true
        }
        SwipeAction.ARCHIVE -> {
            descriptionDialog.archiveWithConfirmationPopup(
                folderRole = folderRole,
                count = 1,
                displayLoader = false,
                onCancel = {
                    // Notify only if the user cancelled the popup (e.g. the thread is not deleted),
                    // otherwise it will notify the next item in the list and make it slightly blink
                    if (threadListAdapter.dataSet.indexOfFirstThread(thread) == position) {
                        threadListAdapter.notifyItemChanged(position)
                    }
                },
            ) {
                actionsViewModel.archiveThreadsOrMessages(
                    threads = listOf(thread),
                    currentFolder = mainViewModel.currentFolder.value,
                    mailbox = mainViewModel.currentMailbox.value!!
                )
            }
        }
        SwipeAction.DELETE -> {
            descriptionDialog.deleteWithConfirmationPopup(
                folderRole = folderRole,
                count = 1,
                displayLoader = false,
                onCancel = {
                    // Notify only if the user cancelled the popup (e.g. the thread is not deleted),
                    // otherwise it will notify the next item in the list and make it slightly blink
                    if (threadListAdapter.dataSet.indexOfFirstThread(thread) == position) {
                        threadListAdapter.notifyItemChanged(position)
                    }
                },
                callback = {
                    if (isPermanentDeleteFolder) threadListAdapter.removeItem(position)
                    actionsViewModel.deleteThreadsOrMessages(
                        threads = listOf(thread),
                        currentFolder = mainViewModel.currentFolder.value,
                        mailbox = mainViewModel.currentMailbox.value!!
                    )
                },
            )
        }
        SwipeAction.FAVORITE -> {
            actionsViewModel.toggleThreadsOrMessagesFavoriteStatus(
                threadsUids = listOf(thread.uid),
                mailbox = mainViewModel.currentMailbox.value!!
            )
            true
        }
        SwipeAction.MOVE -> {
            val navController = findNavController()
            descriptionDialog.moveWithConfirmationPopup(folderRole, count = 1) {
                navController.animatedNavigation(
                    directions = ThreadListFragmentDirections.actionThreadListFragmentToFolderPickerFragment(
                        threadsUids = arrayOf(thread.uid),
                        action = FolderPickerAction.MOVE,
                        sourceFolderId = mainViewModel.currentFolderId ?: Folder.DUMMY_FOLDER_ID
                    ),
                )
            }
            true
        }
        SwipeAction.QUICKACTIONS_MENU -> {
            safelyNavigate(
                ThreadListFragmentDirections.actionThreadListFragmentToThreadActionsBottomSheetDialog(
                    threadUid = thread.uid,
                    shouldLoadDistantResources = false,
                )
            )
            true
        }
        SwipeAction.READ_UNREAD -> {
            actionsViewModel.toggleThreadsOrMessagesSeenStatus(
                threadsUids = listOf(thread.uid),
                currentFolderId = mainViewModel.currentFolderId,
                mailbox = mainViewModel.currentMailbox.value!!
            )
            mainViewModel.currentFilter.value != ThreadFilter.UNSEEN
        }
        SwipeAction.SPAM -> {
            actionsViewModel.toggleThreadsOrMessagesSpamStatus(
                threads = setOf(thread),
                currentFolderId = mainViewModel.currentFolderId,
                mailbox = mainViewModel.currentMailbox.value!!
            )
            false
        }
        SwipeAction.SNOOZE -> {
            val snoozeScheduleType = if (thread.isSnoozed()) {
                SnoozeScheduleType.Modify(thread.uid)
            } else {
                SnoozeScheduleType.Snooze(thread.uid)
            }
            navigateToSnoozeBottomSheet(snoozeScheduleType, thread.snoozeEndDate)
            true
        }
        SwipeAction.NONE -> error("Cannot swipe on an action which is not set")
    }

    private fun LocalSettings.setDefaultSwipeActions() {
        if (swipeRight == SwipeAction.TUTORIAL) swipeRight = SwipeActionsSettingsFragment.DEFAULT_SWIPE_ACTION_RIGHT
        if (swipeLeft == SwipeAction.TUTORIAL) swipeLeft = SwipeActionsSettingsFragment.DEFAULT_SWIPE_ACTION_LEFT
    }

    private fun List<ThreadListItem>.indexOfFirstThread(thread: Thread): Int {
        return indexOfFirst { (it as? ThreadListItem.Content)?.thread == thread }
    }
}
