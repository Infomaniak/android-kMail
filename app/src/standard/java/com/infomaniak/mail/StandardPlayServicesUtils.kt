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
package com.infomaniak.mail

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.showToast
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.firebase.RegisterUserDeviceWorker
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.PlayServicesUtils
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StandardPlayServicesUtils @Inject constructor(
    private val appContext: Context,
    private val localSettings: LocalSettings,
    private val registerUserDeviceWorkerScheduler: RegisterUserDeviceWorker.Scheduler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlayServicesUtils {

    override fun checkPlayServices(fragmentActivity: FragmentActivity): Unit = with(GoogleApiAvailability.getInstance()) {
        val errorCode = isGooglePlayServicesAvailable(fragmentActivity)
        when {
            errorCode == ConnectionResult.SUCCESS -> fragmentActivity.checkFirebaseRegistration()
            isUserResolvableError(errorCode) -> makeGooglePlayServicesAvailable(fragmentActivity)
            else -> fragmentActivity.showToast(R.string.googlePlayServicesAreRequired)
        }
    }

    override fun areGooglePlayServicesNotAvailable(): Boolean {
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext) != ConnectionResult.SUCCESS
    }

    override fun deleteFirebaseToken() {
        FirebaseMessaging.getInstance().deleteToken()
    }

    private fun FragmentActivity.checkFirebaseRegistration() = lifecycleScope.launch(ioDispatcher) {

        val registeredUsersIds = localSettings.firebaseRegisteredUsers.map { it.toInt() }.toSet()
        val noNeedUsersRegistration = AccountUtils.getAllUsersSync().map { it.id }.minus(registeredUsersIds).isEmpty()

        SentryLog.d("firebase", "checkFirebaseRegistration: (skip users registration): $noNeedUsersRegistration")
        if (noNeedUsersRegistration) return@launch

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Sentry.withScope { scope ->
                    scope.level = SentryLevel.ERROR
                    scope.setExtra("task.exception", task.exception.toString())
                    Sentry.captureMessage("Fetching FCM registration token failed")
                }
                SentryLog.w("firebase", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            if (task.result == null) {
                Sentry.withScope { scope ->
                    scope.level = SentryLevel.ERROR
                    Sentry.captureMessage("FirebaseMessaging token is null")
                }
                return@addOnCompleteListener
            }

            val token = task.result
            SentryLog.d("firebase", "checkFirebaseRegistration: token is different ${token != localSettings.firebaseToken}")
            if (token != localSettings.firebaseToken) localSettings.clearRegisteredFirebaseUsers()

            localSettings.firebaseToken = token
            registerUserDeviceWorkerScheduler.scheduleWork()
        }
    }
}
