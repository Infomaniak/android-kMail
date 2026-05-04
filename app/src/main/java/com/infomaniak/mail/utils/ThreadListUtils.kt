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
package com.infomaniak.mail.utils

import android.content.Context
import com.infomaniak.core.common.utils.format
import com.infomaniak.core.common.utils.isInTheFuture
import com.infomaniak.core.common.utils.isThisMonth
import com.infomaniak.core.common.utils.isThisWeek
import com.infomaniak.core.common.utils.isThisYear
import com.infomaniak.core.common.utils.isToday
import com.infomaniak.core.common.utils.isYesterday
import com.infomaniak.core.legacy.utils.capitalizeFirstChar
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.extensions.isLastWeek
import com.infomaniak.mail.utils.extensions.toDate

object ThreadListUtils {
    fun getSectionTitle(thread: Thread, context: Context): String = with(thread.internalDate.toDate()) {
        return when {
            isInTheFuture() -> context.getString(R.string.comingSoon)
            isToday() -> context.getString(R.string.threadListSectionToday)
            isYesterday() -> context.getString(R.string.messageDetailsYesterday)
            isThisWeek() -> context.getString(R.string.threadListSectionThisWeek)
            isLastWeek() -> context.getString(R.string.threadListSectionLastWeek)
            isThisMonth() -> context.getString(R.string.threadListSectionThisMonth)
            isThisYear() -> format("MMMM").capitalizeFirstChar()
            else -> format("MMMM yyyy").capitalizeFirstChar()
        }
    }
}
