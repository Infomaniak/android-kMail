/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
package com.infomaniak.mail.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.workers.SyncMailboxesWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RebootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var syncMailboxesWorkerScheduler: SyncMailboxesWorker.Scheduler

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onReceive(context: Context, intent: Intent?) {
        CoroutineScope(ioDispatcher).launch {
            val appNotStarted = AccountUtils.currentUser == null
            if (intent?.action == Intent.ACTION_BOOT_COMPLETED && appNotStarted) {
                syncMailboxesWorkerScheduler.scheduleWorkIfNeeded()
            }
        }
    }
}
