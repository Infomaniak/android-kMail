/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2026 Infomaniak Network SA
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

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo.State
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.infomaniak.core.common.cancellable
import com.infomaniak.core.legacy.utils.clearStack
import com.infomaniak.core.legacy.utils.hasPermissions
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.LaunchActivity
import com.infomaniak.mail.utils.FetchMessagesManager
import com.infomaniak.mail.utils.IFirebaseProcessNotificationsScheduler
import com.infomaniak.mail.utils.NotificationUtils
import com.infomaniak.mail.utils.NotificationUtils.Companion.GENERIC_NEW_MAILS_NOTIFICATION_ID
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.workers.BaseProcessMessageNotificationsWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process Firebase notifications.
 */
@HiltWorker
class ProcessMessageNotificationsWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val fetchMessagesManager: FetchMessagesManager,
    private val mailboxController: MailboxController,
    private val notificationManagerCompat: NotificationManagerCompat,
    private val notificationUtils: NotificationUtils,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BaseProcessMessageNotificationsWorker(appContext, params) {

    override suspend fun launchWork(): Result = withContext(ioDispatcher) {
        SentryLog.i(TAG, "Work started")

        val userId = inputData.getIntOrNull(USER_ID_KEY) ?: run {
            SentryDebug.sendFailedNotification("No userId in Notification")
            displayGenericNewMailsNotification()
            return@withContext Result.success()
        }
        val mailboxId = inputData.getIntOrNull(MAILBOX_ID_KEY) ?: run {
            SentryDebug.sendFailedNotification("No mailboxId in Notification", userId)
            displayGenericNewMailsNotification()
            return@withContext Result.success()
        }
        val messageUid = inputData.getString(MESSAGE_UID_KEY) ?: run {
            SentryDebug.sendFailedNotification("No messageUid in Notification", userId, mailboxId)
            displayGenericNewMailsNotification()
            return@withContext Result.success()
        }

        // Refresh current User and its Mailboxes
        notificationUtils.updateUserAndMailboxes(mailboxController, TAG)

        val mailbox = mailboxController.getMailbox(userId, mailboxId) ?: run {
            // If the Mailbox doesn't exist in Realm, it's either because :
            // - The Mailbox isn't attached to this User anymore.
            // - The user POSSIBLY recently added this new Mailbox on its account, via the Infomaniak
            //   WebMail or somewhere else. We need to wait until the user opens the app again to
            //   fetch this new Mailbox. Then, we'll be able to handle Notifications for this Mailbox.
            // Either way, we can leave safely.
            return@withContext Result.success()
        }

        val mailboxContentRealm = RealmDatabase.newMailboxContentInstance(userId, mailbox.mailboxId)
        var hasShownNotification = false

        return@withContext runCatching {

            MessageController.getMessage(messageUid, mailboxContentRealm)?.let {
                // If the Message is already in Realm, it means we already fetched it when we received a previous Notification.
                // So we've already shown it in a previous batch of Notifications.
                // We can leave safely.
                hasShownNotification = true
                return@runCatching Result.success()
            }

            hasShownNotification = fetchMessagesManager.execute(scope = this, userId, mailbox, messageUid, mailboxContentRealm)
            SentryLog.i(TAG, "Work finished")
            Result.success()
        }.cancellable().onFailure {
            // This can be a cause that leads to displaying a generic new mail notification
            SentryLog.e(TAG, "Unknown error thrown during notification processing", it)
        }.getOrElse {
            Result.failure()
        }.also {
            if (!hasShownNotification) displayGenericNewMailsNotification()
            mailboxContentRealm.close()
        }
    }

    private fun displayGenericNewMailsNotification() {
        if (appContext.hasPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS))) {

            val openAppIntent = Intent(appContext, LaunchActivity::class.java).clearStack()
            val pendingIntent = PendingIntent.getActivity(
                appContext, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val builder = notificationUtils
                .buildGenericNewMailsNotification(appContext.getString(R.string.notificationNewEmail))
                .setContentIntent(pendingIntent)

            @Suppress("MissingPermission")
            notificationManagerCompat.notify(GENERIC_NEW_MAILS_NOTIFICATION_ID, builder.build())

            // TODO: If the generic new mails notification still pops up too often, maybe
            //  put back this log and the breadcrumbs in `sendFailedNotification()`.
            // Sentry.captureMessage("Send a generic notification", SentryLevel.INFO)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = notificationUtils.buildSyncMessagesServiceNotification()
        return ForegroundInfo(NotificationUtils.SYNC_MESSAGES_ID, notification)
    }

    @Singleton
    class Scheduler @Inject constructor(private val workManager: WorkManager) : IFirebaseProcessNotificationsScheduler {

        fun scheduleWork(userId: Int, mailboxId: Int, messageUid: String) {
            SentryLog.i(TAG, "Work scheduled")

            val workName = workName(userId, mailboxId)
            val workData = workDataOf(USER_ID_KEY to userId, MAILBOX_ID_KEY to mailboxId, MESSAGE_UID_KEY to messageUid)
            val workRequest = OneTimeWorkRequestBuilder<ProcessMessageNotificationsWorker>()
                .addTag(TAG)
                .setInputData(workData)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
        }

        override suspend fun isRunning(): Boolean {
            val workQuery = WorkQuery.Builder
                .fromTags(listOf(TAG))
                .addStates(listOf(State.BLOCKED, State.ENQUEUED, State.RUNNING))
                .build()
            return workManager.getWorkInfosFlow(workQuery).firstOrNull()?.isNotEmpty() == true
        }
    }

    companion object {
        private const val TAG = "ProcessMessageNotificationsWorker"

        private const val USER_ID_KEY = "userIdKey"
        private const val MAILBOX_ID_KEY = "mailboxIdKey"
        private const val MESSAGE_UID_KEY = "messageUidKey"

        private fun workName(userId: Int, mailboxId: Int) = "${userId}_$mailboxId"
    }
}
