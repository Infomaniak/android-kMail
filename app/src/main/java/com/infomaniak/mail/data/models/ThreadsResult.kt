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

data class ThreadsResult(
    val threads: ArrayList<Thread>,
) {
    data class Thread(
        val uid: String,
        @SerializedName("messages_count")
        val messagesCount: Int,
        @SerializedName("unique_messages_count")
        val uniqueMessagesCount: Int,
        @SerializedName("deleted_messages_count")
        val deletedMessagesCount: Int,
        val messages: ArrayList<Message>,
        @SerializedName("unseen_messages")
        val unseenMessages: Int,
        val from: ArrayList<Recipient>,
        val to: ArrayList<Recipient>,
        val cc: ArrayList<Recipient>,
        val bcc: ArrayList<Recipient>,
        val subject: String,
        val date: String,
        @SerializedName("has_attachments")
        val hasAttachments: Boolean,
        @SerializedName("has_st_attachments")
        val hasStAttachments: Boolean,
        @SerializedName("has_drafts")
        val hasDrafts: Boolean,
        val flagged: Boolean,
        val answered: Boolean,
        val forwarded: Boolean,
        val size: Int,
    ) {
        enum class Filter(title: String) {
            ALL("All"),
            SEEN("Seen"),
            UNSEEN("Unseen"),
            STARRED("Starred"),
            UNSTARRED("Unstarred"),
        }
    }
}