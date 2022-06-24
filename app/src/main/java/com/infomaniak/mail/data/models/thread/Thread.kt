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

@file:UseSerializers(RealmListSerializer::class, RealmInstantSerializer::class)

package com.infomaniak.mail.data.models.thread

import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.api.MailApi
import com.infomaniak.mail.data.api.RealmInstantSerializer
import com.infomaniak.mail.data.api.RealmListSerializer
import com.infomaniak.mail.data.cache.MailRealm
import com.infomaniak.mail.data.cache.MailboxContentController
import com.infomaniak.mail.data.cache.MailboxInfoController
import com.infomaniak.mail.data.models.Recipient
import com.infomaniak.mail.data.models.message.Message
import io.realm.*
import io.realm.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
class Thread : RealmObject {
    @PrimaryKey
    var uid: String = ""
    @SerialName("messages_count")
    var messagesCount: Int = 0
    @SerialName("unique_messages_count")
    var uniqueMessagesCount: Int = 0
    @SerialName("deleted_messages_count")
    var deletedMessagesCount: Int = 0
    var messages: RealmList<Message> = realmListOf()
    @SerialName("unseen_messages")
    var unseenMessagesCount: Int = 0
    var from: RealmList<Recipient> = realmListOf()
    var cc: RealmList<Recipient> = realmListOf()
    var bcc: RealmList<Recipient> = realmListOf()
    var to: RealmList<Recipient> = realmListOf()
    var subject: String? = null
    var date: RealmInstant? = null
    @SerialName("has_attachments")
    var hasAttachments: Boolean = false
    @SerialName("has_st_attachments")
    var hasStAttachments: Boolean = false
    @SerialName("has_drafts")
    var hasDrafts: Boolean = false
    var flagged: Boolean = false
    var answered: Boolean = false
    var forwarded: Boolean = false
    var size: Int = 0

    fun initLocalValues(): Thread {
        messages.removeIf { it.isDuplicate }

        from = from.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects
        cc = cc.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects
        bcc = bcc.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects
        to = to.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects

        messages = messages.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects

        return this
    }

    fun updateAndSelect() {
        MailApi.fetchMessagesFromApi(this)
        select()
    }

    fun select() {
        MailRealm.mutableCurrentThreadUidFlow.value = uid
    }

    fun markAsSeen() {
        MailRealm.currentMailboxObjectIdFlow.value?.let { mailboxObjectId ->
            MailboxInfoController.getMailbox(mailboxObjectId)?.let { mailbox ->
                MailboxContentController.getThread(uid)?.let { coldThread ->
                    MailRealm.mailboxContent.writeBlocking {
                        findLatest(coldThread)?.let { hotThread ->
                            hotThread.apply {
                                messages.forEach { it.seen = true }
                                unseenMessagesCount = 0
                            }
                            ApiRepository.markMessagesAsSeen(mailbox.uuid, ArrayList(messages))
                        }
                    }
                }
            }
        }
    }

    enum class ThreadFilter(title: String) {
        ALL("All"),
        SEEN("Seen"),
        UNSEEN("Unseen"),
        STARRED("Starred"),
        UNSTARRED("Unstarred"),
    }
}
