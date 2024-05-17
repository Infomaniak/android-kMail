/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread.actions

import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.mail.ui.MainViewModel

abstract class ActionsBottomSheetDialog : BottomSheetDialogFragment() {

    abstract val mainViewModel: MainViewModel?

    protected fun ActionItemView.setClosingOnClickListener(shouldCloseMultiSelection: Boolean = false, callback: () -> Unit) {
        setOnClickListener {
            if (shouldCloseMultiSelection) mainViewModel?.isMultiSelectOn = false
            findNavController().popBackStack()
            callback()
        }
    }

    protected fun MainActionsView.setClosingOnClickListener(shouldCloseMultiSelection: Boolean = false, callback: (Int) -> Unit) {
        setOnItemClickListener { id ->
            if (shouldCloseMultiSelection) mainViewModel?.isMultiSelectOn = false
            findNavController().popBackStack()
            callback(id)
        }
    }
}
