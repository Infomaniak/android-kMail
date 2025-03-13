/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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

import com.infomaniak.core.utils.apiEnum
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.mail.data.api.RealmInstantSerializer
import com.infomaniak.mail.data.api.UnwrappingJsonListSerializer
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Bimi
import com.infomaniak.mail.data.models.SnoozeState
import com.infomaniak.mail.data.models.SwissTransferFile
import com.infomaniak.mail.data.models.calendar.CalendarEventResponse
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.getMessages.ActivitiesResult.MessageFlags
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.MessageBodyUtils.SplitBody
import com.infomaniak.mail.utils.extensions.replaceContent
import com.infomaniak.mail.utils.extensions.toRealmInstant
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
    @SerialName("date")
    var originalDate: RealmInstant? = null
        private set
    @SerialName("internal_date")
    var internalDate: RealmInstant = Date().toRealmInstant() // This date is always defined, so the default value is meaningless
        private set
    var subject: String? = null
    var from = realmListOf<Recipient>()
    var to = realmListOf<Recipient>()
    var cc = realmListOf<Recipient>()
    var bcc = realmListOf<Recipient>()
    @SerialName("reply_to")
    var replyTo = realmListOf<Recipient>()
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
    // Boolean used to know if this Message is scheduled to be sent sometime in the future, or not
    @SerialName("is_scheduled_draft")
    var isScheduledDraft: Boolean = false
    var body: Body? = null
    @SerialName("has_attachments")
    var hasAttachments: Boolean = false
    var attachments = realmListOf<Attachment>()
    @SerialName("seen")
    var isSeen: Boolean = false
    @SerialName("forwarded")
    var isForwarded: Boolean = false
    @SerialName("answered")
    var isAnswered: Boolean = false
    @SerialName("flagged")
    var isFavorite: Boolean = false
    // Boolean used to know if this Message is currently being sent, but can
    // still be cancelled during 10~30 sec, depending on user configuration
    @SerialName("scheduled")
    var isScheduledMessage: Boolean = false
    var preview: String = ""
    var size: Int = 0
    @SerialName("has_unsubscribe_link")
    var hasUnsubscribeLink: Boolean? = null
    var bimi: Bimi? = null
    @SerialName("schedule_action")
    var unscheduleDraftUrl: String? = null
    @SerialName("snooze_state")
    private var _snoozeState: String? = null
    @SerialName("snooze_end_date")
    var snoozeEndDate: RealmInstant? = null
    @SerialName("snooze_action")
    var snoozeAction: String? = null

    // TODO: Those are unused for now, but if we ever want to use them, we need to save them in `Message.keepHeavyData()`.
    //  If we don't do it now, we'll probably forget to do it in the future.
    //  When we use them, ce can remove all comments here and in `Message.keepHeavyData()`.
    private var _acknowledge: String = Acknowledge.NONE.name.lowercase()
    @SerialName("drive_url")
    var driveUrl: String = ""
    //endregion

    //region Local data (Transient)

    // ------------- !IMPORTANT! -------------
    // Every field that is added in this Transient region should be declared in
    // `initLocalValue()` too to avoid loosing data when updating from the API.
    // If the Field is a "heavy data" (i.e. an embedded object), it should also be added in 'keepHeavyData()'.

    @Transient
    @PersistedName("isFullyDownloaded")
    var areHeavyDataFetched: Boolean = false
    @Transient
    var isTrashed: Boolean = false
    @Transient
    var messageIds = realmSetOf<String>()
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
    @Transient
    var swissTransferFiles = realmListOf<SwissTransferFile>()
    @Transient
    var hasAttachable: Boolean = false
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
    var splitBody: SplitBody? = null
    @Transient
    @Ignore
    var shouldHideDivider: Boolean = false
    //endregion

    @Ignore
    var snoozeState: SnoozeState? by apiEnum(::_snoozeState)


    /**
     * [displayDate] is different than [internalDate] because it must be used when displaying the date of an email but it can't be used
     * to sort messages chronologically.
     * A message's [originalDate] is not always defined. When this happens, we want to display the [internalDate] in its place.
     */
    val displayDate: RealmInstant get() = originalDate ?: internalDate

    val threads by backlinks(Thread::messages)

    private val threadsDuplicatedIn by backlinks(Thread::duplicates)

    // TODO: Remove this `runCatching / getOrElse` when the issue is fixed
    inline val folder
        get() = runCatching {
            val sameFolderThread = threads.singleOrNull { it.folderId == folderId }
            val searchFolderThread = threads.single { it.folderId == FolderController.SEARCH_FOLDER_ID }
            (sameFolderThread ?: searchFolderThread).folder
        }.getOrElse { exception ->

            val reason = when {
                threads.isEmpty() -> "no parent Threads" // Message has 0 parent threads
                exception is NoSuchElementException -> "no parent Thread from correct Folder" // Message has parent threads, but none from the correct folder
                exception is IllegalArgumentException -> "multiple same parent Threads" // Message has multiple parent Threads from the correct Folder
                else -> "null" // Impossibru
            }

            Sentry.captureMessage(
                "Message doesn't have a parent Thread from its own Folder, it should not be possible",
                SentryLevel.ERROR,
            ) { scope ->
                scope.setTag("issueType", reason)
                scope.setExtra("threadsUid", threads.joinToString { it.uid })
                scope.setExtra("threadsCount", "${threads.count()}")
                scope.setExtra("threadsFolder", "${threads.map { "role:[${it.folder.role?.name}] (id:[${it.folder.id}])" }}")
                scope.setExtra("messageUid", uid)
                scope.setExtra("folderId", folderId)
                scope.setExtra("email", AccountUtils.currentMailboxEmail.toString())
                scope.setExtra("exception", exception.message.toString())
            }

            return@getOrElse (threads.firstOrNull { it.folder.id == folderId } ?: threads.first()).folder
        }

    inline val sender get() = from.firstOrNull()

    val calendarAttachment: Attachment? get() = if (isDraft) null else attachments.firstOrNull(Attachment::isCalendarEvent)

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

    fun keepLocalValues(localMessage: Message) {
        initLocalValues(
            areHeavyDataFetched = localMessage.areHeavyDataFetched,
            isTrashed = localMessage.isTrashed,
            messageIds = localMessage.messageIds,
            draftLocalUuid = localMessage.draftLocalUuid,
            isFromSearch = localMessage.isFromSearch,
            isDeletedOnApi = localMessage.isDeletedOnApi,
            latestCalendarEventResponse = localMessage.latestCalendarEventResponse,
            swissTransferFiles = localMessage.swissTransferFiles,
        )
        keepHeavyData(localMessage)
    }

    fun initLocalValues(
        areHeavyDataFetched: Boolean,
        isTrashed: Boolean,
        messageIds: RealmSet<String>,
        draftLocalUuid: String?,
        isFromSearch: Boolean,
        isDeletedOnApi: Boolean,
        latestCalendarEventResponse: CalendarEventResponse?,
        swissTransferFiles: RealmList<SwissTransferFile>,
    ) {
        this.areHeavyDataFetched = areHeavyDataFetched
        this.isTrashed = isTrashed
        this.messageIds = messageIds
        this.draftLocalUuid = draftLocalUuid
        this.isFromSearch = isFromSearch
        this.isDeletedOnApi = isDeletedOnApi
        this.latestCalendarEventResponse = latestCalendarEventResponse
        this.swissTransferFiles.replaceContent(swissTransferFiles)

        this.shortUid = uid.toShortUid()
        this.hasAttachable = hasAttachments || swissTransferUuid != null
    }

    private fun keepHeavyData(message: Message) {
        attachments.replaceContent(message.attachments.copyFromRealm())
        body = message.body?.copyFromRealm()

        // TODO: Those are unused for now, but if we ever want to use them, we need to save them here.
        //  If we don't do it now, we'll probably forget to do it in the future.
        _acknowledge = message._acknowledge
        driveUrl = message.driveUrl
    }

    // If we are supposed to have Attachable (via `hasAttachments` or `swissTransferUuid`),
    // but the lists are empty, we need to fully download the Message again.
    fun isFullyDownloaded(): Boolean {
        return if (
            (hasAttachments && attachments.isEmpty()) ||
            (swissTransferUuid != null && swissTransferFiles.isEmpty())
        ) {
            false
        } else {
            areHeavyDataFetched
        }
    }

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
        isScheduledMessage = flags.isScheduledMessage
    }

    fun shouldBeExpanded(index: Int, lastIndex: Int) = !isDraft && (!isSeen || index == lastIndex)

    fun toThread() = Thread().apply {
        uid = this@Message.uid
        folderId = this@Message.folderId
        messagesIds += this@Message.messageIds
        messages += this@Message
    }

    fun isOrphan(): Boolean = threads.isEmpty() && threadsDuplicatedIn.isEmpty()

    // Be careful when using this method because some folders might contain messages from other folders than itself. This
    // operation should only happen in very specific situations like computing the shortUid of a Message from its uid.
    private fun String.toShortUid(): Int = substringBefore('@').toInt()

    override fun equals(other: Any?) = other === this || (other is Message && other.uid == uid)

    override fun hashCode(): Int = uid.hashCode()

    companion object
}
