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
package com.infomaniak.mail.ui.alertDialogs

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.viewbinding.ViewBinding
import com.google.android.material.button.MaterialButton
import com.infomaniak.lib.core.utils.Utils
import com.infomaniak.lib.core.utils.hideProgress
import com.infomaniak.lib.core.utils.showProgress
import com.infomaniak.mail.R
import dagger.hilt.android.qualifiers.ActivityContext

abstract class BaseAlertDialog(@ActivityContext private val activityContext: Context) {

    protected val activity = activityContext as Activity

    protected abstract val binding: ViewBinding
    protected abstract val alertDialog: AlertDialog

    protected abstract fun initDialog(): AlertDialog

    fun startLoading() {
        alertDialog.setCancelable(false)
        negativeButton.isEnabled = false
        Utils.createRefreshTimer(onTimerFinish = positiveButton::showProgress).start()
    }

    fun resetLoadingAndDismiss() = with(alertDialog) {
        if (isShowing) {
            dismiss()
            setCancelable(true)
            positiveButton.hideProgress(R.string.buttonCreate)
            negativeButton.isEnabled = true
        }
    }

    protected inline val positiveButton get() = (alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as MaterialButton)
    protected inline val negativeButton get() = (alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE) as MaterialButton)
}
