/*
 * Infomaniak ikMail - Android
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
package com.infomaniak.mail.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import androidx.work.PeriodicWorkRequest.Companion.MIN_PERIODIC_INTERVAL_MILLIS
import com.infomaniak.mail.PlayServicesUtils
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.FetchMessagesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routinely fetch Messages (when user doesn't have PlayServices).
 */
@HiltWorker
class SyncMailboxesWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val fetchMessagesManager: FetchMessagesManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BaseCoroutineWorker(appContext, params) {

    private val mailboxInfoRealm by lazy { RealmDatabase.newMailboxInfoInstance }

    override suspend fun launchWork(): Result = withContext(ioDispatcher) {
        Log.d(TAG, "Work launched")

        AccountUtils.getAllUsersSync().forEach { user ->
            MailboxController.getMailboxes(user.id, realm = mailboxInfoRealm).forEach { mailbox ->
                fetchMessagesManager.execute(user.id, mailbox)
            }
        }

        Log.d(TAG, "Work finished")

        Result.success()
    }

    override fun onFinish() {
        mailboxInfoRealm.close()
    }

    @Singleton
    class Scheduler @Inject constructor(
        private val playServicesUtils: PlayServicesUtils,
        private val workManager: WorkManager,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {

        suspend fun scheduleWorkIfNeeded() = withContext(ioDispatcher) {

            if (playServicesUtils.areGooglePlayServicesNotAvailable() && AccountUtils.getAllUsersCount() > 0) {
                Log.d(TAG, "Work scheduled")

                val workRequest =
                    PeriodicWorkRequestBuilder<SyncMailboxesWorker>(MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        // We start with a delayed duration, so that when the app is rebooted the service is not launched
                        .setInitialDelay(INITIAL_DELAY, TimeUnit.MINUTES)
                        .build()

                workManager.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, workRequest)
            }
        }

        fun cancelWork() {
            Log.d(TAG, "Work cancelled")
            workManager.cancelUniqueWork(TAG)
        }
    }

    companion object {

        /** To support the old services, we do not change the name */
        private const val TAG = "SyncMessagesWorker"

        private const val INITIAL_DELAY = 2L

    }
}
