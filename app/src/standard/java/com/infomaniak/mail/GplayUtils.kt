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
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withCreated
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging
import com.infomaniak.lib.core.utils.showToast
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.firebase.RegisterUserDeviceWorker
import com.infomaniak.mail.utils.AccountUtils
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.launch

object GplayUtils {

    fun FragmentActivity.checkPlayServices(): Unit = with(GoogleApiAvailability.getInstance()) {
        val errorCode = isGooglePlayServicesAvailable(this@checkPlayServices)
        when {
            errorCode == ConnectionResult.SUCCESS -> checkFirebaseRegistration()
            isUserResolvableError(errorCode) -> makeGooglePlayServicesAvailable(this@checkPlayServices)
            else -> showToast(R.string.googlePlayServicesAreRequired)
        }
    }

    fun Context.areGooglePlayServicesNotAvailable(): Boolean {
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS
    }

    fun deleteFirebaseToken() = FirebaseMessaging.getInstance().deleteToken()

    private fun FragmentActivity.checkFirebaseRegistration() {
        lifecycleScope.launch {
            lifecycle.withCreated { checkFirebaseRegistration(this@checkFirebaseRegistration) }
        }
    }

    private fun checkFirebaseRegistration(context: Context) {

        val localSettings = LocalSettings.getInstance(context)
        val registeredUsersIds = localSettings.firebaseRegisteredUsers.map { it.toInt() }.toSet()
        val noNeedUsersRegistration = AccountUtils.getAllUsersSync().map { it.id }.minus(registeredUsersIds).isEmpty()

        Log.d("firebase", "checkFirebaseRegistration: (skip users registration): $noNeedUsersRegistration")
        if (noNeedUsersRegistration) return

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Sentry.withScope { scope ->
                    scope.level = SentryLevel.ERROR
                    scope.setExtra("task.exception", task.exception.toString())
                    Sentry.captureMessage("Fetching FCM registration token failed")
                }
                Log.w("firebase", "Fetching FCM registration token failed", task.exception)
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
            Log.d("firebase", "checkFirebaseRegistration: token is different ${token != localSettings.firebaseToken}")
            if (token != localSettings.firebaseToken) localSettings.clearRegisteredFirebaseUsers()

            localSettings.firebaseToken = token
            RegisterUserDeviceWorker.scheduleWork(context)
        }
    }
}
