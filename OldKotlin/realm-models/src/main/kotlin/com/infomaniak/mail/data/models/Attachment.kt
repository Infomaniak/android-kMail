/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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

package com.infomaniak.mail.data.models

import com.infomaniak.core.common.extensions.enumValueOfOrNull
import io.realm.kotlin.types.EmbeddedRealmObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
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
    @InternalModelProperties
    var _uploadStatus: String = AttachmentUploadStatus.NOT_UPLOADED.name
    @Transient
    var uploadLocalUri: String? = null
    //endregion

    val attachmentUploadStatus: AttachmentUploadStatus?
        get() = enumValueOfOrNull<AttachmentUploadStatus>(_uploadStatus)

    val disposition: AttachmentDisposition?
        get() = enumValueOfOrNull<AttachmentDisposition>(_disposition)

    fun initLocalValues(name: String, size: Long, mimeType: String, uri: String): Attachment {
        this.name = name
        this.size = size
        this.mimeType = mimeType
        this.uploadLocalUri = uri

        return this
    }

    companion object
}
