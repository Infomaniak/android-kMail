/*
 * Infomaniak ikMail - Android
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
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.infomaniak.mail.MainApplication
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.PlayServicesUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class KMailFirebaseMessagingService : FirebaseMessagingService() {

    private val mainApplication by lazy { application as MainApplication }
    private val realmMailboxInfo by lazy { RealmDatabase.newMailboxInfoInstance }

    @Inject
    lateinit var localSettings: LocalSettings

    @Inject
    lateinit var notificationManagerCompat: NotificationManagerCompat

    @Inject
    lateinit var playServicesUtils: PlayServicesUtils

    @Inject
    lateinit var processMessageNotificationsScheduler: ProcessMessageNotificationsWorker.Scheduler

    override fun onNewToken(token: String) {
        Log.i(TAG, "onNewToken: new token received")
        localSettings.apply {
            firebaseToken = token
            clearRegisteredFirebaseUsers()
        }
        RegisterUserDeviceWorker.scheduleWork(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.i(TAG, "onMessageReceived: ${message.data}")

        if (AccountUtils.currentUserId == AppSettings.DEFAULT_ID) {
            playServicesUtils.deleteFirebaseToken()
            return
        }

        val userId = message.data["user_id"]?.toInt() ?: return
        val mailboxId = message.data["mailbox_id"]?.toInt() ?: return
        val messageUid = message.data["message_uid"] ?: return

        Log.d(TAG, "onMessageReceived: userId=$userId")
        Log.d(TAG, "onMessageReceived: mailboxId=$mailboxId")
        Log.d(TAG, "onMessageReceived: messageUid=$messageUid")

        if (mainApplication.isAppInBackground) {
            processMessageInBackground(userId, mailboxId, messageUid)
        } else {
            processMessageInForeground(userId, mailboxId)
        }

    }

    private fun processMessageInForeground(userId: Int, mailboxId: Int) {
        Log.i(TAG, "processMessageInForeground: called")
        if (AccountUtils.currentUserId == userId && AccountUtils.currentMailboxId == mailboxId) {
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_MESSAGE_RECEIVED))
        }
    }

    private fun processMessageInBackground(userId: Int, mailboxId: Int, messageUid: String) {
        Log.i(TAG, "processMessageInBackground: called")
        MailboxController.getMailbox(userId, mailboxId, realmMailboxInfo)?.let { mailbox ->
            // Ignore if the Mailbox notification channel is blocked
            if (mailbox.notificationsIsDisabled(notificationManagerCompat)) return
        }

        processMessageNotificationsScheduler.scheduleWork(userId, mailboxId, messageUid)
    }

    override fun onDestroy() {
        realmMailboxInfo.close()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KMailFirebaseMessagingService"
        const val ACTION_MESSAGE_RECEIVED = "action_message_received"
    }
}
