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
package com.infomaniak.mail.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.workers.SyncMessagesWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RebootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        CoroutineScope(Dispatchers.IO).launch {
            val appNotStarted = AccountUtils.currentUser == null
            if (intent?.action == Intent.ACTION_BOOT_COMPLETED && appNotStarted && AccountUtils.getAllUsersCount() > 0) {
                SyncMessagesWorker.scheduleWork(context)
            }
        }
    }
}
