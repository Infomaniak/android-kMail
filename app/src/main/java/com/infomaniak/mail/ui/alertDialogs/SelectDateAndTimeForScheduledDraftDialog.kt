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
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.google.android.material.datepicker.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.databinding.DialogSelectDateAndTimeForScheduledDraftBinding
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleSendBottomSheetDialog.Companion.MAX_SCHEDULE_DELAY_YEARS
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleSendBottomSheetDialog.Companion.MIN_SCHEDULE_DELAY_MINUTES
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import java.util.Date
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

    private var onPositiveButtonClicked: (() -> Unit)? = null
    private var onNegativeButtonClicked: (() -> Unit)? = null
    private var onDismissed: (() -> Unit)? = null

    lateinit var selectedDate: Date

    private var datePicker: MaterialDatePicker<Long>? = null
    private var timePicker: MaterialTimePicker? = null

    @Inject
    lateinit var localSettings: LocalSettings

    private fun initDialog() = with(binding) {

        selectedDate = Date().roundUpToNextTenMinutes()

        setTimePicker()
        setDatePicker()
        setDate()

        MaterialAlertDialogBuilder(context)
            .setView(root)
            .setPositiveButton(R.string.buttonConfirm, null)
            .setNegativeButton(RCore.string.buttonCancel, null)
            .create()
    }

    final override fun resetCallbacks() {
        onPositiveButtonClicked = null
        onNegativeButtonClicked = null
        onDismissed = null
    }

    fun show(
        title: String,
        onPositiveButtonClicked: () -> Unit,
        onNegativeButtonClicked: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null,
    ) {
        showDialogWithBasicInfo(title, R.string.buttonScheduleTitle)
        setupListeners(onPositiveButtonClicked, onNegativeButtonClicked, onDismiss)
    }

    private fun getScheduleDateErrorText(): String = if (selectedDate.isInTheFuture().not()) {
        activityContext.resources.getString(R.string.errorChooseUpcomingDate)
    } else {
        activityContext.resources.getQuantityString(
            R.plurals.errorScheduleDelayTooShort,
            MIN_SCHEDULE_DELAY_MINUTES,
            MIN_SCHEDULE_DELAY_MINUTES,
        )
    }

    private fun setupListeners(
        onPositiveButtonClicked: () -> Unit,
        onNegativeButtonClicked: (() -> Unit)?,
        onDismiss: (() -> Unit)?,
    ) = with(alertDialog) {

        binding.dateField.setOnClickListener { datePicker?.show(super.activity.supportFragmentManager, "tag") }

        datePicker?.addOnPositiveButtonClickListener { time ->
            val date = Date().also { it.time = time }
            selectedDate = selectedDate.setDay(date.day())
            setDate()
        }

        binding.timeField.setOnClickListener { timePicker?.show(super.activity.supportFragmentManager, "tag") }

        timePicker?.addOnPositiveButtonClickListener {
            val hour: Int = timePicker!!.hour
            val minute: Int = timePicker!!.minute

            selectedDate = selectedDate.setHour(hour)
            selectedDate = selectedDate.setMinute(minute)

            binding.timeField.setText(selectedDate.format(FORMAT_DATE_HOUR_MINUTE))

            binding.scheduleDateError.text = getScheduleDateErrorText()
            binding.scheduleDateError.isVisible = selectedDate.isAtLeastXMinutesInTheFuture(MIN_SCHEDULE_DELAY_MINUTES).not()
            positiveButton.isEnabled = selectedDate.isAtLeastXMinutesInTheFuture(MIN_SCHEDULE_DELAY_MINUTES)
        }

        this@SelectDateAndTimeForScheduledDraftDialog.onPositiveButtonClicked = onPositiveButtonClicked
        this@SelectDateAndTimeForScheduledDraftDialog.onNegativeButtonClicked = onNegativeButtonClicked

        positiveButton.setOnClickListener {
            this@SelectDateAndTimeForScheduledDraftDialog.onPositiveButtonClicked?.invoke()
            dismiss()
        }

        negativeButton.setOnClickListener {
            this@SelectDateAndTimeForScheduledDraftDialog.onNegativeButtonClicked?.invoke()
            dismiss()
        }

        onDismiss.let {
            onDismissed = it
            setOnDismissListener { onDismissed?.invoke() }
        }
    }

    private fun setTimePicker() = with(binding) {
        timePicker = MaterialTimePicker.Builder()
            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(selectedDate.hours())
            .setMinute(selectedDate.minutes())
            .setTitleText(context.getString(R.string.selectTimeDialogTitle))
            .build()
    }

    private fun setDatePicker() = with(binding) {
        val dateValidators = listOf(
            DateValidatorPointForward.now(),
            DateValidatorPointBackward.before(Date().addYears(MAX_SCHEDULE_DELAY_YEARS).time),
        )
        val constraintsBuilder = CalendarConstraints.Builder().setValidator(CompositeDateValidator.allOf(dateValidators))

        datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(context.getString(R.string.selectDateDialogTitle))
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .setCalendarConstraints(constraintsBuilder.build())
            .build()
    }

    private fun setDate() {
        binding.dateField.setText(selectedDate.format(FORMAT_DATE_DAY_MONTH_YEAR))
    }

    private fun showDialogWithBasicInfo(
        title: String? = null,
        @StringRes positiveButtonText: Int? = null,
        @StringRes negativeButtonText: Int? = null,
    ) = with(binding) {

        alertDialog.show()

        selectedDate = Date().roundUpToNextTenMinutes()

        binding.timeField.setText(selectedDate.format(FORMAT_DATE_HOUR_MINUTE))

        setTimePicker()
        setDatePicker()

        title?.let(dialogTitle::setText)

        positiveButtonText?.let(positiveButton::setText)
        negativeButtonText?.let(negativeButton::setText)
    }
}
