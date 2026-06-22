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
package com.infomaniak.mail.utils.extensions

import com.infomaniak.dragdropswiperecyclerview.DragDropSwipeRecyclerView
import com.infomaniak.dragdropswiperecyclerview.DragDropSwipeRecyclerView.ListOrientation.DirectionFlag
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.ThreadDensity
import com.infomaniak.mail.data.models.FolderRole
import com.infomaniak.mail.data.models.SwipeAction
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.ui.main.folder.DateSeparatorItemDecoration
import com.infomaniak.mail.ui.main.folder.HeaderItemDecoration
import com.infomaniak.mail.ui.main.folder.ThreadListAdapter
import com.infomaniak.mail.ui.main.folder.ThreadListItem

fun DragDropSwipeRecyclerView.addStickyDateDecoration(adapter: ThreadListAdapter, threadDensity: ThreadDensity) {

    addItemDecoration(
        HeaderItemDecoration(
            parent = this,
            shouldFadeOutHeader = false,
            isHeader = { position ->
                return@HeaderItemDecoration position >= 0 && adapter.dataSet[position] is ThreadListItem.SectionTitle
            },
        ),
    )

    if (threadDensity == ThreadDensity.NORMAL) addItemDecoration(DateSeparatorItemDecoration())
}

fun DragDropSwipeRecyclerView.updateSwipeAvailability(
    localSettings: LocalSettings,
    isMultiSelectOn: Boolean,
    isAllowedToSwipe: (DirectionFlag) -> Boolean
) {
    val isLeftEnabled = localSettings.swipeLeft != SwipeAction.NONE && !isMultiSelectOn && isAllowedToSwipe(DirectionFlag.LEFT)
    if (isLeftEnabled) enableSwipeDirection(DirectionFlag.LEFT) else disableSwipeDirection(DirectionFlag.LEFT)

    val isRightEnabled = localSettings.swipeRight != SwipeAction.NONE && !isMultiSelectOn && isAllowedToSwipe(DirectionFlag.RIGHT)
    if (isRightEnabled) enableSwipeDirection(DirectionFlag.RIGHT) else disableSwipeDirection(DirectionFlag.RIGHT)
}

fun DragDropSwipeRecyclerView.updateSwipeActionEnabledUi(
    swipeAction: SwipeAction,
    swipeDirection: DirectionFlag,
    isEnabled: Boolean,
) {
    fun SwipeAction.iconResOrDisabled(): Int? = if (isEnabled) iconRes else R.drawable.ic_close_small
    fun SwipeAction.backgroundColorOrDisabled(): Int {
        return if (isEnabled) getBackgroundColor(context) else SwipeAction.NONE.getBackgroundColor(context)
    }

    if (swipeDirection == DirectionFlag.LEFT) {
        behindSwipedItemIconDrawableId = swipeAction.iconResOrDisabled()
        behindSwipedItemBackgroundColor = swipeAction.backgroundColorOrDisabled()
    } else if (swipeDirection == DirectionFlag.RIGHT) {
        behindSwipedItemIconSecondaryDrawableId = swipeAction.iconResOrDisabled()
        behindSwipedItemBackgroundSecondaryColor = swipeAction.backgroundColorOrDisabled()
    }
}

fun DragDropSwipeRecyclerView.updateSwipeActionsUi(
    localSettings: LocalSettings,
    featureFlags: Mailbox.FeatureFlagSet?,
    folderRole: FolderRole?
) {
    apply {
        updateSwipeActionEnabledUi(
            swipeAction = localSettings.swipeLeft,
            swipeDirection = DirectionFlag.LEFT,
            isEnabled = localSettings.swipeLeft.canDisplay(folderRole, featureFlags, localSettings),
        )
        updateSwipeActionEnabledUi(
            swipeAction = localSettings.swipeRight,
            swipeDirection = DirectionFlag.RIGHT,
            isEnabled = localSettings.swipeRight.canDisplay(folderRole, featureFlags, localSettings),
        )
    }
}
