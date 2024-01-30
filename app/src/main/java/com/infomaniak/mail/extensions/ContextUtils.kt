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

import android.content.Context
import com.infomaniak.lib.core.utils.format
import com.infomaniak.lib.core.utils.isThisYear
import com.infomaniak.lib.core.utils.isToday
import com.infomaniak.lib.core.utils.isYesterday
import com.infomaniak.mail.R
import java.util.Date

private const val FORMAT_EMAIL_DATE_HOUR = "HH:mm"
private const val FORMAT_EMAIL_DATE_SHORT_DATE = "d MMM"
private const val FORMAT_EMAIL_DATE_LONG_DATE = "d MMM yyyy"

fun Context.mailFormattedDate(date: Date): CharSequence = with(date) {
    return@with when {
        isToday() -> format(FORMAT_EMAIL_DATE_HOUR)
        isYesterday() -> getString(
            R.string.messageDetailsDateAt,
            getString(R.string.messageDetailsYesterday),
            format(FORMAT_EMAIL_DATE_HOUR),
        )
        isThisYear() -> getString(
            R.string.messageDetailsDateAt,
            format(FORMAT_EMAIL_DATE_SHORT_DATE),
            format(FORMAT_EMAIL_DATE_HOUR),
        )
        else -> this@mailFormattedDate.mostDetailedDate(date = this@with)
    }
}

fun Context.mostDetailedDate(date: Date): String = with(date) {
    return@with getString(
        R.string.messageDetailsDateAt,
        format(FORMAT_EMAIL_DATE_LONG_DATE),
        format(FORMAT_EMAIL_DATE_HOUR),
    )
}
