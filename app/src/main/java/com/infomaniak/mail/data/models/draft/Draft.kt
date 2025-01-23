/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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

package com.infomaniak.mail.data.models.draft

import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.correspondent.Recipient
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
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

    var subject: String? = null
    var body: String = ""
    var attachments = realmListOf<Attachment>()

    @SerialName("mime_type")
    var mimeType: String = ""
    @SerialName("identity_id")
    var identityId: String? = null

    @SerialName("action")
    private var _action: String? = null

    @SerialName("in_reply_to")
    var inReplyTo: String? = null
    @SerialName("in_reply_to_uid")
    var inReplyToUid: String? = null
    @SerialName("forwarded_uid")
    var forwardedUid: String? = null

    var references: String? = null
    @SerialName("st_uuid")
    var swissTransferUuid: String? = null

    /**
     * This `delay` should be set to 0 when `scheduleDate` is NOT set.
     * Otherwise, if we remove it, we won't receive any `etop` from the API when sending an Email.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @Serializable(with = ConditionalIntSerializer::class)
    var delay: Int? = 0
        set(value) {
            scheduleDate = null
            field = value
        }

    /**
     * The API requires that this field does not exist, except when scheduling a message.
     * We use a custom serializer for this very reason.
     */
    @SerialName("schedule_date")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @Serializable(with = ConditionalStringSerializer::class)
    var scheduleDate: String? = null
        set(value) {
            delay = null
            field = value
        }
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

    fun initLocalValues(messageUid: String? = null, mimeType: String? = null) {
        messageUid?.let { this.messageUid = it }
        mimeType?.let { this.mimeType = it }
    }

    fun getJsonRequestBody(): MutableMap<String, JsonElement> {
        return draftJson.encodeToJsonElement(this).jsonObject.toMutableMap().apply {
            this[Draft::attachments.name] = JsonArray(attachments.map { JsonPrimitive(it.uuid) })
        }
    }

    enum class DraftAction(val apiCallValue: String, val matomoValue: String) {
        SAVE("save", "saveDraft"),
        SEND("send", "sendMail"),
        SCHEDULE("schedule", "scheduleDraft"),
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
        private val draftJson = Json(ApiController.json) { encodeDefaults = true }
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
