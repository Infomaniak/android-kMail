/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2026 Infomaniak Network SA
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
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.infomaniak.mail.firebase

import android.content.Context
import android.os.Build.VERSION.SDK_INT
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.infomaniak.core.common.areChannelsEnabledFlow
import com.infomaniak.core.common.isChannelEnabledFlow
import com.infomaniak.core.notifications.registration.AbstractNotificationsRegistrationWorker
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.core.twofactorauth.back.notifications.TwoFactorAuthNotifications
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.utils.AccountUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import splitties.systemservices.notificationManager

@HiltWorker
class RegisterUserDeviceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val mailboxController: MailboxController,
) : AbstractNotificationsRegistrationWorker(appContext, params) {

    override suspend fun getConnectedHttpClient(userId: Int) = AccountUtils.getHttpClient(userId)

    override suspend fun currentTopicsForUser(userId: Int) = notificationTopicsForUser(mailboxController, userId).first()
}

fun notificationTopicsForUser(
    mailboxController: MailboxController,
    userId: Int,
): Flow<List<String>> = if (SDK_INT >= 28) {
    mailboxController.getMailboxesAsync(userId = userId).flatMapLatest { mailboxes ->
        val notificationChannelIds = mailboxes.map { it.channelId } + TwoFactorAuthNotifications.CHANNEL_ID
        notificationManager.areChannelsEnabledFlow(notificationChannelIds).map { channelEnabledStates ->
            buildNotificationTopicsList(
                mailboxes = mailboxes,
                shouldAddMailboxNotificationTopic = { mailbox -> channelEnabledStates[mailbox.channelId] == true },
                canShow2faNotifications = channelEnabledStates[TwoFactorAuthNotifications.CHANNEL_ID]
            )
        }
    }.distinctUntilChanged()
} else {
    combine(
        mailboxController.getMailboxesAsync(userId = userId),
        notificationManager.isChannelEnabledFlow(TwoFactorAuthNotifications.CHANNEL_ID)
    ) { mailboxes, canShow2faNotifications ->
        buildNotificationTopicsList(
            mailboxes = mailboxes,
            shouldAddMailboxNotificationTopic = {
                true // We don't filter on API 27 because getting notification enabled status reliably requires tricky polling.
            },
            canShow2faNotifications = canShow2faNotifications // Still allow the user to disable 2FA notifications on this app.
            // Even on API 27, ensure the app won't be selected over another one that has notifications enabled.
        )
    }.distinctUntilChanged()
}

private inline fun buildNotificationTopicsList(
    mailboxes: List<Mailbox>,
    shouldAddMailboxNotificationTopic: (Mailbox) -> Boolean,
    canShow2faNotifications: Boolean?
): List<String> {
    return buildList(capacity = mailboxes.size + 1) {
        mailboxes.forEach { mailbox -> if (shouldAddMailboxNotificationTopic(mailbox)) add(mailbox.notificationTopic()) }
        when (canShow2faNotifications) {
            true -> add(TwoFactorAuthNotifications.TOPIC)
            false -> Unit
            null -> SentryLog.wtf(TAG, errorMessageNull2faChannel)
        }
    }
}

private fun Mailbox.notificationTopic() = "mailbox-$mailboxId"

private const val TAG = "RegisterUserDeviceWorker"
private const val errorMessageNull2faChannel = "The channel was created too late, or was deleted by the app!"
