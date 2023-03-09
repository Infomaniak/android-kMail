/*
 * Infomaniak kMail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.mail.MatomoMail.toMailActionValue
import com.infomaniak.mail.MatomoMail.trackEvent

abstract class ActionsBottomSheetDialog : BottomSheetDialogFragment() {

    protected abstract val currentClassName: String

    protected fun ActionItemView.setClosingOnClickListener(callback: () -> Unit) {
        setOnClickListener {
            findNavController().popBackStack()
            callback()
        }
    }

    protected fun MainActionsView.setClosingOnClickListener(callback: (Int) -> Unit) {
        setOnItemClickListener { id ->
            findNavController().popBackStack()
            callback(id)
        }
    }

    protected fun Fragment.trackBottomSheetMessageActionsEvent(name: String, value: Boolean? = null) {
        trackEvent(category = "bottomSheetMessageActions", name = name, value = value?.toMailActionValue())
    }

    protected fun Fragment.trackBottomSheetThreadActionsEvent(name: String, value: Boolean? = null) {
        trackEvent(category = "bottomSheetThreadActions", name = name, value = value?.toMailActionValue())
    }
}
