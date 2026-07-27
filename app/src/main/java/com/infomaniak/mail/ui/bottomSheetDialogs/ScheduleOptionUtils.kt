/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025-2026 Infomaniak Network SA
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
package com.infomaniak.mail.ui.bottomSheetDialogs

import com.infomaniak.core.common.utils.getNextMonday
import com.infomaniak.core.common.utils.getTimeAtHour
import com.infomaniak.core.common.utils.isAtLeastXMinutesInTheFuture
import com.infomaniak.core.common.utils.isWeekend
import com.infomaniak.core.common.utils.tomorrow
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleOptionUtils.HIDE_INTERVAL
import com.infomaniak.mail.ui.newMessage.MIN_SELECTABLE_DATE_MINUTES
import java.util.Calendar
import java.util.Date
import kotlin.time.Duration.Companion.minutes

object ScheduleOptionUtils {
    val HIDE_INTERVAL = 5.minutes // Beware: the API refuses schedules smaller than 5 minutes

    fun getLastScheduleOptionDate(
        lastSelectedEpoch: Long?,
        currentlyScheduledEpochMillis: Long?,
    ): Date? {
        val lastSelectedDate = lastSelectedEpoch?.let { Date(it) }

        return if (
            lastSelectedDate?.isAtLeastXMinutesInTheFuture(MIN_SELECTABLE_DATE_MINUTES) == true &&
            lastSelectedDate.isNotAlreadySelected(currentlyScheduledEpochMillis)
        ) {
            lastSelectedDate
        } else {
            null
        }
    }

    fun getAvailableScheduleOptions(currentlyScheduledEpochMillis: Long?): List<ScheduleOption> {
        val currentTime = Date()
        return WeekPeriod.getCurrent().scheduleOptions.filter { scheduleOption ->
            scheduleOption.canBeDisplayedAt(currentTime) &&
                    scheduleOption.date().isNotAlreadySelected(currentlyScheduledEpochMillis)
        }
    }

    private fun Date.isNotAlreadySelected(currentlyScheduledEpochMillis: Long?): Boolean {
        return time.truncateToMinute() != currentlyScheduledEpochMillis?.truncateToMinute()
    }

    private fun Long.truncateToMinute(): Long {
        return Calendar.getInstance().apply {
            timeInMillis = this@truncateToMinute
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time.time
    }
}

enum class ScheduleOption(
    private val day: RelativeDay,
    private val hour: HourOfTheDay,
    val titleRes: Int,
    val iconRes: Int,
    val matomoName: MatomoName,
) {
    LaterThisMorning(
        day = RelativeDay.Today,
        hour = HourOfTheDay.Morning,
        titleRes = R.string.laterThisMorning,
        iconRes = R.drawable.ic_morning_sunrise_schedule,
        matomoName = MatomoName.LaterThisMorning,
    ),
    ThisAfternoon(
        day = RelativeDay.Today,
        hour = HourOfTheDay.Afternoon,
        titleRes = R.string.thisAfternoon,
        iconRes = R.drawable.ic_afternoon_schedule,
        matomoName = MatomoName.ThisAfternoon,
    ),
    ThisEvening(
        day = RelativeDay.Today,
        hour = HourOfTheDay.Evening,
        titleRes = R.string.thisEvening,
        iconRes = R.drawable.ic_evening_schedule,
        matomoName = MatomoName.ThisEvening,
    ),
    TomorrowMorning(
        day = RelativeDay.Tomorrow,
        hour = HourOfTheDay.Morning,
        titleRes = R.string.tomorrowMorning,
        iconRes = R.drawable.ic_morning_schedule,
        matomoName = MatomoName.TomorrowMorning,
    ),
    NextMondayMorning(
        day = RelativeDay.NextMonday,
        hour = HourOfTheDay.Morning,
        titleRes = R.string.nextMonday,
        iconRes = R.drawable.ic_arrow_return,
        matomoName = MatomoName.NextMonday,
    ),
    MondayMorning(
        day = RelativeDay.NextMonday,
        hour = HourOfTheDay.Morning,
        titleRes = R.string.mondayMorning,
        iconRes = R.drawable.ic_morning_schedule,
        matomoName = MatomoName.NextMondayMorning,
    ),
    MondayAfternoon(
        day = RelativeDay.NextMonday,
        hour = HourOfTheDay.Afternoon,
        titleRes = R.string.mondayAfternoon,
        iconRes = R.drawable.ic_afternoon_schedule,
        matomoName = MatomoName.NextMondayAfternoon,
    );

    fun date(): Date = day.getDate().getTimeAtHour(hour.hourOfTheDay)
    fun canBeDisplayedAt(date: Date): Boolean = date.time < minimalDisplayTime()
    private fun minimalDisplayTime() = date().time - HIDE_INTERVAL.inWholeMilliseconds
}

private enum class RelativeDay(val getDate: () -> Date) {
    Today({ Date() }),
    Tomorrow({ Date().tomorrow() }),
    NextMonday({ Date().getNextMonday() }),
}

private enum class HourOfTheDay(val hourOfTheDay: Int) {
    Morning(8),
    Afternoon(14),
    Evening(18),
}

/**
 * Represents a period inside the current week. In other words, a timeframe used to group relevant schedule options based on when
 * they should be displayed.
 *
 * @param scheduleOptions The available schedule options that can be displayed to the user during each period
 */
private enum class WeekPeriod(vararg val scheduleOptions: ScheduleOption) {
    Weekday(
        ScheduleOption.LaterThisMorning,
        ScheduleOption.ThisAfternoon,
        ScheduleOption.ThisEvening,
        ScheduleOption.TomorrowMorning,
        ScheduleOption.NextMondayMorning,
    ),
    Weekend(ScheduleOption.MondayMorning, ScheduleOption.MondayAfternoon);

    companion object {
        fun getCurrent(): WeekPeriod = if (Date().isWeekend()) Weekend else Weekday
    }
}
