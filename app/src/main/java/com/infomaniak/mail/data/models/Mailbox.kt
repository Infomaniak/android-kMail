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
package com.infomaniak.mail.data.models

import com.google.gson.annotations.SerializedName
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.MailRealm
import com.infomaniak.mail.data.models.message.Body
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.AccountUtils
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.query

class Mailbox : RealmObject {
    var uuid: String = ""
    var email: String = ""

    @SerializedName("email_idn")
    var emailIdn: String = ""
    var mailbox: String = ""

    @SerializedName("real_mailbox")
    var realMailbox: String = ""

    @SerializedName("link_id")
    var linkId: Int = 0

    @SerializedName("mailbox_id")
    var mailboxId: Int = -1

    @SerializedName("hosting_id")
    var hostingId: Int = 0

    @SerializedName("is_primary")
    var isPrimary: Boolean = false

    @SerializedName("password_status")
    var passwordStatus: String = ""

    @SerializedName("is_password_valid")
    var isPasswordValid: Boolean = false

    @SerializedName("is_valid")
    var isMailboxValid: Boolean = false

    @SerializedName("is_locked")
    var isLocked: Boolean = false

    @SerializedName("has_social_and_commercial_filtering")
    var hasSocialAndCommercialFiltering: Boolean = false

    @SerializedName("show_config_modal")
    var showConfigModal: Boolean = false

    @SerializedName("force_reset_password")
    var forceResetPassword: Boolean = false

    @SerializedName("mda_version")
    var mdaVersion: String = ""

    @SerializedName("is_limited")
    var isLimited: Boolean = false

    @SerializedName("is_free")
    var isFree: Boolean = false

    @SerializedName("daily_limit")
    var dailyLimit: Int = 0

    /**
     * Local
     */
    var userId: Int = -1

    @PrimaryKey
    var objectId: String = ""

    fun initLocalValues(): Mailbox {
        userId = AccountUtils.currentUserId
        objectId = "${mailboxId}_${AccountUtils.currentUserId}"
        return this
    }

    fun getFolders(): List<Folder> {

        fun deleteFolders() { // TODO: Remove it (blocked by https://github.com/realm/realm-kotlin/issues/805)
            MailRealm.mailboxContent.writeBlocking {
                delete(query<Folder>().find())
                delete(query<Thread>().find())
                delete(query<Message>().find())
                delete(query<Recipient>().find())
                delete(query<Body>().find())
                delete(query<Attachment>().find())
            }
        }

        AccountUtils.currentMailboxId = mailboxId
        MailRealm.currentMailbox = this

        deleteFolders()

        val apiFolders = ApiRepository.getFolders(this).data ?: emptyList()

        MailRealm.mailboxContent.writeBlocking { apiFolders.forEach(::copyToRealm) }

        return apiFolders
    }
}
