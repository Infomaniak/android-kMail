/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.DialogCustomReminderPickerBinding
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class CustomReminderPickerDialog @Inject constructor(
    @ActivityContext private val activityContext: Context,
) : BaseAlertDialog(activityContext) {

    private val binding: DialogCustomReminderPickerBinding by lazy {
        DialogCustomReminderPickerBinding.inflate(activity.layoutInflater)
    }

    override val alertDialog = initDialog()

    private var onDelaySelected: ((Int) -> Unit)? = null

    private val timeUnits = TimeUnit.entries.toTypedArray()

    private fun initDialog() = with(binding) {
        MaterialAlertDialogBuilder(activityContext)
            .setTitle(R.string.buttonCustomReminder)
            .setView(root)
            .setPositiveButton(R.string.buttonConfirm, null)
            .setNegativeButton(com.infomaniak.core.legacy.R.string.buttonCancel, null)
            .create()
    }

    fun show(onDelaySelected: (Int) -> Unit) {
        this.onDelaySelected = onDelaySelected
        alertDialog.show()
        setupPickers()
        setupListeners()
    }

    override fun resetCallbacks() {
        onDelaySelected = null
    }

    private fun setupPickers() = with(binding) {
        numberPicker.minValue = 1
        numberPicker.maxValue = timeUnits[0].maxValue
        numberPicker.wrapSelectorWheel = true

        unitPicker.minValue = 0
        unitPicker.maxValue = timeUnits.size - 1
        unitPicker.wrapSelectorWheel = false

        updateUnitLabels(numberPicker.value)
    }

    private fun setupListeners() = with(binding) {
        numberPicker.setOnValueChangedListener { _, oldVal, newVal ->
            if ((oldVal == 1 && newVal > 1) || (oldVal > 1 && newVal == 1)) {
                updateUnitLabels(newVal)
            }
        }

        unitPicker.setOnValueChangedListener { _, _, newUnitIndex ->
            val selectedUnit = timeUnits[newUnitIndex]
            numberPicker.maxValue = selectedUnit.maxValue
        }

        positiveButton.setOnClickListener {
            numberPicker.clearFocus()
            unitPicker.clearFocus()
            onDelaySelected?.invoke(getDelayMinutes())
            alertDialog.dismiss()
        }

        negativeButton.setOnClickListener { alertDialog.cancel() }
    }

    private fun updateUnitLabels(currentNumber: Int) {
        val displayedUnits = timeUnits.map {
            activityContext.resources.getQuantityString(it.titleRes, currentNumber)
        }.toTypedArray()

        binding.unitPicker.displayedValues = displayedUnits
    }

    private fun getDelayMinutes(): Int {
        val number = binding.numberPicker.value
        val selectedUnitIndex = binding.unitPicker.value
        val selectedUnit = timeUnits[selectedUnitIndex]

        return number * selectedUnit.multiplierInMinutes
    }

    companion object {
        private const val MAX_HOURS = 23
        private const val MAX_DAYS = 30

        enum class TimeUnit(
            val titleRes: Int,
            val maxValue: Int,
            val multiplierInMinutes: Int
        ) {
            HOURS(R.plurals.unitHours, MAX_HOURS, 60),
            DAYS(R.plurals.unitDays, MAX_DAYS, 24 * 60)
        }
    }
}
