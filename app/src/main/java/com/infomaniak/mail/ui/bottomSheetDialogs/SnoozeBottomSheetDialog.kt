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
import com.infomaniak.lib.core.utils.setBackNavigationResult
import com.infomaniak.mail.MatomoMail.CUSTOM_SCHEDULE
import com.infomaniak.mail.MatomoMail.LAST_SELECTED_SCHEDULE
import com.infomaniak.mail.MatomoMail.SCHEDULED_CUSTOM_DATE
import com.infomaniak.mail.MatomoMail.trackSnoozeEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.openMyKSuiteUpgradeBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SnoozeBottomSheetDialog @Inject constructor() : SelectScheduleOptionBottomSheet() {

    private val navigationArgs: SnoozeBottomSheetDialogArgs by navArgs()

    override val isCurrentMailboxFree: Boolean by lazy { navigationArgs.isCurrentMailboxFree }

    // Navigation args does not support nullable primitive types, so we use 0L
    // as a replacement (corresponding to Thursday 1 January 1970 00:00:00 UT).
    override val lastSelectedEpoch: Long? by lazy { navigationArgs.lastSelectedScheduleEpochMillis.takeIf { it != 0L } }
    override val currentlyScheduledEpochMillis: Long? by lazy { navigationArgs.currentlyScheduledEpochMillis.takeIf { it != 0L } }

    override val titleRes: Int = R.string.actionSnooze

    override fun onLastScheduleOptionClicked() {
        if (lastSelectedEpoch != null) {
            trackSnoozeEvent(LAST_SELECTED_SCHEDULE)
            setBackNavigationResult(SNOOZE_RESULT, lastSelectedEpoch)
        }
    }

    override fun onScheduleOptionClicked(dateItem: ScheduleOption) {
        trackSnoozeEvent(dateItem.matomoValue)
        setBackNavigationResult(SNOOZE_RESULT, dateItem.date().time)
    }

    override fun onCustomScheduleOptionClicked() {
        if (navigationArgs.isCurrentMailboxFree) {
            trackSnoozeEvent(CUSTOM_SCHEDULE)
            openMyKSuiteUpgradeBottomSheet(SCHEDULED_CUSTOM_DATE)
        } else {
            setBackNavigationResult(OPEN_SNOOZE_DATE_AND_TIME_PICKER, true)
        }
    }

    companion object {
        const val SNOOZE_RESULT = "snooze_result"
        const val OPEN_SNOOZE_DATE_AND_TIME_PICKER = "open_snooze_date_and_time_picker"
    }
}
