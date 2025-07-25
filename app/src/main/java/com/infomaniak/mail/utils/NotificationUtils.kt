/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.NotificationWithIdAndTag
import com.infomaniak.lib.core.utils.NotificationUtilsCore
import com.infomaniak.lib.core.utils.NotificationUtilsCore.Companion.PENDING_INTENT_FLAGS
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.clearStack
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.di.MailboxInfoRealm
import com.infomaniak.mail.receivers.NotificationActionsReceiver
import com.infomaniak.mail.receivers.NotificationActionsReceiver.Companion.ARCHIVE_ACTION
import com.infomaniak.mail.receivers.NotificationActionsReceiver.Companion.DELETE_ACTION
import com.infomaniak.mail.receivers.NotificationActionsReceiver.Companion.EXTRA_PAYLOAD
import com.infomaniak.mail.receivers.NotificationActionsReceiver.Companion.UNDO_ACTION
import com.infomaniak.mail.ui.LaunchActivity
import com.infomaniak.mail.ui.LaunchActivityArgs
import io.realm.kotlin.Realm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.infomaniak.lib.core.R as RCore

@Singleton
class NotificationUtils @Inject constructor(
    private val appContext: Context,
    private val localSettings: LocalSettings,
    @MailboxInfoRealm private val mailboxInfoRealm: Realm,
    private val globalCoroutineScope: CoroutineScope,
) {

    private val notificationsByMailboxId = mutableMapOf<Int, MutableList<NotificationWithIdAndTag>>()
    private val notificationsJobByMailboxId = mutableMapOf<Int, Job?>()

    fun initNotificationChannel() = with(appContext) {
        val channelList = mutableListOf<NotificationChannel>()

        val generalChannel = buildNotificationChannel(
            getString(R.string.notification_channel_id_general),
            getString(R.string.notificationGeneralChannelName),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        channelList.add(generalChannel)

        val draftChannel = buildNotificationChannel(
            getString(R.string.notification_channel_id_draft_service),
            getString(R.string.notificationSyncDraftChannelName),
            NotificationManager.IMPORTANCE_MIN,
        )
        channelList.add(draftChannel)

        val syncMessagesChannel = buildNotificationChannel(
            getString(R.string.notification_channel_id_sync_messages_service),
            getString(R.string.notificationSyncMessagesChannelName),
            NotificationManager.IMPORTANCE_MIN,
        )
        channelList.add(syncMessagesChannel)

        createNotificationChannels(channelList)
    }

    fun initMailNotificationChannel(mailbox: Mailbox) = with(appContext) {
        val groups = mutableListOf<NotificationChannelGroup>()
        val channels = mutableListOf<NotificationChannel>()

        val group = NotificationChannelGroup(mailbox.channelGroupId, mailbox.email)
        groups.add(group)

        val channel = buildNotificationChannel(
            channelId = mailbox.channelId,
            name = getString(R.string.notificationNewMessagesChannelName),
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            groupId = group.id,
        )
        channels.add(channel)

        createNotificationChannels(channels, groups)
    }

    fun buildGeneralNotification(title: String, description: String? = null): NotificationCompat.Builder {
        return appContext.buildNotification(
            channelId = appContext.getString(R.string.notification_channel_id_general),
            icon = defaultSmallIcon,
            title = title,
            description = description,
        )
    }

    fun buildDraftActionsNotification(): Notification {
        return appContext.undeterminedProgressMessageNotificationBuilder(
            channelIdRes = R.string.notification_channel_id_draft_service,
            titleRes = R.string.notificationSyncDraftChannelName,
        ).build()
    }

    fun buildSyncMessagesServiceNotification(): Notification {
        return appContext.undeterminedProgressMessageNotificationBuilder(
            channelIdRes = R.string.notification_channel_id_sync_messages_service,
            titleRes = R.string.notificationSyncMessagesChannelName,
        ).build()
    }

    fun buildGenericNewMailsNotification(title: String): NotificationCompat.Builder {
        return appContext.buildNotification(
            channelId = appContext.getString(R.string.notification_channel_id_sync_messages_service),
            icon = defaultSmallIcon,
            title = title,
        )
    }

    fun buildDraftErrorNotification(
        @StringRes errorMessageRes: Int,
        action: DraftAction,
    ): NotificationCompat.Builder = with(appContext) {
        val resource = if (action == DraftAction.SEND) {
            R.string.notificationTitleCouldNotSendDraft
        } else {
            R.string.notificationTitleCouldNotSaveDraft
        }

        return buildGeneralNotification(
            title = getString(resource),
            description = getString(errorMessageRes),
        )
    }

    fun showMessageNotification(
        scope: CoroutineScope = globalCoroutineScope,
        notificationManagerCompat: NotificationManagerCompat,
        payload: NotificationPayload,
    ): Boolean = with(payload) {
        val mailbox = MailboxController.getMailbox(userId, mailboxId, mailboxInfoRealm) ?: run {
            SentryDebug.sendFailedNotification("Created Notif: no Mailbox in Realm", userId, mailboxId, messageUid)
            return@with false
        }
        val contentIntent = getContentIntent(payload = this, isUndo)
        val notificationBuilder = buildMessageNotification(mailbox.channelId, title, description)

        initMessageNotificationContent(mailbox, contentIntent, notificationBuilder, payload = this)
        showNotifications(scope, mailboxId, notificationManagerCompat)
        return@with true
    }

    @SuppressLint("WrongConstant")
    private fun getContentIntent(
        payload: NotificationPayload,
        isUndo: Boolean,
    ): PendingIntent? = with(payload) {

        if (isUndo) return null

        val requestCode = if (isSummary) mailboxId else threadUid
        val intent = Intent(appContext, LaunchActivity::class.java).clearStack().putExtras(
            LaunchActivityArgs(
                userId = userId,
                mailboxId = mailboxId,
                openThreadUid = if (isSummary) null else threadUid,
            ).toBundle(),
        )
        return PendingIntent.getActivity(appContext, requestCode.hashCode(), intent, PENDING_INTENT_FLAGS)
    }

    private fun buildMessageNotification(
        channelId: String,
        title: String,
        description: String?,
    ): NotificationCompat.Builder {
        return appContext.buildNotification(
            channelId,
            defaultSmallIcon,
            title,
            description,
        ).setCategory(Notification.CATEGORY_EMAIL)
    }

    private fun Context.undeterminedProgressMessageNotificationBuilder(
        @StringRes channelIdRes: Int,
        @StringRes titleRes: Int,
        priority: Int = NotificationCompat.PRIORITY_MIN,
    ) = NotificationCompat.Builder(this, getString(channelIdRes))
        .setContentTitle(getString(titleRes))
        .setSmallIcon(defaultSmallIcon)
        .setProgress(100, 0, true)
        .setPriority(priority)

    private fun initMessageNotificationContent(
        mailbox: Mailbox,
        contentIntent: PendingIntent?,
        notificationBuilder: NotificationCompat.Builder,
        payload: NotificationPayload,
    ) = notificationBuilder.apply {

        if (payload.isSummary) {
            setContentTitle(null)
            setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        } else {
            addActions(payload)
        }

        setOnlyAlertOnce(true)
        setSubText(mailbox.email)
        setContentText(payload.content)
        setColorized(true)
        setContentIntent(contentIntent)
        setGroup(mailbox.notificationGroupKey)
        setGroupSummary(payload.isSummary)
        setExtras(Bundle().apply { putString(EXTRA_MESSAGE_UID, payload.messageUid) })
        color = localSettings.accentColor.getPrimary(appContext)

        SentryLog.i(TAG, "Display notification | Email: ${mailbox.email} | MessageUid: ${payload.messageUid}")

        val notificationWithIdAndTag = NotificationWithIdAndTag(payload.notificationId, build())
        notificationsByMailboxId.getOrPut(mailbox.mailboxId) { mutableListOf() }.add(index = 0, notificationWithIdAndTag)
    }

    private fun showNotifications(
        scope: CoroutineScope,
        mailboxId: Int,
        notificationManagerCompat: NotificationManagerCompat,
    ) {
        notificationsJobByMailboxId[mailboxId]?.cancel()
        notificationsJobByMailboxId[mailboxId] = scope.launch {
            delay(DELAY_DEBOUNCE_NOTIF_MS)
            ensureActive()

            @Suppress("MissingPermission")
            notificationsByMailboxId[mailboxId]?.let { notifications ->
                notificationManagerCompat.notify(notifications.toList())
                notifications.clear()
            }
        }
    }

    @SuppressLint("WrongConstant")
    private fun NotificationCompat.Builder.addActions(payload: NotificationPayload) {

        fun createBroadcastAction(@StringRes title: Int, intent: Intent): NotificationCompat.Action {
            val requestCode = UUID.randomUUID().hashCode()
            val pendingIntent = PendingIntent.getBroadcast(appContext, requestCode, intent, PENDING_INTENT_FLAGS)
            return NotificationCompat.Action(null, appContext.getString(title), pendingIntent)
        }

        fun createActivityAction(@StringRes title: Int, activity: Class<*>, args: Bundle): NotificationCompat.Action {
            val requestCode = UUID.randomUUID().hashCode()
            val intent = Intent(appContext, activity).clearStack().putExtras(args)
            val pendingIntent = PendingIntent.getActivity(appContext, requestCode, intent, PENDING_INTENT_FLAGS)
            return NotificationCompat.Action(null, appContext.getString(title), pendingIntent)
        }

        fun createBroadcastIntent(notificationAction: String): Intent {
            return Intent(appContext, NotificationActionsReceiver::class.java).apply {
                action = notificationAction
                putExtra(EXTRA_PAYLOAD, payload)
            }
        }

        if (payload.isUndo) {
            val undoAction = createBroadcastAction(
                title = RCore.string.buttonCancel,
                intent = createBroadcastIntent(UNDO_ACTION),
            )

            addAction(undoAction)
            return
        }

        val archiveAction = createBroadcastAction(
            title = R.string.actionArchive,
            intent = createBroadcastIntent(ARCHIVE_ACTION),
        )
        val deleteAction = createBroadcastAction(
            title = R.string.actionDelete,
            intent = createBroadcastIntent(DELETE_ACTION),
        )
        val replyAction = createActivityAction(
            title = R.string.actionReply,
            activity = LaunchActivity::class.java,
            args = LaunchActivityArgs(
                userId = payload.userId,
                mailboxId = payload.mailboxId,
                replyToMessageUid = payload.messageUid,
                draftMode = DraftMode.REPLY,
                notificationId = payload.notificationId,
            ).toBundle(),
        )

        addAction(archiveAction)
        addAction(deleteAction)
        addAction(replyAction)
    }

    suspend fun updateUserAndMailboxes(mailboxController: MailboxController, tag: String) {
        // Refresh User
        AccountUtils.updateCurrentUser()

        // Refresh Mailboxes
        SentryLog.d(tag, "Refresh mailboxes from remote")
        with(ApiRepository.getMailboxes()) {
            if (isSuccess()) mailboxController.updateMailboxes(data!!)
        }
    }

    companion object : NotificationUtilsCore() {

        private val TAG: String = NotificationUtils::class.java.simpleName

        private const val DELAY_DEBOUNCE_NOTIF_MS = 1_500L

        @Suppress("MayBeConstant")
        private val defaultSmallIcon = R.drawable.ic_logo_notification

        const val DRAFT_ACTIONS_ID = 1
        const val SYNC_MESSAGES_ID = 2
        const val EXTRA_MESSAGE_UID = "messageUid"

        val GENERIC_NEW_MAILS_NOTIFICATION_ID = "genericNewMailsNotificationId".hashCode()

        fun Context.deleteMailNotificationChannel(mailbox: List<Mailbox>) {
            deleteNotificationChannels(mailbox.map { it.channelId })
        }
    }
}
