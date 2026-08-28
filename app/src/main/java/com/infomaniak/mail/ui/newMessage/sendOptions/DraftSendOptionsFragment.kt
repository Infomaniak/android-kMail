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
import com.infomaniak.core.common.observe
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.toFloat
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.MatomoMail.trackScheduleSendEvent
import com.infomaniak.mail.MatomoMail.trackSendOptionsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.FeatureFlag
import com.infomaniak.mail.databinding.FragmentSendOptionsBinding
import com.infomaniak.mail.ui.alertDialogs.CustomReminderPickerDialog
import com.infomaniak.mail.ui.alertDialogs.SelectDateAndTimeForScheduledDraftDialog
import com.infomaniak.mail.ui.alertDialogs.SelectVisibilityReminderDialog
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleOption
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleOptionUtils
import com.infomaniak.mail.ui.main.settings.SettingRadioButtonView
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel
import com.infomaniak.mail.ui.newMessage.ReminderConfig
import com.infomaniak.mail.ui.newMessage.ReminderPreset
import com.infomaniak.mail.ui.newMessage.ScheduleConfig
import com.infomaniak.mail.utils.date.DateFormatUtils.dayOfWeekDateWithoutYear
import com.infomaniak.mail.utils.date.DateFormatUtils.formatDelayText
import com.infomaniak.mail.utils.extensions.applyContentPaddingStart
import com.infomaniak.mail.utils.extensions.setKSuiteTrailingContent
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
    lateinit var customReminderPickerDialog: CustomReminderPickerDialog

    @Inject
    lateinit var selectVisibilityDialog: SelectVisibilityReminderDialog

    @Inject
    lateinit var localSettings: LocalSettings

    private val currentKSuite: KSuite? by lazy { navigationArgs.currentKSuite }
    private val lastSelectedEpoch: Long? by lazy { navigationArgs.lastSelectedScheduleEpochMillis.takeIf { it != 0L } }
    private val currentlyScheduledEpochMillis: Long? by lazy {
        navigationArgs.currentlyScheduledEpochMillis.takeIf { it != 0L }
    }

    private var shouldRemindRecipient: Boolean
        get() = newMessageViewModel.shouldRemindRecipient.value
        set(value) {
            newMessageViewModel.setShouldRemindRecipient(value)
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSendOptionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        dateAndTimeScheduleDialog.bindAlertToLifecycle(viewLifecycleOwner)
        customReminderPickerDialog.bindAlertToLifecycle(viewLifecycleOwner)
        selectVisibilityDialog.bindAlertToLifecycle(viewLifecycleOwner)

        setupScheduleOptions()
        lastScheduleOption.associatedValue = lastSelectedEpoch?.toString()

        setReminderOptionsVisible(isVisible = false)
        setScheduleOptionsVisible(isVisible = false)

        setupToggles()
        setupScheduleSelection()
        setupReminderOptions()

        observeFeatureFlagUpdates()
        observeScheduleConfig()
        observeReminderConfig()
        observeShouldRemindRecipient()
    }

    private fun observeShouldRemindRecipient() {
        newMessageViewModel.shouldRemindRecipient.observe(viewLifecycleOwner) { shouldRemindRecipient ->
            updateReminderVisibilitySubtitle(shouldRemindRecipient)
        }
    }

    private fun observeFeatureFlagUpdates() = with(binding) {
        newMessageViewModel.featureFlagsLive.observe(viewLifecycleOwner) { featureFlags ->
            val isRemindersEnabled = featureFlags?.contains(FeatureFlag.RESPONSE_REQUIRED) ?: false
            reminderIfNoAnswer.isVisible = isRemindersEnabled
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

    private fun setupCustomScheduleOptionTrailing() {
        binding.customScheduleOption.setKSuiteTrailingContent(currentKSuite)
    }

    private fun onLastScheduleOptionClicked() {
        newMessageViewModel.setScheduleConfig(lastSelectedEpoch?.let(ScheduleConfig::Scheduled) ?: ScheduleConfig.None)
    }

    private fun onCustomScheduleOptionClicked() {
        executeIfAuthorized(MatomoName.ScheduledCustomDate.value) { showCustomScheduleDatePicker() }
    }

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
        setupCustomScheduleOptionTrailing()
    }

    private fun setupToggles() = with(binding) {
        reminderIfNoAnswer.setOnClickListener {
            if (!reminderIfNoAnswer.isChecked) removeReminderOptionsSelection() else defaultReminderSelection()
            trackSendOptionsEvent(MatomoName.ToggleReminder, value = reminderIfNoAnswer.isChecked.toFloat())
        }
        scheduleSending.setOnClickListener {
            if (!scheduleSending.isChecked) removeScheduleOptionsSelection() else defaultScheduleSelection()
            trackSendOptionsEvent(MatomoName.ToggleSchedule, value = scheduleSending.isChecked.toFloat())
        }
    }

    private fun setupScheduleSelection() = with(binding) {
        scheduleOptions.onItemCheckedListener { _, value, _ ->
            val epoch = value?.toLongOrNull()
            newMessageViewModel.setScheduleConfig(if (epoch != null) ScheduleConfig.Scheduled(epoch) else ScheduleConfig.None)
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

        customDelayReminder.setKSuiteTrailingContent(currentKSuite)

        val paddingStartValue = resources.getDimensionPixelSize(R.dimen.startPaddingWithoutIcon)
        (optionsDelays.children + customDelayReminder).forEach { view -> view.applyContentPaddingStart(paddingStartValue) }

        optionsDelays.onItemCheckedListener { _, value, _ ->
            val minutes = value?.toIntOrNull()
            val isValidPreset = ReminderPreset.entries.any { preset -> preset.delayMinutes == minutes }
            if (isValidPreset) trackSendOptionsEvent(MatomoName.SelectedReminderDelta)
            newMessageViewModel.setReminderConfig(
                config = if (minutes != null && isValidPreset) {
                    ReminderConfig.Delayed(minutes, isCustom = false)
                } else {
                    ReminderConfig.None
                }
            )
        }

        customDelayReminder.setOnClickListener { onCustomDelayReminderClicked() }
        reminderVisibility.setOnClickListener { showVisibilityReminderPicker() }
    }

    private fun setReminderOptionsVisible(isVisible: Boolean) {
        TransitionManager.beginDelayedTransition(binding.reminderOptionsWrapper.parent as ViewGroup)
        binding.reminderOptionsWrapper.isVisible = isVisible
    }

    private fun removeReminderOptionsSelection() {
        binding.optionsDelays.clearCheck()
        newMessageViewModel.setReminderConfig(ReminderConfig.None)
        shouldRemindRecipient = true
    }

    private fun defaultReminderSelection() = with(binding) {
        optionsDelays.check(R.id.hours24)
        newMessageViewModel.setReminderConfig(ReminderConfig.Delayed(ReminderPreset.HOURS_24.delayMinutes, isCustom = false))
        shouldRemindRecipient = true
    }

    private fun defaultScheduleSelection() = with(binding) {
        val firstVisibleOption = scheduleOptions.children
            .filterIsInstance<SettingRadioButtonView>()
            .firstOrNull { it.id != R.id.lastScheduleOption }

        firstVisibleOption?.let { option ->
            val epoch = option.associatedValue?.toLongOrNull()
            if (epoch != null) {
                scheduleOptions.check(option.id)
                newMessageViewModel.setScheduleConfig(ScheduleConfig.Scheduled(epoch))
            }
        }
    }

    private fun removeScheduleOptionsSelection() = with(binding) {
        scheduleOptions.clearCheck()
        newMessageViewModel.setScheduleConfig(ScheduleConfig.None)
    }

    private fun setScheduleOptionsVisible(isVisible: Boolean) = with(binding) {
        TransitionManager.beginDelayedTransition(scheduleOptionsWrapper.parent as ViewGroup)
        scheduleOptionsWrapper.isVisible = isVisible
    }

    private fun observeScheduleConfig() {
        newMessageViewModel.scheduleConfig.observe(viewLifecycleOwner) { scheduleConfig ->
            renderScheduleConfig(scheduleConfig)
        }
    }

    private fun observeReminderConfig() {
        newMessageViewModel.reminderConfig.observe(viewLifecycleOwner) { reminderConfig ->
            renderReminderConfig(reminderConfig)
        }
    }

    private fun renderScheduleConfig(scheduleConfig: ScheduleConfig) = with(binding) {
        when (scheduleConfig) {
            is ScheduleConfig.Scheduled -> {
                scheduleSending.isChecked = true
                setScheduleOptionsVisible(isVisible = true)
                handleScheduledConfig(scheduleConfig)
            }
            ScheduleConfig.None -> {
                scheduleSending.isChecked = false
                setScheduleOptionsVisible(isVisible = false)
                scheduleOptions.clearCheck()
                resetCustomScheduleOption()
            }
        }
    }

    private fun resetCustomScheduleOption() = with(binding) {
        customScheduleOption.setCheckMark(displayCheckMark = false)
        customScheduleOption.removeSubtitle()
    }

    private fun checkStandardScheduleOption(optionId: Int) {
        resetCustomScheduleOption()
        binding.scheduleOptions.check(optionId)
    }

    private fun applyCustomSchedule(epoch: Long) = with(binding) {
        scheduleOptions.clearCheck()
        customScheduleOption.setSubtitle(requireContext().dayOfWeekDateWithoutYear(Date(epoch)))
        customScheduleOption.setCheckMark(displayCheckMark = true)
    }

    private fun handleScheduledConfig(config: ScheduleConfig.Scheduled) = with(binding) {
        val epoch = config.epochMillis
        val scheduleStr = epoch.toString()
        val matchedOption = scheduleOptions.children
            .filterIsInstance<SettingRadioButtonView>()
            .firstOrNull { it.associatedValue == scheduleStr }

        when {
            config.isCustom -> applyCustomSchedule(epoch)
            matchedOption != null -> checkStandardScheduleOption(matchedOption.id)
            lastSelectedEpoch != null && lastScheduleOption.associatedValue == scheduleStr -> {
                checkStandardScheduleOption(lastScheduleOption.id)
            }
            else -> applyCustomSchedule(epoch)
        }
    }

    private fun renderReminderConfig(reminderConfig: ReminderConfig) = with(binding) {
        when (reminderConfig) {
            is ReminderConfig.Delayed -> {
                reminderIfNoAnswer.isChecked = true
                setReminderOptionsVisible(isVisible = true)
                handleDelayedConfig(reminderConfig)
            }
            ReminderConfig.None -> {
                reminderIfNoAnswer.isChecked = false
                setReminderOptionsVisible(isVisible = false)
                optionsDelays.clearCheck()
                resetCustomDelayReminder()
            }
        }
    }

    private fun onCustomDelayReminderClicked() {
        executeIfAuthorized(MatomoName.ReminderCustomDate.value) { showCustomDelayReminderDatePicker() }
    }

    private fun resetCustomDelayReminder() = with(binding) {
        customDelayReminder.setCheckMark(displayCheckMark = false)
        customDelayReminder.removeSubtitle()
    }

    private fun applyCustomReminder(delayMinutes: Int) = with(binding) {
        optionsDelays.clearCheck()
        customDelayReminder.setSubtitle(requireContext().formatDelayText(delayMinutes))
        customDelayReminder.setCheckMark(displayCheckMark = true)
    }

    private fun checkStandardReminderOption(optionId: Int?) {
        resetCustomDelayReminder()
        if (optionId != null) binding.optionsDelays.check(optionId) else binding.optionsDelays.clearCheck()
    }

    private fun handleDelayedConfig(config: ReminderConfig.Delayed) {
        if (config.isCustom) {
            applyCustomReminder(config.delayMinutes)
        } else {
            val targetId = when (config.delayMinutes) {
                ReminderPreset.HOURS_24.delayMinutes -> R.id.hours24
                ReminderPreset.DAYS_3.delayMinutes -> R.id.days3
                ReminderPreset.DAYS_7.delayMinutes -> R.id.days7
                else -> null
            }
            checkStandardReminderOption(targetId)
        }
    }

    private fun executeIfAuthorized(matomoName: String, onAuthorized: () -> Unit) {
        when (val kSuite = currentKSuite) {
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
                newMessageViewModel.setScheduleConfig(ScheduleConfig.Scheduled(timestamp, isCustom = true))
                localSettings.lastSelectedScheduleEpochMillis = timestamp
            },
        )
    }

    private fun showCustomDelayReminderDatePicker() {
        customReminderPickerDialog.show(
            onDelaySelected = { delayMinutes ->
                trackScheduleSendEvent(MatomoName.CustomReminder)
                newMessageViewModel.setReminderConfig(ReminderConfig.Delayed(delayMinutes, isCustom = true))
                binding.customDelayReminder.apply {
                    setSubtitle(requireContext().formatDelayText(delayMinutes))
                    setCheckMark(displayCheckMark = true)
                }
                binding.optionsDelays.clearCheck()
            },
        )
    }

    private fun showVisibilityReminderPicker() {
        selectVisibilityDialog.show(
            selectRecipientsAndMe = shouldRemindRecipient,
            onVisibilitySelected = { isRecipientsAndMe ->
                shouldRemindRecipient = isRecipientsAndMe
            },
        )
    }

    private fun updateReminderVisibilitySubtitle(shouldRemindRecipient: Boolean) = with(binding) {
        val subtitleRes = if (shouldRemindRecipient) {
            R.string.selectionReminderRecipientsAndMe
        } else {
            R.string.selectionReminderMeOnly
        }
        reminderVisibility.setSubtitle(subtitleRes)
    }
}
