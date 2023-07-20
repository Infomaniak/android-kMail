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

import android.app.NotificationManager
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
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.getUids
import dagger.hilt.android.AndroidEntryPoint
import io.realm.kotlin.TypedRealm
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
    lateinit var folderController: FolderController

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        CoroutineScope(ioDispatcher).launch { handleNotificationIntent(context, intent) }
    }

    private fun handleNotificationIntent(context: Context, intent: Intent) {

        val userId = intent.getIntExtra(USER_ID, -1)
        val mailboxId = intent.getIntExtra(MAILBOX_ID, -1)
        val realm = RealmDatabase.newMailboxContentInstance(userId, mailboxId)
        val mailbox = MailboxController.getMailbox(userId, mailboxId) ?: return

        val notificationId = intent.getIntExtra(NOTIFICATION_ID, -1)
        val action = intent.action ?: return
        val messageUid = intent.getStringExtra(MESSAGE_UID) ?: return

        val folderRole = when (action) {
            ARCHIVE_ACTION -> FolderRole.ARCHIVE
            DELETE_ACTION -> FolderRole.TRASH
            else -> null
        } ?: return

        executeAction(realm, mailbox, messageUid, folderRole)
        dismissNotification(context, mailbox, notificationId)
    }

    private fun executeAction(realm: TypedRealm, mailbox: Mailbox, messageUid: String, folderRole: FolderRole) {
        val message = MessageController.getMessage(messageUid, realm) ?: return
        val destinationId = folderController.getFolder(folderRole)?.id ?: return

        val threads = message.threads.filter { it.folderId == message.folderId }
        val messages = sharedUtils.getMessagesToMove(threads, message)

        val isSuccess = ApiRepository.moveMessages(mailbox.uuid, messages.getUids(), destinationId).isSuccess()
        if (isSuccess) {
            // TODO: Update the Notification and add an Undo button.
        }
    }

    private fun dismissNotification(context: Context, mailbox: Mailbox, notificationId: Int) {
        if (notificationId == -1) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val count = notificationManager.activeNotifications.count { mailbox.notificationGroupKey in it.groupKey }

        val notificationIdToCancel = if (count <= 2) mailbox.notificationGroupId else notificationId
        notificationManagerCompat.cancel(notificationIdToCancel)
    }

    companion object {
        const val USER_ID = "user_id"
        const val MAILBOX_ID = "mailbox_id"
        const val NOTIFICATION_ID = "notification_id"
        const val MESSAGE_UID = "message_uid"
        const val ARCHIVE_ACTION = "archive_action"
        const val DELETE_ACTION = "delete_action"
    }
}
