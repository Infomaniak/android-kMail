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
package com.infomaniak.mail.utils.extensions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.infomaniak.lib.core.utils.goToPlayStore
import com.infomaniak.lib.core.utils.hasSupportedApplications
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.thread.actions.DownloadAttachmentProgressDialogArgs
import com.infomaniak.mail.utils.extensions.AttachmentExtensions.AttachmentIntentType.OPEN_WITH
import com.infomaniak.mail.utils.extensions.AttachmentExtensions.AttachmentIntentType.SAVE_TO_DRIVE
import io.sentry.Sentry
import java.util.Date

object AttachmentExtensions {

    private const val DRIVE_PACKAGE = "com.infomaniak.drive"
    private const val SAVE_EXTERNAL_ACTIVITY_CLASS = "com.infomaniak.drive.ui.SaveExternalFilesActivity"

    const val DOWNLOAD_ATTACHMENT_RESULT = "download_attachment_result"

    //region Intent
    private fun canSaveOnKDrive(context: Context) = runCatching {
        val packageInfo = context.packageManager.getPackageInfo(DRIVE_PACKAGE, PackageManager.GET_ACTIVITIES)
        packageInfo.activities.any { it.name == SAVE_EXTERNAL_ACTIVITY_CLASS }
    }.getOrElse {
        Sentry.captureException(it)
        false
    }

    private fun Attachment.saveToDriveIntent(context: Context): Intent {
        val cacheFile = getCacheFile(context)

        val uri = FileProvider.getUriForFile(context, context.getString(R.string.ATTACHMENTS_AUTHORITY), cacheFile)
        // context.grantUriPermission()
        val (fileCreatedAt, fileModifiedAt) = getFileDates(context, uri)
        Log.e("gibran", "saveToDriveIntent - fileCreatedAt: ${fileCreatedAt}")
        Log.e("gibran", "saveToDriveIntent - fileModifiedAt: ${fileModifiedAt}")

        return Intent().apply {
            component = ComponentName(DRIVE_PACKAGE, SAVE_EXTERNAL_ACTIVITY_CLASS)
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            setDataAndType(uri, safeMimeType)
            setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun getFileDates(context: Context, uri: Uri): Pair<Date?, Date?> {
        fun Cursor.isValidDate(index: Int) = index != -1 && this.getLong(index) > 0

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                // val (fileCreatedAt, fileModifiedAt) = SyncUtils.getFileDates(cursor)
                val dateTakenIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val dateAddedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)

                val lastModifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val dateModifiedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)

                val fileCreatedAt = when {
                    cursor.isValidDate(dateTakenIndex) -> Date(cursor.getLong(dateTakenIndex))
                    cursor.isValidDate(dateAddedIndex) -> Date(cursor.getLong(dateAddedIndex) * 1000)
                    else -> null
                }

                var fileModifiedAt = when {
                    cursor.isValidDate(lastModifiedIndex) -> Date(cursor.getLong(lastModifiedIndex))
                    cursor.isValidDate(dateModifiedIndex) -> Date(cursor.getLong(dateModifiedIndex) * 1000)
                    fileCreatedAt != null -> fileCreatedAt
                    else -> null
                }

                Log.e("gibran", "getFileDates - cursor.columnNames.joinToString(): ${cursor.columnNames.joinToString()}")

                return fileCreatedAt to fileModifiedAt
            }
        }
        return null to null
    }

    private fun Attachment.openWithIntent(context: Context): Intent {
        val uri = FileProvider.getUriForFile(context, context.getString(R.string.ATTACHMENTS_AUTHORITY), getCacheFile(context))
        return Intent().apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            setDataAndType(uri, safeMimeType)
        }
    }

    fun Attachment.getIntentOrGoToPlayStore(context: Context, intentType: AttachmentIntentType) = when (intentType) {
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
            getIntentOrGoToPlayStore(context, intentType)?.let(context::startActivity)
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
    //endregion

    enum class AttachmentIntentType {
        OPEN_WITH,
        SAVE_TO_DRIVE,
    }
}
