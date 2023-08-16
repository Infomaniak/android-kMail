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

import androidx.lifecycle.LifecycleOwner
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.firebase.RegisterUserDeviceWorker
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.workers.BaseProcessMessageNotificationsWorker

class StandardMainApplication : MainApplication() {

    override fun onCreate() {
        super.onCreate()
        registerUserDeviceIfNeeded()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        BaseProcessMessageNotificationsWorker.cancelWorks(workManager)
    }

    private fun registerUserDeviceIfNeeded() {
        val areGooglePlayServicesAvailable = !playServicesUtils.areGooglePlayServicesNotAvailable()
        if (AccountUtils.currentUserId != AppSettings.DEFAULT_ID && areGooglePlayServicesAvailable && localSettings.firebaseToken != null) {
            RegisterUserDeviceWorker.scheduleWork(context = this)
        }
    }
}
