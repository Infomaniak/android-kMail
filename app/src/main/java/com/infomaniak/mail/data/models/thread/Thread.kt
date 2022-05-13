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

import android.util.Log
import com.google.gson.annotations.SerializedName
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.MailRealm
import com.infomaniak.mail.data.cache.MailboxContentController
import com.infomaniak.mail.data.cache.MailboxInfoController
import com.infomaniak.mail.data.models.Recipient
import com.infomaniak.mail.data.models.message.Message
import io.realm.*
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
    var unseenMessagesCount: Int = 0
    var from: RealmList<Recipient> = realmListOf()
    var cc: RealmList<Recipient> = realmListOf()
    var bcc: RealmList<Recipient> = realmListOf()
    var to: RealmList<Recipient> = realmListOf()
    var subject: String? = null
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

        from = from.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects
        cc = cc.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects
        bcc = bcc.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects
        to = to.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects

        messages = messages.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects

        return this
    }

    fun updateAndSelect(isInternetAvailable: Boolean) {
        fetchMessagesFromApi(isInternetAvailable)
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

    private fun fetchMessagesFromApi(isInternetAvailable: Boolean) {
        // Get current data
        Log.d("API", "Messages: Get current data")
        val messagesFromRealm = messages
        val messagesFromApi = messages.mapNotNull {
            ApiRepository.getMessage(it.resource).data?.also { completedMessage ->
                completedMessage.initLocalValues() // TODO: Remove this when we have EmbeddedObjects
                completedMessage.fullyDownloaded = true
                completedMessage.body?.initLocalValues(completedMessage.uid) // TODO: Remove this when we have EmbeddedObjects
                // TODO: Remove this `forEachIndexed` when we have EmbeddedObjects
                @Suppress("SAFE_CALL_WILL_CHANGE_NULLABILITY", "UNNECESSARY_SAFE_CALL")
                completedMessage.attachments?.forEachIndexed { index, attachment ->
                    attachment.initLocalValues(index, completedMessage.uid)
                }
            }
        }

        // Get outdated data
        Log.d("API", "Messages: Get outdated data")
        val deletableMessages = if (isInternetAvailable) {
            messagesFromRealm.filter { fromRealm ->
                !messagesFromApi.any { fromApi -> fromApi.uid == fromRealm.uid }
            }
        } else {
            emptyList()
        }

        // Save new data
        Log.i("API", "Messages: Save new data")
        messagesFromApi.forEach(MailboxContentController::upsertMessage)

        // Delete outdated data
        Log.e("API", "Messages: Delete outdated data")
        deletableMessages.forEach { MailboxContentController.deleteMessage(it.uid) }
    }

    enum class ThreadFilter(title: String) {
        ALL("All"),
        SEEN("Seen"),
        UNSEEN("Unseen"),
        STARRED("Starred"),
        UNSTARRED("Unstarred"),
    }
}
