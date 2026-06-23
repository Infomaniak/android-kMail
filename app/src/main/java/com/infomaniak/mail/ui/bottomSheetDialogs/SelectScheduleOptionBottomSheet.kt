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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.IntRange
import androidx.annotation.StringRes
import androidx.core.view.children
import androidx.core.view.isVisible
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.legacy.utils.context
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.common.utils.getNextMonday
import com.infomaniak.core.common.utils.getTimeAtHour
import com.infomaniak.core.common.utils.isAtLeastXMinutesInTheFuture
import com.infomaniak.core.common.utils.isWeekend
import com.infomaniak.core.common.utils.tomorrow
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.BottomSheetScheduleOptionsBinding
import com.infomaniak.mail.ui.alertDialogs.SelectDateAndTimeDialog.Companion.MIN_SELECTABLE_DATE_MINUTES
import com.infomaniak.mail.ui.bottomSheetDialogs.HourOfTheDay.Afternoon
import com.infomaniak.mail.ui.bottomSheetDialogs.HourOfTheDay.Evening
import com.infomaniak.mail.ui.bottomSheetDialogs.HourOfTheDay.Morning
import com.infomaniak.mail.ui.bottomSheetDialogs.RelativeDay.NextMonday
import com.infomaniak.mail.ui.bottomSheetDialogs.RelativeDay.Today
import com.infomaniak.mail.ui.bottomSheetDialogs.RelativeDay.Tomorrow
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleOption.LaterThisMorning
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleOption.MondayAfternoon
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleOption.MondayMorning
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleOption.NextMondayMorning
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleOption.ThisAfternoon
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleOption.ThisEvening
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleOption.TomorrowMorning
import com.infomaniak.mail.ui.main.thread.actions.ActionItemView
import com.infomaniak.mail.ui.main.thread.actions.ActionItemView.TrailingContent
import com.infomaniak.mail.utils.date.DateFormatUtils.dayOfWeekDateWithoutYear
import java.util.Calendar
import java.util.Date
import kotlin.time.Duration.Companion.minutes

abstract class SelectScheduleOptionBottomSheet : EdgeToEdgeBottomSheetDialog() {

    private var binding: BottomSheetScheduleOptionsBinding by safeBinding()

    abstract val lastSelectedEpoch: Long?
    abstract val currentlyScheduledEpochMillis: Long?
    abstract val currentKSuite: KSuite?

    @get:StringRes
    abstract val titleRes: Int

    abstract fun onLastScheduleOptionClicked()
    abstract fun onScheduleOptionClicked(dateItem: ScheduleOption)
    abstract fun onCustomScheduleOptionClicked()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetScheduleOptionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        title.text = getString(titleRes)

        computeLastScheduleOption()

        setLastScheduleOptionClickListener()
        createCommonScheduleOptions()
        setCustomScheduleOptionClickListener()

        val shouldDisplayDivider = lastScheduleOption.isVisible
        (scheduleOptions.children.first() as ActionItemView).setDividerVisibility(shouldDisplayDivider)

        customScheduleOption.trailingContent = when (currentKSuite) {
            KSuite.Perso.Free -> TrailingContent.KSuitePersoChip
            KSuite.Pro.Free, KSuite.StarterPack -> TrailingContent.KSuiteProChip
            else -> TrailingContent.Chevron
        }
    }

    private fun computeLastScheduleOption() = with(binding) {
        val lastSelectedDate = lastSelectedEpoch?.let { Date(it) }

        if (lastSelectedDate?.isAtLeastXMinutesInTheFuture(MIN_SELECTABLE_DATE_MINUTES) == true && lastSelectedDate.isNotAlreadySelected()) {
            lastScheduleOption.isVisible = true
            lastScheduleOption.setDescription(context.dayOfWeekDateWithoutYear(date = lastSelectedDate))
        }
    }

    private fun setLastScheduleOptionClickListener() {
        binding.lastScheduleOption.setOnClickListener { onLastScheduleOptionClicked() }
    }

    private fun createCommonScheduleOptions() {
        val currentTime = Date()
        WeekPeriod.getCurrent().scheduleOptions.forEach { scheduleOption ->
            if (scheduleOption.canBeDisplayedAt(currentTime) && scheduleOption.isNotAlreadySelected()) {
                binding.scheduleOptions.addView(createScheduleOptionItem(scheduleOption))
            }
        }
    }

    private fun createScheduleOptionItem(scheduleOption: ScheduleOption): ActionItemView = ActionItemView(binding.context).apply {
        setTitle(scheduleOption.titleRes)
        setDescription(context.dayOfWeekDateWithoutYear(date = scheduleOption.date()))
        setIconResource(scheduleOption.iconRes)
        setOnClickListener { onScheduleOptionClicked(scheduleOption) }
    }

    private fun setCustomScheduleOptionClickListener() {
        binding.customScheduleOption.setOnClickListener { onCustomScheduleOptionClicked() }
    }

    private fun Date.isNotAlreadySelected(): Boolean {
        return time.truncateToMinute() != currentlyScheduledEpochMillis?.truncateToMinute()
    }

    private fun Long.truncateToMinute(): Long {
        return Calendar.getInstance().apply {
            timeInMillis = this@truncateToMinute
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time.time
    }

    private fun ScheduleOption.isNotAlreadySelected() = date().isNotAlreadySelected()
}

private val HIDE_INTERVAL = 5.minutes // Beware: the API refuses schedules smaller than 5 minutes

enum class ScheduleOption(
    private val day: RelativeDay,
    private val hour: HourOfTheDay,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val matomoName: MatomoName,
) {
    LaterThisMorning(
        Today,
        Morning,
        R.string.laterThisMorning,
        R.drawable.ic_morning_sunrise_schedule,
        MatomoName.LaterThisMorning
    ),
    ThisAfternoon(Today, Afternoon, R.string.thisAfternoon, R.drawable.ic_afternoon_schedule, MatomoName.ThisAfternoon),
    ThisEvening(Today, Evening, R.string.thisEvening, R.drawable.ic_evening_schedule, MatomoName.ThisEvening),
    TomorrowMorning(Tomorrow, Morning, R.string.tomorrowMorning, R.drawable.ic_morning_schedule, MatomoName.TomorrowMorning),
    NextMondayMorning(NextMonday, Morning, R.string.nextMonday, R.drawable.ic_arrow_return, MatomoName.NextMonday),

    MondayMorning(NextMonday, Morning, R.string.mondayMorning, R.drawable.ic_morning_schedule, MatomoName.NextMondayMorning),
    MondayAfternoon(
        NextMonday,
        Afternoon,
        R.string.mondayAfternoon,
        R.drawable.ic_afternoon_schedule,
        MatomoName.NextMondayAfternoon
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

private enum class HourOfTheDay(@IntRange(0, 23) val hourOfTheDay: Int) {
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
    Weekday(LaterThisMorning, ThisAfternoon, ThisEvening, TomorrowMorning, NextMondayMorning),
    Weekend(MondayMorning, MondayAfternoon);

    companion object {
        fun getCurrent(): WeekPeriod = if (Date().isWeekend()) Weekend else Weekday
    }
}
