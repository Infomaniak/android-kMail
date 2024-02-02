/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.FileProvider
import com.infomaniak.lib.core.utils.goToPlayStore
import com.infomaniak.lib.core.utils.hasSupportedApplications
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.thread.actions.DownloadAttachmentProgressDialogArgs
import com.infomaniak.mail.utils.AttachmentIntentUtils.AttachmentIntentType.OPEN_WITH
import com.infomaniak.mail.utils.AttachmentIntentUtils.AttachmentIntentType.SAVE_TO_DRIVE
import io.sentry.Sentry

object AttachmentIntentUtils {

    private const val DRIVE_PACKAGE = "com.infomaniak.drive"
    private const val SAVE_EXTERNAL_ACTIVITY_CLASS = "com.infomaniak.drive.ui.SaveExternalFilesActivity"

    const val DOWNLOAD_ATTACHMENT_RESULT = "download_attachment_result"

    private fun canSaveOnKDrive(context: Context) = runCatching {
        val packageInfo = context.packageManager.getPackageInfo(DRIVE_PACKAGE, PackageManager.GET_ACTIVITIES)
        packageInfo.activities.any { it.name == SAVE_EXTERNAL_ACTIVITY_CLASS }
    }.getOrElse {
        Sentry.captureException(it)
        false
    }

    private fun Attachment.saveToDriveIntent(context: Context): Intent {
        val uri = FileProvider.getUriForFile(context, context.getString(R.string.ATTACHMENTS_AUTHORITY), getCacheFile(context))
        return Intent().apply {
            component = ComponentName(DRIVE_PACKAGE, SAVE_EXTERNAL_ACTIVITY_CLASS)
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            setDataAndType(uri, safeMimeType)
        }
    }

    private fun Attachment.openWithIntent(context: Context): Intent {
        val uri = FileProvider.getUriForFile(context, context.getString(R.string.ATTACHMENTS_AUTHORITY), getCacheFile(context))
        return Intent().apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            setDataAndType(uri, safeMimeType)
        }
    }

    fun Attachment.getIntentOrGoToPlaystore(context: Context, intentType: AttachmentIntentType) = when (intentType) {
        OPEN_WITH -> openWithIntent(context)
        SAVE_TO_DRIVE -> if (canSaveOnKDrive(context)) {
            saveToDriveIntent(context)
        } else {
            context.goToPlayStore(DRIVE_PACKAGE)
            null
        }
    }

    fun Attachment.executeIntent(
        context: Context,
        intentType: AttachmentIntentType,
        navigateToDownloadProgressDialog: (Attachment, AttachmentIntentType) -> Unit,
    ) {
        if (hasUsableCache(context) || isInlineCachedFile(context)) {
            getIntentOrGoToPlaystore(context, intentType)?.let(context::startActivity)
        } else {
            navigateToDownloadProgressDialog(this, intentType)
        }
    }

    fun Attachment.openAttachment(
        context: Context,
        navigateToDownloadProgressDialog: (Attachment, AttachmentIntentType) -> Unit,
        snackbarManager: SnackbarManager,
    ) {
        if (openWithIntent(context).hasSupportedApplications(context)) {
            executeIntent(context, OPEN_WITH, navigateToDownloadProgressDialog)
        } else {
            snackbarManager.setValue(context.getString(com.infomaniak.lib.core.R.string.errorNoSupportingAppFound))
        }
    }

    fun Attachment.createDownloadDialogNavArgs(intentType: AttachmentIntentType): Bundle {
        return DownloadAttachmentProgressDialogArgs(
            attachmentResource = resource!!,
            attachmentName = name,
            attachmentType = getFileTypeFromMimeType(),
            intentType = intentType,
        ).toBundle()
    }

    enum class AttachmentIntentType {
        OPEN_WITH, SAVE_TO_DRIVE
    }
}
