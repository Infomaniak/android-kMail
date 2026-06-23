/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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

import com.infomaniak.dragdropswiperecyclerview.listener.OnItemSwipeListener
import com.infomaniak.dragdropswiperecyclerview.listener.OnItemSwipeListener.SwipeDirection
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.SwipeAction
import com.infomaniak.mail.data.models.extensions.folder
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.Utils.isPermanentDeleteFolder

object ThreadSwipeListenerFactory {
    fun create(
        localSettings: LocalSettings,
        threadListAdapter: ThreadListAdapter,
        onRecoveringStarted: () -> Unit,
        performSwipeActionOnThread: (
            swipeAction: SwipeAction,
            thread: Thread,
            position: Int,
            isPermanentDeleteFolder: Boolean,
        ) -> Boolean,
    ): OnItemSwipeListener<ThreadListItem.Content> {
        return object : OnItemSwipeListener<ThreadListItem.Content> {
            override fun onItemSwiped(
                position: Int,
                direction: SwipeDirection,
                item: ThreadListItem.Content,
            ): Boolean {
                val swipeAction = when (direction) {
                    SwipeDirection.LEFT_TO_RIGHT -> localSettings.swipeRight
                    SwipeDirection.RIGHT_TO_LEFT -> localSettings.swipeLeft
                    else -> error("Only SwipeDirection.LEFT_TO_RIGHT and SwipeDirection.RIGHT_TO_LEFT can be triggered")
                }

                val isPermanentDeleteFolder = isPermanentDeleteFolder(item.thread.folder.role)
                val shouldKeepItem =
                    performSwipeActionOnThread(swipeAction, item.thread, position, isPermanentDeleteFolder)

                threadListAdapter.apply {
                    blockOtherSwipes()

                    if (swipeAction != SwipeAction.DELETE || !isPermanentDeleteFolder) {
                        notifyItemChanged(position)
                    }
                }

                onRecoveringStarted()
                return shouldKeepItem
            }
        }
    }
}
