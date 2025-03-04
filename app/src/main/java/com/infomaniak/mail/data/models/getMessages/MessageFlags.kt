/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.mail.data.models.getMessages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageFlags(
    @SerialName("uid")
    override val shortUid: String,
    @SerialName("answered")
    val isAnswered: Boolean,
    @SerialName("flagged")
    val isFavorite: Boolean,
    @SerialName("forwarded")
    val isForwarded: Boolean,
    @SerialName("scheduled")
    val isScheduledMessage: Boolean,
    @SerialName("seen")
    val isSeen: Boolean,
) : CommonMessageFlags
