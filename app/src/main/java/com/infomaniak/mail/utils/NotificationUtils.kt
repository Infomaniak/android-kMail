/*
 * Infomaniak ikMail - Android
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
import com.infomaniak.mail.data.models.draft.Draft.*
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.receivers.NotificationActionsReceiver
import com.infomaniak.mail.receivers.NotificationActionsReceiver.Companion.ARCHIVE_ACTION
import com.infomaniak.mail.receivers.NotificationActionsReceiver.Companion.DELETE_ACTION
import com.infomaniak.mail.receivers.NotificationActionsReceiver.Companion.EXTRA_PAYLOAD
import com.infomaniak.mail.receivers.NotificationActionsReceiver.Companion.UNDO_ACTION
import com.infomaniak.mail.ui.LaunchActivity
import com.infomaniak.mail.ui.LaunchActivityArgs
import com.infomaniak.mail.utils.NotificationUtils.NotificationPayload.*
import com.infomaniak.mail.utils.NotificationUtils.NotificationPayload.NotificationBehavior.*
import java.io.Serializable
import java.util.UUID

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

    fun Context.initMailNotificationChannel(mailbox: List<Mailbox>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelList = mutableListOf<NotificationChannel>()
            val groupList = mutableListOf<NotificationChannelGroup>()

            mailbox.forEach {
                val channelGroup = NotificationChannelGroup(it.channelGroupId, it.email)
                groupList.add(channelGroup)

                val notificationChannel = buildNotificationChannel(
                    it.channelId,
                    getString(R.string.notificationNewMessagesChannelName),
                    NotificationManager.IMPORTANCE_HIGH,
                    groupId = channelGroup.id
                )
                channelList.add(notificationChannel)
            }

            createNotificationChannels(channelList, groupList)
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

    fun Context.buildNewMessageNotification(channelId: String, title: String, description: String?): NotificationCompat.Builder {
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

    fun showNotification(
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

        val mailbox = MailboxController.getMailbox(userId, mailboxId) ?: return@with

        context.buildNewMessageNotification(mailbox.channelId, title, description).apply {

            val notificationId = if (isSummary) mailbox.notificationGroupId else threadUid.hashCode()

            if (isSummary) {
                setContentTitle(null)
                setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            } else {
                addActions(context, notificationId, payload)
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

    private fun NotificationCompat.Builder.addActions(
        context: Context,
        notificationId: Int,
        payload: NotificationPayload,
    ) = with(payload) {

        if (isUndo) {
            val undoAction = context.createBroadcastAction(
                title = R.string.buttonCancel,
                intent = context.createBroadcastIntent(UNDO_ACTION, payload),
            )

            addAction(undoAction)
            return@with
        }

        val archiveAction = context.createBroadcastAction(
            title = R.string.actionArchive,
            intent = context.createBroadcastIntent(ARCHIVE_ACTION, payload),
        )
        val deleteAction = context.createBroadcastAction(
            title = R.string.actionDelete,
            intent = context.createBroadcastIntent(DELETE_ACTION, payload),
        )
        val replyAction = context.createActivityAction(
            title = R.string.actionReply,
            activity = LaunchActivity::class.java,
            args = LaunchActivityArgs(
                userId = userId,
                mailboxId = mailboxId,
                replyToMessageUid = messageUid,
                draftMode = DraftMode.REPLY,
                notificationId = notificationId,
            ).toBundle(),
        )

        addAction(archiveAction)
        addAction(deleteAction)
        addAction(replyAction)
    }

    private fun Context.createBroadcastAction(@StringRes title: Int, intent: Intent): NotificationCompat.Action {
        val context = this
        val requestCode = UUID.randomUUID().hashCode()
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, pendingIntentFlags)
        return NotificationCompat.Action(null, getString(title), pendingIntent)
    }

    private fun Context.createActivityAction(@StringRes title: Int, activity: Class<*>, args: Bundle): NotificationCompat.Action {
        val context = this
        val requestCode = UUID.randomUUID().hashCode()
        val intent = Intent(context, activity).clearStack().putExtras(args)
        val pendingIntent = PendingIntent.getActivity(context, requestCode, intent, pendingIntentFlags)
        return NotificationCompat.Action(null, getString(title), pendingIntent)
    }

    private fun Context.createBroadcastIntent(notificationAction: String, payload: NotificationPayload): Intent {
        return Intent(this, NotificationActionsReceiver::class.java).apply {
            action = notificationAction
            putExtra(EXTRA_PAYLOAD, payload)
        }
    }

    data class NotificationPayload(
        val userId: Int,
        val mailboxId: Int,
        val threadUid: String,
        val messageUid: String? = null,
        var behavior: NotificationBehavior? = null,
        private val payloadTitle: String? = null,
        private val payloadContent: String? = null,
        private val payloadDescription: String? = null,
    ) : Serializable {

        data class NotificationBehavior(
            val type: NotificationType,
            val behaviorTitle: String? = null,
            val behaviorContent: String? = null,
            val behaviorDescription: String? = null,
            val undoResource: String? = null,
        ) : Serializable {
            enum class NotificationType : Serializable {
                SUMMARY,
                UNDO,
            }
        }

        val isSummary get() = behavior?.type == NotificationType.SUMMARY
        val isUndo get() = behavior?.type == NotificationType.UNDO

        val title get() = (if (behavior != null) behavior?.behaviorTitle else payloadTitle) ?: ""
        val content get() = if (behavior != null) behavior?.behaviorContent else payloadContent
        val description get() = if (behavior != null) behavior?.behaviorDescription else payloadDescription
    }
}
