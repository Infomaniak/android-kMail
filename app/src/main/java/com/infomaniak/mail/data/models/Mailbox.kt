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
package com.infomaniak.mail.data.models

import androidx.core.app.NotificationManagerCompat
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class Mailbox : RealmObject {

    //region API data
    var uuid: String = ""
    var email: String = ""
    @SerialName("email_idn")
    var emailIdn: String = ""
    @SerialName("mailbox")
    var mailboxName: String = ""
    @SerialName("real_mailbox")
    var realMailbox: String = ""
    @SerialName("link_id")
    var linkId: Int = 0
    @SerialName("mailbox_id")
    var mailboxId: Int = -1
    @SerialName("hosting_id")
    var hostingId: Int = 0
    @SerialName("is_primary")
    var isPrimary: Boolean = false
    @SerialName("password_status")
    var passwordStatus: String = ""
    @SerialName("is_password_valid")
    var isPasswordValid: Boolean = false
    @SerialName("is_valid")
    var isMailboxValid: Boolean = false
    @SerialName("is_locked")
    var isLocked: Boolean = false
    @SerialName("has_social_and_commercial_filtering")
    var hasSocialAndCommercialFiltering: Boolean = false
    @SerialName("show_config_modal")
    var showConfigModal: Boolean = false
    @SerialName("force_reset_password")
    var forceResetPassword: Boolean = false
    @SerialName("mda_version")
    var mdaVersion: String = ""
    @SerialName("is_limited")
    var isLimited: Boolean = false
    @SerialName("is_free")
    var isFree: Boolean = false
    @SerialName("daily_limit")
    var dailyLimit: Int = 0
    //endregion

    //region Local data (Transient)
    @Transient
    @PrimaryKey
    var objectId: String = ""
    @Transient
    var userId: Int = -1
    @Transient
    var quotas: Quotas? = null
    @Transient
    var inboxUnreadCount: Int = 0
    //endregion

    inline val channelGroupId get() = "$mailboxId"
    inline val channelId get() = "${mailboxId}_channel_id"
    inline val notificationGroupId get() = uuid.hashCode()
    inline val notificationGroupKey get() = uuid

    fun createObjectId(userId: Int): String {
        return "${userId}_${this.mailboxId}"
    }

    fun initLocalValues(userId: Int, inboxUnreadCount: Int): Mailbox {

        this.objectId = createObjectId(userId)
        this.userId = userId
        this.inboxUnreadCount = inboxUnreadCount

        return this
    }

    fun notificationsIsDisabled(notificationManagerCompat: NotificationManagerCompat): Boolean = with(notificationManagerCompat) {
        val isGroupBlocked = getNotificationChannelGroupCompat(channelGroupId)?.isBlocked == true
        val isChannelBlocked = getNotificationChannelCompat(channelId)?.importance == NotificationManagerCompat.IMPORTANCE_NONE
        isChannelBlocked || isGroupBlocked
    }
}
