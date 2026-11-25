/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.mail.data.models.extensions

import android.content.Context
import com.infomaniak.core.legacy.utils.guessMimeType
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.data.models.Attachable
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.AttachmentType
import com.infomaniak.mail.data.models.SwissTransferFile
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.attachment.AttachableMimeTypeUtils
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.resolveContainedFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

val Attachable.downloadUrl get() = ApiRoutes.resource(resource!!)

val Attachable.safeMimeType get() = if (mimeType == Utils.MIMETYPE_UNKNOWN) name.guessMimeType() else mimeType

fun Attachable.getFileTypeFromMimeType(): AttachmentType = AttachableMimeTypeUtils.getFileTypeFromMimeType(safeMimeType)

internal fun File.isUsableAttachmentFile(expectedSize: Long): Boolean =
    isFile && canRead() && (expectedSize == 0L || length() > 0)

internal suspend fun Attachment.getFileForOpening(context: Context, mailbox: Mailbox? = null): File? {
    return withContext(Dispatchers.IO) {
        getUploadLocalFile()?.takeIf { it.isUsableAttachmentFile(size) } ?: getCacheFile(context, mailbox)
    }
}

internal suspend fun Attachment.hasFileForOpening(context: Context, mailbox: Mailbox? = null): Boolean {
    return withContext(Dispatchers.IO) { getFileForOpening(context, mailbox)?.isUsableAttachmentFile(size) == true }
}

internal suspend fun Attachment.getCacheFile(context: Context, mailbox: Mailbox?): File? {
    return if (mailbox == null) getCacheFile(context) else getCacheFile(context, mailbox.userId, mailbox.mailboxId)
}

suspend fun Attachable.getCacheFile(
    context: Context,
    userId: Int = AccountUtils.currentUserId,
    mailboxId: Int = AccountUtils.currentMailboxId,
): File? = when (this) {
    is Attachment -> {
        val cacheFolder = LocalStorageUtils.getAttachmentsCacheDir(context, extractPathFromResource(), userId, mailboxId)
        cacheFolder?.resolveContainedFileName(untrustedName = name)
    }
    is SwissTransferFile -> File("")
}
