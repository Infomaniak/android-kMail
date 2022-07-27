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

import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.data.api.RealmInstantSerializer
import com.infomaniak.mail.data.api.RealmListSerializer
import com.infomaniak.mail.data.models.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.isToday
import com.infomaniak.mail.utils.toDate
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import java.util.*

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

    /**
     * Local
     */
    @Transient
    var displayedDate: String = ""

    fun initLocalValues(): Thread {
        messages.removeIf { it.isDuplicate }

        from = from.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects
        cc = cc.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects
        bcc = bcc.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects
        to = to.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects

        messages = messages.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects

        // TODO: When do we want to update this value? This is a quick fix. The date will
        // TODO: only update when we get data from the API. We probably don't want that.
        displayedDate = formatDate(date?.toDate() ?: Date(0))

        return this
    }

    private fun formatDate(date: Date): String = with(date) {
        when {
            isToday() -> format(FORMAT_DATE_HOUR_MINUTE)
            year() == Date().year() -> format(FORMAT_DATE_SHORT_DAY_ONE_CHAR)
            else -> format(FORMAT_DATE_CLEAR_MONTH_DAY_ONE_CHAR)
        }
    }

    // enum class ThreadFilter(@IdRes val filterNameRes: Int) {
    enum class ThreadFilter(filterNameRes: String) { // TODO: Put these strings in strings.xml (like the `FolderRole` enum class)
        ALL("All"),
        SEEN("Seen"),
        UNSEEN("Unseen"),
        STARRED("Starred"),
        UNSTARRED("Unstarred"),
    }

    companion object {
        fun from(message: Message, threadUid: String?) = Thread().apply {
            uid = threadUid ?: message.uid
            from = message.from
            to = message.to
            bcc = message.bcc
            cc = message.cc
            hasDrafts = true
            hasAttachments = message.hasAttachments
            messages = realmListOf(message)
            subject = message.subject
            date = message.date
            messagesCount = 1
            size = message.attachments.size
            flagged = message.flagged
            displayedDate = formatDate(message.date?.toDate() ?: Date(0))
        }
    }
}
