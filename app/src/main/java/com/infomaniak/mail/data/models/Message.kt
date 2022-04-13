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

import java.util.*

data class Message(
    val uid: String,
    val msgId: String,
    val subject: String,
    val priority: MessagePriority,
    val date: String,
    val size: Int,
    val from: ArrayList<Recipient>,
    val to: ArrayList<Recipient>,
    val cc: ArrayList<Recipient>,
    val bcc: ArrayList<Recipient>,
    val replyTo: ArrayList<Recipient>,
    val body: Body,
    val attachments: ArrayList<Attachment>,
    val dkimStatus: MessageDKIM,
    val attachmentsResource: String,
    val resource: String,
    val downloadResource: String,
    val draftResource: String,
    val stUuid: String,
    val folderId: String,
    val folder: String,
    val references: String,
    val answered: Boolean,
    val isDuplicate: Boolean,
    val isDraft: Boolean,
    val hasAttachments: Boolean,
    val seen: Boolean,
    val scheduled: Boolean,
    val forwarded: Boolean,
    val flagged: Boolean,
    val safeDisplay: Boolean,
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
        val subBody: String,
    )
}