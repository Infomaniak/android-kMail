/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.mail.data.models.thread

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThreadsResult(
    val threads: List<Thread> = emptyList(),
    @SerialName("total_messages_count")
    val totalMessagesCount: Int = 0,
    @SerialName("messages_count")
    val messagesCount: Int = 0,
    @SerialName("current_offset")
    val currentOffset: Int = 0,
    @SerialName("thread_mode")
    val threadMode: String = "on",
    @SerialName("folder_unseen_messages")
    val folderUnseenMessage: Int = 0,
    @SerialName("resource_previous")
    val resourcePrevious: String? = null,
    @SerialName("resource_next")
    val resourceNext: String? = null,
)
