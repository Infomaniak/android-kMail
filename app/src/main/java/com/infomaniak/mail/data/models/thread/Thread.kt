/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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

import android.content.Context
import android.os.Build
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.MatomoMail.SEARCH_FOLDER_FILTER_NAME
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.RealmInstantSerializer
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.extensions.isSmallerThanDays
import com.infomaniak.mail.utils.extensions.toDate
import com.infomaniak.mail.utils.extensions.toRealmInstant
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.*
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.annotations.PrimaryKey
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import java.time.format.FormatStyle
import java.util.Date

@Serializable
class Thread : RealmObject {

    //region Remote data
    @PrimaryKey
    var uid: String = ""
    var messages: RealmList<Message> = realmListOf()
    // This is hardcoded by default to `now`, because the mail protocol allows a date to be null 🤷
    var date: RealmInstant = Date().toRealmInstant()
    @SerialName("unseen_messages")
    var unseenMessagesCount: Int = 0
    var from: RealmList<Recipient> = realmListOf()
    var to: RealmList<Recipient> = realmListOf()
    var subject: String? = null
    @SerialName("has_attachments")
    var hasAttachments: Boolean = false
    @SerialName("has_drafts")
    var hasDrafts: Boolean = false
    @SerialName("flagged")
    var isFavorite: Boolean = false
    @SerialName("answered")
    var isAnswered: Boolean = false
    @SerialName("forwarded")
    var isForwarded: Boolean = false
    //endregion

    //region Local data (Transient)
    @Transient
    var folderId: String = ""
    @Transient
    var folderName: String = ""
    @Transient
    var duplicates: RealmList<Message> = realmListOf()
    @Transient
    var messagesIds: RealmSet<String> = realmSetOf()
    @Transient
    var isFromSearch: Boolean = false
    //endregion

    private val _folders by backlinks(Folder::threads)
    val folder
        get() = run {
            runCatching {
                _folders.single()
            }.getOrElse { exception ->
                Sentry.withScope { scope ->
                    scope.setTag("foldersId", _folders.joinToString { it.id })
                    scope.setTag("foldersCount", "${_folders.count()}")
                    scope.setExtra("foldersCount", "${_folders.count()}")
                    scope.setExtra("folders", "${_folders.map { "name:[${it.role?.name}] (id:[${it.id}])" }}")
                    scope.setExtra("threadUid", uid)
                    scope.setExtra("email", AccountUtils.currentMailboxEmail.toString())
                    scope.setExtra("exception message", exception.message.toString())
                    Sentry.captureMessage("Thread has several or 0 parent folders, it should not be possible", SentryLevel.ERROR)
                }
                _folders.first()
            }
        }

    val isOnlyOneDraft get() = messages.count() == 1 && hasDrafts

    fun addMessageWithConditions(newMessage: Message, realm: TypedRealm) {

        val shouldAddMessage = when (FolderController.getFolder(folderId, realm)?.role) {
            FolderRole.DRAFT -> newMessage.isDraft // In Draft folder: only add draft Messages.
            FolderRole.TRASH -> newMessage.isTrashed // In Trash folder: only add deleted Messages.
            else -> !newMessage.isTrashed // In other folders: only add non-deleted Messages.
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
        hasAttachments = false
        hasDrafts = false
        isFavorite = false
        isAnswered = false
        isForwarded = false
    }

    private fun updateThread() {

        messages.sortBy { it.date }

        messages.forEach { message ->
            messagesIds += message.messageIds
            if (!message.isSeen) unseenMessagesCount++
            from += message.from
            to += message.to
            if (message.hasAttachments) hasAttachments = true
            if (message.isDraft) hasDrafts = true
            if (message.isFavorite) isFavorite = true
            if (message.isAnswered) isAnswered = true
            if (message.isForwarded) isForwarded = true
        }

        date = messages.last { it.folderId == folderId }.date
        subject = messages.first().subject
    }

    fun formatDate(context: Context): String = with(date.toDate()) {
        when {
            isInTheFuture() -> formatNumericalDayMonthYear()
            isToday() -> format(FORMAT_DATE_HOUR_MINUTE)
            isYesterday() -> context.getString(R.string.messageDetailsYesterday)
            isSmallerThanDays(6) -> format(FORMAT_DAY_OF_THE_WEEK)
            isThisYear() -> format(FORMAT_DATE_SHORT_DAY_ONE_CHAR)
            else -> formatNumericalDayMonthYear()
        }
    }

    private fun Date.formatNumericalDayMonthYear(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            formatWithLocal(FormatData.DATE, FormatStyle.SHORT)
        } else {
            format(FORMAT_DATE_CLEAR_MONTH_DAY_ONE_CHAR) // Fallback on unambiguous date format for any local
        }
    }

    fun computeAvatarRecipient(): Recipient? = runCatching {

        val message = messages
            .lastOrNull { it.folder.role != FolderRole.SENT && it.folder.role != FolderRole.DRAFT }
            ?: messages.last()

        val recipients = when (message.folder.role) {
            FolderRole.SENT, FolderRole.DRAFT -> message.to
            else -> message.from
        }

        recipients.firstOrNull()

    }.getOrElse { throwable ->
        Sentry.withScope { scope ->
            scope.setExtra("thread.folder.role", "${folder.role?.name}")
            scope.setExtra("thread.folder.id", folder.id)
            scope.setExtra("thread.uid", uid)
            scope.setExtra("thread.messages.count", "${messages.count()}")
            scope.setExtra("thread.duplicates.count", "${duplicates.count()}")
            scope.setExtra("thread.isFromSearch", "$isFromSearch")
            scope.setExtra("thread.hasDrafts", "$hasDrafts")
            Sentry.captureException(throwable)
        }

        null
    }

    fun computeDisplayedRecipients(): RealmList<Recipient> = when (folder.role) {
        FolderRole.SENT, FolderRole.DRAFT -> to
        else -> from
    }

    fun computePreview(): String {
        if (messages.isEmpty()) return ""

        val message = if (folder.role == FolderRole.SENT) {
            messages.lastOrNull { it.folderId == folderId } ?: messages.last()
        } else {
            messages.last()
        }

        return message.preview
    }

    override fun equals(other: Any?) = other === this || (other is Thread && other.uid == uid)

    override fun hashCode(): Int = uid.hashCode()

    enum class ThreadFilter(val matomoValue: String) {
        ALL(SEARCH_FOLDER_FILTER_NAME),
        SEEN("readFilter"),
        UNSEEN("unreadFilter"),
        STARRED("favoriteFilter"),
        ATTACHMENTS("attachmentFilter"),
        FOLDER(SEARCH_FOLDER_FILTER_NAME),
    }

    companion object {
        const val FORMAT_DAY_OF_THE_WEEK = "EEE"
    }
}
