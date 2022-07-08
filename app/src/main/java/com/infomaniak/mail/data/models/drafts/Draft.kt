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

package com.infomaniak.mail.data.models.drafts

import com.infomaniak.mail.data.api.RealmListSerializer
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Folder.Companion.API_DRAFT_FOLDER_NAME
import com.infomaniak.mail.data.models.Folder.Companion.getDraftsFolder
import com.infomaniak.mail.data.models.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.toRealmInstant
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
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
    @PrimaryKey
    var uuid: String = ""
    @SerialName("identity_id")
    var identityId: Int? = null
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
    var mimeType: String = "text/html"
    var body: String = ""
    var cc: RealmList<Recipient>? = null
    var bcc: RealmList<Recipient>? = null
    var from: RealmList<Recipient> = realmListOf()
    var to: RealmList<Recipient>? = null
    var subject: String = ""
    @SerialName("ack_request")
    var ackRequest: Boolean = false
    var action: String = ""
    var delay: Int = 0
    var priority: String? = null
    @SerialName("st_uuid")
    var stUuid: String? = null
    var attachments: RealmList<Attachment> = realmListOf()

    /**
     * Local
     */
    var parentMessageUid: String = ""
    @Transient
    var isOffline: Boolean = false

    fun initLocalValues(messageUid: String = "") {

        if (uuid.isEmpty()) {
            uuid = "${OFFLINE_DRAFT_UUID_PREFIX}_${UUID.randomUUID()}"
            isOffline = true
        }

        parentMessageUid = messageUid

        cc = cc?.map { it.initLocalValues() }?.toRealmList() // TODO: Remove this when we have EmbeddedObjects
        bcc = bcc?.map { it.initLocalValues() }?.toRealmList() // TODO: Remove this when we have EmbeddedObjects
        to = to?.map { it.initLocalValues() }?.toRealmList() // TODO: Remove this when we have EmbeddedObjects
    }

    fun toMessage() = Message().apply {
        val draft = this@Draft
        uid = draft.parentMessageUid.ifEmpty { draft.uuid }
        draftUuid = draft.uuid
        subject = draft.subject
        folder = API_DRAFT_FOLDER_NAME
        folderId = getDraftsFolder()?.id.toString()
        from = draft.from
        to = draft.to ?: realmListOf()
        cc = draft.cc ?: realmListOf()
        bcc = draft.bcc ?: realmListOf()
        replyTo = draft.replyTo
        isDraft = true
        attachments = draft.attachments
        hasAttachments = draft.attachments.isNotEmpty()
        date = Date().toRealmInstant()
    }

    enum class DraftAction {
        SEND, SAVE
    }

    companion object {
        private const val OFFLINE_DRAFT_UUID_PREFIX = "offline"
    }
}
