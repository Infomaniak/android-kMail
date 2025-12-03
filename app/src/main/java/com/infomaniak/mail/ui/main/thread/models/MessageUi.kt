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
package com.infomaniak.mail.ui.main.thread.models

import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.EmojiReactionUtils.hasAvailableReactionSlot

data class MessageUi(
    val message: Message,
    val emojiReactionsState: Map<String, EmojiReactionStateUi>,
    val isReactionsFeatureAvailable: Boolean,
    val hasUnsubscribeButton: Boolean
) {
    fun hasEmojis(): Boolean = emojiReactionsState.isNotEmpty()
    fun canBeReactedTo(): Boolean = message.isValidReactionTarget && emojiReactionsState.hasAvailableReactionSlot()
}
