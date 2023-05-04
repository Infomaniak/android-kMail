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
package com.infomaniak.mail.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.infomaniak.lib.core.utils.NotificationUtilsCore
import com.infomaniak.lib.core.utils.clearStack
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.ui.LaunchActivity
import com.infomaniak.mail.ui.LaunchActivityArgs
import com.infomaniak.mail.utils.NotificationUtils.showNewMessageNotification
import io.realm.kotlin.Realm
import io.sentry.Sentry
import okhttp3.OkHttpClient
import javax.inject.Inject

class FetchMessagesManager @Inject constructor(
    private val appContext: Context,
    private val notificationManagerCompat: NotificationManagerCompat,
) {

    private val localSettings by lazy { LocalSettings.getInstance(appContext) }

    suspend fun execute(userId: Int, mailbox: Mailbox, mailboxContentRealm: Realm? = null) {
        // Don't launch sync if the mailbox's notifications have been disabled by the user
        if (mailbox.notificationsIsDisabled(notificationManagerCompat)) return

        val realm = mailboxContentRealm ?: RealmDatabase.newMailboxContentInstance(userId, mailbox.mailboxId)
        val folder = FolderController.getFolder(Folder.FolderRole.INBOX, realm) ?: return
        if (folder.cursor == null) return
        val okHttpClient = AccountUtils.getHttpClient(userId)

        // Update local with remote
        val newMessagesThreads = runCatching {
            MessageController.fetchCurrentFolderMessages(mailbox, folder, okHttpClient, realm)
        }.getOrElse {
            if (it is ApiErrorException) it.handleApiErrors() else throw it
            return
        }
        Log.d(TAG, "launchWork: ${mailbox.email} has ${newMessagesThreads.count()} new messages")

        // Notify all new messages
        val unReadThreadsCount = ThreadController.getUnreadThreadsCount(folder)
        newMessagesThreads.forEachIndexed { index, thread ->
            thread.showNotification(
                userId = userId,
                mailbox = mailbox,
                realm = realm,
                unReadThreadsCount = unReadThreadsCount,
                okHttpClient = okHttpClient,
                isLastMessage = index == newMessagesThreads.lastIndex,
            )
        }

        realm.close()
    }

    private suspend fun Thread.showNotification(
        userId: Int,
        mailbox: Mailbox,
        realm: Realm,
        unReadThreadsCount: Int,
        okHttpClient: OkHttpClient,
        isLastMessage: Boolean,
    ) {

        fun contentIntent(isSummary: Boolean): PendingIntent {
            val intent = Intent(appContext, LaunchActivity::class.java).clearStack().apply {
                putExtras(LaunchActivityArgs(if (isSummary) null else uid, userId, mailbox.mailboxId).toBundle())
            }
            val requestCode = if (isSummary) mailbox.uuid else uid
            return PendingIntent.getActivity(
                appContext, requestCode.hashCode(), intent,
                NotificationUtilsCore.pendingIntentFlags
            )
        }

        fun showNotification(contentText: String, isSummary: Boolean, title: String = "", description: String? = null) {
            appContext.showNewMessageNotification(mailbox.channelId, title, description).apply {
                if (isSummary) {
                    setContentTitle(null)
                    setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                }
                setSubText(mailbox.email)
                setContentText(contentText)
                setColorized(true)
                setContentIntent(contentIntent(isSummary = isSummary))
                setGroup(mailbox.notificationGroupKey)
                setGroupSummary(isSummary)
                color = localSettings.accentColor.getPrimary(appContext)

                val notificationId = if (isSummary) mailbox.notificationGroupId else uid.hashCode()
                @Suppress("MissingPermission")
                notificationManagerCompat.notify(notificationId, build())
            }
        }

        ThreadController.fetchIncompleteMessages(messages, mailbox, okHttpClient, realm)

        val message = ThreadController.getThread(uid, realm)?.messages?.last() ?: return

        if (message.isSeen) return // Ignore if it has already been seen

        val subject = appContext.formatSubject(message.subject)
        val preview = if (message.body?.value.isNullOrBlank()) {
            ""
        } else {
            message.body
                ?.let { "\n${MessageBodyUtils.splitBodyAndQuote(it).messageBody.htmlToText().trim()}" }
                ?: message.preview.ifBlank { null }?.let { "\n${it.trim()}" }
                ?: ""
        }
        val formattedPreview = preview.replace("\\n+\\s*".toRegex(), "\n") // Ignore multiple/start whitespaces
        val description = "$subject$formattedPreview"

        // Show message notification
        showNotification(subject, false, message.sender.displayedName(appContext), description)

        // Show group summary notification
        if (isLastMessage) {
            val summaryText = appContext.resources.getQuantityString(
                R.plurals.newMessageNotificationSummary,
                unReadThreadsCount,
                unReadThreadsCount
            )
            showNotification(summaryText, true)
        }
    }

    private fun ApiErrorException.handleApiErrors() {
        when (errorCode) {
            ErrorCode.FOLDER_DOES_NOT_EXIST -> Unit
            else -> Sentry.captureException(this)
        }
    }

    private companion object {
        val TAG = FetchMessagesManager::class.simpleName
    }
}
