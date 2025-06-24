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

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.lib.core.utils.context
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.DialogConfirmationToBlockUserBinding
import com.infomaniak.mail.databinding.DialogConfirmeDeleteFolderBinding
import com.infomaniak.mail.utils.extensions.getStringWithBoldArg
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class ConfirmDeleteFolderDialog @Inject constructor(
    @ActivityContext private val activityContext: Context,
) : BaseAlertDialog(activityContext) {

    // revoir ca
    private val binding: DialogConfirmeDeleteFolderBinding by lazy {
        DialogConfirmeDeleteFolderBinding.inflate(activity.layoutInflater)
    }

    private var onPositiveButtonClick: ((String) -> Unit)? = null

    private var folderId: String? = null

    override val alertDialog: AlertDialog = with(binding) {
        MaterialAlertDialogBuilder(context)
            .setView(root)
            .setPositiveButton(R.string.actionDelete) { _, _ ->
                folderId?.let { onPositiveButtonClick?.invoke(it) }
            }
            .setNegativeButton(com.infomaniak.lib.core.R.string.buttonCancel, null)
            .create()
    }

    override fun resetCallbacks() {
        onPositiveButtonClick = null
    }

    fun show(folderId: String, folderName: String) = with(binding) {
        this@ConfirmDeleteFolderDialog.folderId = folderId
        deleteFolderTitle.text = activityContext.getString(R.string.deleteFolderDialogTitle)
        deleteFolderDescription.text = activityContext.getStringWithBoldArg(R.string.deleteFolderDialogDescription, folderName)
        alertDialog.show()
    }

    fun setPositiveButtonCallback(onPositiveButtonClick: (String) -> Unit) {
        this.onPositiveButtonClick = onPositiveButtonClick
    }
}
