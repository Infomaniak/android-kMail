/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import com.infomaniak.lib.core.utils.serializableExtra
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackNotificationActionEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.FeatureAvailability
import com.infomaniak.mail.utils.NotificationPayload
import com.infomaniak.mail.utils.NotificationPayload.NotificationBehavior
import com.infomaniak.mail.utils.NotificationPayload.NotificationBehavior.NotificationType
import com.infomaniak.mail.utils.NotificationUtils
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.extensions.atLeastOneSucceeded
import com.infomaniak.mail.utils.extensions.getApiException
import com.infomaniak.mail.utils.extensions.getUids
import dagger.hilt.android.AndroidEntryPoint
import io.realm.kotlin.Realm
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var folderController: FolderController

    @Inject
    lateinit var localSettings: LocalSettings

    @Inject
    lateinit var mailboxController: MailboxController

    @Inject
    lateinit var notificationJobsBus: NotificationJobsBus

    @Inject
    lateinit var notificationManagerCompat: NotificationManagerCompat

    @Inject
    lateinit var notificationUtils: NotificationUtils

    @Inject
    lateinit var refreshController: RefreshController

    @Inject
    lateinit var sharedUtils: SharedUtils

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    lateinit var globalCoroutineScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        globalCoroutineScope.launch(ioDispatcher) {
            val payload = intent.serializableExtra(EXTRA_PAYLOAD) as? NotificationPayload ?: return@launch
            val action = intent.action!!
            handleNotificationIntent(context, payload, action)
        }
    }

    private fun handleNotificationIntent(context: Context, payload: NotificationPayload, action: String) {
        // Undo action
        if (action == UNDO_ACTION) {
            trackNotificationActionEvent(MatomoName.CancelClicked)
            executeUndoAction(payload)
            return
        }

        // Other actions
        val (folderRole, undoNotificationTitle, matomoName) = when (action) {
            ARCHIVE_ACTION -> {
                trackNotificationActionEvent(MatomoName.ArchiveClicked)
                Triple(FolderRole.ARCHIVE, R.string.notificationTitleArchive, MatomoName.ArchiveExecuted)
            }
            DELETE_ACTION -> {
                trackNotificationActionEvent(MatomoName.DeleteClicked)
                Triple(FolderRole.TRASH, R.string.notificationTitleDelete, MatomoName.DeleteExecuted)
            }
            else -> null
        } ?: return

        executeAction(context, folderRole, undoNotificationTitle, matomoName, payload)
    }

    private fun executeUndoAction(payload: NotificationPayload) {

        // Cancel action
        notificationJobsBus.unregister(payload.notificationId)

        notificationUtils.showMessageNotification(
            notificationManagerCompat = notificationManagerCompat,
            payload = payload.apply { behavior = null },
        )
    }

    private fun executeAction(
        context: Context,
        folderRole: FolderRole,
        @StringRes undoNotificationTitle: Int,
        matomoName: MatomoName,
        payload: NotificationPayload,
    ) = with(payload) {

        notificationUtils.showMessageNotification(
            notificationManagerCompat = notificationManagerCompat,
            payload = payload.apply {
                behavior = NotificationBehavior(
                    type = NotificationType.UNDO,
                    behaviorTitle = context.getString(undoNotificationTitle),
                )
            },
        )

        val job = globalCoroutineScope.launch(ioDispatcher) {

            delay(UNDO_TIMEOUT)
            ensureActive()

            val realm = RealmDatabase.newMailboxContentInstance(userId, mailboxId)
            val message = MessageController.getMessage(messageUid!!, realm) ?: return@launch
            val threads = message.threads.filter { it.folderId == message.folderId }

            val mailbox = mailboxController.getMailbox(userId, mailboxId) ?: return@launch
            val messages = sharedUtils.getMessagesToMove(threads, message)
            val destinationFolder = folderController.getFolder(folderRole) ?: return@launch
            val okHttpClient = AccountUtils.getHttpClient(userId)

            trackNotificationActionEvent(matomoName)

            val responses = ApiRepository.moveMessages(
                mailboxUuid = mailbox.uuid,
                messagesUids = messages.getUids(),
                destinationId = destinationFolder.id,
                alsoMoveReactionMessages = FeatureAvailability.isReactionsAvailable(mailbox.featureFlags, localSettings),
                okHttpClient = okHttpClient,
            )
            with(responses) {
                if (atLeastOneSucceeded()) {
                    dismissNotification(context, mailbox, notificationId)
                    updateFolders(folders = listOf(message.folder, destinationFolder), mailbox, realm)
                } else {
                    executeUndoAction(payload)
                    Sentry.captureException(first().getApiException()) { scope ->
                        scope.setTag("reason", "Notif action fail because of API call")
                        scope.setExtra("destination folder role", folderRole.name)
                    }
                }
            }
        }

        notificationJobsBus.register(notificationId, job)
    }

    private suspend fun updateFolders(
        folders: List<Folder>,
        mailbox: Mailbox,
        realm: Realm,
    ) {
        folders.forEach { folder ->
            refreshController.refreshThreads(
                refreshMode = RefreshMode.REFRESH_FOLDER,
                mailbox = mailbox,
                folderId = folder.id,
                realm = realm,
            )
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
        const val EXTRA_PAYLOAD = "extra_payload"
        const val ARCHIVE_ACTION = "archive_action"
        const val DELETE_ACTION = "delete_action"
        const val UNDO_ACTION = "undo_action"

        private const val UNDO_TIMEOUT = 6_000L
    }
}
