/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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
@file:UseSerializers(RealmListKSerializer::class, RealmInstantSerializer::class)

package com.infomaniak.mail.data.models.thread

import com.infomaniak.core.common.utils.mailApiEnum
import com.infomaniak.mail.data.api.RealmInstantSerializer
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Snoozable
import com.infomaniak.mail.data.models.SnoozeState
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.extensions.toRealmInstant
import io.realm.kotlin.ext.backlinks
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import java.util.Date

@Serializable
class Thread : RealmObject, Snoozable {

    //region Remote data
    @PrimaryKey
    var uid: String = ""
    var messages = realmListOf<Message>()
    // This value should always be provided because messages always have at least an internalDate. Because of this, the initial value is meaningless
    @SerialName("internal_date")
    var internalDate: RealmInstant = Date().toRealmInstant()
    @SerialName("date")
    var displayDate: RealmInstant = internalDate
    @SerialName("unseen_messages")
    var unseenMessagesCount: Int = 0
    var from = realmListOf<Recipient>()
    var to = realmListOf<Recipient>()
    var subject: String? = null
    @SerialName("has_drafts")
    var hasDrafts: Boolean = false
    @SerialName("flagged")
    var isFavorite: Boolean = false
    var isReminder: Boolean = true // TODO: This is currently always true because the API doesn't return this information for threads
    @SerialName("answered")
    var isAnswered: Boolean = false
    @SerialName("forwarded")
    var isForwarded: Boolean = false
    @SerialName("snooze_state")
    private var _snoozeState: String? = null
    @SerialName("snooze_end_date")
    override var snoozeEndDate: RealmInstant? = null
    @SerialName("snooze_uuid")
    override var snoozeUuid: String? = null
    //endregion

    //region Local data (Transient)
    @Transient
    var folderId: String = ""
    @Transient
    var folderName: String = ""
    @Transient
    var duplicates = realmListOf<Message>()
    @Transient
    var messagesIds = realmSetOf<String>()
    @Transient
    var isFromSearch: Boolean = false
    @Transient
    var hasAttachable: Boolean = false
    // Has been moved (archived, spammed, deleted, moved) but API call hasn't been done yet.
    // It's only used to locally filter the Threads' list.
    @Transient
    var isLocallyMovedOut: Boolean = false
    // When deserializing threads from the api, this way of initializing the value will compute the correct
    // numberOfScheduledDrafts right after deserialization
    @Transient
    var numberOfScheduledDrafts: Int = messages.count { it.isScheduledDraft }
    @Transient
    var isLastInboxMessageSnoozed: Boolean = false

    /**
     * The list messages where messages that are emoji reactions have been filtered out
     */
    @Transient
    var messagesWithContent = realmListOf<Message>()
    //endregion

    val isSeen get() = unseenMessagesCount == 0

    @Ignore
    override var snoozeState: SnoozeState? by mailApiEnum(::_snoozeState)

    // TODO: Put this back in `private` when the Threads parental issues are fixed
    val _folders by backlinks(Folder::threads)

    val isOnlyOneDraft get() = messages.count() == 1 && hasDrafts

    /**
     * Only used for when the api tells us we're trying to automatically unsnooze a thread that's not snoozed
     */
    override fun manuallyUnsnooze() {
        super.manuallyUnsnooze()
        messages.forEach(Message::manuallyUnsnooze)
        duplicates.forEach(Message::manuallyUnsnooze)
    }

    override fun equals(other: Any?) = other === this || (other is Thread && other.uid == uid)

    override fun hashCode(): Int = uid.hashCode()

    companion object {
        const val FORMAT_DAY_OF_THE_WEEK = "EEE"
    }
}
