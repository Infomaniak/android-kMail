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
@file:UseSerializers(RealmListKSerializer::class)

package com.infomaniak.mail.data.models.mailbox

import androidx.core.app.NotificationManagerCompat
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.utils.UnreadDisplay
import com.infomaniak.mail.utils.extensions.getDefault
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers

@Serializable
class Mailbox : RealmObject {

    //region Remote data
    var uuid: String = ""
    var email: String = ""
    @SerialName("mailbox")
    var mailboxName: String = ""
    @SerialName("mailbox_id")
    var mailboxId: Int = AppSettings.DEFAULT_ID
    @SerialName("hosting_id")
    var hostingId: Int = 0
    @SerialName("link_id")
    var linkId: Int = 0
    @SerialName("is_limited")
    var isLimited: Boolean = false
    @SerialName("is_password_valid")
    var isPasswordValid: Boolean = true
    @SerialName("is_primary")
    var isPrimary: Boolean = false
    @SerialName("is_locked")
    var isLocked: Boolean = false
    @SerialName("unseen_messages")
    var unreadCountRemote: Int = 0
    var aliases = realmListOf<String>()
    //endregion

    //region Local data (Transient)
    @Transient
    @PrimaryKey
    var objectId: String = ""
    @Transient
    var local = MailboxLocalValues()
    //endregion

    inline val channelGroupId get() = "$mailboxId"
    inline val channelId get() = "${mailboxId}_channel_id"
    inline val notificationGroupId get() = uuid.hashCode()
    inline val notificationGroupKey get() = uuid

    inline val isValid get() = isPasswordValid && !isLocked

    val unreadCountDisplay: UnreadDisplay
        get() = UnreadDisplay(
            count = local.unreadCountLocal,
            shouldDisplayPastille = local.unreadCountLocal == 0 && unreadCountRemote > 0,
        )

    private fun createObjectId(userId: Int): String = "${userId}_${this.mailboxId}"

    fun initLocalValues(userId: Int, localValues: MailboxLocalValues?) {
        objectId = createObjectId(userId)
        localValues?.let { local = it }
    }

    fun getDefaultSignatureWithFallback(draftMode: DraftMode? = null): Signature = with(local) {
        return signatures.getDefault(draftMode) ?: signatures.first()
    }

    fun notificationsIsDisabled(notificationManagerCompat: NotificationManagerCompat): Boolean = with(notificationManagerCompat) {
        val isGroupBlocked = getNotificationChannelGroupCompat(channelGroupId)?.isBlocked == true
        val isChannelBlocked = getNotificationChannelCompat(channelId)?.importance == NotificationManagerCompat.IMPORTANCE_NONE
        return@with !areNotificationsEnabled() || isGroupBlocked || isChannelBlocked
    }
}
