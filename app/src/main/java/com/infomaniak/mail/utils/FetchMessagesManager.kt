/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
import com.infomaniak.mail.utils.extensions.formatSubject
import com.infomaniak.mail.utils.extensions.removeLineBreaksFromHtml
import com.infomaniak.mail.utils.NotificationUtils.Companion.EXTRA_MESSAGE_UID
import io.realm.kotlin.Realm
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import javax.inject.Inject

class FetchMessagesManager @Inject constructor(
    private val appContext: Context,
    private val notificationManagerCompat: NotificationManagerCompat,
    private val notificationUtils: NotificationUtils,
    private val refreshController: RefreshController,
) {

    private lateinit var coroutineScope: CoroutineScope

    suspend fun execute(
        scope: CoroutineScope,
        userId: Int,
        mailbox: Mailbox,
        sentryMessageUid: String? = null,
        mailboxContentRealm: Realm? = null,
    ) {
        coroutineScope = scope

        // If the user disabled Notifications for this Mailbox, we don't want to display any Notification.
        // We can leave safely.
        if (mailbox.notificationsIsDisabled(notificationManagerCompat)) return

        val realm = mailboxContentRealm ?: RealmDatabase.newMailboxContentInstance(userId, mailbox.mailboxId)
        val folder = FolderController.getFolder(FolderRole.INBOX, realm) ?: run {
            // If we can't find the INBOX in Realm, it means the user never opened this Mailbox.
            // We don't want to display Notifications in this case.
            // We can leave safely.
            // But if a user never opened this Mailbox, we shouldn't have register it to receive Notifications. So this shouldn't happen.
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
            // We only want to display Notifications about Mailboxes that the User opened at least once.
            // If we don't have any cursor for this Mailbox's INBOX, it means it was never opened.
            // We can leave safely.
            // But if a user never opened this Mailbox, we shouldn't have register it to receive Notifications. So this shouldn't happen.
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

        // Dismiss Notifications for Messages that have been read on another device
        notificationManagerCompat.activeNotifications.forEach { statusBarNotification ->
            statusBarNotification.notification.extras.getString(EXTRA_MESSAGE_UID)?.let { messageUid ->
                if (MessageController.getMessage(messageUid, mailboxContentRealm!!)?.isSeen == true) {
                    notificationManagerCompat.cancel(statusBarNotification.id)
                }
            }
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

        val formattedPreview = message.preview.ifBlank { null }?.let { "\n${it.trim()}" } ?: ""
        val body = if (message.body?.value.isNullOrBlank()) {
            formattedPreview
        } else {
            message.body
                ?.let {
                    val content = MessageBodyUtils.splitContentAndQuote(it).content
                    val dirtyDocument = content.removeLineBreaksFromHtml()
                    val cleanedDocument = HtmlSanitizer.getInstance().sanitize(dirtyDocument)
                    return@let "\n${cleanedDocument.wholeText().trim()}"
                }
                ?: formattedPreview
        }

        val subject = appContext.formatSubject(message.subject).take(MAX_CHAR_LIMIT)
        val formattedBody = body.replace("\\n+\\s*".toRegex(), "\n") // Ignore multiple/start whitespaces
        val description = "$subject$formattedBody".take(MAX_CHAR_LIMIT)

        // Show Message notification
        notificationUtils.showMessageNotification(
            scope = coroutineScope,
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
                scope = coroutineScope,
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

    companion object {
        private val TAG: String = FetchMessagesManager::class.java.simpleName

        // Based on what seems to be the limit taken into account by NotificationCompat.Builder, we truncate some fields to avoid
        // pendingIntent that will trigger TransactionTooLargeException further down the line like Sentry ID 41593. We don't need
        // the extra data, it was bound to be truncated anyway.
        private const val MAX_CHAR_LIMIT = 5 * 1_024
    }
}
