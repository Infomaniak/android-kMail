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
package com.infomaniak.mail.firebase

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.infomaniak.core.isChannelEnabled
import com.infomaniak.core.isChannelEnabledFlow
import com.infomaniak.core.notifications.registration.AbstractNotificationsRegistrationWorker
import com.infomaniak.core.twofactorauth.back.notifications.TwoFactorAuthNotifications
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.utils.AccountUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import splitties.systemservices.notificationManager

@HiltWorker
class RegisterUserDeviceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val mailboxController: MailboxController,
) : AbstractNotificationsRegistrationWorker(appContext, params) {

    override suspend fun getConnectedHttpClient(userId: Int) = AccountUtils.getHttpClient(userId)

    override suspend fun currentTopicsForUser(userId: Int) = getNotificationTopicsForUser(mailboxController, userId)
}

fun notificationTopicsForUser(
    mailboxController: MailboxController,
    userId: Int,
): Flow<List<String>> {
    return combine(
        mailboxController.getMailboxesAsync(userId = userId),
        notificationManager.isChannelEnabledFlow(TwoFactorAuthNotifications.CHANNEL_ID)
    ) { mailboxes, canShow2faNotifications ->
        getNotificationTopicsForUser(
            mailboxes = mailboxes,
            canShow2faNotifications = canShow2faNotifications
        )
    }
}

private suspend fun getNotificationTopicsForUser(
    mailboxController: MailboxController,
    userId: Int,
): List<String> {
    return getNotificationTopicsForUser(
        mailboxes = mailboxController.getMailboxes(userId = userId),
        canShow2faNotifications = notificationManager.isChannelEnabled(TwoFactorAuthNotifications.CHANNEL_ID)
    )
}

private fun getNotificationTopicsForUser(
    mailboxes: List<Mailbox>,
    canShow2faNotifications: Boolean?,
): List<String> = buildList(mailboxes.size + 1) {
    mailboxes.forEach { mailbox ->
        add("mailbox-${mailbox.mailboxId}")
    }
    when (canShow2faNotifications) {
        true, null -> { // Should not be null if channels are created before, and are not deleted.
            add(TwoFactorAuthNotifications.TOPIC)
        }
        false -> Unit
    }
}
