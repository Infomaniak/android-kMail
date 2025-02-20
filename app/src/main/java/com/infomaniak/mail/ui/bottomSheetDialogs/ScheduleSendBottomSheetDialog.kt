/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024-2025 Infomaniak Network SA
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
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.MatomoMail.trackScheduleSendEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.BottomSheetScheduleSendBinding
import com.infomaniak.mail.ui.alertDialogs.SelectDateAndTimeForScheduledDraftDialog.Companion.MIN_SCHEDULE_DELAY_MINUTES
import com.infomaniak.mail.ui.main.thread.actions.ActionItemView
import com.infomaniak.mail.ui.main.thread.actions.ActionItemView.TrailingContent
import com.infomaniak.mail.utils.MyKSuiteUiUtils.openMyKSuiteUpgradeBottomSheet
import com.infomaniak.mail.utils.date.DateFormatUtils.dayOfWeekDateWithoutYear
import dagger.hilt.android.AndroidEntryPoint
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class ScheduleSendBottomSheetDialog @Inject constructor() : BottomSheetDialogFragment() {

    private val navigationArgs: ScheduleSendBottomSheetDialogArgs by navArgs()
    private var binding: BottomSheetScheduleSendBinding by safeBinding()

    // Navigation args does not support nullable primitive types, so we use 0L
    // as a replacement (corresponding to Thursday 1 January 1970 00:00:00 UT).
    private var lastSelectedScheduleEpoch: Long = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetScheduleSendBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        lastSelectedScheduleEpoch = navigationArgs.lastSelectedScheduleEpoch

        computeLastScheduleItem()
        setLastScheduleClickListener()
        setCustomScheduleClickListener()

        val timeToDisplay = TimeToDisplay.getTimeToDisplayFromDate()
        Schedule.entries.filter { schedule -> timeToDisplay in schedule.timeToDisplay }.forEach { schedule ->
            scheduleItems.addView(createScheduleItem(schedule))
        }

        val shouldDisplayDivider = lastScheduleItem.isVisible
        (scheduleItems.children.first() as ActionItemView).setDividerVisibility(shouldDisplayDivider)
    }

    private fun computeLastScheduleItem() = with(binding) {
        if (Date(lastSelectedScheduleEpoch).isAtLeastXMinutesInTheFuture(MIN_SCHEDULE_DELAY_MINUTES)) {
            lastScheduleItem.isVisible = true
            lastScheduleItem.setDescription(context.dayOfWeekDateWithoutYear(date = Date(lastSelectedScheduleEpoch)))
        }
    }

    private fun setLastScheduleClickListener() {
        binding.lastScheduleItem.setOnClickListener {
            if (lastSelectedScheduleEpoch != 0L) {
                trackScheduleSendEvent("lastSelectedSchedule")
                setBackNavigationResult(SCHEDULE_DRAFT_RESULT, lastSelectedScheduleEpoch)
            }
        }
    }

    private fun setCustomScheduleClickListener() = with(binding.customScheduleItem) {
        trailingContent = if (navigationArgs.isCurrentMailboxFree) TrailingContent.MyKSuiteChip else TrailingContent.Chevron

        setOnClickListener {
            if (navigationArgs.isCurrentMailboxFree) {
                openMyKSuiteUpgradeBottomSheet(ScheduleSendBottomSheetDialog::class.java.name)
            } else {
                setBackNavigationResult(OPEN_DATE_AND_TIME_SCHEDULE_DIALOG, true)
            }
        }
    }

    private fun createScheduleItem(schedule: Schedule): ActionItemView = ActionItemView(requireContext()).apply {
        setTitle(schedule.scheduleTitleRes)
        setDescription(context.dayOfWeekDateWithoutYear(date = schedule.date()))
        setIconResource(schedule.scheduleIconRes)
        setOnClickListener {
            trackScheduleSendEvent(schedule.matomoValue)
            setBackNavigationResult(SCHEDULE_DRAFT_RESULT, schedule.date().time)
        }
    }

    companion object {
        const val SCHEDULE_DRAFT_RESULT = "schedule_draft_result"
        const val OPEN_DATE_AND_TIME_SCHEDULE_DIALOG = "open_date_and_time_schedule_dialog"
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

enum class Schedule(
    @StringRes val scheduleTitleRes: Int,
    @DrawableRes val scheduleIconRes: Int,
    val date: () -> Date,
    val timeToDisplay: List<TimeToDisplay>,
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
    )
}
