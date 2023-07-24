/*
 * Infomaniak ikMail - Android
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
package com.infomaniak.mail.data.models.thread

import com.infomaniak.mail.data.models.message.Message

@Suppress("DataClassPrivateConstructor")
data class SelectedThread private constructor(
    val uid: String,
    val isFavorite: Boolean,
    val unseenMessagesCount: Int,
    private val messagesCount: Int,
    private val firstMessage: Message,
) {

    constructor(thread: Thread) : this(
        uid = thread.uid,
        isFavorite = thread.isFavorite,
        unseenMessagesCount = thread.unseenMessagesCount,
        messagesCount = thread.messages.count(),
        firstMessage = thread.messages.first(),
    )

    val isSingleMessage get() = messagesCount == 1
    val messageUid get() = firstMessage.uid

    override fun equals(other: Any?) = other === this || (other is SelectedThread && other.uid == uid)

    override fun hashCode(): Int = uid.hashCode()
}
