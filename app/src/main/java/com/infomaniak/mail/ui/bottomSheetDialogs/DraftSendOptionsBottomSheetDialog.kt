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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackScheduleSendEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.BottomSheetSendOptionsBinding
import com.infomaniak.mail.ui.alertDialogs.SelectDateAndTimeForScheduledDraftDialog
import com.infomaniak.mail.ui.main.settings.ItemSettingView
import com.infomaniak.mail.ui.main.settings.SettingRadioButtonView
import com.infomaniak.mail.ui.main.settings.SettingRadioGroupView
import com.infomaniak.mail.ui.main.thread.actions.TrailingContent
import com.infomaniak.mail.utils.date.DateFormatUtils.dayOfWeekDateWithoutYear
import com.infomaniak.mail.utils.extensions.applyContentPaddingStart
import com.infomaniak.mail.utils.openKSuiteProBottomSheet
import com.infomaniak.mail.utils.openMailPremiumBottomSheet
import com.infomaniak.mail.utils.openMyKSuiteUpgradeBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class DraftSendOptionsBottomSheetDialog @Inject constructor() : BaseSchedulePickerBottomSheet() {

    private var binding: BottomSheetSendOptionsBinding by safeBinding()

    private val navigationArgs: DraftSendOptionsBottomSheetDialogArgs by navArgs()

    @Inject
    lateinit var dateAndTimeScheduleDialog: SelectDateAndTimeForScheduledDraftDialog

    override val currentKSuite: KSuite? by lazy { navigationArgs.currentKSuite }

    // Navigation args does not support nullable primitive types, so we use 0L
    // as a replacement (corresponding to Thursday 1 January 1970 00:00:00 UT).
    override val lastSelectedEpoch: Long? by lazy { navigationArgs.lastSelectedScheduleEpochMillis.takeIf { it != 0L } }
    override val currentlyScheduledEpochMillis: Long? by lazy { navigationArgs.currentlyScheduledEpochMillis.takeIf { it != 0L } }

    override val lastScheduleOption get() = binding.lastScheduleOption
    override val scheduleOptionsContainer get() = binding.scheduleOptions
    override val customScheduleOption get() = binding.customScheduleOption

    private var hasLastScheduleOption = false

    private var selectedScheduleEpoch: Long? = null
    private var selectedReminderEpoch: Long? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetSendOptionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        dateAndTimeScheduleDialog.bindAlertToLifecycle(viewLifecycleOwner)

        setupScheduleOptions()
        hasLastScheduleOption = lastScheduleOption.isVisible
        lastScheduleOption.associatedValue = lastSelectedEpoch?.toString()

        setReminderOptionsVisible(isVisible = false)
        setScheduleOptionsVisible(isVisible = false)

        setupToggles()
        setupScheduleSelection()
        setupReminderOptions()
    }

    private fun setupToggles() = with(binding) {
        reminderIfNoAnswer.setOnClickListener { setReminderOptionsVisible(isVisible = reminderIfNoAnswer.isChecked) }
        scheduleSending.setOnClickListener { setScheduleOptionsVisible(isVisible = scheduleSending.isChecked) }
    }

    private fun setupScheduleSelection() = with(binding) {
        scheduleOptions.onItemCheckedListener { _, value, _ ->
            selectedScheduleEpoch = value?.toLongOrNull()
            binding.customScheduleOption.setCheckMark(displayCheckMark = false)
            binding.customScheduleOption.removeSubtitle()
            Log.i("elouan", "selected schedule epoch: $selectedScheduleEpoch")
        }

        val paddingStartValue = resources.getDimensionPixelSize(R.dimen.emptyStatePadding)
        (scheduleOptions.children + customScheduleOption).forEach { view -> view.applyContentPaddingStart(paddingStartValue) }
    }

    private fun setupReminderOptions() = with(binding) {
        hours24.setText(getString(R.string.hoursBeforeSendingReminder, 24))
        days3.setText(getString(R.string.daysBeforeSendingReminder, 3))
        days7.setText(getString(R.string.daysBeforeSendingReminder, 7))

        customDelayReminder.trailingContent = trailingContentFor(currentKSuite)

        val paddingStartValue = resources.getDimensionPixelSize(R.dimen.emptyStatePadding)
        (optionsDelays.children + customDelayReminder).forEach { view -> view.applyContentPaddingStart(paddingStartValue) }

        optionsDelays.onItemCheckedListener { _, value, _ ->
            selectedReminderEpoch = value?.toLongOrNull()
            binding.customDelayReminder.setCheckMark(displayCheckMark = false)
            binding.customDelayReminder.removeSubtitle()
        }

        customDelayReminder.setOnClickListener { onCustomDelayReminderClicked() }
    }

    override fun createScheduleOptionItem(scheduleOption: ScheduleOption): View {
        return SettingRadioButtonView(requireContext()).apply {
            id = View.generateViewId()
            associatedValue = scheduleOption.date().time.toString()
            setText(getString(scheduleOption.titleRes))
            setDescription(context.dayOfWeekDateWithoutYear(date = scheduleOption.date()))
        }
    }

    override fun bindLastScheduleOptionDescription(description: String) = binding.lastScheduleOption.setDescription(description)

    override fun setupCustomScheduleOptionTrailing(kSuite: KSuite?) {
        binding.customScheduleOption.trailingContent = trailingContentFor(kSuite)
    }

    private fun trailingContentFor(kSuite: KSuite?): TrailingContent = when (kSuite) {
        KSuite.Perso.Free -> TrailingContent.KSuitePersoChip
        KSuite.Pro.Free, KSuite.StarterPack -> TrailingContent.KSuiteProChip
        else -> TrailingContent.Chevron
    }


    private fun setReminderOptionsVisible(isVisible: Boolean) = with(binding) {
        optionsDelays.isVisible = isVisible
        customDelayReminder.isVisible = isVisible
    }

    private fun setScheduleOptionsVisible(isVisible: Boolean) = with(binding) {
        lastScheduleOption.isVisible = isVisible && hasLastScheduleOption
        scheduleOptions.isVisible = isVisible
        customScheduleOption.isVisible = isVisible
    }

    override fun onLastScheduleOptionClicked() = Unit

    override fun onScheduleOptionClicked(dateItem: ScheduleOption) = Unit

    override fun onCustomScheduleOptionClicked() = executeIfAuthorized { showCustomScheduleDatePicker() }

    private fun onCustomDelayReminderClicked() = executeIfAuthorized { showCustomDelayReminderDatePicker() }

    private fun executeIfAuthorized(onAuthorized: () -> Unit) {
        val kSuite = currentKSuite
        val matomoName = MatomoName.ScheduledCustomDate.value

        when (kSuite) {
            KSuite.Perso.Free -> openMyKSuiteUpgradeBottomSheet(matomoName)
            KSuite.Pro.Free -> openKSuiteProBottomSheet(kSuite, navigationArgs.isAdmin, matomoName)
            KSuite.StarterPack -> openMailPremiumBottomSheet(matomoName)
            else -> onAuthorized()
        }
    }

    private fun showCustomScheduleDatePicker() {
        dateAndTimeScheduleDialog.show(
            onDateSelected = { timestamp ->
                trackScheduleSendEvent(MatomoName.CustomSchedule)
                selectedScheduleEpoch = timestamp
                applyCustomDateSelectionUi(timestamp, binding.customScheduleOption, binding.scheduleOptions)
            },
        )
    }

    private fun showCustomDelayReminderDatePicker() {
        dateAndTimeScheduleDialog.show(
            onDateSelected = { timestamp ->
                trackScheduleSendEvent(MatomoName.CustomReminder)
                selectedReminderEpoch = timestamp
                applyCustomDateSelectionUi(timestamp, binding.customDelayReminder, binding.optionsDelays)
            },
        )
    }

    private fun applyCustomDateSelectionUi(
        timestamp: Long,
        optionView: ItemSettingView,
        groupView: SettingRadioGroupView
    ) {
        val formattedDate = requireContext().dayOfWeekDateWithoutYear(date = Date(timestamp))

        optionView.setSubtitle(formattedDate)
        optionView.setCheckMark(displayCheckMark = true)
        groupView.clearCheck()
    }
}
