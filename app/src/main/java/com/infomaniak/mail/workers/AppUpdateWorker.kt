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
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.hilt.work.HiltWorker
import androidx.work.*
import androidx.work.WorkInfo.State
import com.google.common.util.concurrent.ListenableFuture
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.stores.StoreUtils
import com.infomaniak.lib.stores.StoresLocalSettings
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
    private val storesLocalSettings: StoresLocalSettings,
) : ListenableWorker(appContext, params) {

    override fun startWork(): ListenableFuture<Result> {
        SentryLog.i(TAG, "Work started")

        return CallbackToFutureAdapter.getFuture { completer ->

            storesLocalSettings.hasAppUpdateDownloaded = false
            StoreUtils.installDownloadedUpdate(
                onSuccess = { completer.setResult(Result.success()) },
                onFailure = { exception ->
                    Sentry.captureException(exception)
                    storesLocalSettings.resetUpdateSettings()
                    completer.setResult(Result.failure())
                },
            )
        }
    }

    private fun CallbackToFutureAdapter.Completer<Result>.setResult(result: Result) {
        set(result)
        SentryLog.d(TAG, "Work finished")
    }

    @Singleton
    class Scheduler @Inject constructor(
        private val playServicesUtils: PlayServicesUtils,
        private val workManager: WorkManager,
        private val storesLocalSettings: StoresLocalSettings,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {

        suspend fun scheduleWorkIfNeeded() = withContext(ioDispatcher) {
            if (playServicesUtils.areGooglePlayServicesAvailable() && storesLocalSettings.hasAppUpdateDownloaded) {
                SentryLog.d(TAG, "Work scheduled")

                val workRequest = OneTimeWorkRequestBuilder<AppUpdateWorker>()
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    // We start with a delayed duration, so that when the app quickly come back to foreground because the user
                    // was just switching apps, the service is not launched
                    .setInitialDelay(INITIAL_DELAY, TimeUnit.SECONDS)
                    .build()

                workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, workRequest)
            }
        }

        suspend fun cancelWorkIfNeeded() = withContext(ioDispatcher) {

            val workInfo = workManager.getWorkInfos(
                WorkQuery.Builder.fromUniqueWorkNames(listOf(TAG))
                    .addStates(listOf(State.BLOCKED, State.ENQUEUED))
                    .build(),
            ).get()

            workInfo.forEach {
                workManager.cancelWorkById(it.id)
                SentryLog.d(TAG, "Work cancelled")
            }
        }
    }

    companion object {
        private const val TAG = "AppUpdateWorker"
        private const val INITIAL_DELAY = 10L // 10s
    }
}
