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
import com.infomaniak.mail.MatomoMail
import com.infomaniak.mail.R

enum class SwipeAction(
    @StringRes val nameRes: Int,
    @ColorRes val colorRes: Int,
    @DrawableRes val iconRes: Int?,
    val matomoValue: String,
) {
    DELETE(R.string.actionDelete, R.color.swipeDelete, R.drawable.ic_bin, MatomoMail.ACTION_DELETE_NAME),
    ARCHIVE(R.string.actionArchive, R.color.swipeArchive, R.drawable.ic_archive_folder, MatomoMail.ACTION_ARCHIVE_NAME),
    READ_UNREAD(
        R.string.settingsSwipeActionReadUnread,
        R.color.swipeReadUnread,
        R.drawable.ic_envelope,
        MatomoMail.ACTION_MARK_AS_SEEN_NAME,
    ),
    MOVE(R.string.actionMove, R.color.swipeMove, R.drawable.ic_email_action_move, MatomoMail.ACTION_MOVE_NAME),
    FAVORITE(R.string.actionShortStar, R.color.swipeFavorite, R.drawable.ic_star, MatomoMail.ACTION_FAVORITE_NAME),
    POSTPONE(R.string.actionSnooze, R.color.swipePostpone, R.drawable.ic_alarm_clock, MatomoMail.ACTION_SNOOZE_NAME),
    SPAM(R.string.actionSpam, R.color.swipeSpam, R.drawable.ic_spam, MatomoMail.ACTION_SPAM_NAME),
    QUICKACTIONS_MENU(
        R.string.settingsSwipeActionQuickActionsMenu,
        R.color.swipeQuickActionMenu,
        R.drawable.ic_param_dots,
        "quickActions",
    ),
    TUTORIAL(R.string.settingsSwipeActionNone, R.color.progressbarTrackColor, null, "tutorial"),
    NONE(R.string.settingsSwipeActionNone, R.color.swipeNone, null, "none");

    @ColorInt
    fun getBackgroundColor(context: Context): Int = context.getColor(colorRes)
}
