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
package com.infomaniak.mail.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationActionsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val notificationId = intent?.getIntExtra(NOTIFICATION_ID, -1) ?: return

        intent.getStringExtra(ARCHIVE_ACTION)?.let { messageUid ->
            archiveMessage(messageUid, notificationId)
        }

        intent.getStringExtra(DELETE_ACTION)?.let { messageUid ->
            deleteMessage(messageUid, notificationId)
        }
    }

    private fun archiveMessage(messageUid: String, notificationId: Int) {

    }

    private fun deleteMessage(messageUid: String, notificationId: Int) {

    }

    companion object {
        const val NOTIFICATION_ID = "notification_id"
        const val ARCHIVE_ACTION = "archive_action"
        const val DELETE_ACTION = "delete_action"
    }
}
