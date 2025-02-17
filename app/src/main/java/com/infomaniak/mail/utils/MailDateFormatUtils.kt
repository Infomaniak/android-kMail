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

    private const val FORMAT_EMAIL_DATE_24HOUR = "HH:mm"
    private const val FORMAT_EMAIL_DATE_12HOUR = "hh:mm a"
    private const val FORMAT_EMAIL_DATE_SHORT_DATE = "d MMM"
    private const val FORMAT_EMAIL_DATE_LONG_DATE = "d MMM yyyy"

    private fun Context.localHourFormat(): String {
        return if (DateFormat.is24HourFormat(this)) FORMAT_EMAIL_DATE_24HOUR else FORMAT_EMAIL_DATE_12HOUR
    }

    fun mailFormattedDate(context: Context, date: Date): CharSequence = with(date) {
        return@with when {
            isToday() -> format(context.localHourFormat())
            isYesterday() -> context.getString(
                R.string.messageDetailsDateAt,
                context.getString(R.string.messageDetailsYesterday),
                format(context.localHourFormat()),
            )
            isThisYear() -> context.getString(
                R.string.messageDetailsDateAt,
                format(FORMAT_EMAIL_DATE_SHORT_DATE),
                format(context.localHourFormat()),
            )
            else -> mostDetailedDate(context, date = this@with)
        }
    }

    fun mostDetailedDate(context: Context, date: Date): String = with(date) {
        return@with formatDateTime(context, FORMAT_EMAIL_DATE_LONG_DATE, context.localHourFormat())
    }

    fun dayOfWeekDate(context: Context, date: Date): String = with(date) {
        return@with formatDateTime(context, FORMAT_DATE_DAY_MONTH, context.localHourFormat())
    }

    private fun Date.formatDateTime(context: Context, dateFormat: String, timeFormat: String) = context.getString(
        R.string.messageDetailsDateAt,
        format(dateFormat),
        format(timeFormat),
    )

    fun Date.formatForHeader(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            formatWithLocal(FormatData.BOTH, FormatStyle.FULL, FormatStyle.SHORT)
        } else {
            format(FORMAT_DATE_DAY_FULL_MONTH_YEAR_WITH_TIME)
        }
    }
}
