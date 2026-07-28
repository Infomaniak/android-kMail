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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackScheduleSendEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.FeatureFlag
import com.infomaniak.mail.databinding.FragmentSendOptionsBinding
import com.infomaniak.mail.ui.alertDialogs.SelectDateAndTimeForScheduledDraftDialog
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleOption
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleOptionUtils
import com.infomaniak.mail.ui.main.settings.ItemSettingView
import com.infomaniak.mail.ui.main.settings.SettingRadioButtonView
import com.infomaniak.mail.ui.main.settings.SettingRadioGroupView
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel
import com.infomaniak.mail.ui.newMessage.ReminderConfig
import com.infomaniak.mail.ui.newMessage.ReminderPreset
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
class DraftSendOptionsFragment : Fragment() {

    private var binding: FragmentSendOptionsBinding by safeBinding()
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()
    private val navigationArgs: DraftSendOptionsFragmentArgs by navArgs()

    @Inject
    lateinit var dateAndTimeScheduleDialog: SelectDateAndTimeForScheduledDraftDialog

    @Inject
    lateinit var localSettings: LocalSettings

    private val currentKSuite: KSuite? by lazy { navigationArgs.currentKSuite }
    private val lastSelectedEpoch: Long? by lazy { navigationArgs.lastSelectedScheduleEpochMillis.takeIf { it != 0L } }
    private val currentlyScheduledEpochMillis: Long? by lazy {
        navigationArgs.currentlyScheduledEpochMillis.takeIf { it != 0L }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSendOptionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        dateAndTimeScheduleDialog.bindAlertToLifecycle(viewLifecycleOwner)

        setupScheduleOptions()
        lastScheduleOption.associatedValue = lastSelectedEpoch?.toString()

        setReminderOptionsVisible(isVisible = false)
        setScheduleOptionsVisible(isVisible = false)

        setupToggles()
        setupScheduleSelection()
        setupReminderOptions()

        restoreStateFromViewModel()

        observeFeatureFlagUpdates()
    }

    private fun observeFeatureFlagUpdates() = with(binding) {
        newMessageViewModel.featureFlagsLive.observe(viewLifecycleOwner) { featureFlags ->
            val isRemindersEnabled = featureFlags?.contains(FeatureFlag.RESPONSE_REQUIRED) ?: false
            reminderLayout.isVisible = isRemindersEnabled
            val isScheduledDraftsEnabled = featureFlags?.contains(FeatureFlag.SCHEDULE_DRAFTS) ?: false
            scheduleSending.isVisible = isScheduledDraftsEnabled
            dividerBottomReminderOptions.isVisible = isRemindersEnabled && isScheduledDraftsEnabled
        }
    }

    private fun createScheduleOptionItem(scheduleOption: ScheduleOption): View {
        return SettingRadioButtonView(requireContext()).apply {
            id = View.generateViewId()
            associatedValue = scheduleOption.date().time.toString()
            setText(getString(scheduleOption.titleRes))
            setDescription(context.dayOfWeekDateWithoutYear(date = scheduleOption.date()))
        }
    }

    private fun bindLastScheduleOptionDescription(description: String) = binding.lastScheduleOption.setDescription(description)

    private fun onLastScheduleOptionClicked() {
        newMessageViewModel.scheduleConfig.value = lastSelectedEpoch?.let(ScheduleConfig::Scheduled) ?: ScheduleConfig.None
    }

    private fun onCustomScheduleOptionClicked() = executeIfAuthorized { showCustomScheduleDatePicker() }

    private fun setupScheduleOptions() = with(binding) {
        val lastDate = ScheduleOptionUtils.getLastScheduleOptionDate(lastSelectedEpoch, currentlyScheduledEpochMillis)
        lastScheduleOption.apply {
            if (lastDate != null) {
                isVisible = true
                setDescription(requireContext().dayOfWeekDateWithoutYear(lastDate))
                setOnClickListener { onLastScheduleOptionClicked() }
            } else {
                isVisible = false
            }
        }

        ScheduleOptionUtils.getAvailableScheduleOptions(currentlyScheduledEpochMillis).forEach { scheduleOption ->
            scheduleOptions.addView(createScheduleOptionItem(scheduleOption))
        }

        customScheduleOption.setOnClickListener { onCustomScheduleOptionClicked() }
    }

    private fun setupToggles() = with(binding) {
        reminderIfNoAnswer.setOnClickListener {
            if (!reminderIfNoAnswer.isChecked) {
                removeReminderOptionsSelection()
            } else {
                defaultReminderSelection()
            }
            setReminderOptionsVisible(isVisible = reminderIfNoAnswer.isChecked)
        }
        scheduleSending.setOnClickListener {
            if (!scheduleSending.isChecked) {
                removeScheduleOptionsSelection()
            } else {
                defaultScheduleSelection()
            }
            setScheduleOptionsVisible(isVisible = scheduleSending.isChecked)
        }
    }

    private fun setupScheduleSelection() = with(binding) {
        scheduleOptions.onItemCheckedListener { _, value, _ ->
            val epoch = value?.toLongOrNull()
            newMessageViewModel.scheduleConfig.value = if (epoch != null) ScheduleConfig.Scheduled(epoch) else ScheduleConfig.None
            customScheduleOption.setCheckMark(displayCheckMark = false)
            customScheduleOption.removeSubtitle()
        }

        val paddingStartValue = resources.getDimensionPixelSize(R.dimen.startPaddingWithoutIcon)
        (scheduleOptions.children + reminderVisibility + customScheduleOption).forEach { view ->
            view.applyContentPaddingStart(paddingStartValue)
        }
    }

    private fun setupReminderOptions() = with(binding) {
        hours24.setText(resources.getQuantityString(R.plurals.hoursBeforeSendingReminder, 24, 24))
        days3.setText(resources.getQuantityString(R.plurals.daysBeforeSendingReminder, 3, 3))
        days7.setText(resources.getQuantityString(R.plurals.daysBeforeSendingReminder, 7, 7))

        val paddingStartValue = resources.getDimensionPixelSize(R.dimen.startPaddingWithoutIcon)
        (optionsDelays.children + customDelayReminder).forEach { view -> view.applyContentPaddingStart(paddingStartValue) }

        optionsDelays.onItemCheckedListener { _, value, _ ->
            val minutes = value?.toIntOrNull()
            val isKnownPreset = ReminderPreset.entries.any { preset -> preset.delayMinutes == minutes }
            newMessageViewModel.reminderConfig.value = if (minutes != null && isKnownPreset) {
                ReminderConfig.Delayed(minutes, isCustom = false)
            } else {
                ReminderConfig.None
            }

            customDelayReminder.setCheckMark(displayCheckMark = false)
            customDelayReminder.removeSubtitle()
        }
    }

    private fun setReminderOptionsVisible(isVisible: Boolean) {
        TransitionManager.beginDelayedTransition(binding.reminderOptionsWrapper.parent as ViewGroup)
        binding.reminderVisibility.isVisible = isVisible
        binding.reminderOptionsWrapper.isVisible = isVisible
    }

    private fun removeReminderOptionsSelection() {
        binding.optionsDelays.clearCheck()
        binding.customDelayReminder.setCheckMark(displayCheckMark = false)
        binding.customDelayReminder.removeSubtitle()
        newMessageViewModel.reminderConfig.value = ReminderConfig.None
    }

    private fun defaultReminderSelection() = with(binding) {
        optionsDelays.check(R.id.hours24)
        newMessageViewModel.reminderConfig.value = ReminderConfig.Delayed(ReminderPreset.HOURS_24.delayMinutes, isCustom = false)
    }

    private fun defaultScheduleSelection() = with(binding) {
        val firstVisibleOption = scheduleOptions.children
            .filterIsInstance<SettingRadioButtonView>()
            .firstOrNull { it.id != R.id.lastScheduleOption }

        firstVisibleOption?.let { option ->
            val epoch = option.associatedValue?.toLongOrNull()
            if (epoch != null) {
                scheduleOptions.check(option.id)
                newMessageViewModel.scheduleConfig.value = ScheduleConfig.Scheduled(epoch)
            }
        }
    }

    private fun removeScheduleOptionsSelection() = with(binding) {
        scheduleOptions.clearCheck()
        customScheduleOption.setCheckMark(displayCheckMark = false)
        customScheduleOption.removeSubtitle()
        newMessageViewModel.scheduleConfig.value = ScheduleConfig.None
    }

    private fun setScheduleOptionsVisible(isVisible: Boolean) = with(binding) {
        TransitionManager.beginDelayedTransition(scheduleOptionsWrapper.parent as ViewGroup)
        scheduleOptionsWrapper.isVisible = isVisible
    }

    private fun restoreStateFromViewModel() {
        restoreScheduleState()
        restoreReminderState()
    }

    private fun restoreScheduleState() = with(binding) {
        fun applyCustomSchedule(epoch: Long) {
            customScheduleOption.setSubtitle(requireContext().dayOfWeekDateWithoutYear(Date(epoch)))
            customScheduleOption.setCheckMark(displayCheckMark = true)
        }

        val savedSchedule = newMessageViewModel.scheduleConfig.value as? ScheduleConfig.Scheduled ?: return@with
        val epoch = savedSchedule.epochMillis

        scheduleSending.isChecked = true
        setScheduleOptionsVisible(isVisible = true)

        val scheduleStr = epoch.toString()
        val matchedOption = scheduleOptions.children
            .filterIsInstance<SettingRadioButtonView>()
            .firstOrNull { it.associatedValue == scheduleStr }

        when {
            savedSchedule.isCustom -> applyCustomSchedule(epoch)
            matchedOption != null -> scheduleOptions.check(matchedOption.id)
            lastSelectedEpoch != null && lastScheduleOption.associatedValue == scheduleStr -> {
                scheduleOptions.check(lastScheduleOption.id)
            }
            else -> applyCustomSchedule(epoch)
        }
    }

    private fun restoreReminderState() = with(binding) {
        val savedReminder = newMessageViewModel.reminderConfig.value ?: ReminderConfig.None
        if (savedReminder !is ReminderConfig.Delayed) return@with

        reminderIfNoAnswer.isChecked = true
        setReminderOptionsVisible(isVisible = true)


        if (savedReminder.isCustom) {
            customDelayReminder.setSubtitle(requireContext().formatDelayText(savedReminder.delayMinutes))
            customDelayReminder.setCheckMark(displayCheckMark = true)
        } else {
            val targetId = when (savedReminder.delayMinutes) {
                ReminderPreset.HOURS_24.delayMinutes -> R.id.hours24
                ReminderPreset.DAYS_3.delayMinutes -> R.id.days3
                ReminderPreset.DAYS_7.delayMinutes -> R.id.days7
                else -> null
            }
            targetId?.let { optionsDelays.check(it) }
        }
    }

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
                newMessageViewModel.scheduleConfig.value = ScheduleConfig.Scheduled(timestamp, isCustom = true)
                localSettings.lastSelectedScheduleEpochMillis = timestamp
                applyCustomDateSelectionUi(timestamp, binding.customScheduleOption, binding.scheduleOptions)
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
