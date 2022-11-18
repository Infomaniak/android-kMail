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

object LocalStorageUtils {

    private const val ATTACHMENTS_UPLOAD_FOLDER = "attachments_upload"

    private inline val Context.attachmentsRoot get() = File(filesDir, ATTACHMENTS_UPLOAD_FOLDER)

    private inline val File.hasEmptyFiles get() = listFiles().isNullOrEmpty()

    private fun getAttachmentsCacheFolder(
        context: Context,
        localDraftUuid: String,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
    ): File {
        return File(context.attachmentsRoot, "$userId/$mailboxId/$localDraftUuid")
    }

    fun copyDataToAttachmentsCache(context: Context, uri: Uri, fileName: String, localDraftUuid: String): File? {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val attachmentsCacheFolder = getAttachmentsCacheFolder(context, localDraftUuid)
            if (!attachmentsCacheFolder.exists()) attachmentsCacheFolder.mkdirs()
            File(attachmentsCacheFolder, fileName).also { file ->
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

    fun deleteAttachmentFromCache(
        context: Context,
        localDraftUuid: String,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
        fileName: String,
    ) {
        File(getAttachmentsCacheFolder(context, localDraftUuid, userId, mailboxId), fileName).delete()
    }

    fun deleteAttachmentsDirIfEmpty(
        context: Context,
        localDraftUuid: String,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
    ) {
        val attachmentsFolder = getAttachmentsCacheFolder(context, localDraftUuid, userId, mailboxId).also {
            if (!it.exists()) return
        }
        val mailboxFolder = attachmentsFolder.parentFile
        val userFolder = mailboxFolder.parentFile
        val attachmentsRoot = userFolder.parentFile


        if (attachmentsFolder.hasEmptyFiles) attachmentsFolder.delete()
        if (mailboxFolder.hasEmptyFiles) mailboxFolder.delete()
        if (userFolder.hasEmptyFiles) userFolder.delete()
        if (attachmentsRoot.hasEmptyFiles) attachmentsRoot.delete()
    }

    fun deleteUserData(context: Context, userId: Int) {
        with(context.attachmentsRoot) {
            File(this, "$userId").deleteRecursively()
            if (this.listFiles()?.isEmpty() == true) deleteRecursively()
        }
    }
}
