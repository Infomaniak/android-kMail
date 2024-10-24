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
package com.infomaniak.mail.ui.bottomSheetDialogs

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.databinding.BottomSheetScheduleSendBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.thread.actions.ActionItemView
import com.infomaniak.mail.ui.main.thread.actions.ActionsBottomSheetDialog
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel
import com.infomaniak.mail.utils.MailDateFormatUtils.mostDetailedDate
import dagger.hilt.android.AndroidEntryPoint
import java.util.Date
import javax.inject.Inject


@AndroidEntryPoint
class ScheduleSendBottomSheetDialog @Inject constructor() : ActionsBottomSheetDialog() {

    private var binding: BottomSheetScheduleSendBinding by safeBinding()
    override val mainViewModel: MainViewModel by activityViewModels()

    private val newMessageViewModel: NewMessageViewModel by activityViewModels()

    @Inject
    lateinit var localSettings: LocalSettings

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetScheduleSendBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        localSettings.lastSelectedSchedule?.let { lastSelectedSchedule ->
            if (Date(lastSelectedSchedule).isInTheFuture()) {
                lastScheduleItem.isVisible = true
                lastScheduleItem.setDescription(
                    mostDetailedDate(
                        context,
                        date = Date(lastSelectedSchedule),
                        format = FORMAT_DATE_DAY_MONTH,
                    )
                )
            }
        }

        lastScheduleItem.setClosingOnClickListener { Log.e("TOTO", "lastScheduleItem: CLICKED!") }
        customScheduleItem.setClosingOnClickListener { newMessageViewModel.showSendBottomSheetDialog() }

        if (!newMessageViewModel.currentMailbox.isFree) {
            customScheduleItem.setClosingOnClickListener { newMessageViewModel.showSendBottomSheetDialog() }
        } else {
            customScheduleItem.setOnClickListener {
                safeNavigate(
                    resId = R.id.upgradeProductBottomSheetDialog,
                    currentClassName = ScheduleSendBottomSheetDialog::class.java.name,
                )
            }
        }

        fun createScheduleItem(schedule: Schedule): ActionItemView = ActionItemView(this.context).apply {
            setTitle(schedule.scheduleTitleRes)
            setDescription(mostDetailedDate(context, date = schedule.date, format = FORMAT_DATE_DAY_MONTH))
            setIconResource(schedule.scheduleIconRes)
            setOnClickListener {
                // TODO: Schedule the mail, using the schedule's date.
                Log.e("TOTO", "createScheduleItem: ${schedule.name} CLICKED!")
            }
        }

        val timeToDisplay = TimeToDisplay.getTimeToDisplayFromDate(Date())
        Schedule.entries.filter { schedule -> timeToDisplay in schedule.timeToDisplay }.forEach { schedule ->
            scheduleItems.addView(createScheduleItem(schedule))
        }

        val shouldDisplayDivider = lastScheduleItem.isVisible
        (scheduleItems.children.first() as ActionItemView).setDividerVisibility(shouldDisplayDivider)
    }
}

enum class TimeToDisplay {
    NIGHT,
    MORNING,
    AFTERNOON,
    EVENING,
    WEEKEND;

    companion object {
        fun getTimeToDisplayFromDate(date: Date): TimeToDisplay = when (date.hours()) {
            in 0..7 -> NIGHT
            in 8..13 -> MORNING
            in 14..19 -> AFTERNOON
            in 20..23 -> EVENING
            else -> NIGHT
        }
    }
}

enum class Schedule(
    @StringRes val scheduleTitleRes: Int,
    @DrawableRes val scheduleIconRes: Int,
    val date: Date,
    val timeToDisplay: List<TimeToDisplay>,
) {
    LATER_THIS_MORNING(
        R.string.laterThisMorning,
        R.drawable.ic_morning_sunrise_schedule,
        Date().getMorning(),
        listOf(TimeToDisplay.NIGHT),
    ),
    MONDAY_MORNING(
        R.string.mondayMorning,
        R.drawable.ic_morning_schedule,
        Date().getNextMonday().getMorning(),
        listOf(TimeToDisplay.WEEKEND),
    ),
    MONDAY_AFTERNOON(
        R.string.mondayAfternoon,
        R.drawable.ic_afternoon_schedule,
        Date().getNextMonday().getAfternoon(),
        listOf(TimeToDisplay.WEEKEND),
    ),
    THIS_AFTERNOON(
        R.string.thisAfternoon,
        R.drawable.ic_afternoon_schedule,
        Date().getAfternoon(),
        listOf(TimeToDisplay.MORNING),
    ),
    THIS_EVENING(
        R.string.thisEvening,
        R.drawable.ic_evening_schedule,
        Date().getEvening(),
        listOf(TimeToDisplay.AFTERNOON)
    ),
    TOMORROW_MORNING(
        R.string.tomorrowMorning,
        R.drawable.ic_morning_schedule,
        Date().getTomorrow().getMorning(),
        listOf(TimeToDisplay.NIGHT, TimeToDisplay.MORNING, TimeToDisplay.AFTERNOON, TimeToDisplay.EVENING),
    ),
    NEXT_MONDAY(
        R.string.nextMonday,
        R.drawable.ic_arrow_return,
        Date().getNextMonday().getMorning(),
        listOf(TimeToDisplay.NIGHT, TimeToDisplay.MORNING, TimeToDisplay.AFTERNOON, TimeToDisplay.EVENING),
    )
}
