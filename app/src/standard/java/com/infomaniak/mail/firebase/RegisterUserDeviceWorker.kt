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

import android.content.Context
import android.util.Log
import androidx.work.*
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.throwErrorAsException
import com.infomaniak.mail.workers.BaseCoroutineWorker
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RegisterUserDeviceWorker(appContext: Context, params: WorkerParameters) : BaseCoroutineWorker(appContext, params) {

    private val localSettings by lazy { LocalSettings.getInstance(appContext) }

    override suspend fun launchWork(): Result = withContext(Dispatchers.IO) {

        Log.i(TAG, "Work started")

        val firebaseToken = localSettings.firebaseToken!!

        AccountUtils.getAllUsersSync().forEach { user ->
            if (localSettings.firebaseRegisteredUsers.contains(user.id.toString())) return@forEach
            val okHttpClient = AccountUtils.getHttpClient(user.id)
            val registrationInfos = RegistrationInfos(applicationContext, firebaseToken)
            val apiResponse = FirebaseApiRepository.registerForNotifications(registrationInfos, okHttpClient)

            if (apiResponse.isSuccess()) {
                Log.i(TAG, "launchWork: ${user.id} has been registered")
                localSettings.markUserAsRegisteredByFirebase(user.id)
            } else {
                runCatching {
                    apiResponse.throwErrorAsException()
                }.onFailure { exception ->
                    Sentry.captureException(exception)
                    Log.w(TAG, "launchWork: register ${user.id} failed", exception)
                }
            }
        }

        Log.i(TAG, "Work finished")

        Result.success()
    }

    companion object {
        private const val TAG = "RegisterUserDeviceWorker"

        fun scheduleWork(context: Context) {
            Log.i(TAG, "work scheduled")

            val workRequest = OneTimeWorkRequestBuilder<RegisterUserDeviceWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, workRequest)
        }
    }
}