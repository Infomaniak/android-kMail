/*
 * Infomaniak kMail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
package com.infomaniak.mail.firebase

import android.content.Intent
import android.util.Log
import androidx.core.os.bundleOf
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.infomaniak.mail.data.LocalSettings

class KMailFirebaseMessagingService : FirebaseMessagingService() {

    private val localSettings by lazy { LocalSettings.getInstance(this) }

    override fun onNewToken(token: String) {
        Log.e("sisi", "KMailFirebaseMessagingService>onNewToken: $token")
        with(localSettings) {
            firebaseToken = token
            clearRegisteredFirebaseUsers()
        }
        RegisterUserDeviceWorker.scheduleWork(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {

        val title = message.notification?.title
        val body = message.notification?.body

        Log.e("sisi", "KMailFirebaseMessagingService>onMessageReceived: ${message.data}")
        Log.e("sisi", "KMailFirebaseMessagingService>onMessageReceived: ${message.notification?.title}")
        Log.e("sisi", "KMailFirebaseMessagingService>onMessageReceived: ${message.notification?.body}")

        if (message.data.isNotEmpty()) {
            val userId = message.data["user_id"]?.toInt() ?: return
            val mailboxId = message.data["mailbox_id"]?.toInt() ?: return
            val messageUid = message.data["message_uid"] ?: return

            Log.e("sisi", "KMailFirebaseMessagingService>onMessageReceived: userId=$userId")
            Log.e("sisi", "KMailFirebaseMessagingService>onMessageReceived: mailboxId=$mailboxId")
            Log.e("sisi", "KMailFirebaseMessagingService>onMessageReceived: messageUid=$userId")

            val intent = Intent(ACTION_MESSAGE_RECEIVED).apply {
                putExtras(bundleOf()) // TODO: handle this
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            ProcessMessageNotificationsWorker.scheduleWork(this, userId, mailboxId, messageUid)
            // TODO: only show notif when app not visible by user
        }
    }

    companion object {
        private val TAG = KMailFirebaseMessagingService::class.simpleName
        const val ACTION_MESSAGE_RECEIVED = "action_message_received"
    }
}