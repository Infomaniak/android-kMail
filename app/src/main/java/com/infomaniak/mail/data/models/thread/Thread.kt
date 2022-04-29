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
package com.infomaniak.mail.data.models.thread

import com.google.gson.annotations.SerializedName
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.MailRealm
import com.infomaniak.mail.data.models.Recipient
import com.infomaniak.mail.data.models.message.Message
import io.realm.*
import io.realm.MutableRealm.UpdatePolicy
import io.realm.annotations.PrimaryKey

class Thread : RealmObject {
    @PrimaryKey
    var uid: String = ""

    @SerializedName("messages_count")
    var messagesCount: Int = 0

    @SerializedName("unique_messages_count")
    var uniqueMessagesCount: Int = 0

    @SerializedName("deleted_messages_count")
    var deletedMessagesCount: Int = 0
    var messages: RealmList<Message> = realmListOf()

    @SerializedName("unseen_messages")
    var unseenMessages: Int = 0
    var from: RealmList<Recipient> = realmListOf()
    var cc: RealmList<Recipient> = realmListOf()
    var bcc: RealmList<Recipient> = realmListOf()
    var to: RealmList<Recipient> = realmListOf()
    var subject: String = ""
    var date: RealmInstant? = null

    @SerializedName("has_attachments")
    var hasAttachments: Boolean = false

    @SerializedName("has_st_attachments")
    var hasStAttachments: Boolean = false

    @SerializedName("has_drafts")
    var hasDrafts: Boolean = false
    var flagged: Boolean = false
    var answered: Boolean = false
    var forwarded: Boolean = false
    var size: Int = 0

    fun initLocalValues(): Thread {
        messages.removeIf { it.isDuplicate }
        return this
    }

    fun getMessages(): List<Message> {

        fun deleteMessages() { // TODO: Remove it (blocked by https://github.com/realm/realm-kotlin/issues/805)
            MailRealm.mailboxContent.writeBlocking {
                delete(query<Message>().find())
                // delete(query<Recipient>().find())
                // delete(query<Body>().find())
                // delete(query<Attachment>().find())
            }
        }

        MailRealm.currentThread = this

        // deleteMessages()

        val apiMessages = mutableListOf<Message>()
        messages.forEach { message ->
            ApiRepository.getMessage(message).data?.let { completedMessage ->
                completedMessage.apply {
                    fullyDownloaded = true
                    // TODO: Remove this `forEachIndexed` when we have EmbeddedObjects
                    attachments.forEachIndexed { index, attachment -> attachment.initLocalValues(uid, index) }
                }
                apiMessages.add(completedMessage)
            }
        }

        messages = apiMessages.toRealmList()
        MailRealm.mailboxContent.writeBlocking { copyToRealm(this@Thread, UpdatePolicy.ALL) }

        return apiMessages
    }

    enum class ThreadFilter(title: String) {
        ALL("All"),
        SEEN("Seen"),
        UNSEEN("Unseen"),
        STARRED("Starred"),
        UNSTARRED("Unstarred"),
    }
}
