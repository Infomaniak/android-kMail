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
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.main.search.SearchViewModel
import com.infomaniak.mail.ui.main.thread.ThreadViewModel.SnoozeScheduleType
import com.infomaniak.mail.ui.main.thread.actions.ActionsViewModel
import io.realm.kotlin.types.RealmInstant

interface SwipeActionHost {
    val fragment: Fragment
    val mainViewModel: MainViewModel
    val actionsViewModel: ActionsViewModel
    val searchViewModel: SearchViewModel
    val localSettings: LocalSettings
    val threadListAdapter: ThreadListAdapter
    val descriptionDialog: DescriptionAlertDialog

    fun showSwipeActionIncompatible()
    fun navigateToSnoozeBottomSheet(snoozeScheduleType: SnoozeScheduleType?, snoozeEndDate: RealmInstant?)
}
