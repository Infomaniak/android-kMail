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
package com.infomaniak.mail.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest.Companion.MIN_PERIODIC_INTERVAL_MILLIS
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.firebase.ProcessMessageNotificationsWorker
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.FetchMessagesManager
import com.infomaniak.mail.utils.NotificationUtils
import com.infomaniak.mail.utils.PlayServicesUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When the user doesn't have PlayServices, we can't receive push notifications via `ProcessMessageNotificationsWorker`.
 * So we have to routinely fetch Messages (every ~15 min).
 *
 * This Worker is also used to fetch Messages every few hours (~4h),
 * even if the user has PlayServices, to be sure that we are up-to-date.
 */
@HiltWorker
class SyncMailboxesWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val fetchMessagesManager: FetchMessagesManager,
    private val mailboxController: MailboxController,
    private val notificationUtils: NotificationUtils,
    private val processMessageNotificationsWorkerScheduler: ProcessMessageNotificationsWorker.Scheduler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BaseCoroutineWorker(appContext, params) {

    override suspend fun launchWork(): Result = withContext(ioDispatcher) {
        SentryLog.i(TAG, "Work launched")

        if (shouldSkip()) return@withContext Result.success()

        // Refresh current User and its Mailboxes
        notificationUtils.updateUserAndMailboxes(mailboxController, TAG)

        AccountUtils.getAllUsersSync().forEach { user ->
            mailboxController.getMailboxes(user.id).forEach { mailbox ->
                if (shouldSkip()) return@withContext Result.success()
                fetchMessagesManager.execute(scope = this, user.id, mailbox)
            }
        }

        SentryLog.i(TAG, "Work finished")

        Result.success()
    }

    private suspend fun shouldSkip(): Boolean {
        return processMessageNotificationsWorkerScheduler.isRunning().also {
            if (it) SentryLog.i(TAG, "Work skipped because ProcessMessageNotificationsWorker is running")
        }
    }

    @Singleton
    class Scheduler @Inject constructor(
        private val playServicesUtils: PlayServicesUtils,
        private val workManager: WorkManager,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {

        suspend fun scheduleWorkIfNeeded() = withContext(ioDispatcher) {

            if (AccountUtils.getAllUsersCount() > 0) {
                SentryLog.d(TAG, "Work scheduled")

                val workRequest =
                    PeriodicWorkRequestBuilder<SyncMailboxesWorker>(getPeriodicInterval(), TimeUnit.MILLISECONDS)
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        // We start with a delayed duration, so that when the app is rebooted the service is not launched
                        .setInitialDelay(INITIAL_DELAY, TimeUnit.MINUTES)
                        .build()

                workManager.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, workRequest)
            }
        }

        fun cancelWork() {
            SentryLog.d(TAG, "Work cancelled")
            workManager.cancelUniqueWork(TAG)
        }

        private fun getPeriodicInterval(): Long {
            val hasGooglePlayServices = playServicesUtils.areGooglePlayServicesAvailable()
            return if (hasGooglePlayServices) PERIODIC_INTERVAL_MILLIS else MIN_PERIODIC_INTERVAL_MILLIS
        }
    }

    companion object {
        private const val TAG = "SyncMessagesWorker" // To support the old services, don't change this name
        private const val INITIAL_DELAY = 2L
        private const val PERIODIC_INTERVAL_MILLIS = 4 * 60 * 60 * 1_000L // 4 hours
    }
}
