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

import com.infomaniak.lib.core.utils.Utils
import com.infomaniak.mail.data.api.RealmInstantSerializer
import com.infomaniak.mail.data.api.RealmListSerializer
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Recipient
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers

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
    var references: String? = null
    var priority: String? = null
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
    var duplicates: RealmList<Duplicate> = realmListOf()
    //endregion

    //region Local data (Transient)
    @Transient
    var draftUuid: String? = null
    @Transient
    var fullyDownloaded: Boolean = false
    @Transient
    var hasUnsubscribeLink: Boolean = false
    @Transient
    var parentLink: Thread? = null // TODO: Use inverse relationship instead (https://github.com/realm/realm-kotlin/issues/591)
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

    val dkimStatus: MessageDKIM get() = Utils.enumValueOfOrNull<MessageDKIM>(_dkimStatus) ?: MessageDKIM.VALID

    enum class MessageDKIM {
        VALID,
        NOT_VALID,
        NOT_SIGNED,
    }
}
