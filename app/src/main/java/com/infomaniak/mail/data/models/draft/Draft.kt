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
import com.infomaniak.mail.data.cache.mailboxContent.SignatureController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.signature.Signature.SignaturePosition
import io.realm.kotlin.MutableRealm
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
    @SerialName("uuid")
    var remoteUuid: String? = null

    @SerialName("reply_to")
    var replyTo: RealmList<Recipient> = realmListOf()

    var from: RealmList<Recipient> = realmListOf()
    var to: RealmList<Recipient> = realmListOf()
    var cc: RealmList<Recipient> = realmListOf()
    var bcc: RealmList<Recipient> = realmListOf()

    var subject: String = ""
    var body: String = ""
    var attachments: RealmList<Attachment> = realmListOf()

    @SerialName("mime_type")
    var mimeType: String = ""
    @SerialName("identity_id")
    var identityId: Int? = null

    @SerialName("priority")
    private var _priority: String? = null
    @SerialName("action")
    var _action: String? = null

    @SerialName("in_reply_to")
    var inReplyTo: String? = null
    @SerialName("in_reply_to_uid")
    var inReplyToUid: String? = null
    @SerialName("forwarded_uid")
    var forwardedUid: String? = null

    var resource: String? = null
    var quote: String = ""
    var references: String? = null
    var delay: Int = 0
    @SerialName("ack_request")
    var ackRequest: Boolean = false
    @SerialName("st_uuid")
    var stUuid: String? = null
    //endregion

    //region Local data (Transient)
    @Transient
    @PrimaryKey
    var localUuid: String = UUID.randomUUID().toString()
    @Transient
    var messageUid: String? = null
    //endregion

    var action
        get() = enumValueOfOrNull<DraftAction>(_action)
        set(value) {
            _action = value?.apiCallValue
        }

    private var priority
        get() = enumValueOfOrNull<Priority>(_priority)
        set(value) {
            _priority = value?.apiCallValue
        }

    fun initLocalValues(messageUid: String? = null, priority: Priority? = null, mimeType: String? = null) {
        messageUid?.let { this.messageUid = it }
        priority?.let { this.priority = it }
        mimeType?.let { this.mimeType = it }
    }

    fun initSignature(realm: MutableRealm) = SignatureController.getDefaultSignature(realm)?.let { defaultSignature ->

        identityId = defaultSignature.id

        from = realmListOf(Recipient().apply {
            this.email = defaultSignature.sender
            this.name = defaultSignature.fullName
        })

        replyTo = realmListOf(Recipient().apply {
            this.email = defaultSignature.replyTo
            this.name = ""
        })

        val html = "<br/><br/><div class=\"editorUserSignature\">${defaultSignature.content}</div>"
        body = when (defaultSignature.position) {
            SignaturePosition.AFTER_REPLY_MESSAGE -> body + html
            else -> html + body
        }
    }

    enum class DraftAction(val apiCallValue: String) {
        SAVE("save"),
        SEND("send"),
    }

    enum class DraftMode {
        NEW_MAIL,
        REPLY,
        REPLY_ALL,
        FORWARD,
    }
}
