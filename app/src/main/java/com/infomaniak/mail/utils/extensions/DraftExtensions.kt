/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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

import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.isNetworkException
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.utils.extensions.AttachmentExtensions.ATTACHMENT_TAG
import com.infomaniak.mail.utils.extensions.AttachmentExtensions.startUpload
import io.realm.kotlin.Realm

suspend fun Draft.uploadAttachments(mailbox: Mailbox, draftController: DraftController, realm: Realm): Boolean {

    val attachmentsToUpload = getNotUploadedAttachments()
    val attachmentsToUploadCount = attachmentsToUpload.count()
    if (attachmentsToUploadCount > 0) {
        SentryLog.d(ATTACHMENT_TAG, "Uploading $attachmentsToUploadCount attachments")
        SentryLog.d(
            tag = ATTACHMENT_TAG,
            msg = "Attachments Uuids to localUris : ${attachmentsToUpload.map { it.uuid to it.uploadLocalUri }}}",
        )
    }

    attachmentsToUpload.forEach { attachment ->
        runCatching {
            attachment.startUpload(localUuid, mailbox, draftController, realm)
        }.onFailure { exception ->
            SentryLog.d(ATTACHMENT_TAG, "${exception.message}", exception)
            if ((exception as Exception).isNetworkException()) throw ApiController.NetworkException()
            throw exception
        }
    }

    return true
}

private fun Draft.getNotUploadedAttachments(): List<Attachment> = attachments.filterNot { it.isAlreadyUploaded }
