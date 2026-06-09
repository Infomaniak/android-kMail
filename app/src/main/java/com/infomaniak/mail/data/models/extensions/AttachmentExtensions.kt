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
@file:OptIn(InternalModelProperties::class)

package com.infomaniak.mail.data.models.extensions

import androidx.core.net.toFile
import androidx.core.net.toUri
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.AttachmentUploadStatus
import com.infomaniak.mail.data.models.InternalModelProperties
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.utils.AttachableMimeTypeUtils
import com.infomaniak.mail.utils.SentryDebug

val Attachment.isCalendarEvent: Boolean get() = AttachableMimeTypeUtils.calendarMatches.contains(mimeType)

fun Attachment.setUploadStatus(attachmentUploadStatus: AttachmentUploadStatus, draft: Draft? = null, step: String = "") {
    draft?.let { SentryDebug.addDraftBreadcrumbs(it, step) }
    _uploadStatus = attachmentUploadStatus.name
}

/**
 * After uploading an Attachment, we replace the local version with the remote one.
 * The remote one doesn't know about local data, so we have to backup them.
 */
fun Attachment.backupLocalData(oldAttachment: Attachment, draft: Draft) {
    localUuid = oldAttachment.localUuid
    uploadLocalUri = oldAttachment.uploadLocalUri
    setUploadStatus(AttachmentUploadStatus.UPLOADED, draft, "backupLocalData -> setUploadStatus")
}

fun Attachment.getUploadLocalFile() = uploadLocalUri?.toUri()?.toFile()

internal fun Attachment.extractPathFromResource(): String {
    return resource?.substringAfter("folder/")?.replace(Regex("(message|attachment)/"), "") ?: ""
}
