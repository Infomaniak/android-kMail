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
package com.infomaniak.mail.ui.bottomSheetDialogs

import androidx.navigation.fragment.navArgs
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackScheduleSendEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.openKSuiteProBottomSheet
import com.infomaniak.mail.utils.openMailPremiumBottomSheet
import com.infomaniak.mail.utils.openMyKSuiteUpgradeBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ScheduleSendBottomSheetDialog @Inject constructor() : SelectScheduleOptionBottomSheet() {

    private val navigationArgs: ScheduleSendBottomSheetDialogArgs by navArgs()

    override val currentKSuite: KSuite? by lazy { navigationArgs.currentKSuite }

    // Navigation args does not support nullable primitive types, so we use 0L
    // as a replacement (corresponding to Thursday 1 January 1970 00:00:00 UT).
    override val lastSelectedEpoch: Long? by lazy { navigationArgs.lastSelectedScheduleEpochMillis.takeIf { it != 0L } }
    override val currentlyScheduledEpochMillis: Long? by lazy { navigationArgs.currentlyScheduledEpochMillis.takeIf { it != 0L } }

    override val titleRes: Int = R.string.scheduleSendingTitle

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
            KSuite.StarterPack -> openMailPremiumBottomSheet(matomoName, substituteClassName = javaClass.name)
            else -> {
                trackScheduleSendEvent(MatomoName.CustomSchedule)
                setBackNavigationResult(OPEN_SCHEDULE_DRAFT_DATE_AND_TIME_PICKER, true)
            }
        }
    }

    companion object {
        const val SCHEDULE_DRAFT_RESULT = "schedule_draft_result"
        const val OPEN_SCHEDULE_DRAFT_DATE_AND_TIME_PICKER = "open_schedule_draft_date_and_time_picker"
    }
}
