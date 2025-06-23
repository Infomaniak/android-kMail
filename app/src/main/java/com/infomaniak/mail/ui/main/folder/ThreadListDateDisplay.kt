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

import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.text.format.DateUtils
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.infomaniak.core.utils.FORMAT_DATE_CLEAR_MONTH_DAY_ONE_CHAR
import com.infomaniak.core.utils.FORMAT_DATE_SHORT_DAY_ONE_CHAR
import com.infomaniak.core.utils.FORMAT_HOUR_MINUTES
import com.infomaniak.core.utils.FormatData
import com.infomaniak.core.utils.format
import com.infomaniak.core.utils.formatWithLocal
import com.infomaniak.core.utils.isInTheFuture
import com.infomaniak.core.utils.isThisYear
import com.infomaniak.core.utils.isToday
import com.infomaniak.core.utils.isYesterday
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.Companion.FORMAT_DAY_OF_THE_WEEK
import com.infomaniak.mail.utils.extensions.isSmallerThanDays
import com.infomaniak.mail.utils.extensions.toDate
import io.realm.kotlin.types.RealmInstant
import java.time.format.FormatStyle
import java.util.Date

enum class ThreadListDateDisplay(
    @DrawableRes val iconRes: Int?,
    @ColorRes val iconColorRes: Int?,
    val formatThreadDate: Context.(Thread) -> String,
) {
    Default(
        iconRes = null,
        iconColorRes = null,
        formatThreadDate = { thread -> defaultFormatting(thread.displayDate) }
    ),
    Scheduled(
        iconRes = R.drawable.ic_editor_clock_thick,
        iconColorRes = R.color.scheduledColor,
        formatThreadDate = { thread -> relativeFormatting(thread.displayDate) }
    ),
    Snoozed(
        iconRes = R.drawable.ic_alarm_clock_thick,
        iconColorRes = R.color.snoozeColor,
        formatThreadDate = { thread ->
            // If the thread is in SnoozeState.Snoozed then we necessarily have a snoozeEndDate
            val date = thread.snoozeEndDate ?: RealmInstant.MIN
            relativeFormatting(date)
        }
    ),
    Unsnoozed(
        iconRes = R.drawable.ic_alarm_clock_thick,
        iconColorRes = R.color.snoozeColor,
        formatThreadDate = { thread ->
            // If the thread is in SnoozeState.Unsnoozed then we necessarily have a snoozeEndDate
            val date = thread.snoozeEndDate ?: RealmInstant.MIN
            defaultFormatting(date)
        }
    )
}

private fun RealmInstant.isInTheFuture() = epochSeconds * 1_000L > System.currentTimeMillis()

private fun Context.relativeFormatting(date: RealmInstant) = DateUtils.getRelativeDateTimeString(
    this,
    date.epochSeconds * 1_000L,
    DateUtils.DAY_IN_MILLIS,
    DateUtils.WEEK_IN_MILLIS,
    0,
)?.toString() ?: ""

private fun Context.defaultFormatting(date: RealmInstant) = with(date.toDate()) {
    when {
        isInTheFuture() -> formatNumericalDayMonthYear()
        isToday() -> format(FORMAT_HOUR_MINUTES)
        isYesterday() -> getString(R.string.messageDetailsYesterday)
        isSmallerThanDays(6) -> format(FORMAT_DAY_OF_THE_WEEK)
        isThisYear() -> format(FORMAT_DATE_SHORT_DAY_ONE_CHAR)
        else -> formatNumericalDayMonthYear()
    }
}

private fun Date.formatNumericalDayMonthYear(): String {
    return if (SDK_INT >= 26) {
        formatWithLocal(FormatData.DATE, FormatStyle.SHORT)
    } else {
        format(FORMAT_DATE_CLEAR_MONTH_DAY_ONE_CHAR) // Fallback on unambiguous date format for any local
    }
}
