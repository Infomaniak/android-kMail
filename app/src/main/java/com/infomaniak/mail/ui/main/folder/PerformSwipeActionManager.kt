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
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.SwipeAction
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.thread.Thread
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
    fun performSwipeAction(
        host: SwipeActionHost,
        swipeAction: SwipeAction,
        thread: Thread,
        position: Int,
        isPermanentDeleteFolder: Boolean,
        currentMailbox: Mailbox,
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
            currentMailbox = currentMailbox
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
        currentMailbox: Mailbox,
    ): Boolean {
        return when (swipeAction) {
            SwipeAction.TUTORIAL -> {
                host.localSettings.setDefaultSwipeActions()
                host.fragment.findNavController()
                    .navigate(R.id.swipeActionsSettingsFragment, args = null, getAnimatedNavOptions())
                true
            }

            SwipeAction.ARCHIVE -> {
                handleArchiveSwipe(host, thread, position, folderRole, currentMailbox)
            }

            SwipeAction.DELETE -> {
                handleDeleteSwipe(host, thread, position, isPermanentDeleteFolder, currentMailbox)
            }

            SwipeAction.FAVORITE -> {
                host.actionsViewModel.toggleThreadsFavoriteStatus(
                    threadsUids = listOf(thread.uid),
                    shouldFavorite = !thread.isFavorite,
                    mailbox = currentMailbox
                )
                true
            }

            SwipeAction.MOVE -> {
                val navController = host.fragment.findNavController()
                host.descriptionDialog.moveWithConfirmationPopup(folderRole, count = 1) {
                    navController.animatedNavigation(
                        directions = host.directionsToMove(
                            threadUid = thread.uid,
                            sourceFolderId = thread.folderId,
                        )
                    )
                }
                true
            }

            SwipeAction.QUICKACTIONS_MENU -> {
                host.fragment.safelyNavigate(host.directionsToQuickActions(thread.uid))
                true
            }

            SwipeAction.READ_UNREAD -> {
                host.actionsViewModel.toggleThreadsSeenStatus(
                    threadsUids = listOf(thread.uid),
                    shouldRead = !thread.isSeen,
                    currentFolderId = thread.folderId,
                    mailbox = currentMailbox,
                )
                true
            }

            SwipeAction.SPAM -> {
                host.actionsViewModel.toggleThreadsSpamStatus(
                    threads = setOf(thread),
                    currentFolderId = thread.folderId,
                    mailbox = currentMailbox,
                )
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
    }

    private fun handleArchiveSwipe(
        host: SwipeActionHost,
        thread: Thread,
        position: Int,
        folderRole: FolderRole?,
        currentMailBox: Mailbox,
    ): Boolean {
        fun onCancel() {
            // Notify only if the user cancelled the popup (e.g. the thread is not deleted),
            // otherwise it will notify the next item in the list and make it slightly blink
            if (host.threadListAdapter.dataSet.indexOfFirstThread(thread) == position) {
                host.threadListAdapter.notifyItemChanged(position)
            }
        }

        fun onSuccess() {
            host.actionsViewModel.archiveThreads(
                threads = listOf(thread),
                currentFolderId = thread.folderId,
                mailbox = currentMailBox,
            )
        }

        return host.descriptionDialog.archiveWithConfirmationPopup(
            folderRole = folderRole,
            count = 1,
            displayLoader = false,
            onCancel = ::onCancel,
            onPositiveButtonClicked = ::onSuccess,
        )
    }

    private fun handleDeleteSwipe(
        host: SwipeActionHost,
        thread: Thread,
        position: Int,
        isPermanentDeleteFolder: Boolean,
        currentMailBox: Mailbox,
    ): Boolean {
        fun onCancel() {
            // Notify only if the user cancelled the popup (e.g. the thread is not deleted),
            // otherwise it will notify the next item in the list and make it slightly blink
            if (host.threadListAdapter.dataSet.indexOfFirstThread(thread) == position) {
                host.threadListAdapter.notifyItemChanged(position)
            }
        }

        fun onHandleDelete() {
            if (isPermanentDeleteFolder) host.threadListAdapter.removeItem(position)
            host.actionsViewModel.deleteThreads(
                threads = listOf(thread),
                currentFolderId = thread.folderId,
                mailbox = currentMailBox,
            )
        }

        val folderRoles =
            thread.messages.mapNotNull { message -> if (message.isSnoozed()) FolderRole.SNOOZED else message.folder.role }

        return host.descriptionDialog.deleteWithConfirmationPopup(
            messagesFolderRoles = folderRoles,
            currentFolderRole = thread.folder.role,
            count = 1,
            displayLoader = false,
            onCancel = ::onCancel,
            callback = ::onHandleDelete,
        )
    }


    private fun LocalSettings.setDefaultSwipeActions() {
        if (swipeRight == SwipeAction.TUTORIAL) swipeRight = SwipeActionsSettingsFragment.DEFAULT_SWIPE_ACTION_RIGHT
        if (swipeLeft == SwipeAction.TUTORIAL) swipeLeft = SwipeActionsSettingsFragment.DEFAULT_SWIPE_ACTION_LEFT
    }

    private fun List<ThreadListItem>.indexOfFirstThread(thread: Thread): Int {
        return indexOfFirst { (it as? ThreadListItem.Content)?.thread == thread }
    }
}
