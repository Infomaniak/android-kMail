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
package com.infomaniak.mail.ui.alertDialogs

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.infomaniak.core.legacy.utils.context
import com.infomaniak.core.common.utils.FORMAT_DATE_DAY_MONTH_YEAR
import com.infomaniak.core.common.utils.day
import com.infomaniak.core.common.utils.format
import com.infomaniak.core.common.utils.hours
import com.infomaniak.core.common.utils.isAtLeastXMinutesInTheFuture
import com.infomaniak.core.common.utils.isInTheFuture
import com.infomaniak.core.common.utils.minutes
import com.infomaniak.core.common.utils.month
import com.infomaniak.core.common.utils.roundUpToNextTenMinutes
import com.infomaniak.core.common.utils.setHour
import com.infomaniak.core.common.utils.setMinute
import com.infomaniak.core.common.utils.year
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.DialogSelectDateAndTimeBinding
import com.infomaniak.mail.utils.date.DateFormatUtils.formatTime
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import com.infomaniak.core.legacy.R as RCore

abstract class SelectDateAndTimeDialog(private val activityContext: Context) : BaseAlertDialog(activityContext) {

    @StringRes
    open val defaultPositiveButtonResId: Int = R.string.buttonConfirm

    abstract fun defineCalendarConstraint(): CalendarConstraints.Builder

    abstract fun getDelayTooShortErrorMessage(): String

    val binding: DialogSelectDateAndTimeBinding by lazy { DialogSelectDateAndTimeBinding.inflate(activity.layoutInflater) }

    override val alertDialog = initDialog()

    private var onDateSelected: ((Long) -> Unit)? = null
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
        onDateSelected = null
        onAbort = null
    }

    fun show(positiveButtonResId: Int? = null, onDateSelected: (Long) -> Unit, onAbort: (() -> Unit)? = null) {
        showDialogWithBasicInfo(positiveButtonResId)
        setupListeners(onDateSelected, onAbort)
    }

    private fun showDialogWithBasicInfo(positiveButtonResId: Int?) {
        alertDialog.show()

        selectDate(Date().roundUpToNextTenMinutes())
        positiveButton.setText(positiveButtonResId ?: defaultPositiveButtonResId)
    }

    private fun setupListeners(onDateSelected: (Long) -> Unit, onAbort: (() -> Unit)?) = with(alertDialog) {

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

        this@SelectDateAndTimeDialog.onDateSelected = onDateSelected
        this@SelectDateAndTimeDialog.onAbort = onAbort

        positiveButton.setOnClickListener {
            this@SelectDateAndTimeDialog.onDateSelected?.invoke(selectedDate.time)
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
        val isValid = date.isAtLeastXMinutesInTheFuture(MIN_SELECTABLE_DATE_MINUTES)

        if (isValid.not()) binding.errorMessage.text = getErrorText(date)
        binding.errorMessage.isVisible = isValid.not()
        positiveButton.isEnabled = isValid
    }

    private fun getErrorText(date: Date): String = if (date.isInTheFuture()) {
        getDelayTooShortErrorMessage()
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
        // MaterialDatePicker expects the `setSelection()` time to be defined as UTC time and not local time
        val utcTime = dateToDisplay.time + TimeZone.getDefault().getOffset(dateToDisplay.time)

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(binding.context.getString(R.string.selectDateDialogTitle))
            .setSelection(utcTime)
            .setCalendarConstraints(defineCalendarConstraint().build())
            .build()

        datePicker.addOnPositiveButtonClickListener(onDateSelected)

        datePicker.show(super.activity.supportFragmentManager, null)
    }

    companion object {
        const val MIN_SELECTABLE_DATE_MINUTES = 5
    }
}
