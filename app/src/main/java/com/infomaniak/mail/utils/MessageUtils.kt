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
package com.infomaniak.mail.utils

import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread

object MessageUtils {

    suspend fun getJunkMessagesAndMessagesToBlockUsers(
        threadController: ThreadController,
        messageController: MessageController,
        featureFlagsLive: Mailbox.FeatureFlagSet?,
        threadsUids: List<String>,
        localSettings: LocalSettings,
    ): JunkMessagesData {
        val threadList = threadController.getThreads(threadsUids)
        val messagesFromUsersToBlock: MutableMap<Recipient, Message> = mutableMapOf()
        val lastMessagesOfThreads = threadList.map { thread ->
            thread.getDisplayedMessages(featureFlagsLive, localSettings).forEach { message ->
                message.from.firstOrNull()?.let { user ->
                    if (!user.isMe() && user !in messagesFromUsersToBlock) messagesFromUsersToBlock.put(user, message)
                }
            }

            messageController.getLastMessageToExecuteAction(thread, featureFlagsLive)
        }

        return JunkMessagesData(junkMessages = lastMessagesOfThreads, messagesFromUsersToBlock = messagesFromUsersToBlock)
    }
}

sealed class ThreadMessageInteraction(open val messageUid: String) {
    data class Action(val thread: Thread, override val messageUid: String) : ThreadMessageInteraction(messageUid)
    data class Reaction(override val messageUid: String): ThreadMessageInteraction(messageUid)
}

data class ThreadMessageToExecuteInteraction(
    val thread: Thread,
    val messageUidToExecuteAction: String,
    val messageUidToExecuteReaction: String?
)
data class JunkMessagesData(val junkMessages: List<Message>, val messagesFromUsersToBlock: Map<Recipient, Message>)
