/*
 * Infomaniak Mail - Android
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
package com.infomaniak.mail.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.stores.StoreUtils
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.PlayServicesUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class AppUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val localSettings: LocalSettings,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BaseCoroutineWorker(appContext, params) {

    override suspend fun launchWork(): Result = withContext(ioDispatcher) {
        SentryLog.i(TAG, "Work started")
        StoreUtils.installDownloadedUpdate { exception ->
            localSettings.apply {
                isUserWantingUpdates = false // This avoid the user being instantly reprompted to download update
                localSettings.hasAppUpdateDownloaded = false
            }
            Sentry.captureException(exception)
        }

        SentryLog.d(TAG, "Work finished")

        Result.success()
    }

    @Singleton
    class Scheduler @Inject constructor(
        private val playServicesUtils: PlayServicesUtils,
        private val workManager: WorkManager,
        private val localSettings: LocalSettings,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {

        suspend fun scheduleWorkIfNeeded() = withContext(ioDispatcher) {
            if (playServicesUtils.areGooglePlayServicesAvailable() && localSettings.hasAppUpdateDownloaded) {
                SentryLog.d(TAG, "Work scheduled")

                val workRequest =
                    OneTimeWorkRequestBuilder<AppUpdateWorker>()
                        .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                        // We start with a delayed duration, so that when the app is rebooted the service is not launched
                        .setInitialDelay(INITIAL_DELAY, TimeUnit.SECONDS)
                        .build()

                workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, workRequest)
            }
        }

        fun cancelWork() {
            SentryLog.d(TAG, "Work cancelled")
            workManager.cancelUniqueWork(TAG)
        }
    }

    companion object {
        private const val TAG = "AppUpdateWorker"
        private const val INITIAL_DELAY = 15L //15s
    }
}
