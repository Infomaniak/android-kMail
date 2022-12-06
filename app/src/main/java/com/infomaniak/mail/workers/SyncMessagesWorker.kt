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

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavDeepLinkBuilder
import androidx.work.*
import androidx.work.PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.thread.Thread
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
        Log.d(TAG, "SyncMessagesWorker>launchWork: launched")

        AccountUtils.getAllUsersSync().forEach { user ->
            MailboxController.getMailboxes(user.id).forEach loopMailboxes@{ mailbox ->

                val realm = RealmDatabase.newMailboxContentInstance(user.id, mailbox.mailboxId)
                val folder = FolderController.getFolder(FolderRole.INBOX) ?: return@loopMailboxes
                if (folder.cursor == null) return@loopMailboxes

                val okHttpClient = AccountUtils.getHttpClient(user.id)

                val newMessagesThreads =
                    MessageController.fetchCurrentFolderMessages(mailbox, folder.id, threadMode, okHttpClient, realm)

                newMessagesThreads.forEach { thread ->
                    thread.showNotification(folder.id, mailbox, realm)
                }

                realm.close()
            }
        }

        Log.d(TAG, "SyncMessagesWorker>launchWork: finished")

        Result.success()
    }

    private fun Thread.showNotification(folderId: String, mailbox: Mailbox, realm: Realm) {
        MessageController.getLastMessage(uid, folderId, realm)?.let { message ->

            val subject = message.getFormattedSubject(applicationContext)
            val preview = if (message.preview.isEmpty()) "" else "\n${message.preview}"
            val description = subject + preview

            val pendingIntent = NavDeepLinkBuilder(applicationContext)
                .setGraph(R.navigation.main_navigation)
                .setDestination(R.id.threadListFragment) // TODO : navigate to the message
                .createPendingIntent()

            applicationContext.showNewMessageNotification(mailbox.channelId, message.sender.name, description).apply {
                setSubText(mailbox.email)
                setContentText(message.subject)
                setColorized(true)
                setContentIntent(pendingIntent)
                color = localSettings.accentColor.getPrimary(applicationContext)
                notificationManagerCompat.notify(uid.hashCode(), build())
            }
        }
    }

    companion object {
        private const val TAG = "SyncMessagesWorker"

        fun scheduleWork(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<SyncMessagesWorker>(MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                // We start with a delayed duration, so that when the app is rebooted the service is not launched
                .setInitialDelay(2, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.REPLACE, workRequest)
        }

        fun cancelWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(TAG)
        }
    }
}
