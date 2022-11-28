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
package com.infomaniak.mail.utils

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.infomaniak.lib.core.utils.NotificationUtilsCore
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Mailbox

object NotificationUtils : NotificationUtilsCore() {

    private const val DEFAULT_SMALL_ICON = R.drawable.ic_logo_notification

    fun Context.initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelList = mutableListOf<NotificationChannel>()

            val generalChannel = buildNotificationChannel(
                getString(R.string.notification_channel_id_general),
                getString(R.string.notificationGeneralChannelName),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channelList.add(generalChannel)

            val draftChannel = buildNotificationChannel(
                getString(R.string.notification_channel_id_draft_service),
                getString(R.string.notificationSyncDraftChannelName),
                NotificationManager.IMPORTANCE_MIN
            )
            channelList.add(draftChannel)

            val syncMessagesChannel = buildNotificationChannel(
                getString(R.string.notification_channel_id_sync_messages_service),
                getString(R.string.notificationSyncMessagesChannelName),
                NotificationManager.IMPORTANCE_MIN
            )
            channelList.add(syncMessagesChannel)

            createNotificationChannels(channelList)
        }
    }

    fun Context.initMailNotificationChannel(mailbox: List<Mailbox>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelList = mutableListOf<NotificationChannel>()
            val groupList = mutableListOf<NotificationChannelGroup>()

            mailbox.forEach {
                val channelGroup = NotificationChannelGroup(it.mailboxId.toString(), it.email)
                groupList.add(channelGroup)

                val notificationChannel = buildNotificationChannel(
                    it.channelId,
                    getString(R.string.notificationNewMessagesChannelName),
                    NotificationManager.IMPORTANCE_HIGH,
                    groupId = channelGroup.id
                )
                channelList.add(notificationChannel)
            }

            createNotificationChannels(channelList, groupList)
        }
    }

    fun Context.deleteMailNotificationChannel(mailbox: List<Mailbox>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelList = mutableListOf<String>()

            mailbox.forEach { channelList.add(it.channelId) }

            deleteNotificationChannels(channelList)
        }
    }

    fun Context.showGeneralNotification(
        title: String,
        description: String? = null,
    ): NotificationCompat.Builder {
        return buildNotification(
            channelId = getString(R.string.notification_channel_id_general),
            icon = DEFAULT_SMALL_ICON,
            title = title,
            description = description
        )
    }
}
