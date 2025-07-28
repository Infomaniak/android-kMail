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
package com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies

import com.infomaniak.mail.data.models.Snoozable
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.message.Message.Companion.parseMessagesIds
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.computeReactionsPerMessageId
import com.infomaniak.mail.data.models.thread.overrideWith
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.types.RealmList

object ThreadRecomputations {
    fun Thread.recomputeThread(realm: MutableRealm? = null) {

        messages.sortBy { it.internalDate }
        // All of the following methods should not be inside of Thread to begin with. At least the input list of messages is
        // extracted once so every other following logic is forced to base its processing on this unique list of messages. We
        // avoid side effects and unnecessary coupling
        val allMessages = messages

        val lastCurrentFolderMessage = allMessages.lastOrNull { it.folderId == folderId }
        val lastMessage = if (isFromSearch) {
            // In the search, some threads (such as threads from the snooze folder) won't have any messages with the same folderId
            // as the thread folderId. This is an expected behavior and we don't want to delete it in this case. We just need to
            // fallback on the last message of the thread.
            lastCurrentFolderMessage ?: allMessages.lastOrNull()
        } else {
            lastCurrentFolderMessage
        }

        if (lastMessage == null) {
            // Delete Thread if empty. Do not rely on this deletion code being part of the method's logic, it's a temporary fix. If
            // threads should be deleted, then they need to be deleted outside this method.
            if (isManaged()) realm?.delete(this)
            return
        }

        resetThread()

        updateThread(lastMessage, allMessages)

        recomputeMessagesWithContent(allMessages)

        // Remove duplicates in Recipients lists
        val unmanagedFrom = if (from.getRealm<Realm>() == null) from else from.copyFromRealm()
        val unmanagedTo = if (to.getRealm<Realm>() == null) to else to.copyFromRealm()
        from = unmanagedFrom.distinct().toRealmList()
        to = unmanagedTo.distinct().toRealmList()
    }

    private fun Thread.resetThread() {
        unseenMessagesCount = 0
        from = realmListOf()
        to = realmListOf()
        hasDrafts = false
        isFavorite = false
        isAnswered = false
        isForwarded = false
        hasAttachable = false
        numberOfScheduledDrafts = 0
        snoozeState = null
        snoozeEndDate = null
        snoozeUuid = null
        isLastInboxMessageSnoozed = false
    }

    private fun Thread.updateThread(lastMessage: Message, allMessages: RealmList<Message>) {

        fun Thread.updateSnoozeStatesBasedOn(message: Message) {
            message.snoozeState?.let {
                snoozeState = it
                snoozeEndDate = message.snoozeEndDate
                snoozeUuid = message.snoozeUuid
            }
        }

        allMessages.forEach { message ->
            messagesIds += message.messageIds
            if (!message.isSeen) unseenMessagesCount++
            from += message.from
            to += message.to
            if (message.isDraft) hasDrafts = true
            if (message.isFavorite) isFavorite = true
            if (message.isAnswered) {
                isAnswered = true
                isForwarded = false
            }
            if (message.isForwarded) {
                isForwarded = true
                isAnswered = false
            }
            if (message.hasAttachable) hasAttachable = true
            if (message.isScheduledDraft) numberOfScheduledDrafts++

            updateSnoozeStatesBasedOn(message)
        }

        duplicates.forEach { message ->
            if (!message.isSeen) unseenMessagesCount++
            updateSnoozeStatesBasedOn(message)
        }

        displayDate = lastMessage.displayDate
        internalDate = lastMessage.internalDate
        subject = allMessages.first().subject

        isLastInboxMessageSnoozed = allMessages.isLastInboxMessageSnoozed(folderId)
    }

    /**
     * This method determines whether the last inbox message in the list is snoozed. If there are none, it returns false.
     *
     * Instead of querying Realm every time to retrieve the inbox folder ID, we rely on the fact that [Snoozable.isSnoozed] only
     * returns `true` for messages whose [Message.folderId] matches the inbox folder ID.
     *
     * To illustrate the reasoning:
     * | folderId == inbox | isSnoozed() | Comment                        | Result         |
     * |-------------------|-------------|--------------------------------|----------------|
     * | false             | false       | isSnoozed always returns false | false          |
     * | false             | true        | This situation doesn't exist   | doesn't matter |
     * |-------------------|-------------|--------------------------------|----------------|
     * | true              | false       |                                | false          |
     * | true              | true        |                                | true           |
     *
     * => Only returns true when the last message of inbox is snoozed
     */
    @Suppress("NullableBooleanElvis")
    private fun List<Message>.isLastInboxMessageSnoozed(threadFolderId: String): Boolean {
        return lastOrNull { it.folderId == threadFolderId }?.isSnoozed() ?: false
    }

    fun Thread.recomputeMessagesWithContent(allMessages: List<Message>) {
        val (reactionsPerMessageId, threadMessageIds) = computeReactionsPerMessageId(allMessages)

        messagesWithContent.clear()
        allMessages.forEach { message ->
            reactionsPerMessageId[message.messageId]?.let { reactions ->
                message.emojiReactions.overrideWith(reactions)
            }

            val targetMessageIds = message.inReplyTo ?: ""
            val isHiddenEmojiReaction = message.isReaction && isTargetMessageInThread(targetMessageIds, threadMessageIds)
            if (isHiddenEmojiReaction.not()) messagesWithContent += message
        }
    }

    private fun isTargetMessageInThread(targetMessageIds: String, threadMessageIds: Set<String>): Boolean {
        return targetMessageIds.parseMessagesIds().any(threadMessageIds::contains)
    }
}
