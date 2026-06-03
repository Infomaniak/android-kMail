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
package com.infomaniak.mail.data.models.calendar

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.R

enum class AttendanceState(
    val apiValue: String,
    @DrawableRes val icon: Int?,
    @ColorRes val iconColor: Int?,
    val matomoName: MatomoName?,
) {
    ACCEPTED("ACCEPTED", R.drawable.ic_check_rounded, R.color.greenSuccess, MatomoName.ReplyYes),
    NEEDS_ACTION("NEEDS-ACTION", null, null, null),
    TENTATIVE("TENTATIVE", R.drawable.ic_calendar_maybe, R.color.iconColorSecondaryText, MatomoName.ReplyMaybe),
    DECLINED("DECLINED", R.drawable.ic_calendar_no, R.color.redDestructiveAction, MatomoName.ReplyNo),
}
