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

import com.infomaniak.mail.utils.NotificationPayload.NotificationBehavior.NotificationType
import java.io.Serializable

/**
 * This payload contains all data needed to create a Message notification and its alternative behaviors.
 */
data class NotificationPayload(
    val userId: Int,
    val mailboxId: Int,
    val threadUid: String,
    val messageUid: String? = null,
    var notificationId: Int,
    var behavior: NotificationBehavior? = null,
    private val payloadTitle: String? = null,
    private val payloadContent: String? = null,
    private val payloadDescription: String? = null,
) : Serializable {

    val isSummary get() = behavior?.type == NotificationType.SUMMARY
    val isUndo get() = behavior?.type == NotificationType.UNDO

    val title get() = (if (behavior != null) behavior?.behaviorTitle else payloadTitle) ?: ""
    val content get() = if (behavior != null) behavior?.behaviorContent else payloadContent
    val description get() = if (behavior != null) behavior?.behaviorDescription else payloadDescription

    data class NotificationBehavior(
        val type: NotificationType,
        val behaviorTitle: String? = null,
        val behaviorContent: String? = null,
        val behaviorDescription: String? = null,
    ) : Serializable {
        enum class NotificationType : Serializable {
            SUMMARY,
            UNDO,
        }
    }
}
