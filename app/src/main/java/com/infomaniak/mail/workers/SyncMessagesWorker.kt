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
package com.infomaniak.mail.workers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import androidx.work.PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS
import com.infomaniak.lib.core.utils.NotificationUtilsCore.Companion.pendingIntentFlags
import com.infomaniak.lib.core.utils.clearStack
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.ui.LaunchActivity
import com.infomaniak.mail.ui.LaunchActivityArgs
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.NotificationUtils.showNewMessageNotification
import io.realm.kotlin.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SyncMessagesWorker(appContext: Context, params: WorkerParameters) : BaseCoroutineWorker(appContext, params) {

    private val localSettings by lazy { LocalSettings.getInstance(applicationContext) }
    private val threadMode by lazy { localSettings.threadMode }

    private val notificationManagerCompat by lazy { NotificationManagerCompat.from(applicationContext) }

    override suspend fun launchWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Work launched")

        AccountUtils.getAllUsersSync().forEach { user ->
            MailboxController.getMailboxes(user.id).forEach loopMailboxes@{ mailbox ->

                val realm = RealmDatabase.newMailboxContentInstance(user.id, mailbox.mailboxId)
                val folder = FolderController.getFolder(FolderRole.INBOX) ?: return@loopMailboxes
                if (folder.cursor == null) return@loopMailboxes

                val okHttpClient = AccountUtils.getHttpClient(user.id)

                val newMessagesThreads =
                    MessageController.fetchCurrentFolderMessages(mailbox, folder.id, threadMode, okHttpClient, realm)

                newMessagesThreads.forEach { thread ->
                    thread.showNotification(folder.id, user.id, mailbox, realm)
                }

                realm.close()
            }
        }

        Log.d(TAG, "Work finished")

        Result.success()
    }

    private fun Thread.showNotification(folderId: String, userId: Int, mailbox: Mailbox, realm: Realm) {
        MessageController.getLastMessage(uid, folderId, realm)?.let { message ->
            if (message.seen) return // Ignore if it has already been seen

            val subject = message.getFormattedSubject(applicationContext)
            val preview = if (message.preview.isEmpty()) "" else "\n${message.preview}"
            val description = "$subject$preview"

            val intent = Intent(applicationContext, LaunchActivity::class.java).clearStack().apply {
                putExtras(LaunchActivityArgs(uid, userId, mailbox.mailboxId).toBundle())
            }
            val contentIntent = PendingIntent.getActivity(applicationContext, 0, intent, pendingIntentFlags)

            applicationContext.showNewMessageNotification(mailbox.channelId, message.sender.name, description).apply {
                setSubText(mailbox.email)
                setContentText(message.subject)
                setColorized(true)
                setContentIntent(contentIntent)
                color = localSettings.accentColor.getPrimary(applicationContext)
                notificationManagerCompat.notify(uid.hashCode(), build())
            }
        }
    }

    companion object {
        private const val TAG = "SyncMessagesWorker"

        fun scheduleWork(context: Context) {
            Log.d(TAG, "Work scheduled")

            val workRequest = PeriodicWorkRequestBuilder<SyncMessagesWorker>(MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                // We start with a delayed duration, so that when the app is rebooted the service is not launched
                .setInitialDelay(2, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.REPLACE, workRequest)
        }

        fun cancelWork(context: Context) {
            Log.d(TAG, "Work cancelled")
            WorkManager.getInstance(context).cancelUniqueWork(TAG)
        }
    }
}
