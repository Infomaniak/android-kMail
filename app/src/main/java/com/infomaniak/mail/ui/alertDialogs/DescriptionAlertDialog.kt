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

import android.content.Context
import androidx.annotation.StringRes
import com.infomaniak.lib.core.utils.hideProgress
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.AlertDialogUtils.startLoading
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class DescriptionAlertDialog @Inject constructor(
    @ActivityContext private val activityContext: Context,
) : BaseAlertDialog(activityContext) {

    fun show(
        title: String,
        description: CharSequence?,
        @StringRes confirmButtonText: Int? = null,
        displayCancelButton: Boolean = true,
        displayLoader: Boolean = true,
        onPositiveButtonClicked: () -> Unit,
        onDismissed: (() -> Unit)? = null,
    ) {
        showDialog(
            title,
            description,
            confirmButtonText,
            displayCancelButton,
            onPositiveButtonClicked,
            onDismissed,
        )

        if (displayLoader) {
            positiveButton.setOnClickListener {
                onPositiveButtonClicked()
                alertDialog.startLoading()
            }
        }
    }

    fun resetLoadingAndDismiss() = with(alertDialog) {
        if (isShowing) {
            dismiss()
            setCancelable(true)
            positiveButton.hideProgress(R.string.buttonCreate)
            negativeButton.isEnabled = true
        }
    }

    fun showDeletePermanentlyDialog(
        deletedCount: Int,
        displayLoader: Boolean,
        onPositiveButtonClicked: () -> Unit,
        onDismissed: (() -> Unit)? = null
    ) {
        show(
            title = activityContext.resources.getQuantityString(
                R.plurals.threadListDeletionConfirmationAlertTitle,
                deletedCount,
                deletedCount
            ),
            description = activityContext.resources.getQuantityString(
                R.plurals.threadListDeletionConfirmationAlertDescription,
                deletedCount
            ),
            displayLoader = displayLoader,
            onPositiveButtonClicked = onPositiveButtonClicked,
            onDismissed = onDismissed,
        )
    }
}
