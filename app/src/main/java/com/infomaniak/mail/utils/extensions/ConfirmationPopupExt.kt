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

fun DescriptionAlertDialog.deleteWithConfirmationPopup(
    messagesFolderRoles: List<FolderRole>,
    currentFolderRole: FolderRole?,
    count: Int,
    displayLoader: Boolean = true,
    onCancel: (() -> Unit)? = null,
    callback: () -> Unit,
): Boolean {
    var isDialogShown = true
    val isDraftFolder = currentFolderRole == FolderRole.DRAFT
    val isPermanentlyDeleteFolder = messagesFolderRoles.contains(FolderRole.SCHEDULED_DRAFTS) ||
            messagesFolderRoles.contains(FolderRole.SPAM) || messagesFolderRoles.contains(FolderRole.TRASH) ||
            (messagesFolderRoles.contains(FolderRole.DRAFT) && !isDraftFolder)
    when {
        messagesFolderRoles.contains(FolderRole.SNOOZED) -> showDeleteSnoozeDialog(count, displayLoader, callback, onCancel)
        isPermanentlyDeleteFolder -> showDeletePermanentlyDialog(
            count,
            displayLoader,
            callback,
            messagesFolderRoles.contains(FolderRole.SCHEDULED_DRAFTS),
            onCancel,
        )
        isDraftFolder -> callback()
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
    hasScheduleMessages: Boolean = false,
    onCancel: (() -> Unit)? = null,
) {
    val description = if (hasScheduleMessages) {
        binding.context.resources.getQuantityString(R.plurals.scheduleDeleteConfirmAlertDescription, deletedCount)
    } else {
        binding.context.resources.getQuantityString(R.plurals.threadListDeletionConfirmationAlertDescription, deletedCount)
    }

    show(
        title = binding.context.resources.getQuantityString(
            R.plurals.threadListDeletionConfirmationAlertTitle,
            deletedCount,
            deletedCount,
        ),
        description = description,
        displayLoader = displayLoader,
        onPositiveButtonClicked = {
            onPositiveButtonClicked()
            resetLoadingAndDismiss()
        },
        onCancel = onCancel,
    )
}

private fun DescriptionAlertDialog.showDeleteSnoozeDialog(
    deletedCount: Int,
    displayLoader: Boolean,
    onPositiveButtonClicked: () -> Unit,
    onCancel: (() -> Unit)? = null,
) = show(
    title = binding.context.getString(R.string.actionDelete),
    description = binding.context.resources.getQuantityString(R.plurals.snoozeDeleteConfirmAlertDescription, deletedCount),
    displayLoader = displayLoader,
    onPositiveButtonClicked = {
        onPositiveButtonClicked()
        resetLoadingAndDismiss()
    },
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
        onPositiveButtonClicked = {
            onPositiveButtonClicked()
            resetLoadingAndDismiss()
        },
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
            onPositiveButtonClicked = {
                onPositiveButtonClicked()
                resetLoadingAndDismiss()
            },
            onCancel = onCancel,
        )
    } else {
        onPositiveButtonClicked()
    }
    return isDialogShown
}

fun DescriptionAlertDialog.replyWithConfirmationPopup(
    hasNoReplyRecipients: Boolean,
    displayLoader: Boolean = false,
    onCancel: (() -> Unit)? = null,
    onPositiveButtonClicked: () -> Unit,
) {
    if (hasNoReplyRecipients) {
        show(
            title = binding.context.getString(R.string.actionReply),
            description = binding.context.resources.getString(R.string.alertSenderNoReply),
            displayLoader = displayLoader,
            onPositiveButtonClicked = { onPositiveButtonClicked() },
            onCancel = onCancel,
        )
    } else {
        onPositiveButtonClicked()
    }
}
