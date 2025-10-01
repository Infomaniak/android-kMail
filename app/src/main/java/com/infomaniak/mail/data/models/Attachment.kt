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
package com.infomaniak.mail.data.models

import android.content.Context
import androidx.core.net.toFile
import androidx.core.net.toUri
import com.infomaniak.core.legacy.utils.Utils.enumValueOfOrNull
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.utils.AttachableMimeTypeUtils
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.SentryDebug
import io.realm.kotlin.types.EmbeddedRealmObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File
import java.util.UUID

@Serializable
class Attachment : EmbeddedRealmObject, Attachable {

    //region Remote data
    var uuid: String = ""
    @SerialName("mime_type")
    override var mimeType: String = ""
    override var size: Long = 0L
    override var name: String = ""
    @SerialName("disposition")
    private var _disposition: String? = null
    @SerialName("content_id")
    var contentId: String? = null
    @SerialName("original_content_id")
    var originalContentId: String? = null
    override var resource: String? = null
    @SerialName("drive_url")
    var driveUrl: String? = null
    //endregion

    //region Local data (Transient)

    // ------------- !IMPORTANT! -------------
    // Every field that is added in this Transient region should be declared in
    // `initLocalValue()` too to avoid loosing data when updating from the API.

    @Transient
    override var localUuid: String = UUID.randomUUID().toString()
    @Transient
    private var _uploadStatus: String = AttachmentUploadStatus.NOT_UPLOADED.name
    @Transient
    var uploadLocalUri: String? = null
    //endregion

    val attachmentUploadStatus: AttachmentUploadStatus?
        get() = enumValueOfOrNull<AttachmentUploadStatus>(_uploadStatus)

    val isCalendarEvent: Boolean get() = AttachableMimeTypeUtils.calendarMatches.contains(mimeType)

    val disposition: AttachmentDisposition?
        get() = enumValueOfOrNull<AttachmentDisposition>(_disposition)

    fun initLocalValues(name: String, size: Long, mimeType: String, uri: String): Attachment {
        this.name = name
        this.size = size
        this.mimeType = mimeType
        this.uploadLocalUri = uri

        return this
    }

    /**
     * After uploading an Attachment, we replace the local version with the remote one.
     * The remote one doesn't know about local data, so we have to backup them.
     */
    fun backupLocalData(oldAttachment: Attachment, draft: Draft) {
        localUuid = oldAttachment.localUuid
        uploadLocalUri = oldAttachment.uploadLocalUri
        setUploadStatus(AttachmentUploadStatus.UPLOADED, draft, "backupLocalData -> setUploadStatus")
    }

    fun setUploadStatus(attachmentUploadStatus: AttachmentUploadStatus, draft: Draft? = null, step: String = "") {
        draft?.let { SentryDebug.addDraftBreadcrumbs(it, step) }
        _uploadStatus = attachmentUploadStatus.name
    }

    override fun getFileTypeFromMimeType() = AttachableMimeTypeUtils.getFileTypeFromMimeType(safeMimeType)

    override fun hasUsableCache(context: Context, file: File?, userId: Int, mailboxId: Int): Boolean {
        val cachedFile = file ?: getCacheFile(context, userId, mailboxId)
        return cachedFile.length() > 0 && cachedFile.canRead()
    }

    override fun isInlineCachedFile(context: Context): Boolean {
        return getCacheFile(context).exists() && disposition == AttachmentDisposition.INLINE
    }

    override fun getCacheFile(context: Context, userId: Int, mailboxId: Int): File {
        val cacheFolder = LocalStorageUtils.getAttachmentsCacheDir(context, extractPathFromResource(), userId, mailboxId)
        return File(cacheFolder, name)
    }

    private fun extractPathFromResource(): String {
        return resource?.substringAfter("folder/")?.replace(Regex("(message|attachment)/"), "") ?: ""
    }

    fun getUploadLocalFile() = uploadLocalUri?.toUri()?.toFile()

    companion object
}
