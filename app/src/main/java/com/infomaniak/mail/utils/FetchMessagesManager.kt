/*
 * Infomaniak Mail - Android
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
package com.infomaniak.mail.utils

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.NotificationPayload.NotificationBehavior
import com.infomaniak.mail.utils.NotificationPayload.NotificationBehavior.NotificationType
import io.realm.kotlin.Realm
import okhttp3.OkHttpClient
import javax.inject.Inject

class FetchMessagesManager @Inject constructor(
    private val appContext: Context,
    private val notificationManagerCompat: NotificationManagerCompat,
    private val notificationUtils: NotificationUtils,
    private val refreshController: RefreshController,
    private val threadController: ThreadController,
) {

    suspend fun execute(userId: Int, mailbox: Mailbox, sentryMessageUid: String? = null, mailboxContentRealm: Realm? = null) {

        if (mailbox.notificationsIsDisabled(notificationManagerCompat)) {
            // If the user disabled Notifications for this Mailbox, we don't want to display any Notification.
            // We can leave safely.
            SentryDebug.sendFailedNotification("Notifications are disabled", userId, mailbox.mailboxId, sentryMessageUid, mailbox)
            return
        }

        val realm = mailboxContentRealm ?: RealmDatabase.newMailboxContentInstance(userId, mailbox.mailboxId)
        val folder = FolderController.getFolder(FolderRole.INBOX, realm) ?: run {
            // If we can't find the INBOX in Realm, it means the user never opened this Mailbox.
            // We don't want to display Notifications in this case.
            // We can leave safely.
            SentryDebug.sendFailedNotification("No Folder in Realm", userId, mailbox.mailboxId, sentryMessageUid, mailbox)
            return
        }

        if (folder.cursor == null) {
            SentryDebug.sendFailedNotification("Folder's cursor is null", userId, mailbox.mailboxId, sentryMessageUid, mailbox)
            return
        }
        val okHttpClient = AccountUtils.getHttpClient(userId)

        // Update Local with Remote
        val newMessagesThreads = refreshController.refreshThreads(
            refreshMode = RefreshMode.REFRESH_FOLDER_WITH_ROLE,
            mailbox = mailbox,
            folder = folder,
            okHttpClient = okHttpClient,
            realm = realm,
        ).let { (threads, throwable) ->
            if (threads == null) {
                SentryDebug.sendFailedNotification(
                    reason = "RefreshThreads failed",
                    userId = userId,
                    mailboxId = mailbox.mailboxId,
                    messageUid = sentryMessageUid,
                    mailbox = mailbox,
                    throwable = throwable,
                )
                return
            }
            return@let threads
        }

        SentryLog.d(TAG, "launchWork: ${mailbox.email} has ${newMessagesThreads.count()} Threads with new Messages")

        // Notify Threads with new Messages
        val unReadThreadsCount = ThreadController.getUnreadThreadsCount(folder)
        newMessagesThreads.forEachIndexed { index, thread ->
            thread.showThreadNotification(
                userId = userId,
                mailbox = mailbox,
                realm = realm,
                unReadThreadsCount = unReadThreadsCount,
                isLastMessage = index == newMessagesThreads.lastIndex,
                sentryMessageUid = sentryMessageUid,
                okHttpClient = okHttpClient,
            )
        }

        realm.close()
    }

    private suspend fun Thread.showThreadNotification(
        userId: Int,
        mailbox: Mailbox,
        realm: Realm,
        unReadThreadsCount: Int,
        isLastMessage: Boolean,
        sentryMessageUid: String?,
        okHttpClient: OkHttpClient,
    ) {

        threadController.fetchMessagesHeavyData(messages, realm, okHttpClient)

        val message = MessageController.getThreadLastMessageInFolder(uid, realm) ?: run {
            SentryDebug.sendFailedNotification("No Message in the Thread", userId, mailbox.mailboxId, sentryMessageUid, mailbox)
            return
        }
        if (message.isSeen) {
            // If the Message has already been seen before receiving the Notification, we don't want to display it.
            // We can leave safely.
            SentryDebug.sendFailedNotification("Message already seen", userId, mailbox.mailboxId, sentryMessageUid, mailbox)
            return
        }

        val subject = appContext.formatSubject(message.subject)
        val preview = if (message.body?.value.isNullOrBlank()) {
            ""
        } else {
            message.body
                ?.let { "\n${MessageBodyUtils.splitContentAndQuote(it).content.htmlToText().trim()}" }
                ?: message.preview.ifBlank { null }?.let { "\n${it.trim()}" }
                ?: ""
        }
        val formattedPreview = preview.replace("\\n+\\s*".toRegex(), "\n") // Ignore multiple/start whitespaces
        val description = "$subject$formattedPreview"

        // Show Message notification
        notificationUtils.showMessageNotification(
            notificationManagerCompat = notificationManagerCompat,
            payload = NotificationPayload(
                userId = userId,
                mailboxId = mailbox.mailboxId,
                threadUid = uid,
                messageUid = message.uid,
                notificationId = uid.hashCode(),
                payloadTitle = message.sender(message.folder.role)?.displayedName(appContext),
                payloadContent = subject,
                payloadDescription = description,
            )
        )

        // Show Group Summary notification
        if (isLastMessage) {
            val summaryText = appContext.resources.getQuantityString(
                R.plurals.newMessageNotificationSummary,
                unReadThreadsCount,
                unReadThreadsCount,
            )
            notificationUtils.showMessageNotification(
                notificationManagerCompat = notificationManagerCompat,
                payload = NotificationPayload(
                    userId = userId,
                    mailboxId = mailbox.mailboxId,
                    threadUid = uid,
                    notificationId = mailbox.notificationGroupId,
                    behavior = NotificationBehavior(
                        type = NotificationType.SUMMARY,
                        behaviorContent = summaryText,
                    ),
                ),
            )
        }
    }

    private companion object {
        val TAG: String = FetchMessagesManager::class.java.simpleName
    }
}
