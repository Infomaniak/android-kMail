/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.ui.main.SnackbarManager
import io.sentry.Sentry
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import com.infomaniak.lib.core.R as RCore

object LocalStorageUtils {

    private const val ATTACHMENTS_CACHE_DIR = "attachments_cache"
    private const val ATTACHMENTS_UPLOAD_DIR = "attachments_upload"
    private const val HIDDEN_FILE_NAME = "HIDDEN_FILE_NAME"
    private const val NAME_TOO_LONG_EXCEPTION = "ENAMETOOLONG"

    private inline val Context.attachmentsCacheRootDir get() = File(cacheDir, ATTACHMENTS_CACHE_DIR)
    private inline val Context.attachmentsUploadRootDir get() = File(filesDir, ATTACHMENTS_UPLOAD_DIR)

    //region Cache
    fun getAttachmentsCacheDir(
        context: Context,
        attachmentPath: String,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
    ): File {
        return File(generateRootDir(context.attachmentsCacheRootDir, userId, mailboxId), attachmentPath)
    }

    fun downloadThenSaveAttachmentToCacheDir(context: Context, localAttachment: Attachment): Boolean {

        fun Response.saveAttachmentTo(outputFile: File): Boolean {
            if (!isSuccessful) return false

            body?.byteStream()?.use { inputStream ->
                saveAttachmentToCacheDir(inputStream, outputFile)
                return true
            }

            return false
        }

        val attachment = runCatching { localAttachment.resource?.let(ApiRepository::downloadAttachment) }.getOrNull()
        return attachment?.saveAttachmentTo(localAttachment.getCacheFile(context)) == true
    }

    /**
     * Save the Attachment in disk memory.
     * The file remains unreadable as long as it's being processed.
     */
    fun saveAttachmentToCacheDir(inputStream: InputStream, outputFile: File) = with(outputFile) {
        if (exists()) delete()
        inputStream.buffered().use {
            parentFile?.mkdirs()
            if (createNewFile()) {
                setReadable(false)
                outputStream().use(inputStream::copyTo)
                setReadable(true)
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

    fun saveAttachmentToUploadDir(
        context: Context,
        uri: Uri,
        fileName: String,
        draftLocalUuid: String,
        attachmentLocalUuid: String,
        snackbarManager: SnackbarManager,
    ): File? {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val attachmentsUploadDir = getAttachmentUploadDir(context, draftLocalUuid, attachmentLocalUuid)
            attachmentsUploadDir.mkdirs()
            val hashedFileName = "${uri.toString().substringAfter("document/").hashCode()}_$fileName"

            return@use getFileToUpload(context, uri, snackbarManager, attachmentsUploadDir, hashedFileName, inputStream)
        } ?: run {
            Sentry.withScope { scope ->
                scope.setExtra("uri is absolute", uri.isAbsolute.toString())
                scope.setExtra("uri is relative", uri.isRelative.toString())
                scope.setExtra("uri is opaque", uri.isOpaque.toString())
                Sentry.captureMessage("failed to access uri")
            }
            null
        }
    }

    private fun getFileToUpload(
        context: Context,
        uri: Uri,
        snackbarManager: SnackbarManager,
        attachmentsUploadDir: File,
        hashedFileName: String,
        inputStream: InputStream,
    ): File? {
        val file = File(attachmentsUploadDir, hashedFileName)
        val isSuccess = runCatching {
            FileOutputStream(file).use(inputStream::copyTo)
            true
        }.getOrElse {
            Sentry.withScope { scope ->
                scope.setExtra("uri", uri.toString().replace(file.path, HIDDEN_FILE_NAME))
                val exception = it.message
                    ?.let { message -> AttachmentMissingFileException(message.replace(file.path, HIDDEN_FILE_NAME)) }
                    ?: it

                exception.stackTrace = it.stackTrace
                Sentry.captureException(exception)

                val isNameLong = exception.message?.contains(NAME_TOO_LONG_EXCEPTION) == true
                val snackbarMessageId = if (isNameLong) R.string.errorFileNameTooLong else RCore.string.errorFileNotFound

                snackbarManager.postValue(context.getString(snackbarMessageId))
            }
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
