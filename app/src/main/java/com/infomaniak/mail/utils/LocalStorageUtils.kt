/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import io.sentry.Sentry
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object LocalStorageUtils {

    private const val ATTACHMENTS_CACHE_DIR = "attachments_cache"
    private const val ATTACHMENTS_UPLOAD_DIR = "attachments_upload"

    private inline val Context.attachmentsCacheRootDir get() = File(cacheDir, ATTACHMENTS_CACHE_DIR)
    private inline val Context.attachmentsUploadRootDir get() = File(filesDir, ATTACHMENTS_UPLOAD_DIR)

    private inline val File.hasEmptyFiles get() = listFiles().isNullOrEmpty()


    fun getAttachmentsCacheDir(
        context: Context,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
    ): File {
        return generateRootDir(context.attachmentsCacheRootDir, userId, mailboxId)
    }

    fun getAttachmentsUploadDir(
        context: Context,
        localDraftUuid: String,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
    ): File {
        return File(generateRootDir(context.attachmentsUploadRootDir, userId, mailboxId), localDraftUuid)
    }

    fun saveCacheAttachment(inputStream: InputStream, outputFile: File) = with(outputFile) {
        if (exists()) delete()
        inputStream.buffered().use {
            parentFile?.mkdirs()
            outputStream().use { output -> inputStream.copyTo(output) }
        }
    }

    fun saveUploadAttachment(context: Context, uri: Uri, fileName: String, localDraftUuid: String): File? {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val attachmentsCacheDir = getAttachmentsUploadDir(context, localDraftUuid)
            if (!attachmentsCacheDir.exists()) attachmentsCacheDir.mkdirs()
            File(attachmentsCacheDir, fileName).also { file ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } ?: run {
            Sentry.withScope { scope ->
                scope.setExtra("uri", uri.toString())
                Sentry.captureMessage("failed to access uri")
            }
            null
        }
    }

    private fun deleteAttachmentsCaches(context: Context) = context.attachmentsCacheRootDir.deleteRecursively()

    fun deleteAttachmentsUploadsDirIfEmpty(
        context: Context,
        localDraftUuid: String,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
    ) {
        val attachmentsDir = getAttachmentsUploadDir(context, localDraftUuid, userId, mailboxId).also {
            if (!it.exists()) return
        }
        val mailboxDir = attachmentsDir.parentFile ?: return
        val userDir = mailboxDir.parentFile ?: return
        val attachmentsRootDir = userDir.parentFile ?: return

        if (attachmentsDir.hasEmptyFiles) attachmentsDir.delete()
        if (mailboxDir.hasEmptyFiles) mailboxDir.delete()
        if (userDir.hasEmptyFiles) userDir.delete()
        if (attachmentsRootDir.hasEmptyFiles) attachmentsRootDir.delete()
    }

    fun deleteUserData(context: Context, userId: Int) {
        deleteAttachmentsCaches(context)
        with(context.attachmentsUploadRootDir) {
            File(this, "$userId").deleteRecursively()
            if (this.listFiles()?.isEmpty() == true) deleteRecursively()
        }
    }

    private fun generateRootDir(directory: File, userId: Int, mailboxId: Int): File {
        return File(directory, "$userId/$mailboxId")
    }
}
