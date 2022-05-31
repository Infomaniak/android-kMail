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
package com.infomaniak.mail.data.models

import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// @RealmClass(embedded = true) // TODO: https://github.com/realm/realm-kotlin/issues/551
@Serializable
class Attachment : RealmObject {
    @PrimaryKey // TODO: Remove `@PrimaryKey` when we have EmbeddedObjects
    var uuid: String = ""
    @SerialName("mime_type")
    var mimeType: String = ""
    var encoding: String = ""
    var size: Int = 0
    var name: String = ""
    private var disposition: String? = null
    @SerialName("content_id")
    var contentId: String? = null
    var resource: String = ""
    @SerialName("drive_url")
    var driveUrl: String? = null
    var localUri: String = ""
    var thumbnail: String = ""

    // TODO: Remove this method when we have EmbeddedObjects
    fun initLocalValues(position: Int, parentMessageUid: String) {
        uuid = "attachment_${position}_${parentMessageUid}"
    }

    fun getDisposition(): AttachmentDisposition? = enumValueOfOrNull<AttachmentDisposition>(disposition)

    override fun equals(other: Any?): Boolean = other is Attachment && other.uuid == uuid

    override fun hashCode(): Int = uuid.hashCode()

    enum class AttachmentDisposition {
        INLINE,
        ATTACHMENT,
    }
}
