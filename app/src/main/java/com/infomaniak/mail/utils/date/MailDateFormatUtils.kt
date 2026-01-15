/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024-2026 Infomaniak Network SA
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
package com.infomaniak.mail.utils.date

import android.content.Context
import com.infomaniak.core.common.utils.FormatData
import com.infomaniak.core.common.utils.formatWithLocal
import com.infomaniak.core.common.utils.isThisYear
import com.infomaniak.core.common.utils.isToday
import com.infomaniak.core.common.utils.isYesterday
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.date.DateFormatUtils.dayOfWeekDateWithYear
import com.infomaniak.mail.utils.date.DateFormatUtils.dayOfWeekDateWithoutYear
import com.infomaniak.mail.utils.date.DateFormatUtils.formatTime
import com.infomaniak.mail.utils.date.DateFormatUtils.fullDateWithYear
import com.infomaniak.mail.utils.date.DateFormatUtils.fullDateWithoutYear
import java.time.format.FormatStyle
import java.util.Date

object MailDateFormatUtils {

    fun Context.mailFormattedDate(date: Date): CharSequence = with(date) {
        return when {
            isToday() -> formatTime(this)
            isYesterday() -> getString(
                R.string.messageDetailsDateAt,
                getString(R.string.messageDetailsYesterday),
                formatTime(this),
            )
            isThisYear() -> fullDateWithoutYear(this)
            else -> fullDateWithYear(this)
        }
    }

    fun Date.formatForHeader(): String {
        return formatWithLocal(FormatData.BOTH, FormatStyle.FULL, FormatStyle.SHORT)
    }

    fun Context.formatDayOfWeekAdaptiveYear(date: Date): String = when {
        date.isThisYear() -> dayOfWeekDateWithoutYear(date)
        else -> dayOfWeekDateWithYear(date)
    }
}
