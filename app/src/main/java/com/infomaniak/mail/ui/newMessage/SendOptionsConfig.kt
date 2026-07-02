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
package com.infomaniak.mail.ui.newMessage

import androidx.annotation.StringRes
import com.infomaniak.mail.R

sealed class ScheduleConfig {
    data object None : ScheduleConfig()
    data class Scheduled(val epochMillis: Long, val isCustom: Boolean = false) : ScheduleConfig()
}

sealed class ReminderConfig {
    data object None : ReminderConfig()
    data class Delayed(val delayMinutes: Int, val isCustom: Boolean = false) : ReminderConfig()
}

enum class ReminderPreset(val titleRes: Int, val delayMinutes: Int) {
    HOURS_24(R.plurals.hoursBeforeSendingReminder, MINUTES_IN_A_DAY),
    DAYS_3(R.plurals.daysBeforeSendingReminder, 3 * MINUTES_IN_A_DAY),
    DAYS_7(R.plurals.daysBeforeSendingReminder, 7 * MINUTES_IN_A_DAY);
}

const val MIN_SELECTABLE_DATE_MINUTES = 5
const val MINUTES_IN_A_DAY = 1440
