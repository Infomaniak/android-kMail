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
package com.infomaniak.mail.data.models.attachment

import com.google.gson.annotations.SerializedName
import io.realm.RealmObject

class Attachment : RealmObject {
    var uuid: String = ""
    var partId: String = ""
    var mimeType: String = ""
    var encoding: String = ""
    var size: Int = 0
    var name: String = ""

    @SerializedName("disposition")
    private var _disposition: String? = null
    var contentId: String = ""
    var resource: String = ""
    var driveUrl: String = ""
    var data: AttachmentData? = null
    var localUrl: String = ""
    var thumbnail: String = ""

    fun getDisposition(): AttachmentDisposition? = when (_disposition) {
        AttachmentDisposition.INLINE.name -> AttachmentDisposition.INLINE
        AttachmentDisposition.ATTACHMENT.name -> AttachmentDisposition.ATTACHMENT
        else -> null
    }

    enum class AttachmentDisposition {
        INLINE,
        ATTACHMENT,
    }
}