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
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore.Files.FileColumns
import androidx.core.content.FileProvider
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.goToPlayStore
import com.infomaniak.lib.core.utils.hasSupportedApplications
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.thread.actions.DownloadAttachmentProgressDialogArgs
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.WorkerUtils.UploadMissingLocalFileException
import com.infomaniak.mail.utils.extensions.AttachmentExtensions.AttachmentIntentType.OPEN_WITH
import com.infomaniak.mail.utils.extensions.AttachmentExtensions.AttachmentIntentType.SAVE_TO_DRIVE
import io.realm.kotlin.Realm
import io.sentry.Sentry
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import com.infomaniak.lib.core.R as RCore

object AttachmentExtensions {

    // TODO: Delete logs with this tag when Attachments' `uuid` problem will be resolved
    const val ATTACHMENT_TAG = "attachmentUpload"
    const val DOWNLOAD_ATTACHMENT_RESULT = "download_attachment_result"

    private const val DRIVE_PACKAGE = "com.infomaniak.drive"
    private const val SAVE_EXTERNAL_ACTIVITY_CLASS = "com.infomaniak.drive.ui.SaveExternalFilesActivity"

    //region Intent
    private fun canSaveOnKDrive(context: Context) = runCatching {
        val packageInfo = context.packageManager.getPackageInfo(DRIVE_PACKAGE, PackageManager.GET_ACTIVITIES)
        packageInfo.activities!!.any { it.name == SAVE_EXTERNAL_ACTIVITY_CLASS }
    }.getOrElse {
        Sentry.captureException(it)
        false
    }

    private fun Attachment.saveToDriveIntent(context: Context): Intent {
        val fileFromCache = getCacheFile(context)
        val lastModifiedDate = fileFromCache.lastModified()
        val uri = FileProvider.getUriForFile(context, context.getString(R.string.ATTACHMENTS_AUTHORITY), fileFromCache)

        return Intent().apply {
            component = ComponentName(DRIVE_PACKAGE, SAVE_EXTERNAL_ACTIVITY_CLASS)
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(FileColumns.DATE_MODIFIED, lastModifiedDate)
            setDataAndType(uri, safeMimeType)
        }
    }

    private fun Uri.saveToDriveIntent(): Intent {
        return Intent().apply {
            component = ComponentName(DRIVE_PACKAGE, SAVE_EXTERNAL_ACTIVITY_CLASS)
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, this@saveToDriveIntent)
        }
    }

    private fun Attachment.openWithIntent(context: Context): Intent {
        val file = getUploadLocalFile() ?: getCacheFile(context)
        val uri = FileProvider.getUriForFile(context, context.getString(R.string.ATTACHMENTS_AUTHORITY), file)

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

    // TODO Keep same logic
    fun Uri.getIntentOrGoToPlayStore(context: Context) {
        if (canSaveOnKDrive(context)) {
            saveToDriveIntent().let(context::startActivity)
        } else {
            context.goToPlayStore(DRIVE_PACKAGE)
        }
    }

    fun Attachment.executeIntent(
        context: Context,
        intentType: AttachmentIntentType,
        navigateToDownloadProgressDialog: (Attachment, AttachmentIntentType) -> Unit,
    ) {
        if (hasUsableCache(context, getUploadLocalFile()) || isInlineCachedFile(context)) {
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
            snackbarManager.setValue(context.getString(RCore.string.errorNoSupportingAppFound))
        }
    }

    fun Attachment.createDownloadDialogNavArgs(intentType: AttachmentIntentType): Bundle {
        return DownloadAttachmentProgressDialogArgs(
            attachmentLocalUuid = localUuid,
            attachmentName = name,
            attachmentType = getFileTypeFromMimeType(),
            intentType = intentType,
        ).toBundle()
    }
    //endregion

    suspend fun Attachment.startUpload(
        draftLocalUuid: String,
        mailbox: Mailbox,
        draftController: DraftController,
        realm: Realm,
    ) {
        val attachmentFile = getUploadLocalFile().also {
            if (it?.exists() != true) {
                SentryLog.d(ATTACHMENT_TAG, "No local file for attachment $name")
                throw UploadMissingLocalFileException()
            }
        }

        val userApiToken = AccountUtils.getUserById(mailbox.userId)?.apiToken?.accessToken ?: return
        val headers = HttpUtils.getHeaders(contentType = null).newBuilder()
            .set("Authorization", "Bearer $userApiToken")
            .addUnsafeNonAscii("x-ws-attachment-filename", name)
            .add("x-ws-attachment-mime-type", mimeType)
            .add("x-ws-attachment-disposition", "attachment")
            .build()
        val request = Request.Builder().url(ApiRoutes.createAttachment(mailbox.uuid))
            .headers(headers)
            .post(attachmentFile!!.asRequestBody(mimeType.toMediaType()))
            .build()

        val response = AccountUtils.getHttpClient(mailbox.userId).newCall(request).execute()

        val apiResponse = ApiController.json.decodeFromString<ApiResponse<Attachment>>(response.body?.string() ?: "")
        if (apiResponse.isSuccess() && apiResponse.data != null) {
            updateLocalAttachment(draftLocalUuid, apiResponse.data!!, draftController, realm)
        } else {
            val baseMessage = "Upload failed for attachment $localUuid"
            val errorMessage = "error : ${apiResponse.translatedError}"
            SentryLog.i(
                tag = ATTACHMENT_TAG,
                msg = "$baseMessage - $errorMessage - data : ${apiResponse.data}",
                throwable = apiResponse.getApiException(),
            )

            apiResponse.throwErrorAsException()
        }
    }

    private suspend fun Attachment.updateLocalAttachment(
        draftLocalUuid: String,
        remoteAttachment: Attachment,
        draftController: DraftController,
        realm: Realm,
    ) {
        realm.write {
            draftController.updateDraft(draftLocalUuid, realm = this) { draft ->

                val uuidToLocalUri = draft.attachments.map { it.uuid to it.uploadLocalUri }
                SentryLog.d(ATTACHMENT_TAG, "When removing uploaded attachment, we found (uuids to localUris): $uuidToLocalUri")
                SentryLog.d(ATTACHMENT_TAG, "Target uploadLocalUri is: $uploadLocalUri")

                remoteAttachment.backupLocalData(oldAttachment = this@updateLocalAttachment, draft)

                SentryLog.d(ATTACHMENT_TAG, "Uploaded attachment uuid: ${remoteAttachment.uuid}")
                SentryLog.d(ATTACHMENT_TAG, "Uploaded attachment localUuid: ${remoteAttachment.localUuid}")
                SentryLog.d(ATTACHMENT_TAG, "Uploaded attachment uploadLocalUri: ${remoteAttachment.uploadLocalUri}")

                draft.attachments.apply {
                    delete(findSpecificAttachment(attachment = this@updateLocalAttachment)!!)
                    add(remoteAttachment)
                }

                SentryDebug.addDraftBreadcrumbs(draft, step = "update local Attachment after success upload")
            }
        }
    }

    fun List<Attachment>.findSpecificAttachment(attachment: Attachment) = singleOrNull { it.localUuid == attachment.localUuid }

    enum class AttachmentIntentType {
        OPEN_WITH,
        SAVE_TO_DRIVE,
    }
}
