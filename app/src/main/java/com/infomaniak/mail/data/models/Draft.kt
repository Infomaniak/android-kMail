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

import com.google.gson.annotations.SerializedName
import com.infomaniak.mail.data.models.attachment.Attachment
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.realmListOf

class Draft : RealmObject {
    var uuid: String = ""

    @SerializedName("identity_id")
    var identityId: String = ""

    @SerializedName("in_reply_to_uid")
    var inReplyToUid: String? = null

    @SerializedName("forwarded_uid")
    var forwardedUid: String? = null
    var references: String? = null

    @SerializedName("in_reply_to")
    var inReplyTo: String? = null

    @SerializedName("mime_type")
    var mimeType: String = "any/any"
    var body: String = ""
    var cc: RealmList<Recipient> = realmListOf()
    var bcc: RealmList<Recipient> = realmListOf()
    var to: RealmList<Recipient> = realmListOf()
    var subject: String = ""

    @SerializedName("ack_request")
    var ackRequest: Boolean = false
    var priority: String? = null

    @SerializedName("st_uuid")
    var stUuid: String? = null
    var attachments: RealmList<Attachment> = realmListOf()
}