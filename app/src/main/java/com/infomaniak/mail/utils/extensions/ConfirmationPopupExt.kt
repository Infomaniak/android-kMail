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
package com.infomaniak.mail.utils.extensions

import com.infomaniak.core.legacy.utils.context
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.utils.Utils

fun DescriptionAlertDialog.deleteWithConfirmationPopup(
    folderRole: FolderRole?,
    count: Int,
    displayLoader: Boolean = true,
    onCancel: (() -> Unit)? = null,
    callback: () -> Unit,
): Boolean {
    var isDialogShown = true
    when {
        folderRole == FolderRole.SNOOZED -> showDeleteSnoozeDialog(count, displayLoader, callback, onCancel)
        folderRole == FolderRole.DRAFT -> callback()
        Utils.isPermanentDeleteFolder(folderRole) -> showDeletePermanentlyDialog(count, displayLoader, callback, onCancel)
        else -> {
            isDialogShown = false
            callback()
        }
    }
    return isDialogShown
}

private fun DescriptionAlertDialog.showDeletePermanentlyDialog(
    deletedCount: Int,
    displayLoader: Boolean,
    onPositiveButtonClicked: () -> Unit,
    onCancel: (() -> Unit)? = null,
) = show(
    title = binding.context.resources.getQuantityString(
        R.plurals.threadListDeletionConfirmationAlertTitle,
        deletedCount,
        deletedCount,
    ),
    description = binding.context.resources.getQuantityString(
        R.plurals.threadListDeletionConfirmationAlertDescription,
        deletedCount,
    ),
    displayLoader = displayLoader,
    onPositiveButtonClicked = onPositiveButtonClicked,
    onCancel = onCancel,
)

private fun DescriptionAlertDialog.showDeleteSnoozeDialog(
    deletedCount: Int,
    displayLoader: Boolean,
    onPositiveButtonClicked: () -> Unit,
    onCancel: (() -> Unit)? = null,
) = show(
    title = binding.context.getString(R.string.actionDelete),
    description = binding.context.resources.getQuantityString(R.plurals.snoozeDeleteConfirmAlertDescription, deletedCount),
    displayLoader = displayLoader,
    onPositiveButtonClicked = onPositiveButtonClicked,
    onCancel = onCancel,
)

/**
 * IMPORTANT: If there is a navigation in the [onPositiveButtonClicked] lambda, it NEEDS to use the provided navController,
 * otherwise it crashes because it's not attached to a fragment
 */
fun DescriptionAlertDialog.moveWithConfirmationPopup(
    folderRole: FolderRole?,
    count: Int,
    onPositiveButtonClicked: () -> Unit,
) = if (folderRole == FolderRole.SNOOZED) {
    show(
        title = binding.context.getString(R.string.actionMove),
        description = binding.context.resources.getQuantityString(R.plurals.snoozeMoveConfirmAlertDescription, count),
        displayLoader = false,
        onPositiveButtonClicked = { onPositiveButtonClicked() },
    )
} else {
    onPositiveButtonClicked()
}

fun DescriptionAlertDialog.archiveWithConfirmationPopup(
    folderRole: FolderRole?,
    count: Int,
    displayLoader: Boolean = true,
    onCancel: (() -> Unit)? = null,
    onPositiveButtonClicked: () -> Unit,
): Boolean {
    val isDialogShown = folderRole == FolderRole.SNOOZED
    if (isDialogShown) {
        show(
            title = binding.context.getString(R.string.actionArchive),
            description = binding.context.resources.getQuantityString(R.plurals.snoozeArchiveConfirmAlertDescription, count),
            displayLoader = displayLoader,
            onPositiveButtonClicked = onPositiveButtonClicked,
            onCancel = onCancel,
        )
    } else {
        onPositiveButtonClicked()
    }
    return isDialogShown
}
