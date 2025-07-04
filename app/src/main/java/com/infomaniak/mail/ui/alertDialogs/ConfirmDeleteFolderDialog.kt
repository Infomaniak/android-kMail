/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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

import android.R.attr.description
import android.R.attr.negativeButtonText
import android.R.attr.positiveButtonText
import android.content.Context
import com.infomaniak.mail.MatomoMail.trackCreateFolderEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.extensions.getStringWithBoldArg
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class ConfirmDeleteFolderDialog @Inject constructor(
    @ActivityContext private val activityContext: Context,
) : DescriptionAlertDialog(activityContext) {

    private var onPositiveButtonClick: ((String) -> Unit)? = null

    fun show(folderId: String, folderName: String) = with(binding) {
        show(
            title = activityContext.getString(R.string.deleteFolderDialogTitle),
            description = activityContext.getStringWithBoldArg(R.string.deleteFolderDialogDescription, folderName),
            positiveButtonText = R.string.buttonYes,
            negativeButtonText = R.string.buttonNo,
            onPositiveButtonClicked = { onPositiveButtonClick?.invoke(folderId) }
        )
    }

    fun setPositiveButtonCallback(onPositiveButtonClick: (String) -> Unit) {
        activityContext.trackCreateFolderEvent("deleteConfirm")
        this.onPositiveButtonClick = onPositiveButtonClick
    }
}
