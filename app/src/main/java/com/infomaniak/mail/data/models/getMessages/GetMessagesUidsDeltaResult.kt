/*
 * Infomaniak kMail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
data class GetMessagesUidsDeltaResult(
    @SerialName("deleted")
    val deletedShortUids: List<String>,
    @SerialName("added")
    val addedShortUids: List<String>,
    @SerialName("updated")
    val updatedMessages: List<MessageFlags>,
    @SerialName("signature")
    val cursor: String,
) {
    @Serializable
    data class MessageFlags(
        @SerialName("uid")
        val shortUid: String,
        val answered: Boolean,
        @SerialName("flagged")
        val isFavorite: Boolean,
        val forwarded: Boolean,
        val scheduled: Boolean,
        val seen: Boolean,
    )
}
