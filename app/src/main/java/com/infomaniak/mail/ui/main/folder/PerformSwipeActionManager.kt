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

import androidx.fragment.app.Fragment
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.legacy.utils.safeNavigate
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
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.main.folderPicker.FolderPickerAction
import com.infomaniak.mail.ui.main.settings.appearance.swipe.SwipeActionsSettingsFragment
import com.infomaniak.mail.ui.main.thread.ThreadViewModel.SnoozeScheduleType
import com.infomaniak.mail.utils.extensions.animatedNavigation
import com.infomaniak.mail.utils.extensions.archiveWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.deleteWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.getAnimatedNavOptions
import com.infomaniak.mail.utils.extensions.moveWithConfirmationPopup
import io.realm.kotlin.types.RealmInstant

object PerformSwipeActionManager {

    interface SwipeActionHost {
        val fragment: Fragment
        val mainViewModel: MainViewModel
        val localSettings: LocalSettings
        val threadListAdapter: ThreadListAdapter
        val descriptionDialog: DescriptionAlertDialog

        fun showSwipeActionIncompatible()

        fun directionsToMove(threadUid: String, sourceFolderId: String): NavDirections
        fun directionsToQuickActions(threadUid: String): NavDirections

        fun navigateToSnoozeBottomSheet(snoozeScheduleType: SnoozeScheduleType?, snoozeEndDate: RealmInstant?)
    }


    /**
     * Generic API usable by SearchFragment (and others).
     *
     * The boolean return value is used to know if we should keep the Thread in
     * the RecyclerView (true), or remove it when the swipe is done (false).
     */
    fun performSwipeAction(
        host: SwipeActionHost,
        swipeAction: SwipeAction,
        thread: Thread,
        position: Int,
        isPermanentDeleteFolder: Boolean,
    ): Boolean {
        val folderRole = thread.folder.role
        if (!swipeAction.canDisplay(folderRole, host.mainViewModel.featureFlagsLive.value, host.localSettings)) {
            host.showSwipeActionIncompatible()
            return true
        }

        trackEvent(MatomoCategory.SwipeActions, swipeAction.matomoName, TrackerAction.DRAG)

        val shouldKeepItemBecauseOfAction = performSwipeActionInternal(
            host = host,
            swipeAction = swipeAction,
            folderRole = folderRole,
            thread = thread,
            position = position,
            isPermanentDeleteFolder = isPermanentDeleteFolder,
        )

        val shouldKeepItemBecauseOfNoConnection = !host.mainViewModel.hasNetwork
        return shouldKeepItemBecauseOfAction || shouldKeepItemBecauseOfNoConnection
    }

    private fun performSwipeActionInternal(
        host: SwipeActionHost,
        swipeAction: SwipeAction,
        folderRole: FolderRole?,
        thread: Thread,
        position: Int,
        isPermanentDeleteFolder: Boolean,
    ) = when (swipeAction) {
        SwipeAction.TUTORIAL -> {
            host.localSettings.setDefaultSwipeActions()
            host.fragment.findNavController()
                .navigate(R.id.swipeActionsSettingsFragment, args = null, getAnimatedNavOptions())
            true
        }

        SwipeAction.ARCHIVE -> {
            host.descriptionDialog.archiveWithConfirmationPopup(
                folderRole = folderRole,
                count = 1,
                displayLoader = false,
                onCancel = {
                    if (host.threadListAdapter.dataSet.indexOfFirstThread(thread) == position) {
                        host.threadListAdapter.notifyItemChanged(position)
                    }
                },
            ) {
                host.mainViewModel.archiveThread(thread.uid)
            }
        }

        SwipeAction.DELETE -> {
            host.descriptionDialog.deleteWithConfirmationPopup(
                folderRole = folderRole,
                count = 1,
                displayLoader = false,
                onCancel = {
                    if (host.threadListAdapter.dataSet.indexOfFirstThread(thread) == position) {
                        host.threadListAdapter.notifyItemChanged(position)
                    }
                },
                callback = {
                    if (isPermanentDeleteFolder) host.threadListAdapter.removeItem(position)
                    host.mainViewModel.deleteThread(thread.uid)
                },
            )
        }

        SwipeAction.FAVORITE -> {
            host.mainViewModel.toggleThreadFavoriteStatus(thread.uid)
            true
        }

        SwipeAction.MOVE -> {
            val navController = host.fragment.findNavController()
            host.descriptionDialog.moveWithConfirmationPopup(folderRole, count = 1) {
                navController.animatedNavigation(
                    directions = host.directionsToMove(
                        threadUid = thread.uid,
                        sourceFolderId = host.mainViewModel.currentFolderId ?: Folder.DUMMY_FOLDER_ID,
                    ),
                    currentClassName = host.fragment.javaClass.name,
                )
            }
            true
        }

        SwipeAction.QUICKACTIONS_MENU -> {
            host.fragment.safelyNavigate(host.directionsToQuickActions(thread.uid))
            true
        }

        SwipeAction.READ_UNREAD -> {
            host.mainViewModel.toggleThreadSeenStatus(thread.uid)
            host.mainViewModel.currentFilter.value != ThreadFilter.UNSEEN
        }

        SwipeAction.SPAM -> {
            host.mainViewModel.toggleThreadSpamStatus(listOf(thread.uid))
            false
        }

        SwipeAction.SNOOZE -> {
            val snoozeScheduleType = if (thread.isSnoozed()) {
                SnoozeScheduleType.Modify(thread.uid)
            } else {
                SnoozeScheduleType.Snooze(thread.uid)
            }
            host.navigateToSnoozeBottomSheet(snoozeScheduleType, thread.snoozeEndDate)
            true
        }

        SwipeAction.NONE -> error("Cannot swipe on an action which is not set")
    }

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
            safeNavigate(ThreadListFragmentDirections.actionThreadListFragmentToSettingsFragment())
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
                mainViewModel.archiveThread(thread.uid)
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
                    mainViewModel.deleteThread(thread.uid)
                },
            )
        }
        SwipeAction.FAVORITE -> {
            mainViewModel.toggleThreadFavoriteStatus(thread.uid)
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
                    currentClassName = javaClass.name,
                )
            }
            true
        }
        SwipeAction.QUICKACTIONS_MENU -> {
            safeNavigate(
                ThreadListFragmentDirections.actionThreadListFragmentToThreadActionsBottomSheetDialog(
                    threadUid = thread.uid,
                    shouldLoadDistantResources = false,
                )
            )
            true
        }
        SwipeAction.READ_UNREAD -> {
            mainViewModel.toggleThreadSeenStatus(thread.uid)
            mainViewModel.currentFilter.value != ThreadFilter.UNSEEN
        }
        SwipeAction.SPAM -> {
            mainViewModel.toggleThreadSpamStatus(listOf(thread.uid))
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
