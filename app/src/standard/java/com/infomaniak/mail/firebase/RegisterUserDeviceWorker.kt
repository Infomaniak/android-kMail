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
package com.infomaniak.mail.firebase

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.throwErrorAsException
import com.infomaniak.mail.workers.BaseCoroutineWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class RegisterUserDeviceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val localSettings: LocalSettings,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BaseCoroutineWorker(appContext, params) {

    override suspend fun launchWork(): Result = withContext(ioDispatcher) {

        SentryLog.i(TAG, "Work started")

        val firebaseToken = localSettings.firebaseToken!!

        AccountUtils.getAllUsersSync().forEach { user ->
            val okHttpClient = AccountUtils.getHttpClient(user.id)
            val registrationInfo = RegistrationInfo(applicationContext, firebaseToken)
            val apiResponse = FirebaseApiRepository.registerForNotifications(registrationInfo, okHttpClient)

            if (apiResponse.isSuccess()) {
                SentryLog.i(TAG, "launchWork: ${user.id} has been registered")
                localSettings.markUserAsRegisteredByFirebase(user.id)
            } else {
                runCatching {
                    apiResponse.throwErrorAsException()
                }.onFailure { exception ->
                    if (exception !is ApiController.NetworkException) Sentry.captureException(exception)
                    SentryLog.w(TAG, "launchWork: register ${user.id} failed", exception)
                    return@withContext Result.retry()
                }
            }
        }

        SentryLog.i(TAG, "Work finished")

        Result.success()
    }

    @Singleton
    class Scheduler @Inject constructor(private val workManager: WorkManager) {

        /**
         * Schedule RegisterUserDeviceWorker
         *
         * To avoid problems with the refreshToken at login time, we delay the register by [INITIAL_DELAY].
         * For future launches, we'll reset the register to the same duration, as this method is also used in the [com.infomaniak.mail.MainApplication].
         * so when the app is relaunched several times, only the last relaunch is taken into account.
         */
        fun scheduleWork() {
            SentryLog.i(TAG, "Work scheduled")

            val workRequest = OneTimeWorkRequestBuilder<RegisterUserDeviceWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(INITIAL_DELAY, TimeUnit.SECONDS)
                .build()

            workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, workRequest)
        }
    }

    companion object {
        private const val TAG = "RegisterUserDeviceWorker"
        private const val INITIAL_DELAY = 15L // 15s
    }
}
