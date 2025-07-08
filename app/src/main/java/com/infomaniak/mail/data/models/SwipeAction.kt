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
    val matomoValue: String,
    private val swipeDisplayBehavior: SwipeDisplayBehavior = alwaysDisplay,
) : SwipeDisplayBehavior by swipeDisplayBehavior {
    DELETE(R.string.actionDelete, R.color.swipeDelete, R.drawable.ic_bin, MatomoName.Delete.toString()),
    ARCHIVE(R.string.actionArchive, R.color.swipeArchive, R.drawable.ic_archive_folder, MatomoName.Archive.toString()),
    READ_UNREAD(
        R.string.settingsSwipeActionReadUnread,
        R.color.swipeReadUnread,
        R.drawable.ic_envelope,
        MatomoName.MarkAsSeen.toString(),
    ),
    MOVE(R.string.actionMove, R.color.swipeMove, R.drawable.ic_email_action_move, MatomoName.Move.toString()),
    FAVORITE(R.string.actionShortStar, R.color.swipeFavorite, R.drawable.ic_star, MatomoName.Favorite.toString()),
    SNOOZE(R.string.actionSnooze, R.color.swipeSnooze, R.drawable.ic_alarm_clock, MatomoName.Snooze.toString(), snoozeDisplay),
    SPAM(R.string.actionSpam, R.color.swipeSpam, R.drawable.ic_spam, MatomoName.Spam.toString()),
    QUICKACTIONS_MENU(
        R.string.settingsSwipeActionQuickActionsMenu,
        R.color.swipeQuickActionMenu,
        R.drawable.ic_param_dots,
        "quickActions",
    ),
    TUTORIAL(R.string.settingsSwipeActionToDefine, R.color.progressbarTrackColor, null, "tutorial"),
    NONE(R.string.settingsSwipeActionNone, R.color.swipeNone, null, "none", neverDisplay);

    @ColorInt
    fun getBackgroundColor(context: Context): Int = context.getColor(colorRes)
}

private val alwaysDisplay = SwipeDisplayBehavior { _, _, _ -> true }

private val neverDisplay = SwipeDisplayBehavior { _, _, _ -> false }

private val snoozeDisplay = SwipeDisplayBehavior { role, featureFlags, localSettings ->
    (role == FolderRole.INBOX || role == FolderRole.SNOOZED) && SharedUtils.isSnoozeAvailable(featureFlags, localSettings)
}
