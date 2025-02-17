/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024-2025 Infomaniak Network SA
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
package com.infomaniak.mail.utils

import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.R
import java.time.format.FormatStyle
import java.util.Date

object MailDateFormatUtils {

    fun Context.mailFormattedDate(date: Date): CharSequence = with(date) {
        return when {
            isToday() -> format(localHourFormat())
            isYesterday() -> getString(
                R.string.messageDetailsDateAt,
                getString(R.string.messageDetailsYesterday),
                format(localHourFormat()),
            )
            isThisYear() -> fullDateWithYear(this)
            else -> fullDateWithoutYear(date = this)
        }
    }

    fun Date.formatForHeader(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            formatWithLocal(FormatData.BOTH, FormatStyle.FULL, FormatStyle.SHORT)
        } else {
            format(FORMAT_DATE_DAY_FULL_MONTH_YEAR_WITH_TIME)
        }
    }
}

// Do not use the 12/24 hours format directly. Call localHourFormat() instead
private const val FORMAT_DATE_24HOUR = "HH:mm"
private const val FORMAT_DATE_12HOUR = "hh:mm a"
private const val FORMAT_DATE_WITH_YEAR = "d MMM yyyy"
private const val FORMAT_DATE_WITHOUT_YEAR = "d MMM"

fun Context.fullDateWithYear(date: Date): String {
    return date.formatDateTime(this, FORMAT_DATE_WITHOUT_YEAR, localHourFormat())
}

fun Context.fullDateWithoutYear(date: Date): String {
    return date.formatDateTime(this, FORMAT_DATE_WITH_YEAR, localHourFormat())
}

fun Context.dayOfWeekDate(date: Date): String {
    return date.formatDateTime(this, FORMAT_DATE_DAY_MONTH, localHourFormat())
}

private fun Date.formatDateTime(context: Context, dateFormat: String, timeFormat: String) = context.getString(
    R.string.messageDetailsDateAt,
    format(dateFormat),
    format(timeFormat),
)

private fun Context.localHourFormat(): String {
    return if (DateFormat.is24HourFormat(this)) FORMAT_DATE_24HOUR else FORMAT_DATE_12HOUR
}
