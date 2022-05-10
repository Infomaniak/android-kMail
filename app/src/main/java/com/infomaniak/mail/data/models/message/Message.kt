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
package com.infomaniak.mail.data.models.message

import com.google.gson.annotations.SerializedName
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.MailRealm
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Draft
import com.infomaniak.mail.data.models.Recipient
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.*
import io.realm.MutableRealm.UpdatePolicy
import io.realm.annotations.Ignore
import io.realm.annotations.PrimaryKey

class Message : RealmObject {
    @PrimaryKey
    var uid: String = ""

    @SerializedName("msg_id")
    var msgId: String = ""
    var date: RealmInstant? = null
    var subject: String = ""
    var from: RealmList<Recipient> = realmListOf()
    var cc: RealmList<Recipient> = realmListOf()
    var bcc: RealmList<Recipient> = realmListOf()
    var to: RealmList<Recipient> = realmListOf()

    @SerializedName("reply_to")
    var replyTo: RealmList<Recipient> = realmListOf()
    var references: String? = null
    var priority: String? = null

    @SerializedName("dkim_status")
    private var dkimStatus: String? = null

    @SerializedName("folder_id")
    var folderId: String = ""
    var folder: String = ""

    @SerializedName("st_uuid")
    var stUuid: String? = null
    var resource: String = ""

    @SerializedName("download_resource")
    var downloadResource: String = ""

    @SerializedName("is_draft")
    var isDraft: Boolean = false

    @SerializedName("draft_resource")
    var draftResource: String = ""
    var body: Body? = null

    @SerializedName("has_attachments")
    var hasAttachments: Boolean = false

    @SerializedName("attachments_resources")
    var attachmentsResource: String? = null
    var attachments: RealmList<Attachment> = realmListOf()
    var seen: Boolean = false
    var forwarded: Boolean = false
    var answered: Boolean = false
    var flagged: Boolean = false
    var scheduled: Boolean = false
    var size: Int = 0

    @SerializedName("safe_display")
    var safeDisplay: Boolean = false

    @SerializedName("is_duplicate")
    var isDuplicate: Boolean = false

    /**
     * Local
     */
    var fullyDownloaded: Boolean = false
    var hasUnsubscribeLink: Boolean = false
    var parentLink: Thread? = null

    @Ignore
    var isExpandedHeaderMode = false

    fun initLocalValues(): Message {
        from = from.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects
        cc = cc.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects
        bcc = bcc.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects
        to = to.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects
        replyTo = replyTo.map { it.initLocalValues() }.toRealmList() // TODO: Remove this when we have EmbeddedObjects

        return this
    }

    fun select() {
        MailRealm.mutableCurrentMessageUidFlow.value = uid
    }

    fun getDraft(): Draft? {
        val apiDraft = ApiRepository.getDraft(draftResource).data
        apiDraft?.let { draft ->
            draft.apply {
                initLocalValues(uid)
                // TODO: Remove this `forEachIndexed` when we have EmbeddedObjects
                attachments.forEachIndexed { index, attachment -> attachment.initLocalValues(index, uid) }
            }
            MailRealm.mailboxContent.writeBlocking { copyToRealm(draft, UpdatePolicy.ALL) }
        }
        return apiDraft
    }

    fun getDkimStatus(): MessageDKIM? = when (dkimStatus) {
        MessageDKIM.VALID.value -> MessageDKIM.VALID
        MessageDKIM.NOT_VALID.value -> MessageDKIM.NOT_VALID
        MessageDKIM.NOT_SIGNED.value -> MessageDKIM.NOT_SIGNED
        else -> null
    }

    enum class MessageDKIM(val value: String?) {
        VALID(null),
        NOT_VALID("not_valid"),
        NOT_SIGNED("not_signed"),
    }
}
