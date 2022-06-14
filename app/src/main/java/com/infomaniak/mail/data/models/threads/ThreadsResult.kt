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
package com.infomaniak.mail.data.models.threads

import com.google.gson.annotations.SerializedName

data class ThreadsResult(
    val threads: ArrayList<Thread>?,

    @SerializedName("total_messages_count")
    val totalMessagesCount: Int = 0,

    @SerializedName("messages_count")
    val messagesCount: Int = 0,

    @SerializedName("current_offset")
    val currentOffset: Int = 0,

    @SerializedName("thread_mode")
    val threadMode: String = "on",

    @SerializedName("folder_unseen_messages")
    val folderUnseenMessage: Int = 0,

    @SerializedName("resource_previous")
    val resourcePrevious: String? = null,

    @SerializedName("resource_next")
    val resourceNext: String? = null,
)
