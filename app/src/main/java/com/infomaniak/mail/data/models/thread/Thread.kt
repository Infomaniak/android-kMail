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

import android.content.Context
import androidx.annotation.IdRes
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.RealmInstantSerializer
import com.infomaniak.mail.data.api.RealmListSerializer
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.isSmallerThanDays
import com.infomaniak.mail.utils.toDate
import io.realm.kotlin.ext.realmListOf
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

    //region API data
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
    @SerialName("date")
    private var _date: RealmInstant = RealmInstant.MAX
    @SerialName("has_attachments")
    var hasAttachments: Boolean = false
    @SerialName("has_st_attachments")
    var hasStAttachments: Boolean = false
    @SerialName("has_drafts")
    var hasDrafts: Boolean = false
    @SerialName("flagged")
    var isFavorite: Boolean = false
    var answered: Boolean = false
    var forwarded: Boolean = false
    var size: Int = 0
    //endregion

    //region Local data (Transient)
    @Transient
    var mailboxUuid: String = ""
    //endregion

    val date: Date get() = _date.toDate()

    fun initLocalValues(mailboxUuid: String): Thread {
        this.messages.removeIf { it.isDuplicate }
        this.mailboxUuid = mailboxUuid

        return this
    }

    fun formatDate(context: Context): String = with(date) {
        when {
            isToday() -> format(FORMAT_DATE_HOUR_MINUTE)
            isYesterday() -> context.getString(R.string.messageDetailsYesterday)
            isSmallerThanDays(6) -> format(FORMAT_DAY_OF_THE_WEEK)
            isThisYear() -> format(FORMAT_DATE_SHORT_DAY_ONE_CHAR)
            else -> format(FORMAT_DATE_CLEAR_MONTH_DAY_ONE_CHAR)
        }
    }

    fun isOnlyOneDraft(): Boolean = messages.count() == 1 && messages.first().isDraft

    enum class ThreadFilter(@IdRes val filterNameRes: Int) {
        ALL(R.string.searchAllMessages),
        SEEN(R.string.searchFilterRead),
        UNSEEN(R.string.searchFilterUnread),
        STARRED(R.string.favoritesFolder),
        ATTACHMENTS(R.string.searchFilterAttachment),
        FOLDER(R.string.searchFilterFolder),
    }

    companion object {
        const val FORMAT_DAY_OF_THE_WEEK = "EEE"
    }
}
