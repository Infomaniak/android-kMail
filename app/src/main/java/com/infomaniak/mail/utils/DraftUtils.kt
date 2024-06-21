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
package com.infomaniak.mail.utils

import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.isNetworkException
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Attachment.UploadStatus
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.utils.extensions.AttachmentExtensions.ATTACHMENT_TAG
import com.infomaniak.mail.utils.extensions.AttachmentExtensions.findSpecificAttachment
import com.infomaniak.mail.utils.extensions.AttachmentExtensions.startUpload
import io.realm.kotlin.Realm
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

val attachmentsUploadMutex = Mutex()

suspend fun uploadAttachmentsWithMutex(
    draft: Draft,
    mailbox: Mailbox,
    draftController: DraftController,
    realm: Realm,
): Draft = attachmentsUploadMutex.withLock {
    draft.uploadAttachments(mailbox, draftController, realm)
    val updatedDraft = DraftController.getDraft(draft.localUuid, realm)!!
    return@withLock updatedDraft
}

private suspend fun Draft.uploadAttachments(mailbox: Mailbox, draftController: DraftController, realm: Realm) {

    fun getAwaitingAttachments(): List<Attachment> = attachments.filter { it.uploadStatus == UploadStatus.AWAITING }

    fun setUploadStatus(attachment: Attachment, uploadStatus: UploadStatus) {
        realm.writeBlocking {
            draftController.updateDraft(localUuid, realm = this) {
                it.attachments.findSpecificAttachment(attachment)?.setUploadStatus(uploadStatus)
            }
        }
    }

    val attachmentsToUpload = getAwaitingAttachments()
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
            setUploadStatus(attachment, UploadStatus.ONGOING)
            attachment.startUpload(localUuid, mailbox, draftController, realm)
        }.onFailure { exception ->
            setUploadStatus(attachment, UploadStatus.AWAITING)
            SentryLog.d(ATTACHMENT_TAG, "${exception.message}", exception)
            if ((exception as Exception).isNetworkException()) throw ApiController.NetworkException()
            throw exception
        }
    }
}
