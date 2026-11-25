/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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

import android.content.Context
import android.net.Uri
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.extensions.getCacheFile
import com.infomaniak.mail.data.models.extensions.isUsableAttachmentFile
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.ui.main.SnackbarManager
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import com.infomaniak.core.legacy.R as RCore

object LocalStorageUtils {

    private const val ATTACHMENTS_CACHE_DIR = "attachments_cache"
    private const val ATTACHMENTS_UPLOAD_DIR = "attachments_upload"
    private const val EML_CACHE_DIR = "eml_export"
    private const val HIDDEN_FILE_NAME = "HIDDEN_FILE_NAME"
    private const val NAME_TOO_LONG_EXCEPTION = "ENAMETOOLONG"

    private inline val Context.attachmentsCacheRootDir get() = File(cacheDir, ATTACHMENTS_CACHE_DIR)
    private inline val Context.attachmentsUploadRootDir get() = File(filesDir, ATTACHMENTS_UPLOAD_DIR)

    //region Cache
    fun getEmlCacheDir(context: Context): File = File(context.cacheDir, EML_CACHE_DIR)

    fun clearEmlCacheDir(context: Context) {
        getEmlCacheDir(context).deleteRecursively()
    }

    suspend fun getAttachmentsCacheDir(
        context: Context,
        attachmentPath: String,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
    ): File? {
        val cacheRoot = generateRootDir(context.attachmentsCacheRootDir, userId, mailboxId)
        return cacheRoot.resolveContainedPath(attachmentPath)
    }

    suspend fun downloadThenSaveAttachmentToCacheDir(
        context: Context,
        attachment: Attachment,
        mailbox: Mailbox? = null,
    ): Boolean {
        val cacheFile = attachment.getCacheFile(context, mailbox) ?: return false
        val resource = attachment.resource ?: return false

        return ApiRepository.downloadAttachment(resource, mailbox?.userId).use { response ->
            response.isSuccessful && saveAttachmentToCacheDir(response.body.byteStream(), cacheFile, attachment.size)
        }
    }

    suspend fun saveAttachmentToCacheDir(inputStream: InputStream, outputFile: File, expectedSize: Long): Boolean {
        return inputStream.use {
            val parent = checkNotNull(outputFile.parentFile)
            parent.mkdirs()
            val stagedFile = File.createTempFile("attachment-", ".part", parent)
            try {
                stagedFile.outputStream().use(inputStream::copyTo)
                if (!stagedFile.isUsableAttachmentFile(expectedSize)) return@use false

                currentCoroutineContext().ensureActive()
                Files.move(stagedFile.toPath(), outputFile.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
                true
            } finally {
                if (stagedFile.exists() && !stagedFile.delete()) {
                    SentryLog.e("LocalStorageUtils", "Could not delete staged attachment")
                }
            }
        }
    }

    private fun deleteAttachmentsCacheDir(context: Context) = context.attachmentsCacheRootDir.deleteRecursively()
    //endregion

    //region Upload
    private fun getAttachmentUploadDir(
        context: Context,
        draftLocalUuid: String,
        attachmentLocalUuid: String,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
    ): File {
        return getDraftUploadDir(context, "${draftLocalUuid}/${attachmentLocalUuid}", userId, mailboxId)
    }

    private fun getDraftUploadDir(
        context: Context,
        draftLocalUuid: String,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
    ): File {
        return File(generateRootDir(context.attachmentsUploadRootDir, userId, mailboxId), draftLocalUuid)
    }

    suspend fun saveAttachmentToUploadDir(
        context: Context,
        uri: Uri,
        fileName: String,
        draftLocalUuid: String,
        attachmentLocalUuid: String,
        snackbarManager: SnackbarManager,
    ): File? = withContext(Dispatchers.IO) {
        return@withContext context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val attachmentsUploadDir = getAttachmentUploadDir(context, draftLocalUuid, attachmentLocalUuid)
            attachmentsUploadDir.mkdirs()
            val hashedFileName = "${uri.toString().substringAfter("document/").hashCode()}_${fileName.toSafeFileName()}"

            return@use getFileToUpload(context, uri, snackbarManager, attachmentsUploadDir, hashedFileName, inputStream)
        } ?: run {
            Sentry.captureMessage("failed to access uri") { scope ->
                scope.setExtra("uri is absolute", uri.isAbsolute.toString())
                scope.setExtra("uri is relative", uri.isRelative.toString())
                scope.setExtra("uri is opaque", uri.isOpaque.toString())
            }
            null
        }
    }

    private suspend fun getFileToUpload(
        context: Context,
        uri: Uri,
        snackbarManager: SnackbarManager,
        attachmentsUploadDir: File,
        hashedFileName: String,
        inputStream: InputStream,
    ): File? {
        val file = attachmentsUploadDir.resolveContainedFileName(hashedFileName) ?: return null
        val isSuccess = runCatching {
            FileOutputStream(file).use(inputStream::copyTo)
            true
        }.getOrElse {

            val exception = it.message
                ?.let { message -> AttachmentMissingFileException(message.replace(file.path, HIDDEN_FILE_NAME)) }
                ?: it
            exception.stackTrace = it.stackTrace

            Sentry.captureException(exception) { scope ->
                scope.setExtra("uri", uri.toString().replace(file.path, HIDDEN_FILE_NAME))
            }

            val isNameLong = exception.message?.contains(NAME_TOO_LONG_EXCEPTION) == true
            val snackbarMessageId = if (isNameLong) R.string.errorFileNameTooLong else RCore.string.errorFileNotFound

            snackbarManager.postValue(context.getString(snackbarMessageId))

            false
        }

        return if (isSuccess) file else null
    }

    fun deleteAttachmentUploadDir(
        context: Context,
        draftLocalUuid: String,
        attachmentLocalUuid: String,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
    ) {

        val attachmentDir = getAttachmentUploadDir(context, draftLocalUuid, attachmentLocalUuid, userId, mailboxId).also {
            if (!it.exists()) return
        }

        // The File.delete() function only delete the file if it has no children, that's precisely what we want here
        attachmentDir.delete()

        deleteDraftUploadDir(context, draftLocalUuid, userId, mailboxId)
    }

    fun deleteDraftUploadDir(
        context: Context,
        draftLocalUuid: String,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
        mustForceDelete: Boolean = false,
    ) {
        val draftDir = getDraftUploadDir(context, draftLocalUuid, userId, mailboxId).also {
            if (!it.exists()) return
        }

        val mailboxDir = draftDir.parentFile ?: return
        val userDir = mailboxDir.parentFile ?: return
        val attachmentsRootDir = userDir.parentFile ?: return

        // The File.delete() function only delete the file if it has no children, that's precisely what we want here
        if (mustForceDelete) draftDir.deleteRecursively() else draftDir.delete()
        if (!mailboxDir.delete()) return
        if (!userDir.delete()) return
        attachmentsRootDir.delete()
    }
    //endregion

    //region Global
    fun deleteUserData(context: Context, userId: Int) {
        deleteAttachmentsCacheDir(context)
        with(context.attachmentsUploadRootDir) {
            File(this, "$userId").deleteRecursively()
            if (this.listFiles()?.isEmpty() == true) deleteRecursively()
        }
    }

    private fun generateRootDir(directory: File, userId: Int, mailboxId: Int): File {
        return File(directory, "$userId/$mailboxId")
    }
    //endregion

    private class AttachmentMissingFileException(message: String) : Exception(message)
}
