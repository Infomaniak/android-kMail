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

import androidx.lifecycle.LifecycleOwner
import com.infomaniak.core.notifications.registration.NotificationsRegistrationManager
import com.infomaniak.mail.di.StandardEntryPoint
import com.infomaniak.mail.firebase.RegisterUserDeviceWorker
import com.infomaniak.mail.firebase.notificationTopicsForUser
import com.infomaniak.mail.workers.BaseProcessMessageNotificationsWorker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

class StandardMainApplication : MainApplication() {

    private val hiltEntryPoint by lazy { EntryPointAccessors.fromApplication(this, StandardEntryPoint::class.java) }

    override fun onCreate() {
        super.onCreate()
        registerUserDeviceIfNeeded()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        BaseProcessMessageNotificationsWorker.cancelWorks(hiltEntryPoint.workManager())
    }

    private fun registerUserDeviceIfNeeded() {
        applicationScope.launch {
            NotificationsRegistrationManager.sharedInstance.scheduleWorkerOnUpdate<RegisterUserDeviceWorker>(
                latestNotificationTopics = { notificationTopicsForUser(mailboxController, it) }
            )
        }
    }
}
