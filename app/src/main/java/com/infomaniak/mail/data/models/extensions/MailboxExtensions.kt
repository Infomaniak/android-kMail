/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.mail.data.models.extensions

import androidx.core.app.NotificationManagerCompat
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.utils.UnreadDisplay
import com.infomaniak.mail.utils.extensions.getDefault

inline val Mailbox.kSuite: KSuite?
    get() = when {
        // For KSuite Pro tiers, only Free & Standard are relevant in kMail, all Pro paid tiers got the same functionalities
        isKSuitePro && isKSuiteProFree -> KSuite.Pro.Free
        isKSuitePro && !isKSuiteProFree -> KSuite.Pro.Standard
        isKSuitePerso && isLimited -> KSuite.Perso.Free
        isKSuitePerso && !isLimited -> KSuite.Perso.Plus
        isPartOfStarterPack -> KSuite.StarterPack
        else -> null // It's an older offer, but it checks out.
    }

fun Mailbox.notificationsIsDisabled(notificationManagerCompat: NotificationManagerCompat): Boolean =
    with(notificationManagerCompat) {
        val isGroupBlocked = getNotificationChannelGroupCompat(channelGroupId)?.isBlocked == true
        val isChannelBlocked = getNotificationChannelCompat(channelId)?.importance == NotificationManagerCompat.IMPORTANCE_NONE
        return@with !areNotificationsEnabled() || isGroupBlocked || isChannelBlocked
    }

val Mailbox.unreadCountDisplay: UnreadDisplay
    get() = UnreadDisplay(
        count = unreadCountLocal,
        shouldDisplayPastille = unreadCountLocal == 0 && unreadCountRemote > 0,
    )

fun Mailbox.getDefaultSignatureWithFallback(): Signature {
    return signatures.getDefault() ?: signatures.first()
}
