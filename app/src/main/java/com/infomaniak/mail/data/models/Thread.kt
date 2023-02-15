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

package com.infomaniak.mail.data.models

import android.content.Context
import androidx.annotation.IdRes
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.RealmInstantSerializer
import com.infomaniak.mail.data.api.RealmListSerializer
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.isSmallerThanDays
import com.infomaniak.mail.utils.toDate
import com.infomaniak.mail.utils.toRealmInstant
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.backlinks
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.ext.toRealmList
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
        messagesIds += newMessage.messageIds

        val folderRole = FolderController.getFolder(folderId, realm)?.role
        val isInTrash = newMessage.isInTrash(realm)

        // If the Message is deleted, but we are not in the Trash: ignore it, just leave.
        if (folderRole != FolderRole.TRASH && isInTrash) return

        val shouldAddMessage = when (folderRole) {
            FolderRole.DRAFT -> newMessage.isDraft // Only add draft Messages in Draft folder
            FolderRole.TRASH -> isInTrash // Only add deleted Messages in Trash folder
            else -> true
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

    fun recomputeThread(realm: MutableRealm) {

        // Delete Thread if empty
        if (messages.none { it.folderId == folderId }) {
            realm.delete(this)
            return
        }

        resetThread()

        updateThread()

        // Remove duplicates in Recipients lists
        from = from.toRecipientsList().distinct().toRealmList()
        to = to.toRecipientsList().distinct().toRealmList()
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

    private fun RealmList<Recipient>.toRecipientsList(): List<Recipient> {
        return map { Recipient().initLocalValues(it.email, it.name) }
    }

    enum class ThreadFilter(@IdRes val filterNameRes: Int) {
        ALL(R.string.searchAllMessages),
        SEEN(R.string.searchFilterRead),
        UNSEEN(R.string.searchFilterUnread),
        STARRED(R.string.favoritesFolder),
        ATTACHMENTS(R.string.searchFilterAttachment),
        FOLDER(R.string.searchFilterFolder),
    }

    @Serializable
    data class ThreadResult(
        val threads: List<Thread>? = null,
        @SerialName("total_messages_count")
        val totalMessagesCount: Int,
        @SerialName("messages_count")
        val messagesCount: Int,
        @SerialName("current_offset")
        val currentOffset: Int,
        @SerialName("thread_mode")
        val threadMode: String,
        @SerialName("folder_unseen_messages")
        val folderUnseenMessages: Int,
        @SerialName("resource_previous")
        val resourcePrevious: String?,
        @SerialName("resource_next")
        val resourceNext: String?,
    )

    companion object {
        const val FORMAT_DAY_OF_THE_WEEK = "EEE"
    }
}
