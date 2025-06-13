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
package com.infomaniak.mail.data.models.thread

import com.infomaniak.mail.data.models.message.EmojiReactionState
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.message.Message.Companion.parseMessagesIds
import io.realm.kotlin.types.RealmDictionary

fun computeReactionsPerMessageId(allMessages: List<Message>): ReactionData {
    val reactionsPerMessageId = mutableMapOf<String, MutableMap<String, EmojiReactionState>>()
    val messageIds = mutableSetOf<String>()

    for (message in allMessages) {
        message.messageId?.let { messageIds += it }
        reactionsPerMessageId.addReactionsOf(message)
    }

    return ReactionData(reactionsPerMessageId, messageIds)
}

private fun MutableMap<String, MutableMap<String, EmojiReactionState>>.addReactionsOf(message: Message) {
    val emoji = message.emojiReaction ?: return

    val replyToIds = message.inReplyTo?.parseMessagesIds() ?: emptyList()
    replyToIds.forEach { replyToId ->
        val emojis = getOrPut(replyToId) { emptyEmojiReaction(emoji) }

        if (emojis.containsKey(emoji).not()) {
            emojis[emoji] = EmojiReactionState()
        }

        emojis[emoji]!!.apply {
            count += 1
            hasReacted = message.from.any { it.isMe() }
        }
    }
}

data class ReactionData(
    val reactionsPerMessageId: Map<String, MutableMap<String, EmojiReactionState>>,
    val messageIds: Set<String>,
)

private fun emptyEmojiReaction(emoji: String) = mutableMapOf(emoji to EmojiReactionState())

fun RealmDictionary<EmojiReactionState?>.overrideWith(reactions: Map<String, EmojiReactionState>) {
    clear()
    reactions.forEach { (emoji, state) ->
        this[emoji] = state
    }
}
