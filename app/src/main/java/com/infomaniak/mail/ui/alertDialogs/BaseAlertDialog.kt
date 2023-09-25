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
import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.lib.core.utils.context
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.DialogDescriptionBinding
import dagger.hilt.android.qualifiers.ActivityContext

abstract class BaseAlertDialog(@ActivityContext private val activityContext: Context) {

    protected val activity = activityContext as Activity
    protected val binding by lazy { DialogDescriptionBinding.inflate(activity.layoutInflater) }


    var description: CharSequence = ""
    var title: String = ""
    @StringRes
    var confirmButtonText = R.string.buttonConfirm
    var onDismissed: (() -> Unit)? = null

    protected val alertDialog = initDialog()

    protected inline val positiveButton get() = (alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as MaterialButton)
    protected inline val negativeButton get() = (alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE) as MaterialButton)

    private fun initDialog() = with(binding) {
        MaterialAlertDialogBuilder(context)
            .setView(root)
            .setPositiveButton(confirmButtonText, null)
            .setOnDismissListener { onDismissed?.invoke() }
            .create()
    }
}
