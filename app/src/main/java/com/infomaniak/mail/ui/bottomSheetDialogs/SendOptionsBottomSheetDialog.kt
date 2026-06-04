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
import androidx.core.view.isVisible
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackScheduleSendEvent
import com.infomaniak.mail.databinding.BottomSheetSendOptionsBinding
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleSendBottomSheetDialog.Companion.OPEN_SCHEDULE_DRAFT_DATE_AND_TIME_PICKER
import com.infomaniak.mail.ui.bottomSheetDialogs.ScheduleSendBottomSheetDialog.Companion.SCHEDULE_DRAFT_RESULT
import com.infomaniak.mail.utils.openKSuiteProBottomSheet
import com.infomaniak.mail.utils.openMailPremiumBottomSheet
import com.infomaniak.mail.utils.openMyKSuiteUpgradeBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SendOptionsBottomSheetDialog @Inject constructor() : SelectScheduleOptionBottomSheet() {

    private var binding: BottomSheetSendOptionsBinding by safeBinding()

    private val navigationArgs: SendOptionsBottomSheetDialogArgs by navArgs()

    override val currentKSuite: KSuite? by lazy { navigationArgs.currentKSuite }

    // Navigation args does not support nullable primitive types, so we use 0L
    // as a replacement (corresponding to Thursday 1 January 1970 00:00:00 UT).
    override val lastSelectedEpoch: Long? by lazy { navigationArgs.lastSelectedScheduleEpochMillis.takeIf { it != 0L } }
    override val currentlyScheduledEpochMillis: Long? by lazy { navigationArgs.currentlyScheduledEpochMillis.takeIf { it != 0L } }

    override val lastScheduleOption get() = binding.lastScheduleOption
    override val scheduleOptionsContainer get() = binding.scheduleOptions
    override val customScheduleOption get() = binding.customScheduleOption

    // Whether a "last selected schedule" option is relevant. Stored to restore it when the schedule section is revealed.
    private var hasLastScheduleOption = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetSendOptionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        setupScheduleOptions()
        hasLastScheduleOption = lastScheduleOption.isVisible

        setReminderOptionsVisible(isVisible = false)
        setScheduleOptionsVisible(isVisible = false)

        setupToggles()
    }

    private fun setupToggles() = with(binding) {
        reminderIfNoAnswer.setOnClickListener { setReminderOptionsVisible(isVisible = reminderIfNoAnswer.isChecked) }
        scheduleSending.setOnClickListener { setScheduleOptionsVisible(isVisible = scheduleSending.isChecked) }

        customDelayReminder.setOnClickListener {
            // TODO: Open the custom reminder delay picker once the reminder feature is implemented.
        }
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

    override fun onLastScheduleOptionClicked() {
        if (lastSelectedEpoch != null) {
            trackScheduleSendEvent(MatomoName.LastSelectedSchedule)
            setBackNavigationResult(SCHEDULE_DRAFT_RESULT, lastSelectedEpoch)
        }
    }

    override fun onScheduleOptionClicked(dateItem: ScheduleOption) {
        trackScheduleSendEvent(dateItem.matomoName)
        setBackNavigationResult(SCHEDULE_DRAFT_RESULT, dateItem.date().time)
    }

    override fun onCustomScheduleOptionClicked() {
        val kSuite = currentKSuite
        val matomoName = MatomoName.ScheduledCustomDate.value
        when (kSuite) {
            KSuite.Perso.Free -> openMyKSuiteUpgradeBottomSheet(matomoName)
            KSuite.Pro.Free -> openKSuiteProBottomSheet(kSuite, navigationArgs.isAdmin, matomoName)
            KSuite.StarterPack -> openMailPremiumBottomSheet(matomoName)
            else -> {
                trackScheduleSendEvent(MatomoName.CustomSchedule)
                setBackNavigationResult(OPEN_SCHEDULE_DRAFT_DATE_AND_TIME_PICKER, true)
            }
        }
    }
}


