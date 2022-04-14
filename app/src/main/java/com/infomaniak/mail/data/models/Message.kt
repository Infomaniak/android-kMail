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

data class Message(
    val uid: String,
    @SerializedName("msg_id")
    val msgId: String,
    val date: String,
    val subject: String,
    val from: ArrayList<Recipient>,
    val to: ArrayList<Recipient>,
    val cc: ArrayList<Recipient>,
    val bcc: ArrayList<Recipient>,
    @SerializedName("reply_to")
    val replyTo: ArrayList<Recipient>,
    val references: String?,
    val priority: MessagePriority,
    @SerializedName("dkim_status")
    val dkimStatus: MessageDKIM,
    @SerializedName("folder_id")
    val folderId: String,
    val folder: String,
    @SerializedName("st_uuid")
    val stUuid: String?,
    val resource: String,
    @SerializedName("download_resource")
    val downloadResource: String,
    @SerializedName("is_draft")
    val isDraft: Boolean,
    val body: Body,
    @SerializedName("has_attachments")
    val hasAttachments: Boolean,
    @SerializedName("attachments_resources")
    val attachmentsResource: String?,
    val attachments: ArrayList<Attachment>?,
    val seen: Boolean,
    val forwarded: Boolean,
    val answered: Boolean,
    val flagged: Boolean,
    val scheduled: Boolean,
    val size: Int,
    @SerializedName("safe_display")
    val safeDisplay: Boolean,
    val isDuplicate: Boolean,

    /**
     * Local
     */
    val draftResource: String,
    val hasUnsubscribeLink: Boolean,
    val parentLink: Thread,
) {
    enum class MessagePriority {
        LOW,
        NORMAL,
        HIGH,
    }

    enum class MessageDKIM(name: String?) {
        VALID(null),
        NOT_VALID("not_valid"),
        NOT_SIGNED("not_signed"),
    }

    data class Body(
        val value: String,
        val type: String,
        val subBody: String?,
    )
}