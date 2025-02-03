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
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.BottomSheetScheduleSendBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.thread.actions.ActionItemView
import com.infomaniak.mail.ui.main.thread.actions.ActionsBottomSheetDialog
import com.infomaniak.mail.utils.MailDateFormatUtils.mostDetailedDate
import dagger.hilt.android.AndroidEntryPoint
import java.util.Date
import javax.inject.Inject


@AndroidEntryPoint
class ScheduleSendBottomSheetDialog @Inject constructor() : ActionsBottomSheetDialog() {

    private val navigationArgs: ScheduleSendBottomSheetDialogArgs by navArgs()

    private var binding: BottomSheetScheduleSendBinding by safeBinding()
    override val mainViewModel: MainViewModel by activityViewModels()

    // Navigation args does not support nullable primitive types, so we use 0L
    // as a replacement (corresponding to Thursday 1 January 1970 00:00:00 UT).
    private var lastSelectedScheduleEpoch: Long = 0L

    private fun computeLastScheduleItem() = with(binding) {
        if (Date(lastSelectedScheduleEpoch).isAtLeastXMinutesInTheFuture(MIN_SCHEDULE_DELAY_MINUTES)) {
            lastScheduleItem.isVisible = true
            lastScheduleItem.setDescription(
                mostDetailedDate(
                    context,
                    date = Date(lastSelectedScheduleEpoch),
                    format = FORMAT_DATE_DAY_MONTH,
                ),
            )
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetScheduleSendBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        lastSelectedScheduleEpoch = navigationArgs.lastSelectedScheduleEpoch

        computeLastScheduleItem()

        lastScheduleItem.setOnClickListener {
            val draftResource = navigationArgs.draftResource

            if (navigationArgs.isAlreadyScheduled) {
                if (draftResource != null && lastSelectedScheduleEpoch != 0L) {
                    trackEvent(navigationArgs.matomoCategory, "lastSchedule")
                    setBackNavigationResult(SCHEDULE_SEND_RESULT, lastSelectedScheduleEpoch)
                }
            } else {
                if (lastSelectedScheduleEpoch != 0L) {
                    trackEvent(navigationArgs.matomoCategory, "lastSchedule")
                    setBackNavigationResult(SCHEDULE_SEND_RESULT, lastSelectedScheduleEpoch)
                }
            }
        }

        customScheduleItem.setOnClickListener {
            if (navigationArgs.isCurrentMailboxFree) {
                safeNavigate(
                    resId = R.id.upgradeProductBottomSheetDialog,
                    currentClassName = ScheduleSendBottomSheetDialog::class.java.name,
                )
            } else {
                setBackNavigationResult(OPEN_DATE_AND_TIME_SCHEDULE_DIALOG, true)
            }
        }

        fun createScheduleItem(schedule: Schedule): ActionItemView = ActionItemView(this.context).apply {
            setTitle(schedule.scheduleTitleRes)
            setDescription(mostDetailedDate(context, date = schedule.date(), format = FORMAT_DATE_DAY_MONTH))
            setIconResource(schedule.scheduleIconRes)
            setOnClickListener {
                trackEvent(navigationArgs.matomoCategory, schedule.matomoValue)
                setBackNavigationResult(SCHEDULE_SEND_RESULT, schedule.date().time)
            }
        }

        val timeToDisplay = TimeToDisplay.getTimeToDisplayFromDate()
        Schedule.entries.filter { schedule -> timeToDisplay in schedule.timeToDisplay }.forEach { schedule ->
            scheduleItems.addView(createScheduleItem(schedule))
        }

        val shouldDisplayDivider = lastScheduleItem.isVisible
        (scheduleItems.children.first() as ActionItemView).setDividerVisibility(shouldDisplayDivider)
    }

    companion object {
        const val MIN_SCHEDULE_DELAY_MINUTES = 5
        const val MAX_SCHEDULE_DELAY_YEARS = 10

        const val SCHEDULE_SEND_RESULT = "schedule_send_result"
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
