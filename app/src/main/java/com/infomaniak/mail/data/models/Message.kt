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
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.RealmClass

open class Message(
    var uid: String = "",
    @SerializedName("msg_id")
    var msgId: String = "",
    var date: String = "",
    var subject: String = "",
    var from: RealmList<Recipient> = RealmList(),
    var to: RealmList<Recipient> = RealmList(),
    var cc: RealmList<Recipient> = RealmList(),
    var bcc: RealmList<Recipient> = RealmList(),
    @SerializedName("reply_to")
    var replyTo: RealmList<Recipient> = RealmList(),
    var references: String = "",
    @SerializedName("priority")
    private var _priority: String? = null,
    @SerializedName("dkim_status")
    private var _dkimStatus: String? = null,
    @SerializedName("folder_id")
    var folderId: String = "",
    var folder: String = "",
    @SerializedName("st_uuid")
    var stUuid: String = "",
    var resource: String = "",
    @SerializedName("download_resource")
    var downloadResource: String = "",
    @SerializedName("is_draft")
    var isDraft: Boolean = false,
    @SerializedName("draft_resource")
    var draftResource: String = "",
    var body: MessageBody? = null,
    @SerializedName("has_attachments")
    var hasAttachments: Boolean = false,
    @SerializedName("attachments_resources")
    var attachmentsResource: String = "",
    var attachments: RealmList<Attachment> = RealmList(),
    var seen: Boolean = false,
    var forwarded: Boolean = false,
    var answered: Boolean = false,
    var flagged: Boolean = false,
    var scheduled: Boolean = false,
    var size: Int = 0,
    @SerializedName("safe_display")
    var safeDisplay: Boolean = false,
    var isDuplicate: Boolean = false,

    /**
     * Local
     */
    var hasUnsubscribeLink: Boolean = false,
    var parentLink: Thread? = null,
) : RealmObject() {

    fun getPriority(): MessagePriority? = when (_priority) {
        MessagePriority.LOW.name -> MessagePriority.LOW
        MessagePriority.NORMAL.name -> MessagePriority.NORMAL
        MessagePriority.HIGH.name -> MessagePriority.HIGH
        else -> null
    }

    enum class MessagePriority {
        LOW,
        NORMAL,
        HIGH,
    }

    fun getDkimStatus(): MessageDKIM? = when (_dkimStatus) {
        MessageDKIM.VALID.value -> MessageDKIM.VALID
        MessageDKIM.NOT_VALID.value -> MessageDKIM.NOT_VALID
        MessageDKIM.NOT_SIGNED.value -> MessageDKIM.NOT_SIGNED
        else -> null
    }

    enum class MessageDKIM(val value: String?) {
        VALID(null),
        NOT_VALID("not_valid"),
        NOT_SIGNED("not_signed"),
    }
}

@RealmClass(embedded = true)
open class MessageBody(
    var value: String = "",
    var type: String = "",
    var subBody: String? = null,
) : RealmObject()