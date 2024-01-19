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

package com.infomaniak.mail.data.models.message

import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.mail.data.api.RealmInstantSerializer
import com.infomaniak.mail.data.api.UnwrappingJsonListSerializer
import com.infomaniak.mail.data.cache.mailboxContent.FolderController.Companion.SEARCH_FOLDER_ID
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.calendar.CalendarEventResponse
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.getMessages.ActivitiesResult.MessageFlags
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.MessageBodyUtils
import com.infomaniak.mail.utils.toRealmInstant
import com.infomaniak.mail.utils.toShortUid
import io.realm.kotlin.ext.*
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.types.*
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PersistedName
import io.realm.kotlin.types.annotations.PrimaryKey
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import java.util.Date

@Serializable
class Message : RealmObject {

    //region Remote data
    @PrimaryKey
    var uid: String = ""
    @SerialName("msg_id")
    var messageId: String? = null
    // This is hardcoded by default to `now`, because the mail protocol allows a date to be null 🤷
    var date: RealmInstant = Date().toRealmInstant()
    var subject: String? = null
    var from: RealmList<Recipient> = realmListOf()
    var to: RealmList<Recipient> = realmListOf()
    var cc: RealmList<Recipient> = realmListOf()
    var bcc: RealmList<Recipient> = realmListOf()
    @SerialName("reply_to")
    var replyTo: RealmList<Recipient> = realmListOf()
    @SerialName("in_reply_to")
    @Serializable(with = UnwrappingJsonListSerializer::class)
    var inReplyTo: String? = null
    var references: String? = null
    @SerialName("dkim_status")
    private var _dkimStatus: String? = null
    @SerialName("folder_id")
    var folderId: String = ""
    @SerialName("st_uuid")
    var swissTransferUuid: String? = null
    var resource: String = ""
    @SerialName("is_draft")
    var isDraft: Boolean = false
    @SerialName("draft_resource")
    var draftResource: String? = null
    var body: Body? = null
    @SerialName("has_attachments")
    var hasAttachments: Boolean = false
    var attachments: RealmList<Attachment> = realmListOf()
    @SerialName("seen")
    var isSeen: Boolean = false
    @SerialName("forwarded")
    var isForwarded: Boolean = false
    @SerialName("answered")
    var isAnswered: Boolean = false
    @SerialName("flagged")
    var isFavorite: Boolean = false
    @SerialName("scheduled")
    var isScheduled: Boolean = false
    var preview: String = ""
    var size: Int = 0
    @SerialName("has_unsubscribe_link")
    var hasUnsubscribeLink: Boolean? = null

    // TODO: Those are unused for now, but if we ever want to use them, we need to save them in `Message.keepHeavyData()`.
    //  If we don't do it now, we'll probably forget to do it in the future.
    //  When we use them, ce can remove all comments here and in `Message.keepHeavyData()`.
    private var _acknowledge: String = Acknowledge.NONE.name.lowercase()
    @SerialName("drive_url")
    var driveUrl: String = ""
    //endregion

    //region Local data (Transient)
    @Transient
    @PersistedName("isFullyDownloaded")
    private var _isFullyDownloaded: Boolean = false
    @Transient
    var isTrashed: Boolean = false
    @Transient
    var messageIds: RealmSet<String> = realmSetOf()
    @Transient
    var draftLocalUuid: String? = null
    @Transient
    var isFromSearch: Boolean = false
    @Transient
    var shortUid: Int = -1
    @Transient
    var isDeletedOnApi: Boolean = false
    @Transient
    var latestCalendarEventResponse: CalendarEventResponse? = null
    //endregion

    //region UI data (Transient & Ignore)
    @Transient
    @Ignore
    var detailsAreExpanded = false
    // Although it might seem more logical to place the `splitBody` within the `body` of
    // the Message, this approach is not feasible. The reason is that we must mark it as
    // `@Ignore` in Realm. Placing it inside the `body` would result in data not updating
    // correctly, as the relationship between Body and Message relies on a Realm query.
    @Transient
    @Ignore
    var splitBody: MessageBodyUtils.SplitBody? = null
    //endregion

    val threads by backlinks(Thread::messages)

    private val threadsDuplicatedIn by backlinks(Thread::duplicates)

    private val _folders by backlinks(Folder::messages)
    val folder
        get() = run {
            runCatching {
                _folders.single { _folders.count() == 1 || it.id != SEARCH_FOLDER_ID }
            }.getOrElse {
                Sentry.withScope { scope ->
                    scope.level = SentryLevel.ERROR
                    scope.setExtra("folders", "${_folders.map { "name:[${it.name}] (id:[${it.id}])" }}")
                    scope.setExtra("foldersCount", "${_folders.count()}")
                    scope.setExtra("messageUid", uid)
                    scope.setExtra("email", AccountUtils.currentMailboxEmail.toString())
                    Sentry.captureMessage("Message has several or 0 parent folders, it should not be possible")
                }
                _folders.first()
            }
        }

    inline val sender
        get() = from.firstOrNull().also {
            if (it == null) SentryLog.e("ThreadAdapter", "Message $uid has empty from")
        }

    val calendarAttachment: Attachment? get() = attachments.firstOrNull(Attachment::isCalendarEvent)

    val dkimStatus: MessageDKIM get() = enumValueOfOrNull<MessageDKIM>(_dkimStatus) ?: MessageDKIM.VALID

    enum class MessageDKIM {
        VALID,
        NOT_VALID,
        NOT_SIGNED,
    }

    var acknowledge: Acknowledge?
        get() = enumValueOfOrNull<Acknowledge>(_acknowledge)
        set(value) {
            value?.name?.lowercase()?.let { _acknowledge = it }
        }

    enum class Acknowledge {
        NONE,
        PENDING,
        ACKNOWLEDGED,
    }

    fun initLocalValues(
        date: RealmInstant,
        isFullyDownloaded: Boolean,
        isTrashed: Boolean,
        isFromSearch: Boolean,
        draftLocalUuid: String?,
        messageIds: RealmSet<String>? = null,
    ) {
        this.date = date
        this._isFullyDownloaded = isFullyDownloaded
        this.isTrashed = isTrashed
        draftLocalUuid?.let { this.draftLocalUuid = it }
        this.isFromSearch = isFromSearch
        this.messageIds = messageIds ?: computeMessageIds()
        shortUid = uid.toShortUid()
    }

    fun keepHeavyData(message: Message) {
        attachments = message.attachments.copyFromRealm().toRealmList()
        body = message.body?.copyFromRealm()

        // TODO: Those are unused for now, but if we ever want to use them, we need to save them here.
        //  If we don't do it now, we'll probably forget to do it in the future.
        _acknowledge = message._acknowledge
        driveUrl = message.driveUrl
    }

    // We had a bug once where we lost the Attachments.
    // So if it happens again, we need to fully download the Message again.
    fun isFullyDownloaded(): Boolean = if (hasAttachments && attachments.isEmpty()) false else _isFullyDownloaded

    private inline fun <reified T : TypedRealmObject> RealmList<T>.detachedFromRealm(depth: UInt = UInt.MIN_VALUE): List<T> {
        return if (isManaged()) copyFromRealm(depth) else this
    }

    fun getRecipientsForReplyTo(replyAll: Boolean = false): Pair<List<Recipient>, List<Recipient>> {

        fun cleanedFrom() = from.detachedFromRealm().filterNot { it.isMe() }

        val cleanedTo = to.detachedFromRealm().filterNot { it.isMe() }
        val cleanedCc = cc.detachedFromRealm().filterNot { it.isMe() }

        var to = replyTo.detachedFromRealm().filterNot { it.isMe() }.ifEmpty { cleanedFrom() }
        var cc = emptyList<Recipient>()

        if (to.isEmpty()) {
            to = cleanedTo
        } else if (replyAll) {
            cc = cleanedTo
        }

        if (to.isEmpty()) {
            to = cleanedCc
        } else if (replyAll) {
            cc = cc + cleanedCc
        }

        if (to.isEmpty()) to = from.detachedFromRealm()

        return to to cc
    }

    fun computeMessageIds(): RealmSet<String> {

        fun String.ifNotBlank(completion: (String) -> Unit) {
            if (isNotBlank()) completion(this)
        }

        // Encountered formats so far:
        // `x@x.x`
        // `<x@x.x>`
        // `<x@x.x><x@x.x><x@x.x>`
        // `<x@x.x> <x@x.x> <x@x.x>`
        // `<x@x.x> <x@x.x> x@x.x`
        fun String.parseMessagesIds(): List<String> = this
            .removePrefix("<")
            .removeSuffix(">")
            .split(">\\s*<|>?\\s+<?".toRegex())

        return realmSetOf<String>().apply {
            messageId?.ifNotBlank { addAll(it.parseMessagesIds()) }
            references?.ifNotBlank { addAll(it.parseMessagesIds()) }
            inReplyTo?.ifNotBlank { addAll(it.parseMessagesIds()) }
        }
    }

    fun updateFlags(flags: MessageFlags) {
        isSeen = flags.isSeen
        isFavorite = flags.isFavorite
        isAnswered = flags.isAnswered
        isForwarded = flags.isForwarded
        isScheduled = flags.isScheduled
    }

    fun shouldBeExpanded(index: Int, lastIndex: Int) = !isDraft && (!isSeen || index == lastIndex)

    fun toThread() = Thread().apply {
        uid = this@Message.uid
        folderId = this@Message.folderId
        messagesIds += this@Message.messageIds
        messages += this@Message
    }

    fun isOrphan(): Boolean = threads.isEmpty() && threadsDuplicatedIn.isEmpty()

    override fun equals(other: Any?) = other === this || (other is Message && other.uid == uid)

    override fun hashCode(): Int = uid.hashCode()
}
