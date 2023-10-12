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
import com.infomaniak.html.cleaner.HtmlSanitizer
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
import io.sentry.SentryLevel
import okhttp3.OkHttpClient
import javax.inject.Inject

class FetchMessagesManager @Inject constructor(
    private val appContext: Context,
    private val notificationManagerCompat: NotificationManagerCompat,
    private val notificationUtils: NotificationUtils,
    private val refreshController: RefreshController,
) {

    suspend fun execute(userId: Int, mailbox: Mailbox, sentryMessageUid: String? = null, mailboxContentRealm: Realm? = null) {

        if (mailbox.notificationsIsDisabled(notificationManagerCompat)) {
            // If the user disabled Notifications for this Mailbox, we don't want to display any Notification.
            // We can leave safely.
            SentryDebug.sendFailedNotification(
                reason = "Notifications are disabled",
                sentryLevel = SentryLevel.INFO,
                userId = userId,
                mailboxId = mailbox.mailboxId,
                messageUid = sentryMessageUid,
                mailbox = mailbox,
            )
            return
        }

        val realm = mailboxContentRealm ?: RealmDatabase.newMailboxContentInstance(userId, mailbox.mailboxId)
        val folder = FolderController.getFolder(FolderRole.INBOX, realm) ?: run {
            // If we can't find the INBOX in Realm, it means the user never opened this Mailbox.
            // We don't want to display Notifications in this case.
            // We can leave safely.
            SentryDebug.sendFailedNotification(
                reason = "No Folder in Realm",
                sentryLevel = SentryLevel.WARNING,
                userId = userId,
                mailboxId = mailbox.mailboxId,
                messageUid = sentryMessageUid,
                mailbox = mailbox,
            )
            return
        }

        if (folder.cursor == null) {
            SentryDebug.sendFailedNotification(
                reason = "Folder's cursor is null",
                sentryLevel = SentryLevel.WARNING,
                userId = userId,
                mailboxId = mailbox.mailboxId,
                messageUid = sentryMessageUid,
                mailbox = mailbox,
            )
            return
        }
        val okHttpClient = AccountUtils.getHttpClient(userId)

        // Update Local with Remote
        val threadsWithNewMessages = refreshController.refreshThreads(
            refreshMode = RefreshMode.REFRESH_FOLDER_WITH_ROLE,
            mailbox = mailbox,
            folder = folder,
            okHttpClient = okHttpClient,
            realm = realm,
        ).let { (threads, throwable) ->
            if (threads == null) {
                SentryDebug.sendFailedNotification(
                    reason = "RefreshThreads failed",
                    sentryLevel = SentryLevel.ERROR,
                    userId = userId,
                    mailboxId = mailbox.mailboxId,
                    messageUid = sentryMessageUid,
                    mailbox = mailbox,
                    throwable = throwable,
                )
                return
            }
            return@let threads.toList()
        }

        SentryLog.d(TAG, "LaunchWork: ${mailbox.email} has ${threadsWithNewMessages.count()} Threads with new Messages")

        if (threadsWithNewMessages.isEmpty()) {
            SentryDebug.sendFailedNotification(
                reason = "No new Message",
                sentryLevel = SentryLevel.WARNING,
                userId = userId,
                mailboxId = mailbox.mailboxId,
                messageUid = sentryMessageUid,
                mailbox = mailbox,
            )
            return
        }

        // Notify Threads with new Messages
        val unReadThreadsCount = ThreadController.getUnreadThreadsCount(folder)
        threadsWithNewMessages.forEachIndexed { index, thread ->
            thread.showThreadNotification(
                userId = userId,
                mailbox = mailbox,
                realm = realm,
                unReadThreadsCount = unReadThreadsCount,
                isLastMessage = index == threadsWithNewMessages.lastIndex,
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

        ThreadController.fetchMessagesHeavyData(messages, realm, okHttpClient)

        val message = MessageController.getThreadLastMessageInFolder(uid, realm) ?: run {
            SentryDebug.sendFailedNotification(
                reason = "No Message in the Thread",
                sentryLevel = SentryLevel.ERROR,
                userId = userId,
                mailboxId = mailbox.mailboxId,
                messageUid = sentryMessageUid,
                mailbox = mailbox,
            )
            return
        }
        if (message.isSeen) {
            // If the Message has already been seen before receiving the Notification, we don't want to display it.
            // We can leave safely.
            SentryDebug.sendFailedNotification(
                reason = "Message already seen",
                sentryLevel = SentryLevel.INFO,
                userId = userId,
                mailboxId = mailbox.mailboxId,
                messageUid = sentryMessageUid,
                mailbox = mailbox,
            )
            return
        }

        val subject = appContext.formatSubject(message.subject)
        val preview = if (message.body?.value.isNullOrBlank()) {
            ""
        } else {
            message.body
                ?.let {
                    val content = MessageBodyUtils.splitContentAndQuote(it).content
                    val dirtyDocument = content.removeLineBreaksFromHtml()
                    val cleanedDocument = HtmlSanitizer.getInstance().sanitize(dirtyDocument)
                    return@let "\n${cleanedDocument.wholeText().trim()}"
                }
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
                payloadTitle = message.sender?.displayedName(appContext),
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
