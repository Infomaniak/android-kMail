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
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackScheduleSendEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.FeatureFlag
import com.infomaniak.mail.databinding.FragmentSendOptionsBinding
import com.infomaniak.mail.ui.alertDialogs.CustomReminderPickerDialog
import com.infomaniak.mail.ui.alertDialogs.SelectDateAndTimeForScheduledDraftDialog
import com.infomaniak.mail.ui.alertDialogs.SelectVisibilityReminderDialog
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleOption
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleOptionsHelper
import com.infomaniak.mail.ui.main.settings.ItemSettingView
import com.infomaniak.mail.ui.main.settings.SettingRadioButtonView
import com.infomaniak.mail.ui.main.settings.SettingRadioGroupView
import com.infomaniak.mail.ui.main.thread.actions.TrailingContent
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
    lateinit var customReminderPickerDialog: CustomReminderPickerDialog

    @Inject
    lateinit var selectVisibilityDialog: SelectVisibilityReminderDialog

    @Inject
    lateinit var localSettings: LocalSettings

    private var pendingScheduleConfig: ScheduleConfig = ScheduleConfig.None
    private var pendingReminderConfig: ReminderConfig = ReminderConfig.None
    private var pendingShouldRemindRecipient: Boolean = true
    private var pendingLastSelectedScheduleEpochMillis: Long? = null

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
        customReminderPickerDialog.bindAlertToLifecycle(viewLifecycleOwner)
        selectVisibilityDialog.bindAlertToLifecycle(viewLifecycleOwner)

        pendingLastSelectedScheduleEpochMillis = lastSelectedEpoch

        setupToolbar()
        ScheduleOptionsHelper(
            context = requireContext(),
            lastScheduleOption = lastScheduleOption,
            scheduleOptionsContainer = binding.scheduleOptions,
            customScheduleOption = customScheduleOption,
            lastSelectedEpoch = lastSelectedEpoch,
            currentlyScheduledEpochMillis = currentlyScheduledEpochMillis,
            currentKSuite = currentKSuite,
            onLastScheduleOptionClicked = ::onLastScheduleOptionClicked,
            onCustomScheduleOptionClicked = ::onCustomScheduleOptionClicked,
            createScheduleOptionItem = ::createScheduleOptionItem,
            bindLastScheduleOptionDescription = ::bindLastScheduleOptionDescription,
            setupCustomScheduleOptionTrailing = ::setupCustomScheduleOptionTrailing,
        ).setup()
        lastScheduleOption.associatedValue = lastSelectedEpoch?.toString()

        setReminderOptionsVisible(isVisible = false)
        setScheduleOptionsVisible(isVisible = false)

        pendingShouldRemindRecipient = newMessageViewModel.shouldRemindRecipient.value ?: true
        updateReminderVisibilitySubtitle()

        setupToggles()
        setupScheduleSelection()
        setupReminderOptions()

        restoreStateFromViewModel()

        saveButton.setOnClickListener { saveOptions() }

        observeFeatureFlagUpdates()
    }

    private fun observeFeatureFlagUpdates() = with(binding) {
        newMessageViewModel.featureFlagsLive.observe(viewLifecycleOwner) { featureFlags ->
            val isScheduledDraftsEnabled = featureFlags?.contains(FeatureFlag.SCHEDULE_DRAFTS) ?: false
            val isRemindersEnabled = featureFlags?.contains(FeatureFlag.RESPONSE_REQUIRED) ?: false
            reminderLayout.isVisible = isRemindersEnabled
            reminderTopDivider.isVisible = isRemindersEnabled
            dividerBottomReminderOptions.isVisible = isRemindersEnabled
            if (!isRemindersEnabled) {
                reminderIfNoAnswer.isChecked = false
                setReminderOptionsVisible(isVisible = false)
                removeReminderOptionsSelection()
            }
            scheduleSending.isVisible = isScheduledDraftsEnabled
            dividerTopScheduleOptions.isVisible = isScheduledDraftsEnabled
            if (!isScheduledDraftsEnabled) {
                scheduleSending.isChecked = false
                setScheduleOptionsVisible(isVisible = false)
                removeScheduleOptionsSelection()
            }
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

    private fun setupCustomScheduleOptionTrailing(kSuite: KSuite?) {
        binding.customScheduleOption.trailingContent = trailingContentFor(kSuite)
    }

    private fun onLastScheduleOptionClicked() {
        pendingScheduleConfig = lastSelectedEpoch?.let(ScheduleConfig::Scheduled) ?: ScheduleConfig.None
        pendingLastSelectedScheduleEpochMillis = null
    }

    private fun onCustomScheduleOptionClicked() {
        executeIfAuthorized(MatomoName.ScheduledCustomDate.value) { showCustomScheduleDatePicker() }
    }

    private fun setupToolbar() = with(binding.toolbar) {
        setNavigationOnClickListener { findNavController().popBackStack() }
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
        hours24.setText(resources.getQuantityString(R.plurals.hoursBeforeSendingReminder, 24, 24))
        days3.setText(resources.getQuantityString(R.plurals.daysBeforeSendingReminder, 3, 3))
        days7.setText(resources.getQuantityString(R.plurals.daysBeforeSendingReminder, 7, 7))

        customDelayReminder.trailingContent = trailingContentFor(currentKSuite)

        val paddingStartValue = resources.getDimensionPixelSize(R.dimen.emptyStatePadding)
        (optionsDelays.children + customDelayReminder).forEach { view -> view.applyContentPaddingStart(paddingStartValue) }

        optionsDelays.onItemCheckedListener { _, value, _ ->
            val minutes = value?.toIntOrNull()
            val isKnownPreset = ReminderPreset.entries.any { preset -> preset.delayMinutes == minutes }
            pendingReminderConfig = if (minutes != null && isKnownPreset) {
                ReminderConfig.Delayed(minutes, isCustom = false)
            } else {
                ReminderConfig.None
            }

            customDelayReminder.setCheckMark(displayCheckMark = false)
            customDelayReminder.removeSubtitle()
        }

        customDelayReminder.setOnClickListener { onCustomDelayReminderClicked() }
        reminderVisibility.setOnClickListener { showVisibilityReminderPicker() }
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

    private fun removeReminderOptionsSelection() {
        binding.optionsDelays.clearCheck()
        binding.customDelayReminder.setCheckMark(displayCheckMark = false)
        binding.customDelayReminder.removeSubtitle()
        pendingReminderConfig = ReminderConfig.None
        pendingShouldRemindRecipient = true
        updateReminderVisibilitySubtitle()
    }

    private fun defaultReminderSelection() = with(binding) {
        optionsDelays.check(R.id.hours24)
        pendingReminderConfig = ReminderConfig.Delayed(ReminderPreset.HOURS_24.delayMinutes, isCustom = false)
        pendingShouldRemindRecipient = true
        updateReminderVisibilitySubtitle()
    }

    private fun defaultScheduleSelection() = with(binding) {
        val firstVisibleOption = scheduleOptions.children
            .filterIsInstance<SettingRadioButtonView>()
            .firstOrNull { it.id != R.id.lastScheduleOption }

        firstVisibleOption?.let { option ->
            val epoch = option.associatedValue?.toLongOrNull()
            if (epoch != null) {
                scheduleOptions.check(option.id)
                pendingScheduleConfig = ScheduleConfig.Scheduled(epoch)
                pendingLastSelectedScheduleEpochMillis = null
            }
        }
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

        pendingScheduleConfig = savedSchedule
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

        pendingReminderConfig = savedReminder
        pendingShouldRemindRecipient = newMessageViewModel.shouldRemindRecipient.value ?: true
        reminderIfNoAnswer.isChecked = true
        setReminderOptionsVisible(isVisible = true)
        updateReminderVisibilitySubtitle()


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

    private fun onCustomDelayReminderClicked() =
        executeIfAuthorized(MatomoName.ReminderCustomDelta.value) { showCustomDelayReminderDatePicker() }

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
                pendingScheduleConfig = ScheduleConfig.Scheduled(timestamp, isCustom = true)
                pendingLastSelectedScheduleEpochMillis = timestamp
                applyCustomDateSelectionUi(timestamp, binding.customScheduleOption, binding.scheduleOptions)
            },
        )
    }

    private fun showCustomDelayReminderDatePicker() {
        customReminderPickerDialog.show(
            onDelaySelected = { delayMinutes ->
                trackScheduleSendEvent(MatomoName.CustomReminder)
                pendingReminderConfig = ReminderConfig.Delayed(delayMinutes, isCustom = true)
                binding.customDelayReminder.setSubtitle(requireContext().formatDelayText(delayMinutes))
                binding.customDelayReminder.setCheckMark(displayCheckMark = true)
                binding.optionsDelays.clearCheck()
            },
        )
    }

    private fun showVisibilityReminderPicker() {
        selectVisibilityDialog.show(
            selectRecipientsAndMe = pendingShouldRemindRecipient,
            onVisibilitySelected = { isRecipientsAndMe ->
                pendingShouldRemindRecipient = isRecipientsAndMe
                updateReminderVisibilitySubtitle()
            },
        )
    }

    private fun updateReminderVisibilitySubtitle() = with(binding) {
        val subtitleRes = if (pendingShouldRemindRecipient) {
            R.string.selectionReminderRecipientsAndMe
        } else {
            R.string.selectionReminderMeOnly
        }
        reminderVisibility.setSubtitle(subtitleRes)
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
        newMessageViewModel.shouldRemindRecipient.value = pendingShouldRemindRecipient

        if (pendingScheduleConfig is ScheduleConfig.Scheduled) {
            pendingLastSelectedScheduleEpochMillis?.let { localSettings.lastSelectedScheduleEpochMillis = it }
        }

        findNavController().popBackStack()
    }
}
