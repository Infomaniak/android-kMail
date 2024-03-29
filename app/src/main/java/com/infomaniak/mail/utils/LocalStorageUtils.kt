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
import com.infomaniak.mail.data.api.ApiRepository
import io.sentry.Sentry
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object LocalStorageUtils {

    private const val ATTACHMENTS_CACHE_DIR = "attachments_cache"
    private const val ATTACHMENTS_UPLOAD_DIR = "attachments_upload"

    private inline val Context.attachmentsCacheRootDir get() = File(cacheDir, ATTACHMENTS_CACHE_DIR)
    private inline val Context.attachmentsUploadRootDir get() = File(filesDir, ATTACHMENTS_UPLOAD_DIR)

    private inline val File.hasNoChildren get() = listFiles().isNullOrEmpty()

    //region Cache
    fun getAttachmentsCacheDir(
        context: Context,
        attachmentPath: String,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
    ): File {
        return File(generateRootDir(context.attachmentsCacheRootDir, userId, mailboxId), attachmentPath)
    }

    fun saveAttachmentToCache(resource: String, cacheFile: File): Boolean {
        fun Response.saveAttachmentTo(outputFile: File): Boolean {
            if (!isSuccessful) return false
            return body?.byteStream()?.use { inputStream ->
                saveCacheAttachment(inputStream, outputFile)
                true
            } ?: false
        }

        return runCatching { ApiRepository.downloadAttachment(resource) }.getOrNull()?.saveAttachmentTo(cacheFile) ?: false
    }

    /**
     * Save the Attachment in disk memory.
     * The file remains unreadable as long as it's being processed.
     */
    fun saveCacheAttachment(inputStream: InputStream, outputFile: File) = with(outputFile) {
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

    private fun deleteAttachmentsCaches(context: Context) = context.attachmentsCacheRootDir.deleteRecursively()
    //endregion

    //region Upload
    private fun getAttachmentsUploadDir(
        context: Context,
        draftLocalUuid: String,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
    ): File {
        return File(generateRootDir(context.attachmentsUploadRootDir, userId, mailboxId), draftLocalUuid)
    }

    fun saveUploadAttachment(context: Context, uri: Uri, fileName: String, draftLocalUuid: String): File? {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val attachmentsUploadDir = getAttachmentsUploadDir(context, draftLocalUuid)
            if (!attachmentsUploadDir.exists()) attachmentsUploadDir.mkdirs()
            val hashedFileName = "${uri.toString().substringAfter("document/").hashCode()}_$fileName"
            File(attachmentsUploadDir, hashedFileName).also { file ->
                FileOutputStream(file).use(inputStream::copyTo)
            }
        } ?: run {
            Sentry.withScope { scope ->
                scope.setExtra("uri", uri.toString())
                Sentry.captureMessage("failed to access uri")
            }
            null
        }
    }

    fun deleteAttachmentsUploadsDirIfEmpty(
        context: Context,
        draftLocalUuid: String,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
    ) {
        val attachmentsDir = getAttachmentsUploadDir(context, draftLocalUuid, userId, mailboxId).also {
            if (!it.exists()) return
        }
        val mailboxDir = attachmentsDir.parentFile ?: return
        val userDir = mailboxDir.parentFile ?: return
        val attachmentsRootDir = userDir.parentFile ?: return

        if (attachmentsDir.hasNoChildren) attachmentsDir.delete()
        if (mailboxDir.hasNoChildren) mailboxDir.delete()
        if (userDir.hasNoChildren) userDir.delete()
        if (attachmentsRootDir.hasNoChildren) attachmentsRootDir.delete()
    }
    //endregion

    //region Global
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
    //endregion
}
