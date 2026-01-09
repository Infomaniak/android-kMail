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
package com.infomaniak.mail.ui.alertDialogs

import android.content.Context
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.CompositeDateValidator
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.DateValidatorPointForward
import com.infomaniak.core.common.utils.addYears
import com.infomaniak.mail.R
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import java.util.Date
import javax.inject.Inject

@ActivityScoped
open class SelectDateAndTimeForSnoozeDialog @Inject constructor(
    @ActivityContext private val activityContext: Context,
) : SelectDateAndTimeDialog(activityContext) {

    override fun defineCalendarConstraint(): CalendarConstraints.Builder {
        val dateValidators = listOf(
            DateValidatorPointForward.now(),
            DateValidatorPointBackward.before(Date().addYears(MAX_SCHEDULE_DELAY_YEARS).time),
        )
        return CalendarConstraints.Builder().setValidator(CompositeDateValidator.allOf(dateValidators))
    }

    override fun getDelayTooShortErrorMessage(): String = activityContext.getString(
        R.string.errorScheduledSnoozeDelayTooShort,
        MIN_SELECTABLE_DATE_MINUTES,
    )

    companion object {
        const val MAX_SCHEDULE_DELAY_YEARS = 1
    }
}
