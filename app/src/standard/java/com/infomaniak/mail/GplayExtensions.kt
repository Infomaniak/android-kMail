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
package com.infomaniak.mail

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.firebase.ProcessMessageNotificationsWorker
import com.infomaniak.mail.firebase.RegisterUserDeviceWorker
import com.infomaniak.mail.utils.AccountUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun FragmentActivity.checkPlayServices() {
    val apiAvailability = GoogleApiAvailability.getInstance()
    val errorCode = apiAvailability.isGooglePlayServicesAvailable(this)

    if (errorCode == ConnectionResult.SUCCESS) {
        checkFirebaseRegistration()
    } else {
        if (apiAvailability.isUserResolvableError(errorCode)) {
            apiAvailability.makeGooglePlayServicesAvailable(this)
        } else {
            Toast.makeText(this, R.string.googlePlayServicesAreRequired, Toast.LENGTH_SHORT).show()
        }
    }
}

fun Context.isGooglePlayServicesNotAvailable(): Boolean = with(GoogleApiAvailability.getInstance()) {
    return isGooglePlayServicesAvailable(this@isGooglePlayServicesNotAvailable) != ConnectionResult.SUCCESS
}

fun Context.cancelFirebaseProcessWorks() {
    ProcessMessageNotificationsWorker.cancelWorks(this)
}

private fun FragmentActivity.checkFirebaseRegistration() {
    lifecycleScope.launchWhenCreated {
        checkFirebaseRegistration(this@checkFirebaseRegistration)
    }
}

private suspend fun checkFirebaseRegistration(context: Context) = withContext(Dispatchers.IO) {

    val localSettings = LocalSettings.getInstance(context)
    val registeredUsersIds = localSettings.firebaseRegisteredUsers.map { it.toInt() }.toSet()
    val noNeedUsersRegistration = AccountUtils.getAllUsersSync().map { it.id }.minus(registeredUsersIds).isEmpty()

    Log.d("firebase", "checkFirebaseRegistration: (skip users registration): $noNeedUsersRegistration")
    if (noNeedUsersRegistration) return@withContext

    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        if (!task.isSuccessful) {
            Log.w("firebase", "Fetching FCM registration token failed", task.exception)
            return@addOnCompleteListener
        }

        val token = task.result
        Log.d("firebase", "checkFirebaseRegistration: token is different ${token != localSettings.firebaseToken}")
        if (token != localSettings.firebaseToken) localSettings.clearRegisteredFirebaseUsers()

        localSettings.firebaseToken = token
        RegisterUserDeviceWorker.scheduleWork(context)
    }
}
