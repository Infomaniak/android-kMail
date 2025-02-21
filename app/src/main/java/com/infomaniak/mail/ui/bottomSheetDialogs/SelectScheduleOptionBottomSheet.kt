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
package com.infomaniak.mail.ui.bottomSheetDialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.children
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.core.utils.*
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.BottomSheetScheduleOptionsBinding
import com.infomaniak.mail.ui.alertDialogs.SelectDateAndTimeDialog.Companion.MIN_SELECTABLE_DATE_MINUTES
import com.infomaniak.mail.ui.main.thread.actions.ActionItemView
import com.infomaniak.mail.utils.date.DateFormatUtils.dayOfWeekDateWithoutYear
import java.util.Date


abstract class SelectScheduleOptionBottomSheet : BottomSheetDialogFragment() {

    private var binding: BottomSheetScheduleOptionsBinding by safeBinding()

    abstract val lastSelectedEpoch: Long?

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
    }

    private fun computeLastScheduleOption() = with(binding) {
        val lastSelectedDate = lastSelectedEpoch?.let { Date(it) }

        if (lastSelectedDate?.isAtLeastXMinutesInTheFuture(MIN_SELECTABLE_DATE_MINUTES) == true) {
            lastScheduleOption.isVisible = true
            lastScheduleOption.setDescription(context.dayOfWeekDateWithoutYear(date = lastSelectedDate))
        }
    }

    private fun setLastScheduleOptionClickListener() {
        binding.lastScheduleOption.setOnClickListener { onLastScheduleOptionClicked() }
    }

    private fun createCommonScheduleOptions() {
        val currentTime = TimeToDisplay.getTimeToDisplayFromDate()
        ScheduleOption.entries.forEach { scheduleOption ->
            if (scheduleOption.canBeDisplayedAt(currentTime)) {
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
}

enum class TimeToDisplay {
    NIGHT,
    MORNING,
    AFTERNOON,
    EVENING,
    WEEKEND;

    companion object {
        fun getTimeToDisplayFromDate(): TimeToDisplay {
            val now = Date()
            val timeSlot = Date(now.time)
            return if (now.isWeekend()) {
                WEEKEND
            } else {
                when (now) {
                    in timeSlot.setHour(7).setMinute(55)..timeSlot.setHour(13).setMinute(54) -> MORNING
                    in timeSlot.setHour(13).setMinute(55)..timeSlot.setHour(17).setMinute(54) -> AFTERNOON
                    in timeSlot.setHour(17).setMinute(55)..timeSlot.setHour(23).setMinute(54) -> EVENING
                    else -> NIGHT // Between 23:55 and 7:54, inclusive
                }
            }
        }
    }
}

enum class ScheduleOption(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val date: () -> Date,
    private val timeToDisplay: List<TimeToDisplay>,
    val matomoValue: String,
) {
    LATER_THIS_MORNING(
        R.string.laterThisMorning,
        R.drawable.ic_morning_sunrise_schedule,
        { Date().getMorning() },
        listOf(TimeToDisplay.NIGHT),
        "laterThisMorning",
    ),
    MONDAY_MORNING(
        R.string.mondayMorning,
        R.drawable.ic_morning_schedule,
        { Date().getNextMonday().getMorning() },
        listOf(TimeToDisplay.WEEKEND),
        "nextMondayMorning",
    ),
    MONDAY_AFTERNOON(
        R.string.mondayAfternoon,
        R.drawable.ic_afternoon_schedule,
        { Date().getNextMonday().getAfternoon() },
        listOf(TimeToDisplay.WEEKEND),
        "nextMondayAfternoon",
    ),
    THIS_AFTERNOON(
        R.string.thisAfternoon,
        R.drawable.ic_afternoon_schedule,
        { Date().getAfternoon() },
        listOf(TimeToDisplay.MORNING),
        "thisAfternoon",
    ),
    THIS_EVENING(
        R.string.thisEvening,
        R.drawable.ic_evening_schedule,
        { Date().getEvening() },
        listOf(TimeToDisplay.AFTERNOON),
        "thisEvening",
    ),
    TOMORROW_MORNING(
        R.string.tomorrowMorning,
        R.drawable.ic_morning_schedule,
        { Date().tomorrow().getMorning() },
        listOf(TimeToDisplay.NIGHT, TimeToDisplay.MORNING, TimeToDisplay.AFTERNOON, TimeToDisplay.EVENING),
        "tomorrowMorning",
    ),
    NEXT_MONDAY(
        R.string.nextMonday,
        R.drawable.ic_arrow_return,
        { Date().getNextMonday().getMorning() },
        listOf(TimeToDisplay.NIGHT, TimeToDisplay.MORNING, TimeToDisplay.AFTERNOON, TimeToDisplay.EVENING),
        "nextMonday",
    );

    fun canBeDisplayedAt(timeToDisplay: TimeToDisplay): Boolean = timeToDisplay in this.timeToDisplay
}
