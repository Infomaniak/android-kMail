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
import android.os.Build
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.thread.Thread.Companion.FORMAT_DAY_OF_THE_WEEK
import com.infomaniak.mail.utils.extensions.isSmallerThanDays
import com.infomaniak.mail.utils.extensions.toDate
import io.realm.kotlin.types.RealmInstant
import java.time.format.FormatStyle
import java.util.Date

enum class DateDisplay(@DrawableRes val icon: Int?, val formatDate: Context.(RealmInstant) -> String) {
    Default(
        icon = null,
        formatDate = { date -> formatPastDate(date) }
    ),
    Scheduled(
        icon = R.drawable.ic_scheduled_messages,
        formatDate = { date -> formatRelativeFutureDate(date) }
    ),
}

private fun Context.formatRelativeFutureDate(date: RealmInstant) = DateUtils.getRelativeDateTimeString(
    this,
    date.epochSeconds * 1000,
    DateUtils.DAY_IN_MILLIS,
    DateUtils.WEEK_IN_MILLIS,
    DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH,
)!!.toString()

private fun Context.formatPastDate(date: RealmInstant) = with(date.toDate()) {
    when {
        isInTheFuture() -> formatNumericalDayMonthYear()
        isToday() -> format(FORMAT_DATE_HOUR_MINUTE)
        isYesterday() -> getString(R.string.messageDetailsYesterday)
        isSmallerThanDays(6) -> format(FORMAT_DAY_OF_THE_WEEK)
        isThisYear() -> format(FORMAT_DATE_SHORT_DAY_ONE_CHAR)
        else -> formatNumericalDayMonthYear()
    }
}

private fun Date.formatNumericalDayMonthYear(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        formatWithLocal(FormatData.DATE, FormatStyle.SHORT)
    } else {
        format(FORMAT_DATE_CLEAR_MONTH_DAY_ONE_CHAR) // Fallback on unambiguous date format for any local
    }
}
