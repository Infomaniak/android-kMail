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
import com.infomaniak.lib.core.api.ApiController.NetworkException
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
import com.infomaniak.mail.utils.NotificationUtils.Companion.EXTRA_MESSAGE_UID
import com.infomaniak.mail.utils.extensions.formatSubject
import com.infomaniak.mail.utils.extensions.removeLineBreaksFromHtml
import io.realm.kotlin.Realm
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
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

    private fun shouldLogToSentry(throwable: Throwable?): Boolean {
        return when (throwable) {
            is CancellationException, is NetworkException -> false
            is ApiErrorException -> {
                throwable.errorCode != ErrorCode.ACCESS_DENIED && throwable.errorCode != ErrorCode.NOT_AUTHORIZED
            }
            else -> true
        }
    }

    // We can arrive here for Mailboxes we did not open yet. That's why we check before doing anything.
    suspend fun execute(
        scope: CoroutineScope,
        userId: Int,
        mailbox: Mailbox,
        sentryMessageUid: String? = null,
        mailboxContentRealm: Realm? = null,
    ): Boolean {
        coroutineScope = scope

        // If the user disabled Notifications for this Mailbox, we don't want to display any Notification.
        // We can leave safely.
        if (mailbox.notificationsIsDisabled(notificationManagerCompat)) {
            SentryDebug.sendFailedNotification(
                reason = "Notifications are disabled",
                sentryLevel = SentryLevel.ERROR,
                userId = userId,
                mailboxId = mailbox.mailboxId,
                messageUid = sentryMessageUid,
                mailbox = mailbox,
            )
            return false
        }

        val realm = mailboxContentRealm ?: RealmDatabase.newMailboxContentInstance(userId, mailbox.mailboxId)
        val folder = FolderController.getFolder(FolderRole.INBOX, realm) ?: run {
            // If we can't find the INBOX in Realm, it means the user never opened this Mailbox.
            // We don't want to display Notifications in this case.
            // We can leave safely.
            SentryDebug.sendFailedNotification(
                reason = "Can't find the INBOX in Realm",
                sentryLevel = SentryLevel.ERROR,
                userId = userId,
                mailboxId = mailbox.mailboxId,
                messageUid = sentryMessageUid,
                mailbox = mailbox,
            )
            realm.close()
            return false
        }

        if (folder.cursor == null) {
            // We only want to display Notifications about Mailboxes that the User opened at least once.
            // If we don't have any cursor for this Mailbox's INBOX, it means it was never opened.
            // We can leave safely.
            SentryDebug.sendFailedNotification(
                reason = "INBOX was never opened",
                sentryLevel = SentryLevel.ERROR,
                userId = userId,
                mailboxId = mailbox.mailboxId,
                messageUid = sentryMessageUid,
                mailbox = mailbox,
            )
            realm.close()
            return false
        }

        val okHttpClient = AccountUtils.getHttpClient(userId)

        // Update Local with Remote
        val threadsWithNewMessages = refreshController.refreshThreads(
            refreshMode = RefreshMode.REFRESH_FOLDER_WITH_ROLE,
            mailbox = mailbox,
            folderId = folder.id,
            okHttpClient = okHttpClient,
            realm = realm,
        ).let { (threads, throwable) ->

            if (threads == null) {
                if (shouldLogToSentry(throwable)) {
                    SentryDebug.sendFailedNotification(
                        reason = "RefreshThreads failed",
                        sentryLevel = SentryLevel.ERROR,
                        userId = userId,
                        mailboxId = mailbox.mailboxId,
                        messageUid = sentryMessageUid,
                        mailbox = mailbox,
                        throwable = throwable,
                    )
                }
                realm.close()
                return false
            }

            return@let threads.filter { it.folderId == folder.id }.toList()
        }

        SentryLog.d(TAG, "LaunchWork: ${mailbox.email} has ${threadsWithNewMessages.count()} Threads with new Messages")

        // Dismiss Notifications for Messages that have been read on another device
        notificationManagerCompat.activeNotifications.forEach { statusBarNotification ->
            statusBarNotification.notification.extras.getString(EXTRA_MESSAGE_UID)?.let { messageUid ->
                if (MessageController.getMessage(messageUid, realm)?.isSeen == true) {
                    notificationManagerCompat.cancel(statusBarNotification.id)
                }
            }
        }

        /**
         * We don't want to display an empty group notification saying "0 new messages".
         *
         * If we only have 1 active notification for a specific Mailbox, it means that there is either :
         * - 1 normal notification,
         * - or 1 empty group notification.
         * In case of the later, we need to cancel it.
         */
        with(notificationManagerCompat) {
            val mailboxGroupNotifications = activeNotifications.filter { it.notification.group == mailbox.notificationGroupKey }
            if (mailboxGroupNotifications.size == 1) cancel(mailbox.notificationGroupId)
        }

        // When we fetched Messages, we didn't find any new Message.
        // It means we already got them all when we received a previous notification.
        // We can leave safely.
        if (threadsWithNewMessages.isEmpty()) {
            SentryDebug.sendFailedNotification(
                reason = "All new Messages were already received in a previous notification",
                sentryLevel = SentryLevel.ERROR,
                userId = userId,
                mailboxId = mailbox.mailboxId,
                messageUid = sentryMessageUid,
                mailbox = mailbox,
            )
            realm.close()
            return false
        }

        // Notify Threads with new Messages
        val unReadThreadsCount = threadsWithNewMessages.count()
        var hasShownNotification = false
        threadsWithNewMessages.forEachIndexed { index, thread ->
            thread.showThreadNotification(
                userId = userId,
                mailbox = mailbox,
                realm = realm,
                unReadThreadsCount = unReadThreadsCount,
                isLastMessage = index == threadsWithNewMessages.lastIndex,
                sentryMessageUid = sentryMessageUid,
                okHttpClient = okHttpClient,
            ).let {
                if (it) hasShownNotification = true
            }
        }

        realm.close()
        return hasShownNotification
    }

    private suspend fun Thread.showThreadNotification(
        userId: Int,
        mailbox: Mailbox,
        realm: Realm,
        unReadThreadsCount: Int,
        isLastMessage: Boolean,
        sentryMessageUid: String?,
        okHttpClient: OkHttpClient,
    ): Boolean {

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
            return false
        }

        // If the Message has already been seen before receiving the Notification, we don't want to display it.
        // We can leave safely.
        if (message.isSeen) {
            SentryDebug.sendFailedNotification(
                reason = "Message was already seen",
                sentryLevel = SentryLevel.ERROR,
                userId = userId,
                mailboxId = mailbox.mailboxId,
                messageUid = sentryMessageUid,
                mailbox = mailbox,
            )
            return false
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
        val hasShownNotification = notificationUtils.showMessageNotification(
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
        if (hasShownNotification && isLastMessage) {
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

        return hasShownNotification
    }

    companion object {
        private val TAG: String = FetchMessagesManager::class.java.simpleName

        // Based on what seems to be the limit taken into account by NotificationCompat.Builder, we truncate some fields to avoid
        // pendingIntent that will trigger TransactionTooLargeException further down the line like Sentry ID 41593. We don't need
        // the extra data, it was bound to be truncated anyway.
        private const val MAX_CHAR_LIMIT = 5 * 1_024
    }
}
