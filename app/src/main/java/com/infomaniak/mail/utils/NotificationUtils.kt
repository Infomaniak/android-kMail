/*
 * Infomaniak Mail - Android
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
package com.infomaniak.mail.utils

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.infomaniak.lib.core.utils.NotificationUtilsCore
import com.infomaniak.lib.core.utils.clearStack
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.receivers.NotificationActionsReceiver
import com.infomaniak.mail.receivers.NotificationActionsReceiver.Companion.ARCHIVE_ACTION
import com.infomaniak.mail.receivers.NotificationActionsReceiver.Companion.DELETE_ACTION
import com.infomaniak.mail.receivers.NotificationActionsReceiver.Companion.EXTRA_PAYLOAD
import com.infomaniak.mail.receivers.NotificationActionsReceiver.Companion.UNDO_ACTION
import com.infomaniak.mail.ui.LaunchActivity
import com.infomaniak.mail.ui.LaunchActivityArgs
import java.util.UUID
import com.infomaniak.lib.core.R as RCore

object NotificationUtils : NotificationUtilsCore() {

    private const val DEFAULT_SMALL_ICON = R.drawable.ic_logo_notification

    const val DRAFT_ACTIONS_ID = 1

    fun Context.initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelList = mutableListOf<NotificationChannel>()

            val generalChannel = buildNotificationChannel(
                getString(R.string.notification_channel_id_general),
                getString(R.string.notificationGeneralChannelName),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channelList.add(generalChannel)

            val draftChannel = buildNotificationChannel(
                getString(R.string.notification_channel_id_draft_service),
                getString(R.string.notificationSyncDraftChannelName),
                NotificationManager.IMPORTANCE_MIN
            )
            channelList.add(draftChannel)

            val syncMessagesChannel = buildNotificationChannel(
                getString(R.string.notification_channel_id_sync_messages_service),
                getString(R.string.notificationSyncMessagesChannelName),
                NotificationManager.IMPORTANCE_MIN
            )
            channelList.add(syncMessagesChannel)

            createNotificationChannels(channelList)
        }
    }

    fun Context.initMailNotificationChannel(mailboxes: List<Mailbox>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val groups = mutableListOf<NotificationChannelGroup>()
            val channels = mutableListOf<NotificationChannel>()

            mailboxes.forEach {
                val group = NotificationChannelGroup(it.channelGroupId, it.email)
                groups.add(group)

                val channel = buildNotificationChannel(
                    channelId = it.channelId,
                    name = getString(R.string.notificationNewMessagesChannelName),
                    importance = NotificationManager.IMPORTANCE_HIGH,
                    groupId = group.id,
                )
                channels.add(channel)
            }

            createNotificationChannels(channels, groups)
        }
    }

    fun Context.deleteMailNotificationChannel(mailbox: List<Mailbox>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            deleteNotificationChannels(mailbox.map { it.channelId })
        }
    }

    fun Context.buildGeneralNotification(title: String, description: String? = null): NotificationCompat.Builder {
        return buildNotification(
            channelId = getString(R.string.notification_channel_id_general),
            icon = DEFAULT_SMALL_ICON,
            title = title,
            description = description,
        )
    }

    private fun Context.buildMessageNotification(
        channelId: String,
        title: String,
        description: String?,
    ): NotificationCompat.Builder {
        return buildNotification(channelId, DEFAULT_SMALL_ICON, title, description)
            .setCategory(Notification.CATEGORY_EMAIL)
    }

    fun Context.buildDraftActionsNotification(): NotificationCompat.Builder {
        val channelId = getString(R.string.notification_channel_id_draft_service)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notificationSyncDraftChannelName))
            .setSmallIcon(DEFAULT_SMALL_ICON)
            .setProgress(100, 0, true)
    }

    fun Context.buildDraftErrorNotification(@StringRes errorMessageRes: Int, action: DraftAction): NotificationCompat.Builder {
        return buildGeneralNotification(
            title = getString(if (action == DraftAction.SEND) R.string.notificationTitleCouldNotSendDraft else R.string.notificationTitleCouldNotSaveDraft),
            description = getString(errorMessageRes),
        )
    }

    fun showMessageNotification(
        context: Context,
        notificationManagerCompat: NotificationManagerCompat,
        payload: NotificationPayload,
    ) = with(payload) {

        fun contentIntent(mailboxUuid: String, isSummary: Boolean, isUndo: Boolean): PendingIntent? {
            if (isUndo) return null

            val intent = Intent(context, LaunchActivity::class.java).clearStack().putExtras(
                LaunchActivityArgs(
                    userId = userId,
                    mailboxId = mailboxId,
                    openThreadUid = if (isSummary) null else threadUid,
                ).toBundle(),
            )
            val requestCode = if (isSummary) mailboxUuid else threadUid
            return PendingIntent.getActivity(context, requestCode.hashCode(), intent, pendingIntentFlags)
        }

        val mailbox = MailboxController.getMailbox(userId, mailboxId) ?: run {
            SentryDebug.sendFailedNotification("Created Notif: no Mailbox in Realm", userId, mailboxId, messageUid)
            return@with
        }

        context.buildMessageNotification(mailbox.channelId, title, description).apply {

            if (isSummary) {
                setContentTitle(null)
                setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            } else {
                addActions(context, payload)
            }

            setSubText(mailbox.email)
            setContentText(content)
            setColorized(true)
            setContentIntent(contentIntent(mailbox.uuid, isSummary, isUndo))
            setGroup(mailbox.notificationGroupKey)
            setGroupSummary(isSummary)
            color = LocalSettings.getInstance(context).accentColor.getPrimary(context)

            @Suppress("MissingPermission")
            notificationManagerCompat.notify(notificationId, build())
        }
    }

    private fun NotificationCompat.Builder.addActions(context: Context, payload: NotificationPayload) {

        fun createBroadcastAction(@StringRes title: Int, intent: Intent): NotificationCompat.Action {
            val requestCode = UUID.randomUUID().hashCode()
            val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, pendingIntentFlags)
            return NotificationCompat.Action(null, context.getString(title), pendingIntent)
        }

        fun createActivityAction(@StringRes title: Int, activity: Class<*>, args: Bundle): NotificationCompat.Action {
            val requestCode = UUID.randomUUID().hashCode()
            val intent = Intent(context, activity).clearStack().putExtras(args)
            val pendingIntent = PendingIntent.getActivity(context, requestCode, intent, pendingIntentFlags)
            return NotificationCompat.Action(null, context.getString(title), pendingIntent)
        }

        fun createBroadcastIntent(notificationAction: String): Intent {
            return Intent(context, NotificationActionsReceiver::class.java).apply {
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
}
