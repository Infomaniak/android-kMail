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
package com.infomaniak.mail.ui.alertDialogs

import android.content.Context
import android.text.format.DateFormat
import androidx.core.view.isVisible
import com.google.android.material.datepicker.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.DialogSelectDateAndTimeForScheduledDraftBinding
import com.infomaniak.mail.utils.date.DateFormatUtils.formatTime
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import javax.inject.Inject
import com.infomaniak.lib.core.R as RCore

@ActivityScoped
open class SelectDateAndTimeForScheduledDraftDialog @Inject constructor(
    @ActivityContext private val activityContext: Context,
) : BaseAlertDialog(activityContext) {

    val binding: DialogSelectDateAndTimeForScheduledDraftBinding by lazy {
        DialogSelectDateAndTimeForScheduledDraftBinding.inflate(activity.layoutInflater)
    }

    override val alertDialog = initDialog()

    private var onSchedule: ((Long) -> Unit)? = null
    private var onAbort: (() -> Unit)? = null

    private lateinit var selectedDate: Date

    private fun initDialog() = with(binding) {
        MaterialAlertDialogBuilder(context)
            .setView(root)
            .setPositiveButton(R.string.buttonConfirm, null)
            .setNegativeButton(RCore.string.buttonCancel, null)
            .create()
    }

    final override fun resetCallbacks() {
        onSchedule = null
        onAbort = null
    }

    fun show(onSchedule: (Long) -> Unit, onAbort: (() -> Unit)? = null) {
        showDialogWithBasicInfo()
        setupListeners(onSchedule, onAbort)
    }

    private fun showDialogWithBasicInfo() {
        alertDialog.show()

        selectDate(Date().roundUpToNextTenMinutes())
        positiveButton.setText(R.string.buttonScheduleTitle)
    }

    private fun setupListeners(onSchedule: (Long) -> Unit, onAbort: (() -> Unit)?) = with(alertDialog) {

        binding.dateField.setOnClickListener {
            showDatePicker(selectedDate) { time ->
                val date = Date(time).let { newDate ->
                    Calendar.getInstance().apply {
                        set(newDate.year(), newDate.month(), newDate.day(), selectedDate.hours(), selectedDate.minutes(), 0)
                    }.time
                }

                selectDate(date)
            }
        }

        binding.timeField.setOnClickListener {
            showTimePicker(selectedDate) { hour, minute ->
                selectDate(selectedDate.setHour(hour).setMinute(minute))
            }
        }

        this@SelectDateAndTimeForScheduledDraftDialog.onSchedule = onSchedule
        this@SelectDateAndTimeForScheduledDraftDialog.onAbort = onAbort

        positiveButton.setOnClickListener {
            this@SelectDateAndTimeForScheduledDraftDialog.onSchedule?.invoke(selectedDate.time)
            dismiss()
        }

        negativeButton.setOnClickListener { cancel() }

        setOnCancelListener { onAbort?.invoke() }
    }

    private fun selectDate(date: Date) {
        updateErrorMessage(date)
        selectedDate = date
        with(binding) {
            dateField.setText(date.format(FORMAT_DATE_DAY_MONTH_YEAR))
            timeField.setText(context.formatTime(date))
        }
    }

    private fun updateErrorMessage(date: Date) {
        val isValid = date.isAtLeastXMinutesInTheFuture(MIN_SCHEDULE_DELAY_MINUTES)

        if (isValid.not()) binding.scheduleDateError.text = getScheduleDateErrorText(date)
        binding.scheduleDateError.isVisible = isValid.not()
        positiveButton.isEnabled = isValid
    }

    private fun getScheduleDateErrorText(date: Date): String = if (date.isInTheFuture()) {
        activityContext.resources.getQuantityString(
            R.plurals.errorScheduleDelayTooShort,
            MIN_SCHEDULE_DELAY_MINUTES,
            MIN_SCHEDULE_DELAY_MINUTES,
        )
    } else {
        activityContext.resources.getString(R.string.errorChooseUpcomingDate)
    }

    private fun showTimePicker(dateToDisplay: Date, onDateSelected: (Int, Int) -> Unit) {
        val timePicker = MaterialTimePicker.Builder()
            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
            .setTimeFormat(if (DateFormat.is24HourFormat(activityContext)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .setHour(dateToDisplay.hours())
            .setMinute(dateToDisplay.minutes())
            .setTitleText(binding.context.getString(R.string.selectTimeDialogTitle))
            .build()

        timePicker.addOnPositiveButtonClickListener { onDateSelected(timePicker.hour, timePicker.minute) }

        timePicker.show(super.activity.supportFragmentManager, null)
    }

    private fun showDatePicker(dateToDisplay: Date, onDateSelected: (Long) -> Unit) {
        val dateValidators = listOf(
            DateValidatorPointForward.now(),
            DateValidatorPointBackward.before(Date().addYears(MAX_SCHEDULE_DELAY_YEARS).time),
        )
        val constraintsBuilder = CalendarConstraints.Builder().setValidator(CompositeDateValidator.allOf(dateValidators))

        // MaterialDatePicker expects the `setSelection()` time to be defined as UTC time and not local time
        val utcTime = dateToDisplay.time + TimeZone.getDefault().getOffset(dateToDisplay.time)

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(binding.context.getString(R.string.selectDateDialogTitle))
            .setSelection(utcTime)
            .setCalendarConstraints(constraintsBuilder.build())
            .build()

        datePicker.addOnPositiveButtonClickListener(onDateSelected)

        datePicker.show(super.activity.supportFragmentManager, null)
    }

    companion object {
        const val MIN_SCHEDULE_DELAY_MINUTES = 5
        const val MAX_SCHEDULE_DELAY_YEARS = 10
    }
}
