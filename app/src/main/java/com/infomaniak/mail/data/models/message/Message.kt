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

package com.infomaniak.mail.data.models.message

import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.mail.data.api.RealmInstantSerializer
import com.infomaniak.mail.data.api.RealmListSerializer
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Thread
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Priority
import com.infomaniak.mail.data.models.getMessages.GetMessagesUidsDeltaResult.MessageFlags
import com.infomaniak.mail.utils.toRealmInstant
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.*
import io.realm.kotlin.types.*
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import java.util.*

@Serializable
class Message : RealmObject {

    //region API data
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
    var inReplyTo: String? = null
    var references: String? = null
    @SerialName("priority")
    private var _priority: String? = null
    @SerialName("dkim_status")
    private var _dkimStatus: String? = null
    @SerialName("folder_id")
    var folderId: String = ""
    @SerialName("st_uuid")
    var swissTransferUuid: String? = null
    var resource: String = ""
    @SerialName("download_resource")
    var downloadResource: String = ""
    @SerialName("is_draft")
    var isDraft: Boolean = false
    @SerialName("draft_resource")
    var draftResource: String? = null
    var body: Body? = null
    @SerialName("has_attachments")
    var hasAttachments: Boolean = false
    @SerialName("attachments_resources")
    var attachmentsResource: String? = null
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
    @SerialName("safe_display")
    var safeDisplay: Boolean? = null
    @SerialName("has_unsubscribe_link")
    var hasUnsubscribeLink: Boolean? = null
    //endregion

    //region Local data (Transient)
    @Transient
    var isFullyDownloaded: Boolean = false
    @Transient
    var isSpam: Boolean = false
    @Transient
    var messageIds: RealmSet<String> = realmSetOf()
    @Transient
    var draftLocalUuid: String? = null
    //endregion

    //region UI data (Ignore & Transient)
    @Ignore
    @Transient
    var detailsAreExpanded = false
    //endregion

    val threads by backlinks(Thread::messages)

    val threadsDuplicatedIn by backlinks(Thread::duplicates)

    private val _folders by backlinks(Folder::messages)
    val folder get() = _folders.single()

    inline val shortUid get() = uid.split("@").first().toLong()
    inline val sender get() = from.first()

    var priority
        get() = enumValueOfOrNull<Priority>(_priority)
        set(value) {
            _priority = value?.apiCallValue
        }

    val dkimStatus: MessageDKIM get() = enumValueOfOrNull<MessageDKIM>(_dkimStatus) ?: MessageDKIM.VALID

    enum class MessageDKIM {
        VALID,
        NOT_VALID,
        NOT_SIGNED,
    }

    fun initLocalValues(
        isFullyDownloaded: Boolean,
        messageIds: RealmSet<String>,
        isSpam: Boolean,
        date: RealmInstant,
        draftLocalUuid: String?,
    ) {
        this.isFullyDownloaded = isFullyDownloaded
        this.messageIds = messageIds
        this.isSpam = isSpam
        this.date = date
        draftLocalUuid?.let { this.draftLocalUuid = it }
    }

    private inline fun <reified T : TypedRealmObject> RealmList<T>.detachedFromRealm(depth: UInt = UInt.MIN_VALUE): List<T> {
        return if (isManaged()) copyFromRealm(depth) else this
    }

    fun getRecipientForReplyTo(replyAll: Boolean = false): Pair<List<Recipient>, List<Recipient>> {
        val cleanedTo = to.detachedFromRealm().filter { !it.isMe() }
        val cleanedCc = cc.detachedFromRealm().filter { !it.isMe() }

        fun cleanedFrom() = from.detachedFromRealm().filter { !it.isMe() }
        var to = replyTo.detachedFromRealm().filter { !it.isMe() }.ifEmpty { cleanedFrom() }
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

    fun initMessageIds() {

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

        messageIds = realmSetOf<String>().apply {
            messageId?.let { addAll(it.parseMessagesIds()) }
            references?.let { addAll(it.parseMessagesIds()) }
            inReplyTo?.let { addAll(it.parseMessagesIds()) }
        }
    }

    fun updateFlags(flags: MessageFlags) {
        isSeen = flags.isSeen
        isFavorite = flags.isFavorite
        isAnswered = flags.isAnswered
        isForwarded = flags.isForwarded
        isScheduled = flags.isScheduled
    }

    fun isInTrash(realm: TypedRealm) = FolderController.getFolder(FolderRole.TRASH, realm)?.id == folderId

    fun shouldBeExpanded(index: Int, lastIndex: Int) = !isDraft && (!isSeen || index == lastIndex)

    fun toThread() = Thread().apply {
        uid = this@Message.uid
        folderId = this@Message.folderId
    }
}
