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
package com.infomaniak.mail.data.models

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.utils.SharedUtils

enum class SwipeAction(
    @StringRes val nameRes: Int,
    @ColorRes val colorRes: Int,
    @DrawableRes val iconRes: Int?,
    val matomoName: MatomoName,
    private val swipeDisplayBehavior: SwipeDisplayBehavior = alwaysDisplay,
) : SwipeDisplayBehavior by swipeDisplayBehavior {
    DELETE(R.string.actionDelete, R.color.swipeDelete, R.drawable.ic_bin, MatomoName.Delete),
    ARCHIVE(R.string.actionArchive, R.color.swipeArchive, R.drawable.ic_archive_folder, MatomoName.Archive),
    READ_UNREAD(
        R.string.settingsSwipeActionReadUnread,
        R.color.swipeReadUnread,
        R.drawable.ic_envelope,
        MatomoName.MarkAsSeen,
    ),
    MOVE(R.string.actionMove, R.color.swipeMove, R.drawable.ic_email_action_move, MatomoName.Move),
    FAVORITE(R.string.actionShortStar, R.color.swipeFavorite, R.drawable.ic_star, MatomoName.Favorite),
    SNOOZE(R.string.actionSnooze, R.color.swipeSnooze, R.drawable.ic_alarm_clock, MatomoName.Snooze, snoozeDisplay),
    SPAM(R.string.actionSpam, R.color.swipeSpam, R.drawable.ic_spam, MatomoName.Spam),
    QUICKACTIONS_MENU(
        R.string.settingsSwipeActionQuickActionsMenu,
        R.color.swipeQuickActionMenu,
        R.drawable.ic_param_dots,
        MatomoName.QuickActions,
    ),
    TUTORIAL(R.string.settingsSwipeActionToDefine, R.color.progressbarTrackColor, null, MatomoName.Tutorial),
    NONE(R.string.settingsSwipeActionNone, R.color.swipeNone, null, MatomoName.None, neverDisplay);

    @ColorInt
    fun getBackgroundColor(context: Context): Int = context.getColor(colorRes)
}

private val alwaysDisplay = SwipeDisplayBehavior { _, _, _ -> true }

private val neverDisplay = SwipeDisplayBehavior { _, _, _ -> false }

private val snoozeDisplay = SwipeDisplayBehavior { role, featureFlags, localSettings ->
    (role == FolderRole.INBOX || role == FolderRole.SNOOZED) && SharedUtils.isSnoozeAvailable(featureFlags, localSettings)
}
