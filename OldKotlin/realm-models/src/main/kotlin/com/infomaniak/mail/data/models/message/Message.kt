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

package com.infomaniak.mail.data.models.message

import com.infomaniak.core.common.utils.mailApiEnum
import com.infomaniak.mail.data.api.RealmInstantSerializer
import com.infomaniak.mail.data.api.UnwrappingJsonListSerializer
import com.infomaniak.mail.data.models.AcknowledgeStatus
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Bimi
import com.infomaniak.mail.data.models.InternalModelProperties
import com.infomaniak.mail.data.models.Snoozable
import com.infomaniak.mail.data.models.SnoozeState
import com.infomaniak.mail.data.models.SwissTransferFile
import com.infomaniak.mail.data.models.calendar.CalendarEventResponse
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.internal.enumValueOfOrNull
import com.infomaniak.mail.internal.replaceContent
import com.infomaniak.mail.utils.extensions.toRealmInstant
import io.realm.kotlin.ext.backlinks
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PersistedName
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import java.util.Date

@Serializable
class Message : RealmObject, Snoozable {

    //region Remote data
    @PrimaryKey
    var uid: String = ""
    @SerialName("msg_id")
    var messageId: String? = null
    @SerialName("internal_date")
    var internalDate: RealmInstant = Date().toRealmInstant() // This date is always defined, so the default value is meaningless
        private set

    /**
     * [displayDate] is different than [internalDate] because it must be used when displaying the date of an email but it can't be
     * used to sort messages chronologically.
     */
    @SerialName("date")
    var displayDate: RealmInstant = internalDate
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
    override var snoozeEndDate: RealmInstant? = null
    @SerialName("snooze_uuid")
    override var snoozeUuid: String? = null
    var headers: Headers? = null
    @SerialName("encrypted")
    var isEncrypted: Boolean = false
    @SerialName("crypt_password_validity")
    var encryptionPasswordValidity: RealmInstant? = null
    @SerialName("acknowledge")
    var _acknowledgeStatus: String? = null
    @SerialName("emoji_reaction")
    var emojiReaction: String? = null
    @SerialName("emoji_reaction_not_allowed_reason")
    var _emojiReactionNotAllowedReason: String? = null
        @InternalModelProperties
        set
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
    @Transient
    var emojiReactions: RealmList<EmojiReactionState> = realmListOf()
    //endregion

    //region UI data (Transient & Ignore)
    @Transient
    @Ignore
    var detailsAreExpanded = false
    /**
     * Although it might seem more logical to place the [splitBody] within the [body] of
     * the [Message], this approach is not feasible. The reason is that we must mark it as
     * `@Ignore` in Realm. Placing it inside the [body] would result in data not updating
     * correctly, as the relationship between [body] and [Message] relies on a Realm query.
     */
    @Transient
    @Ignore
    var splitBody: SplitBody? = null
    @Transient
    @Ignore
    var shouldHideDivider: Boolean = false
    //endregion

    @Ignore
    override var snoozeState: SnoozeState? by mailApiEnum(::_snoozeState)

    @Ignore
    var emojiReactionNotAllowedReason: EmojiReactionNotAllowedReason? by mailApiEnum(::_emojiReactionNotAllowedReason)

    @Ignore
    var acknowledgeStatus: AcknowledgeStatus? by mailApiEnum(::_acknowledgeStatus)

    val isValidReactionTarget get() = _emojiReactionNotAllowedReason == null

    val isReaction get() = emojiReaction != null

    val threads by backlinks(Thread::messages)

    val threadsDuplicatedIn by backlinks(Thread::duplicates)

    val allRecipients inline get() = listOf(*to.toTypedArray(), *cc.toTypedArray(), *bcc.toTypedArray())

    inline val sender get() = from.firstOrNull()

    val hasAcknowledge: Boolean get() = acknowledgeStatus != null
    val hasPendingAcknowledgement: Boolean get() = acknowledgeStatus == AcknowledgeStatus.Pending

    val dkimStatus: MessageDKIM get() = enumValueOfOrNull<MessageDKIM>(_dkimStatus) ?: MessageDKIM.VALID

    enum class MessageDKIM {
        VALID,
        NOT_VALID,
        NOT_SIGNED,
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
            emojiReactions = localMessage.emojiReactions,
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
        emojiReactions: RealmList<EmojiReactionState>,
    ) {
        this.areHeavyDataFetched = areHeavyDataFetched
        this.isTrashed = isTrashed
        this.messageIds = messageIds
        this.draftLocalUuid = draftLocalUuid
        this.isFromSearch = isFromSearch
        this.isDeletedOnApi = isDeletedOnApi
        this.latestCalendarEventResponse = latestCalendarEventResponse
        this.swissTransferFiles.replaceContent(swissTransferFiles)
        this.emojiReactions = emojiReactions

        this.shortUid = uid.toShortUid()
        this.hasAttachable = hasAttachments || swissTransferUuid != null
    }

    private fun keepHeavyData(message: Message) {
        attachments.replaceContent(message.attachments.copyFromRealm())
        body = message.body?.copyFromRealm()
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

    fun computeMessageIds(): RealmSet<String> {

        fun String.ifNotBlank(completion: (String) -> Unit) {
            if (isNotBlank()) completion(this)
        }

        return realmSetOf<String>().apply {
            messageId?.ifNotBlank { addAll(it.parseMessagesIds()) }
            references?.ifNotBlank { addAll(it.parseMessagesIds()) }
            inReplyTo?.ifNotBlank { addAll(it.parseMessagesIds()) }
        }
    }

    fun hasUnreadContent() = !isSeen || emojiReactions.any { !it.isSeen }

    fun shouldBeExpanded(index: Int, lastIndex: Int) = (!isDraft || isScheduledMessage) && (hasUnreadContent() || index == lastIndex)

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

    companion object {
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
    }
}
