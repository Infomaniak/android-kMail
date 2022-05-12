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

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.infomaniak.mail.R

object NotificationUtils {

    private const val DEFAULT_SMALL_ICON = R.drawable.ic_logo_notification

    fun Context.initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelList = ArrayList<NotificationChannel>()

            val generalChannel = createNotificationChannel(
                getString(R.string.notification_channel_id_general),
                getString(R.string.notificationGeneralChannelName),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            channelList.add(generalChannel)

            val notificationManager = getSystemService(Application.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannels(channelList)
        }
    }

    fun Context.showGeneralNotification(
        title: String,
        description: String? = null,
    ): NotificationCompat.Builder {
        val channelId = getString(R.string.notification_channel_id_general)
        return NotificationCompat.Builder(this, channelId).apply {
            setTicker(title)
            setAutoCancel(true)
            setContentTitle(title)
            description?.let { setStyle(NotificationCompat.BigTextStyle().bigText(it)) }
            setSmallIcon(DEFAULT_SMALL_ICON)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun Context.createNotificationChannel(
        channelId: String,
        name: String,
        importance: Int,
    ): NotificationChannel {
        return NotificationChannel(channelId, name, importance).apply {
            when (importance) {
                NotificationManager.IMPORTANCE_HIGH -> {
                    enableLights(true)
                    setShowBadge(true)
                    lightColor = getColor(R.color.primary)
                }
                else -> {
                    enableLights(false)
                    setShowBadge(false)
                    enableVibration(false)
                }
            }
        }
    }
}
