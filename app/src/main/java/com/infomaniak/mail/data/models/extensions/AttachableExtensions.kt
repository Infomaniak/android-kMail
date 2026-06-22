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
import com.infomaniak.mail.data.models.AttachmentDisposition
import com.infomaniak.mail.data.models.AttachmentType
import com.infomaniak.mail.data.models.SwissTransferFile
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.AttachableMimeTypeUtils
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.Utils
import java.io.File

val Attachable.downloadUrl get() = ApiRoutes.resource(resource!!)

val Attachable.safeMimeType get() = if (mimeType == Utils.MIMETYPE_UNKNOWN) name.guessMimeType() else mimeType

fun Attachable.getFileTypeFromMimeType(): AttachmentType = AttachableMimeTypeUtils.getFileTypeFromMimeType(safeMimeType)

fun Attachable.hasUsableCache(
    context: Context,
    file: File? = null,
    userId: Int = AccountUtils.currentUserId,
    mailboxId: Int = AccountUtils.currentMailboxId,
): Boolean = when (this) {
    is Attachment -> {
        val cachedFile = file ?: getCacheFile(context, userId, mailboxId)
        cachedFile.length() > 0 && cachedFile.canRead()
    }
    is SwissTransferFile -> false
}

fun Attachable.isInlineCachedFile(context: Context): Boolean = when (this) {
    is Attachment -> getCacheFile(context).exists() && disposition == AttachmentDisposition.INLINE
    is SwissTransferFile -> false
}

fun Attachable.getCacheFile(
    context: Context,
    userId: Int = AccountUtils.currentUserId,
    mailboxId: Int = AccountUtils.currentMailboxId,
): File = when (this) {
    is Attachment -> {
        val cacheFolder = LocalStorageUtils.getAttachmentsCacheDir(context, extractPathFromResource(), userId, mailboxId)
        File(cacheFolder, name)
    }
    is SwissTransferFile -> File("")
}
