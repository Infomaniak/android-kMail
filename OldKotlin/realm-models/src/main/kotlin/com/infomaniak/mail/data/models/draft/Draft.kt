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
@file:UseSerializers(RealmListKSerializer::class)
@file:OptIn(InternalModelProperties::class)

package com.infomaniak.mail.data.models.draft

import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.InternalModelProperties
import com.infomaniak.mail.data.models.correspondent.Recipient
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

@OptIn(ExperimentalSerializationApi::class)
@Serializable
class Draft : RealmObject {

    //region Remote data
    @SerialName("uuid")
    var remoteUuid: String? = null

    var to = realmListOf<Recipient>()
    var cc = realmListOf<Recipient>()
    var bcc = realmListOf<Recipient>()

    @SerialName("mentions")
    var mentions = realmListOf<String>()

    var subject: String? = null
    var body: String = ""
    var attachments = realmListOf<Attachment>()

    @SerialName("mime_type")
    var mimeType: String = ""
    @SerialName("identity_id")
    var identityId: String? = null

    @SerialName("action")
    @InternalModelProperties
    var _action: String? = null

    @SerialName("ack_request")
    var ackRequest: Boolean = false

    @SerialName("in_reply_to")
    var inReplyTo: String? = null
    @SerialName("in_reply_to_uid")
    var inReplyToUid: String? = null
    @SerialName("forwarded_uid")
    var forwardedUid: String? = null

    var references: String? = null
    @SerialName("st_uuid")
    var swissTransferUuid: String? = null

    @SerialName("encrypted")
    var isEncrypted: Boolean = false
    @SerialName("encryption_password")
    var encryptionKey: String? = null

    @EncodeDefault(EncodeDefault.Mode.NEVER) // The api will always reject this field if it's null
    @SerialName("emoji_reaction")
    var emojiReaction: String? = null

    /**
     * We can't have both `delay` & `scheduleDate`. They are mutually exclusive.
     *
     * `delay` should be set to 0 when `scheduleDate` is not set.
     * If we don't set it to 0, we won't receive any `etop` from the API when sending an Email.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @Serializable(with = ConditionalIntSerializer::class)
    var delay: Int? = 0
        set(value) {
            scheduleDate = null
            field = value
        }

    /**
     * We can't have both `delay` & `scheduleDate`. They are mutually exclusive.
     *
     * The API requires that this field does not exist, except when scheduling a message.
     * We use a custom serializer for this very reason.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @Serializable(with = ConditionalStringSerializer::class)
    @SerialName("schedule_date")
    var scheduleDate: String? = null
        set(value) {
            delay = null
            field = value
        }
    //endregion

    //region Local data (Transient)

    // ------------- !IMPORTANT! -------------
    // Every field that is added in this Transient region should be declared in
    // `initLocalValue()` too to avoid loosing data when updating from the API.

    @Transient
    @PrimaryKey
    var localUuid: String = UUID.randomUUID().toString()
    @Transient
    var messageUid: String? = null
    //endregion

    fun initLocalValues(messageUid: String? = null, mimeType: String? = null) {
        messageUid?.let { this.messageUid = it }
        mimeType?.let { this.mimeType = it }
    }

    enum class DraftMode {
        REPLY,
        REPLY_ALL,
        FORWARD,
        NEW_MAIL,
    }

    companion object {
        const val NO_IDENTITY = -1

        val actionPropertyName get() = Draft::_action.name
    }
}

object ConditionalIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ConditionalIntField", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int?) {
        if (shouldSerialize(value)) encoder.encodeInt(value ?: 0)
    }

    override fun deserialize(decoder: Decoder): Int = decoder.decodeInt()

    private fun shouldSerialize(value: Int?): Boolean = value != null
}

object ConditionalStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ConditionalStringField", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        if (shouldSerialize(value)) encoder.encodeString(value ?: "")
    }

    override fun deserialize(decoder: Decoder): String = decoder.decodeString()

    private fun shouldSerialize(value: String?): Boolean = value != null
}
