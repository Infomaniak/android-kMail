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
import androidx.annotation.StringRes
import com.infomaniak.mail.MatomoMail.trackRenameFolderEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.extensions.getFolderCreationError
import dagger.hilt.android.qualifiers.ActivityContext
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class ModifyNameFolderDialog @Inject constructor(
    @ActivityContext private val activityContext: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val folderController: FolderController,
) : InputAlertDialog(activityContext, ioDispatcher) {

    private var folderId: String? = null

    fun show(renameFolderLastName: String, folderId: String, @StringRes confirmButtonText: Int = R.string.buttonValid) {
        show(
            title = R.string.renameFolder,
            hint = R.string.newFolderDialogHint,
            confirmButtonText = confirmButtonText,
            renameFolderLastName = renameFolderLastName,
        )
        this.folderId = folderId
    }

    fun setCallbacks(onPositiveButtonClicked: (String, String) -> Unit) = setCallbacks(
        onPositiveButtonClicked = { folderName ->
            activityContext.trackRenameFolderEvent("rename")
            onPositiveButtonClicked(folderName, folderId!!)
        },
        onErrorCheck = { folderName ->
            activityContext.getFolderCreationError(folderName, folderController)
        },
    )
}
