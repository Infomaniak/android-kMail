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

    private val navigationArgs: ScheduleSendBottomSheetDialogArgs by navArgs()

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

        localSettings.lastSelectedScheduleDate?.let { lastSelectedSchedule ->
            if (Date(lastSelectedSchedule).isAtLeastTenMinutesInTheFuture()) {
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

        lastScheduleItem.setClosingOnClickListener {
            val draftResource = navigationArgs.draftResource
            val lastSelectedScheduleDate = localSettings.lastSelectedScheduleDate

            if (navigationArgs.isAlreadyScheduled) {
                if (draftResource != null && lastSelectedScheduleDate != null) {
                    mainViewModel.rescheduleDraft(draftResource, Date(lastSelectedScheduleDate))
                }
            } else {
                lastSelectedScheduleDate?.let {
                    newMessageViewModel.setScheduleDate(Date(it))
                    newMessageViewModel.triggerSendMessage()
                }
            }
        }

        if (newMessageViewModel.currentMailbox.isFree) {
            customScheduleItem.setOnClickListener {
                safeNavigate(
                    resId = R.id.upgradeProductBottomSheetDialog,
                    currentClassName = ScheduleSendBottomSheetDialog::class.java.name,
                )
            }
        } else {
            customScheduleItem.setClosingOnClickListener {
                if (navigationArgs.isAlreadyScheduled) {
                    mainViewModel.showSelectDateAndTimeForScheduleDialog()
                } else {
                    newMessageViewModel.showSelectDateAndTimeForScheduleDialog()
                }
            }
        }

        fun createScheduleItem(schedule: Schedule): ActionItemView = ActionItemView(this.context).apply {
            setTitle(schedule.scheduleTitleRes)
            setDescription(mostDetailedDate(context, date = schedule.date, format = FORMAT_DATE_DAY_MONTH))
            setIconResource(schedule.scheduleIconRes)
            setClosingOnClickListener {
                if (navigationArgs.isAlreadyScheduled) {
                    navigationArgs.draftResource?.let { mainViewModel.rescheduleDraft(draftResource = it, schedule.date) }
                } else {
                    newMessageViewModel.setScheduleDate(schedule.date)
                    newMessageViewModel.triggerSendMessage()
                }
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
        fun getTimeToDisplayFromDate(date: Date): TimeToDisplay {
            return if (date.isWeekend()) WEEKEND else when (date.hours()) {
                in 0..7 -> NIGHT
                in 8..13 -> MORNING
                in 14..19 -> AFTERNOON
                in 20..23 -> EVENING
                else -> NIGHT
            }
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
