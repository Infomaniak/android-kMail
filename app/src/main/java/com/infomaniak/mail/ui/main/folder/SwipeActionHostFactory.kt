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
package com.infomaniak.mail.ui.main.folder

import androidx.fragment.app.Fragment
import androidx.navigation.NavDirections
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.main.thread.ThreadViewModel.SnoozeScheduleType
import com.infomaniak.mail.ui.main.thread.actions.ActionsViewModel
import io.realm.kotlin.types.RealmInstant

object SwipeActionHostFactory {
    fun create(
        fragment: Fragment,
        mainViewModel: MainViewModel,
        actionsViewModel: ActionsViewModel,
        localSettings: LocalSettings,
        threadListAdapter: ThreadListAdapter,
        descriptionDialog: DescriptionAlertDialog,
        showSwipeActionIncompatible: () -> Unit,
        directionsToMove: (threadUid: String, sourceFolderId: String) -> NavDirections,
        directionsToQuickActions: (threadUid: String) -> NavDirections,
        navigateToSnoozeBottomSheet: (SnoozeScheduleType?, RealmInstant?) -> Unit,
    ): SwipeActionHost {
        return object : SwipeActionHost {
            override val fragment = fragment
            override val mainViewModel = mainViewModel
            override val actionsViewModel = actionsViewModel
            override val localSettings = localSettings
            override val threadListAdapter = threadListAdapter
            override val descriptionDialog = descriptionDialog

            override fun showSwipeActionIncompatible() = showSwipeActionIncompatible()

            override fun directionsToMove(threadUid: String, sourceFolderId: String): NavDirections {
                return directionsToMove(threadUid, sourceFolderId)
            }

            override fun directionsToQuickActions(threadUid: String): NavDirections {
                return directionsToQuickActions(threadUid)
            }

            override fun navigateToSnoozeBottomSheet(
                snoozeScheduleType: SnoozeScheduleType?,
                snoozeEndDate: RealmInstant?,
            ) {
                navigateToSnoozeBottomSheet(snoozeScheduleType, snoozeEndDate)
            }
        }
    }
}
