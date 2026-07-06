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
package com.infomaniak.mail.ui.bottomSheetDialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.BottomSheetReminderOptionsBinding
import com.infomaniak.mail.ui.alertDialogs.CustomReminderPickerDialog
import com.infomaniak.mail.ui.main.thread.actions.ActionItemView
import com.infomaniak.mail.ui.main.thread.actions.TrailingContent
import com.infomaniak.mail.ui.newMessage.ReminderPreset
import com.infomaniak.mail.utils.openKSuiteProBottomSheet
import com.infomaniak.mail.utils.openMailPremiumBottomSheet
import com.infomaniak.mail.utils.openMyKSuiteUpgradeBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ReminderBottomSheetDialog @Inject constructor() : EdgeToEdgeBottomSheetDialog() {

    private var binding: BottomSheetReminderOptionsBinding by safeBinding()
    private val navigationArgs: ReminderBottomSheetDialogArgs by navArgs()

    @Inject
    lateinit var customReminderPickerDialog: CustomReminderPickerDialog

    private val currentKSuite: KSuite? by lazy { navigationArgs.currentKSuite }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetReminderOptionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        customReminderPickerDialog.bindAlertToLifecycle(viewLifecycleOwner)

        val titleRes = if (navigationArgs.isFromActionsBottomSheet) {
            R.string.createReminderBottomSheetTitle
        } else {
            R.string.reminderBottomSheetTitle
        }
        binding.title.text = getString(titleRes)
        createPresetOptions()
        setupCustomOption()
    }

    private fun createPresetOptions() {
        listOf(ReminderPreset.HOURS_24, ReminderPreset.DAYS_3, ReminderPreset.DAYS_7).forEach { preset ->
            val itemView = ActionItemView(requireContext()).apply {
                val days = preset.delayMinutes / (24 * 60)
                val dateText = if (preset.delayMinutes % (24 * 60) == 0 && days > 0) {
                    resources.getQuantityString(R.plurals.daysBeforeSendingReminder, days, days)
                } else {
                    val hours = preset.delayMinutes / 60
                    resources.getQuantityString(R.plurals.hoursBeforeSendingReminder, hours, hours)
                }

                setTitle(dateText)
                removeIcon()
                setOnClickListener { onPresetSelected(preset) }
            }
            binding.reminderOptions.addView(itemView)
        }
    }

    private fun setupCustomOption() {
        binding.customReminderOption.removeIcon()
        binding.customReminderOption.trailingContent = when (currentKSuite) {
            KSuite.Perso.Free -> TrailingContent.KSuitePersoChip
            KSuite.Pro.Free, KSuite.StarterPack -> TrailingContent.KSuiteProChip
            else -> TrailingContent.Chevron
        }
        binding.customReminderOption.setOnClickListener { onCustomReminderClicked() }
    }

    private fun onPresetSelected(preset: ReminderPreset) {
        setBackNavigationResult(REMINDER_RESULT, preset.delayMinutes)
        dismiss()
    }

    private fun onCustomReminderClicked() {
        val kSuite = currentKSuite
        val matomoName = MatomoName.CustomReminder.value
        when (kSuite) {
            KSuite.Perso.Free -> openMyKSuiteUpgradeBottomSheet(matomoName)
            KSuite.Pro.Free -> openKSuiteProBottomSheet(kSuite, navigationArgs.isAdmin, matomoName)
            KSuite.StarterPack -> openMailPremiumBottomSheet(matomoName)
            else -> showCustomDelayReminderDatePicker()
        }
    }

    private fun showCustomDelayReminderDatePicker() {
        customReminderPickerDialog.show(
            onDelaySelected = { delayMinutes ->
                setBackNavigationResult(REMINDER_RESULT, delayMinutes)
                dismiss()
            },
        )
    }

    companion object {
        const val REMINDER_RESULT = "reminder_result"
    }
}
