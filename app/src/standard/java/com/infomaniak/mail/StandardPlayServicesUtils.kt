/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging
import com.infomaniak.core.legacy.utils.showToast
import com.infomaniak.mail.utils.PlayServicesUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StandardPlayServicesUtils @Inject constructor(
    private val appContext: Context,
) : PlayServicesUtils {

    override fun checkPlayServices(fragmentActivity: FragmentActivity): Unit = with(GoogleApiAvailability.getInstance()) {
        val errorCode = isGooglePlayServicesAvailable(fragmentActivity)
        when {
            errorCode == ConnectionResult.SUCCESS -> Unit
            isUserResolvableError(errorCode) -> makeGooglePlayServicesAvailable(fragmentActivity)
            else -> fragmentActivity.showToast(R.string.googlePlayServicesAreRequired)
        }
    }

    override fun areGooglePlayServicesAvailable(): Boolean {
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS
    }

    override fun deleteFirebaseToken() {
        FirebaseMessaging.getInstance().deleteToken()
    }
}
