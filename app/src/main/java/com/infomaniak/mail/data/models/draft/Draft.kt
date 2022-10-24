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
@file:UseSerializers(RealmListSerializer::class)

package com.infomaniak.mail.data.models.draft

import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.mail.data.api.RealmListSerializer
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.correspondent.Recipient
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import java.util.*

@Serializable
class Draft : RealmObject {

    //region API data
    @PrimaryKey
    var uuid: String = ""
    @SerialName("identity_id")
    var identityId: Int? = null
    var resource: String? = null
    @SerialName("in_reply_to_uid")
    var inReplyToUid: String? = null
    @SerialName("forwarded_uid")
    var forwardedUid: String? = null
    var quote: String = ""
    var references: String? = null
    @SerialName("reply_to")
    var replyTo: RealmList<Recipient> = realmListOf()
    @SerialName("in_reply_to")
    var inReplyTo: String? = null
    @SerialName("mime_type")
    var mimeType: String = "any/any"
    var body: String = ""
    var cc: RealmList<Recipient> = realmListOf()
    var bcc: RealmList<Recipient> = realmListOf()
    var from: RealmList<Recipient> = realmListOf()
    var to: RealmList<Recipient> = realmListOf()
    var subject: String = ""
    @SerialName("ack_request")
    var ackRequest: Boolean = false
    @SerialName("action")
    private var _action: String = ""
    var delay: Int = 0
    @SerialName("priority")
    private var _priority: String? = null
    @SerialName("st_uuid")
    var stUuid: String? = null
    var attachments: RealmList<Attachment> = realmListOf()
    //endregion

    //region Local data (Transient)
    @Transient
    var isLocal: Boolean = false
    @Transient
    var parentMessageUid: String = "" // TODO: Use inverse relationship instead (https://github.com/realm/realm-kotlin/issues/591)
    //endregion

    var action
        get() = enumValueOfOrNull<DraftAction>(_action)
        set(value) {
            _action = value.toString()
        }

    var priority
        get() = enumValueOfOrNull<Priority>(_priority)
        set(value) {
            _priority = value.toString()
        }

    fun initLocalValues(messageUid: String? = null, priority: Priority? = null): Draft {
        if (uuid.isEmpty()) {
            uuid = UUID.randomUUID().toString()
            isLocal = true
        }
        messageUid?.let { this.parentMessageUid = it }
        priority?.let { this.priority = it }

        return this
    }

    enum class DraftAction {
        SEND,
        SAVE;

        override fun toString() = name.lowercase()
    }
}
