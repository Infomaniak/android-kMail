/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.RealmInstantSerializer
import com.infomaniak.mail.data.api.RealmListSerializer
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.isSmallerThanDays
import com.infomaniak.mail.utils.toDate
import com.infomaniak.mail.utils.toRealmInstant
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.*
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import java.util.Date

@Serializable
class Thread : RealmObject {

    @PrimaryKey
    var uid: String = ""
    var folderId: String = ""
    var messages: RealmList<Message> = realmListOf()
    var duplicates: RealmList<Message> = realmListOf()
    var messagesIds: RealmSet<String> = realmSetOf()

    var date: RealmInstant = Date().toRealmInstant()
    @SerialName("unseen_messages")
    var unseenMessagesCount: Int = 0
    var from: RealmList<Recipient> = realmListOf()
    var to: RealmList<Recipient> = realmListOf()
    var subject: String? = null
    var size: Int = 0
    @SerialName("has_attachments")
    var hasAttachments: Boolean = false
    var hasDrafts: Boolean = false
    @SerialName("flagged")
    var isFavorite: Boolean = false
    var isAnswered: Boolean = false
    @SerialName("forwarded")
    var isForwarded: Boolean = false
    var isScheduled: Boolean = false

    //region Local data
    @Transient
    var isFromSearch: Boolean = false
    //endregion

    private val _folders by backlinks(Folder::threads)
    val folder get() = _folders.single()

    fun addFirstMessage(message: Message) {
        messagesIds += message.messageIds
        messages.add(message)
    }

    fun addMessageWithConditions(newMessage: Message, realm: TypedRealm) {

        val folderRole = FolderController.getFolder(folderId, realm)?.role
        val isInTrash = newMessage.isInTrash(realm)

        val shouldAddMessage = when (folderRole) {
            FolderRole.DRAFT -> newMessage.isDraft // In Draft folder: only add draft Messages.
            FolderRole.TRASH -> isInTrash // In Trash folder: only add deleted Messages.
            else -> !isInTrash // In other folders: only add non-deleted Messages.
        }

        if (shouldAddMessage) {
            val twinMessage = messages.firstOrNull { it.messageId == newMessage.messageId }
            if (twinMessage == null) {
                messages.add(newMessage)
            } else {
                addDuplicatedMessage(twinMessage, newMessage)
            }
        }
    }

    private fun addDuplicatedMessage(twinMessage: Message, newMessage: Message) {
        val isTwinTheRealMessage = twinMessage.folderId == folderId
        if (isTwinTheRealMessage) {
            duplicates.add(newMessage)
        } else {
            messages.remove(twinMessage)
            duplicates.add(twinMessage)
            messages.add(newMessage)
        }
    }

    fun recomputeThread(realm: MutableRealm? = null) {

        // Delete Thread if empty
        if (messages.none { it.folderId == folderId }) {
            if (isManaged()) realm?.delete(this)
            return
        }

        resetThread()

        updateThread()

        // Remove duplicates in Recipients lists
        val unmanagedFrom = if (from.getRealm<Realm>() == null) from else from.copyFromRealm()
        val unmanagedTo = if (to.getRealm<Realm>() == null) to else to.copyFromRealm()
        from = unmanagedFrom.distinct().toRealmList()
        to = unmanagedTo.distinct().toRealmList()
    }

    private fun resetThread() {
        unseenMessagesCount = 0
        from = realmListOf()
        to = realmListOf()
        size = 0
        hasAttachments = false
        hasDrafts = false
        isFavorite = false
        isAnswered = false
        isForwarded = false
        isScheduled = false
    }

    private fun updateThread() {

        messages.sortBy { it.date }

        messages.forEach { message ->
            messagesIds += message.messageIds
            if (!message.isSeen) unseenMessagesCount++
            from += message.from
            to += message.to
            size += message.size
            if (message.hasAttachments) hasAttachments = true
            if (message.isDraft) hasDrafts = true
            if (message.isFavorite) isFavorite = true
            if (message.isAnswered) isAnswered = true
            if (message.isForwarded) isForwarded = true
            if (message.isScheduled) isScheduled = true
        }

        date = messages.last { it.folderId == folderId }.date
        subject = messages.first().subject
    }

    fun formatDate(context: Context): String = with(date.toDate()) {
        when {
            isToday() -> format(FORMAT_DATE_HOUR_MINUTE)
            isYesterday() -> context.getString(R.string.messageDetailsYesterday)
            isSmallerThanDays(6) -> format(FORMAT_DAY_OF_THE_WEEK)
            isThisYear() -> format(FORMAT_DATE_SHORT_DAY_ONE_CHAR)
            else -> format(FORMAT_DATE_CLEAR_MONTH_DAY_ONE_CHAR)
        }
    }

    fun isOnlyOneDraft(): Boolean = hasDrafts && messages.count() == 1

    fun computeAvatarRecipient(): Recipient {
        val message = messages.lastOrNull { it.folder.role != FolderRole.SENT } ?: messages.last()
        return message.from.first()
    }

    fun computeDisplayedRecipients(): RealmList<Recipient> {
        return if (folder.role == FolderRole.DRAFT) to else from
    }

    fun computePreview(): String {
        val message = if (folder.role == FolderRole.SENT) {
            messages.lastOrNull { it.folderId == folderId } ?: messages.last()
        } else {
            messages.last()
        }

        return message.preview
    }

    enum class ThreadFilter(val matomoValue: String) {
        ALL("folderFilter"),
        SEEN("readFilter"),
        UNSEEN("unreadFilter"),
        STARRED("favoriteFilter"),
        ATTACHMENTS("attachmentFilter"),
        FOLDER("folderFilter"),
    }

    companion object {
        const val FORMAT_DAY_OF_THE_WEEK = "EEE"
    }
}
