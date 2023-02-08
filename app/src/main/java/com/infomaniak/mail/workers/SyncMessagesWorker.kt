/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
package com.infomaniak.mail.workers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import androidx.work.PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.NotificationUtilsCore.Companion.pendingIntentFlags
import com.infomaniak.lib.core.utils.clearStack
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.Thread
import com.infomaniak.mail.ui.LaunchActivity
import com.infomaniak.mail.ui.LaunchActivityArgs
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ApiErrorException
import com.infomaniak.mail.utils.ApiErrorException.*
import com.infomaniak.mail.utils.NotificationUtils.showNewMessageNotification
import com.infomaniak.mail.utils.formatSubject
import com.infomaniak.mail.utils.htmlToText
import io.realm.kotlin.Realm
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class SyncMessagesWorker(appContext: Context, params: WorkerParameters) : BaseCoroutineWorker(appContext, params) {

    private val localSettings by lazy { LocalSettings.getInstance(applicationContext) }
    private val mailboxInfoRealm by lazy { RealmDatabase.newMailboxInfoInstance }

    private val notificationManagerCompat by lazy { NotificationManagerCompat.from(applicationContext) }

    override suspend fun launchWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Work launched")

        AccountUtils.getAllUsersSync().forEach { user ->
            MailboxController.getMailboxes(user.id, mailboxInfoRealm).forEach loopMailboxes@{ mailbox ->

                // Don't launch sync if the mailbox's notifications have been disabled by the user
                if (mailbox.isNotificationsBlocked()) return@loopMailboxes

                val realm = RealmDatabase.newMailboxContentInstance(user.id, mailbox.mailboxId)
                val folder = FolderController.getFolder(FolderRole.INBOX, realm) ?: return@loopMailboxes
                if (folder.cursor == null) return@loopMailboxes
                val okHttpClient = AccountUtils.getHttpClient(user.id)

                // Update local with remote
                val newMessagesThreads = runCatching {
                    MessageController.fetchCurrentFolderMessages(mailbox, folder.id, okHttpClient, realm)
                }.getOrElse {
                    if (it is ApiErrorException) handleApiErrors(it) else throw it
                    return@loopMailboxes
                }
                Log.d(TAG, "launchWork: ${mailbox.email} has ${newMessagesThreads.count()} new messages")

                // Notify all new messages
                val unReadThreadsCount = ThreadController.getUnreadThreadsCount(folder.id, realm)
                newMessagesThreads.forEach { thread ->
                    thread.showNotification(user.id, mailbox, unReadThreadsCount, realm, okHttpClient)
                }

                realm.close()
            }
        }

        Log.d(TAG, "Work finished")

        Result.success()
    }

    override fun onFinish() {
        mailboxInfoRealm.close()
    }

    private fun Mailbox.isNotificationsBlocked(): Boolean = with(notificationManagerCompat) {
        val isGroupBlocked = getNotificationChannelGroupCompat(channelGroupId)?.isBlocked == true
        val isChannelBlocked = getNotificationChannelCompat(channelId)?.importance == NotificationManagerCompat.IMPORTANCE_NONE
        isChannelBlocked || isGroupBlocked
    }

    private fun Thread.showNotification(
        userId: Int,
        mailbox: Mailbox,
        unReadThreadsCount: Int,
        realm: Realm,
        okHttpClient: OkHttpClient
    ) {

        fun contentIntent(isSummary: Boolean): PendingIntent {
            val intent = Intent(applicationContext, LaunchActivity::class.java).clearStack().apply {
                putExtras(LaunchActivityArgs(if (isSummary) null else uid, userId, mailbox.mailboxId).toBundle())
            }
            val requestCode = if (isSummary) mailbox.uuid else uid
            return PendingIntent.getActivity(applicationContext, requestCode.hashCode(), intent, pendingIntentFlags)
        }

        fun showNotification(contentText: String, isSummary: Boolean, title: String = "", description: String? = null) {
            applicationContext.showNewMessageNotification(mailbox.channelId, title, description).apply {
                if (isSummary) setContentTitle(null)
                setSubText(mailbox.email)
                setContentText(contentText)
                setColorized(true)
                setContentIntent(contentIntent(isSummary = isSummary))
                setGroup(mailbox.notificationGroupKey)
                setGroupSummary(isSummary)
                color = localSettings.accentColor.getPrimary(applicationContext)

                val notificationId = if (isSummary) mailbox.notificationGroupId else uid.hashCode()
                @Suppress("MissingPermission")
                notificationManagerCompat.notify(notificationId, build())
            }
        }

        ThreadController.fetchIncompleteMessages(messages, mailbox, okHttpClient, realm)

        val message = ThreadController.getThread(uid, realm)?.messages?.last() ?: return

        if (message.isSeen) return // Ignore if it has already been seen

        val subject = applicationContext.formatSubject(message.subject)
        val preview = message.body?.value?.ifBlank { null }
            ?.let { "\n${it.htmlToText().trim()}" } // TODO: remove body history
            ?: message.preview.ifBlank { null }?.let { "\n${it.trim()}" }
            ?: ""
        val formattedPreview = preview.replace("\\n+\\s*".toRegex(), "\n") // Ignore multiple/start whitespaces
        val description = "$subject$formattedPreview"

        // Show message notification
        showNotification(subject, false, message.sender.displayedName(applicationContext), description)
        // Show group summary notification
        val summaryText = applicationContext.resources.getQuantityString(
            R.plurals.newMessageNotificationSummary,
            unReadThreadsCount,
            unReadThreadsCount
        )
        showNotification(summaryText, true)
    }

    private fun handleApiErrors(exception: ApiErrorException) {
        when (ApiController.json.decodeFromString<ApiResponse<Any>>(exception.message!!).error?.code) {
            ErrorCodes.FOLDER_DOES_NOT_EXIST -> Unit
            else -> Sentry.captureException(exception)
        }
    }

    companion object {
        private const val TAG = "SyncMessagesWorker"

        fun scheduleWork(context: Context) {
            Log.d(TAG, "Work scheduled")

            val workRequest = PeriodicWorkRequestBuilder<SyncMessagesWorker>(MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                // We start with a delayed duration, so that when the app is rebooted the service is not launched
                .setInitialDelay(2, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.REPLACE, workRequest)
        }

        fun cancelWork(context: Context) {
            Log.d(TAG, "Work cancelled")
            WorkManager.getInstance(context).cancelUniqueWork(TAG)
        }
    }
}
