/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.mail.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.NotificationUtils.Companion.buildNotification
import com.infomaniak.mail.workers.SyncMailboxesWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SyncMailsService : Service() {

    @Inject
    lateinit var notificationManagerCompat: NotificationManagerCompat

    @Inject
    lateinit var syncMailboxesWorkerScheduler: SyncMailboxesWorker.Scheduler

    private val unlockPhoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            CoroutineScope(Dispatchers.IO).launch {
                val appStarted = AccountUtils.currentUser != null
                if (intent.action.equals(Intent.ACTION_USER_PRESENT) && appStarted) {
                    syncMailboxesWorkerScheduler.scheduleWorkIfNeeded(shouldBeExecuteNow = true)
                }
            }
        }
    }

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            SYNC_NOTIFICATION_ID,
            applicationContext.buildNotification(
                channelId = getString(R.string.notification_channel_id_sync_messages_service),
                icon = R.drawable.ic_logo_notification,
                title = getString(R.string.syncServiceNotifTitle),
                description = getString(R.string.syncServiceNotifDescription),
            ).build(),
            foregroundServiceType,
        )

        registerReceiver(unlockPhoneReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    override fun onDestroy() {
        unregisterReceiver(unlockPhoneReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        private const val SYNC_NOTIFICATION_ID = 1000
    }
}
