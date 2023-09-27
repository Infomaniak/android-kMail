/*
 * Infomaniak Mail - Android
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
package com.infomaniak.mail.utils

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.infomaniak.lib.core.utils.Utils
import com.infomaniak.lib.core.utils.hideProgress
import com.infomaniak.lib.core.utils.showProgress
import com.infomaniak.mail.R

object AlertDialogUtils {

    fun AlertDialog.startLoading() {
        setCancelable(false)
        negativeButton.isEnabled = false
        Utils.createRefreshTimer(onTimerFinish = positiveButton::showProgress).start()
    }

    fun AlertDialog.resetLoadingAndDismiss() {
        if (isShowing) {
            dismiss()
            setCancelable(true)
            positiveButton.hideProgress(R.string.buttonCreate)
            negativeButton.isEnabled = true
        }
    }

    inline val AlertDialog.positiveButton get() = (getButton(DialogInterface.BUTTON_POSITIVE) as MaterialButton)
    inline val AlertDialog.negativeButton get() = (getButton(DialogInterface.BUTTON_NEGATIVE) as MaterialButton)
}
