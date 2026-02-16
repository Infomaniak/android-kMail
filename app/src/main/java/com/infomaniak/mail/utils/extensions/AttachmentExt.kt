/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2026 Infomaniak Network SA
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
import android.os.Bundle
import android.provider.MediaStore.Files.FileColumns
import androidx.core.content.FileProvider
import com.infomaniak.core.common.extensions.goToAppStore
import com.infomaniak.core.legacy.utils.hasSupportedApplications
import com.infomaniak.core.network.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.thread.actions.DownloadAttachmentProgressDialogArgs
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.SaveOnKDriveUtils.DRIVE_PACKAGE
import com.infomaniak.mail.utils.SaveOnKDriveUtils.SAVE_EXTERNAL_ACTIVITY_CLASS
import com.infomaniak.mail.utils.SaveOnKDriveUtils.canSaveOnKDrive
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.WorkerUtils.UploadMissingLocalFileException
import com.infomaniak.mail.utils.extensions.AttachmentExt.AttachmentIntentType.OPEN_WITH
import com.infomaniak.mail.utils.extensions.AttachmentExt.AttachmentIntentType.SAVE_TO_DRIVE
import io.realm.kotlin.Realm
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.infomaniak.core.legacy.R as RCore

object AttachmentExt {

    // TODO: Delete logs with this tag when Attachments' `uuid` problem will be resolved
    const val ATTACHMENT_TAG = "attachmentUpload"
    const val DOWNLOAD_ATTACHMENT_RESULT = "download_attachment_result"

    //region Intent
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

    private fun Attachment.openWithIntent(context: Context): Intent {
        val file = getUploadLocalFile() ?: getCacheFile(context)
        val uri = FileProvider.getUriForFile(context, context.getString(R.string.ATTACHMENTS_AUTHORITY), file)

        return Intent().apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            setDataAndType(uri, safeMimeType)
        }
    }

    fun Attachment.getIntentOrGoToAppStore(context: Context, intentType: AttachmentIntentType) = when (intentType) {
        OPEN_WITH -> openWithIntent(context)
        SAVE_TO_DRIVE -> if (canSaveOnKDrive(context)) {
            saveToDriveIntent(context)
        } else {
            context.goToAppStore(DRIVE_PACKAGE)
            null
        }
    }

    fun Attachment.executeIntent(
        context: Context,
        intentType: AttachmentIntentType,
        navigateToDownloadProgressDialog: (Attachment, AttachmentIntentType) -> Unit,
    ) {
        if (hasUsableCache(context, getUploadLocalFile()) || isInlineCachedFile(context)) {
            getIntentOrGoToAppStore(context, intentType)?.let(context::startActivity)
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

    suspend fun Attachment.startUpload(draftLocalUuid: String, mailbox: Mailbox, realm: Realm) {
        val attachmentFile = getUploadLocalFile().let { file ->
            if (file?.exists() != true) {
                SentryLog.d(ATTACHMENT_TAG, "No local file for attachment $name")
                throw UploadMissingLocalFileException()
            }
            file
        }

        val userApiToken = AccountUtils.getUserById(mailbox.userId)?.apiToken?.accessToken ?: return
        val apiResponse = ApiRepository.createAttachments(attachmentFile, attachment = this, mailbox, userApiToken)

        if (apiResponse?.isSuccess() == true && apiResponse.data != null) {
            withContext(NonCancellable) {
                updateLocalAttachment(draftLocalUuid, apiResponse.data!!, realm)
            }
        } else {
            val baseMessage = "Upload failed for attachment $localUuid"
            val errorMessage = "error : ${apiResponse?.translateError()}"
            SentryLog.i(
                tag = ATTACHMENT_TAG,
                msg = "$baseMessage - $errorMessage - data : ${apiResponse?.data}",
                throwable = apiResponse?.getApiException(),
            )

            apiResponse?.throwErrorAsException()
        }
    }

    private suspend fun Attachment.updateLocalAttachment(draftLocalUuid: String, remoteAttachment: Attachment, realm: Realm) {
        realm.write {
            DraftController.updateDraftBlocking(draftLocalUuid, realm = this) { draft ->

                val uuidToLocalUri = draft.attachments.map { it.uuid to it.uploadLocalUri }
                SentryLog.d(ATTACHMENT_TAG, "When removing uploaded attachment, we found (uuids to localUris): $uuidToLocalUri")
                SentryLog.d(ATTACHMENT_TAG, "Target uploadLocalUri is: $uploadLocalUri")

                remoteAttachment.backupLocalData(oldAttachment = this@updateLocalAttachment, draft)

                SentryLog.d(ATTACHMENT_TAG, "Uploaded attachment uuid: ${remoteAttachment.uuid}")
                SentryLog.d(ATTACHMENT_TAG, "Uploaded attachment localUuid: ${remoteAttachment.localUuid}")
                SentryLog.d(ATTACHMENT_TAG, "Uploaded attachment uploadLocalUri: ${remoteAttachment.uploadLocalUri}")

                draft.attachments.apply {
                    findSpecificAttachment(attachment = this@updateLocalAttachment)?.let(::delete)
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
