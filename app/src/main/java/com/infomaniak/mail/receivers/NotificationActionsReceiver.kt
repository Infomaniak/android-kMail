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
import androidx.core.app.NotificationManagerCompat
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.getUids
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationManagerCompat: NotificationManagerCompat

    @Inject
    lateinit var sharedUtils: SharedUtils

    @Inject
    lateinit var mailboxContentRealm: RealmDatabase.MailboxContent

    @Inject
    lateinit var folderController: FolderController

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onReceive(context: Context?, intent: Intent?) {
        CoroutineScope(ioDispatcher).launch { intent?.let(::handleNotificationIntent) }
    }

    private fun handleNotificationIntent(intent: Intent) {

        val notificationId = intent.getIntExtra(NOTIFICATION_ID, -1)
        val action = intent.action ?: return
        val messageUid = intent.getStringExtra(MESSAGE_UID) ?: return

        val folderRole = when (action) {
            ARCHIVE_ACTION -> FolderRole.ARCHIVE
            DELETE_ACTION -> FolderRole.TRASH
            else -> null
        } ?: return

        executeAction(messageUid, folderRole)
        dismissNotification(notificationId)
    }

    private fun executeAction(messageUid: String, folderRole: FolderRole) {
        val mailbox = MailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId) ?: return
        val message = MessageController.getMessage(messageUid, mailboxContentRealm()) ?: return
        val destinationId = folderController.getFolder(folderRole)?.id ?: return

        val threads = message.threads.filter { it.folderId == message.folderId }
        val messages = sharedUtils.getMessagesToMove(threads, message)

        ApiRepository.moveMessages(mailbox.uuid, messages.getUids(), destinationId)
    }

    private fun dismissNotification(notificationId: Int) {
        if (notificationId == -1) return

        notificationManagerCompat.cancel(notificationId)
        // TODO: Dismiss the "summary notification" if there is no more Notifications (maybe store a list of all NotificationsIds ?)
    }

    companion object {
        const val NOTIFICATION_ID = "notification_id"
        const val MESSAGE_UID = "message_uid"
        const val ARCHIVE_ACTION = "archive_action"
        const val DELETE_ACTION = "delete_action"
    }
}
