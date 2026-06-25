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

package com.infomaniak.mail.ui.newMessage.sendOptions

import android.os.Bundle
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackScheduleSendEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.databinding.FragmentSendOptionsBinding
import com.infomaniak.mail.ui.alertDialogs.CustomReminderPickerDialog
import com.infomaniak.mail.ui.alertDialogs.SelectDateAndTimeForScheduledDraftDialog
import com.infomaniak.mail.ui.bottomSheetDialogs.BaseSchedulePickerBottomSheet
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleOption
import com.infomaniak.mail.ui.main.settings.ItemSettingView
import com.infomaniak.mail.ui.main.settings.SettingRadioButtonView
import com.infomaniak.mail.ui.main.settings.SettingRadioGroupView
import com.infomaniak.mail.ui.main.thread.actions.TrailingContent
import com.infomaniak.mail.ui.newMessage.DelayHours.DAYS_3
import com.infomaniak.mail.ui.newMessage.DelayHours.DAYS_7
import com.infomaniak.mail.ui.newMessage.DelayHours.HOURS_24
import com.infomaniak.mail.ui.newMessage.HOURS_IN_A_DAY
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel
import com.infomaniak.mail.ui.newMessage.ReminderConfig
import com.infomaniak.mail.ui.newMessage.ScheduleConfig
import com.infomaniak.mail.utils.date.DateFormatUtils.dayOfWeekDateWithoutYear
import com.infomaniak.mail.utils.date.DateFormatUtils.formatDelayText
import com.infomaniak.mail.utils.extensions.applyContentPaddingStart
import com.infomaniak.mail.utils.openKSuiteProBottomSheet
import com.infomaniak.mail.utils.openMailPremiumBottomSheet
import com.infomaniak.mail.utils.openMyKSuiteUpgradeBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class DraftSendOptionsFragment : BaseSchedulePickerBottomSheet() {

    private var binding: FragmentSendOptionsBinding by safeBinding()
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()
    private val navigationArgs: DraftSendOptionsFragmentArgs by navArgs()

    @Inject
    lateinit var dateAndTimeScheduleDialog: SelectDateAndTimeForScheduledDraftDialog

    @Inject
    lateinit var customReminderPickerDialog: CustomReminderPickerDialog

    @Inject
    lateinit var localSettings: LocalSettings

    private var pendingScheduleConfig: ScheduleConfig = ScheduleConfig.None
    private var pendingReminderConfig: ReminderConfig = ReminderConfig.None
    private var pendingLastSelectedScheduleEpochMillis: Long? = null
    private var hasLastScheduleOption = false

    override val currentKSuite: KSuite? by lazy { navigationArgs.currentKSuite }
    override val lastSelectedEpoch: Long? by lazy { navigationArgs.lastSelectedScheduleEpochMillis.takeIf { it != 0L } }
    override val currentlyScheduledEpochMillis: Long? by lazy {
        navigationArgs.currentlyScheduledEpochMillis.takeIf { it != 0L }
    }

    override val lastScheduleOption get() = binding.lastScheduleOption
    override val scheduleOptionsContainer get() = binding.scheduleOptions
    override val customScheduleOption get() = binding.customScheduleOption

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSendOptionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        dateAndTimeScheduleDialog.bindAlertToLifecycle(viewLifecycleOwner)
        customReminderPickerDialog.bindAlertToLifecycle(viewLifecycleOwner)

        pendingLastSelectedScheduleEpochMillis = lastSelectedEpoch

        setupToolbar()
        setupScheduleOptions()
        hasLastScheduleOption = lastSelectedEpoch != null
        lastScheduleOption.associatedValue = lastSelectedEpoch?.toString()

        setReminderOptionsVisible(isVisible = false)
        setScheduleOptionsVisible(isVisible = false)

        setupToggles()
        setupScheduleSelection()
        setupReminderOptions()

        restoreStateFromViewModel()

        saveButton.setOnClickListener { saveOptions() }
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

    override fun onLastScheduleOptionClicked() {
        pendingScheduleConfig = lastSelectedEpoch?.let(ScheduleConfig::Scheduled) ?: ScheduleConfig.None
        pendingLastSelectedScheduleEpochMillis = null
    }

    override fun onScheduleOptionClicked(dateItem: ScheduleOption) {
        pendingScheduleConfig = ScheduleConfig.Scheduled(dateItem.date().time)
        pendingLastSelectedScheduleEpochMillis = null
    }

    override fun onCustomScheduleOptionClicked() = executeIfAuthorized { showCustomScheduleDatePicker() }

    private fun setupToolbar() = with(binding.toolbar) {
        setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun setupToggles() = with(binding) {
        reminderIfNoAnswer.setOnClickListener {
            if (!reminderIfNoAnswer.isChecked) {
                removeReminderOptionsSelection()
            }
            setReminderOptionsVisible(isVisible = reminderIfNoAnswer.isChecked)
        }
        scheduleSending.setOnClickListener {
            if (!scheduleSending.isChecked) {
                removeScheduleOptionsSelection()
            }
            setScheduleOptionsVisible(isVisible = scheduleSending.isChecked)
        }
    }

    private fun setupScheduleSelection() = with(binding) {
        scheduleOptions.onItemCheckedListener { _, value, _ ->
            val epoch = value?.toLongOrNull()
            pendingScheduleConfig = if (epoch != null) ScheduleConfig.Scheduled(epoch) else ScheduleConfig.None
            pendingLastSelectedScheduleEpochMillis = null
            customScheduleOption.setCheckMark(displayCheckMark = false)
            customScheduleOption.removeSubtitle()
        }

        val paddingStartValue = resources.getDimensionPixelSize(R.dimen.emptyStatePadding)
        (scheduleOptions.children + reminderVisibility + customScheduleOption).forEach { view ->
            view.applyContentPaddingStart(paddingStartValue)
        }
    }

    private fun setupReminderOptions() = with(binding) {
        hours24.setText(resources.getQuantityString(R.plurals.hoursBeforeSendingReminder, HOURS_24.hours, HOURS_24.hours))
        days3.setText(
            resources.getQuantityString(
                R.plurals.daysBeforeSendingReminder,
                DAYS_3.hours / HOURS_IN_A_DAY,
                DAYS_3.hours / HOURS_IN_A_DAY
            )
        )
        days7.setText(
            resources.getQuantityString(
                R.plurals.daysBeforeSendingReminder,
                DAYS_7.hours / HOURS_IN_A_DAY,
                DAYS_7.hours / HOURS_IN_A_DAY
            )
        )

        customDelayReminder.trailingContent = trailingContentFor(currentKSuite)

        val paddingStartValue = resources.getDimensionPixelSize(R.dimen.emptyStatePadding)
        (optionsDelays.children + customDelayReminder).forEach { view -> view.applyContentPaddingStart(paddingStartValue) }

        optionsDelays.onItemCheckedListener { _, value, _ ->
            val hours = value?.toIntOrNull()
            val delay = when (hours) {
                HOURS_24.hours -> HOURS_24
                DAYS_3.hours -> DAYS_3
                DAYS_7.hours -> DAYS_7
                else -> null
            }
            pendingReminderConfig = delay?.let { ReminderConfig.Preset(it) } ?: ReminderConfig.None
            customDelayReminder.setCheckMark(displayCheckMark = false)
            customDelayReminder.removeSubtitle()
        }

        customDelayReminder.setOnClickListener { onCustomDelayReminderClicked() }
    }

    private fun trailingContentFor(kSuite: KSuite?): TrailingContent = when (kSuite) {
        KSuite.Perso.Free -> TrailingContent.KSuitePersoChip
        KSuite.Pro.Free, KSuite.StarterPack -> TrailingContent.KSuiteProChip
        else -> TrailingContent.Chevron
    }

    private fun setReminderOptionsVisible(isVisible: Boolean) {
        TransitionManager.beginDelayedTransition(binding.reminderOptionsWrapper.parent as ViewGroup)
        binding.dividerTopReminderOptions.isVisible = isVisible
        binding.reminderVisibility.isVisible = isVisible
        binding.reminderOptionsWrapper.isVisible = isVisible
    }

    private fun removeReminderOptionsSelection() = with(binding) {
        optionsDelays.clearCheck()
        customDelayReminder.setCheckMark(displayCheckMark = false)
        customDelayReminder.removeSubtitle()
        pendingReminderConfig = ReminderConfig.None
    }

    private fun removeScheduleOptionsSelection() = with(binding) {
        scheduleOptions.clearCheck()
        customScheduleOption.setCheckMark(displayCheckMark = false)
        customScheduleOption.removeSubtitle()
        pendingScheduleConfig = ScheduleConfig.None
        pendingLastSelectedScheduleEpochMillis = null
    }

    private fun setScheduleOptionsVisible(isVisible: Boolean) = with(binding) {
        TransitionManager.beginDelayedTransition(scheduleOptionsWrapper.parent as ViewGroup)
        dividerTopScheduleOptions.isVisible = isVisible
        lastScheduleOption.isVisible = isVisible && hasLastScheduleOption
        scheduleOptionsWrapper.isVisible = isVisible
    }

    private fun restoreStateFromViewModel() {
        restoreScheduleState()
        restoreReminderState()
    }

    private fun restoreScheduleState() = with(binding) {
        val savedSchedule = newMessageViewModel.scheduleConfig.value as? ScheduleConfig.Scheduled ?: return@with
        val epoch = savedSchedule.epochMillis

        pendingScheduleConfig = savedSchedule
        scheduleSending.isChecked = true
        setScheduleOptionsVisible(isVisible = true)

        val scheduleStr = epoch.toString()
        val matchedOption = scheduleOptions.children
            .filterIsInstance<SettingRadioButtonView>()
            .firstOrNull { it.associatedValue == scheduleStr }

        when {
            matchedOption != null -> scheduleOptions.check(matchedOption.id)
            hasLastScheduleOption && lastScheduleOption.associatedValue == scheduleStr -> scheduleOptions.check(lastScheduleOption.id)
            else -> {
                customScheduleOption.setSubtitle(requireContext().dayOfWeekDateWithoutYear(Date(epoch)))
                customScheduleOption.setCheckMark(displayCheckMark = true)
            }
        }
    }

    private fun restoreReminderState() = with(binding) {
        val savedReminder = newMessageViewModel.reminderConfig.value ?: ReminderConfig.None
        if (savedReminder is ReminderConfig.None) return@with

        pendingReminderConfig = savedReminder
        reminderIfNoAnswer.isChecked = true
        setReminderOptionsVisible(isVisible = true)

        when (savedReminder) {
            is ReminderConfig.Custom -> {
                customDelayReminder.setSubtitle(requireContext().formatDelayText(savedReminder.delayMillis))
                customDelayReminder.setCheckMark(displayCheckMark = true)
            }
            is ReminderConfig.Preset -> {
                val targetId = when (savedReminder.delayHours) {
                    HOURS_24 -> R.id.hours24
                    DAYS_3 -> R.id.days3
                    DAYS_7 -> R.id.days7
                }
                optionsDelays.check(targetId)
            }
            else -> Unit
        }
    }

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
                pendingScheduleConfig = ScheduleConfig.Scheduled(timestamp)
                pendingLastSelectedScheduleEpochMillis = timestamp
                applyCustomDateSelectionUi(timestamp, binding.customScheduleOption, binding.scheduleOptions)
            },
        )
    }

    private fun showCustomDelayReminderDatePicker() {
        customReminderPickerDialog.show(
            onDelaySelected = { delayMillis ->
                trackScheduleSendEvent(MatomoName.CustomReminder)
                pendingReminderConfig = ReminderConfig.Custom(delayMillis)
                binding.customDelayReminder.setSubtitle(requireContext().formatDelayText(delayMillis))
                binding.customDelayReminder.setCheckMark(displayCheckMark = true)
                binding.optionsDelays.clearCheck()
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

    private fun saveOptions() {
        newMessageViewModel.scheduleConfig.value = pendingScheduleConfig
        newMessageViewModel.reminderConfig.value = pendingReminderConfig

        if (pendingScheduleConfig is ScheduleConfig.Scheduled) {
            pendingLastSelectedScheduleEpochMillis?.let { localSettings.lastSelectedScheduleEpochMillis = it }
        }

        findNavController().popBackStack()
    }
}
