/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.mail.extensions

import android.os.Build
import com.infomaniak.lib.core.utils.FORMAT_DATE_CLEAR_MONTH_DAY_ONE_CHAR
import com.infomaniak.lib.core.utils.FORMAT_EVENT_DATE
import com.infomaniak.lib.core.utils.format
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

fun Date.formatNumericalDayMonthYear(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // Adapts order of day, month and year according to the locale
        val shortDateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
        toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(shortDateFormatter)
    } else {
        // Fallback on unambiguous date format for any locale
        format(FORMAT_DATE_CLEAR_MONTH_DAY_ONE_CHAR)
    }
}

//TODO A refacto quand la PR de Gibran sera mergé
fun Date.formatNumericalDayMonthYearWithTime(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val shortDateFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        toInstant().atZone(ZoneId.systemDefault()).format(shortDateFormatter)
    } else {
        // Fallback on unambiguous date format for any locale
        format(FORMAT_EVENT_DATE)
    }
}
