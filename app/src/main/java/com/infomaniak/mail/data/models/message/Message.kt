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

package com.infomaniak.mail.data.models.message

import android.content.Context
import android.widget.TextView
import androidx.annotation.StyleRes
import androidx.core.content.res.ResourcesCompat
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.RealmInstantSerializer
import com.infomaniak.mail.data.api.RealmListSerializer
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Priority
import com.infomaniak.mail.data.models.getMessages.GetMessagesUidsDeltaResult.MessageFlags
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.kotlin.ext.backlinks
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import com.infomaniak.lib.core.R as RCore

@Serializable
class Message : RealmObject {

    //region API data
    @PrimaryKey
    var uid: String = ""
    @SerialName("msg_id")
    var msgId: String = ""
    var date: RealmInstant? = null
    var subject: String? = null
    var from: RealmList<Recipient> = realmListOf()
    var cc: RealmList<Recipient> = realmListOf()
    var bcc: RealmList<Recipient> = realmListOf()
    var to: RealmList<Recipient> = realmListOf()
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
    var folder: String = ""
    @SerialName("st_uuid")
    var stUuid: String? = null
    var resource: String = ""
    @SerialName("download_resource")
    var downloadResource: String = ""
    @SerialName("is_draft")
    var isDraft: Boolean = false
    @SerialName("draft_resource")
    var draftResource: String = ""
    var body: Body? = null
    @SerialName("has_attachments")
    var hasAttachments: Boolean = false
    @SerialName("attachments_resources")
    var attachmentsResource: String? = null
    var attachments: RealmList<Attachment> = realmListOf()
    var seen: Boolean = false
    var forwarded: Boolean = false
    var answered: Boolean = false
    @SerialName("flagged")
    var isFavorite: Boolean = false
    var scheduled: Boolean = false
    var preview: String = ""
    var size: Int = 0
    @SerialName("safe_display")
    var safeDisplay: Boolean = false
    @SerialName("is_duplicate")
    var isDuplicate: Boolean = false
    //endregion

    //region Local data (Transient)
    @Transient
    var draftLocalUuid: String? = null
    @Transient
    var fullyDownloaded: Boolean = false
    @Transient
    var hasUnsubscribeLink: Boolean = false
    @Transient
    var messageIds: RealmSet<String> = realmSetOf()
    //endregion

    //region UI data (Ignore & Transient)
    @Ignore
    @Transient
    var isExpanded = false
    @Ignore
    @Transient
    var isExpandedHeaderMode = false
    @Ignore
    @Transient
    var detailsAreExpanded = false
    //endregion

    inline val shortUid get() = uid.split("@").first().toLong()
    inline val sender get() = from.first()

    val parentThread by backlinks(Thread::messages)

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

    fun getFormattedSubject(context: Context): String {
        return if (subject.isNullOrBlank()) {
            context.getString(R.string.noSubjectTitle)
        } else {
            subject!!.replace("\n+".toRegex(), " ")
        }
    }

    fun TextView.setFormattedSubject(@StyleRes resId: Int) {
        text = getFormattedSubject(context)
        if (subject.isNullOrBlank()) {
            typeface = ResourcesCompat.getFont(context, RCore.font.suisseintl_regular_italic)
        } else {
            setTextAppearance(resId)
        }
    }

    fun updateFlags(flags: MessageFlags) {
        seen = flags.seen
        isFavorite = flags.isFavorite
        answered = flags.answered
        forwarded = flags.forwarded
        scheduled = flags.scheduled
    }

    fun toThread() = Thread().apply {
        uid = this@Message.uid
    }
}
