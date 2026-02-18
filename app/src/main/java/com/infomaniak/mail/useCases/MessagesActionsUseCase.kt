/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.mail.useCases

import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.ImpactedFolders
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshCallbacks
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.MoveResult
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.FeatureAvailability
import com.infomaniak.mail.utils.FolderRoleUtils
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.extensions.atLeastOneFailed
import com.infomaniak.mail.utils.extensions.atLeastOneSucceeded
import com.infomaniak.mail.utils.extensions.getFoldersIds
import com.infomaniak.mail.utils.extensions.getUids
import javax.inject.Inject

class MessagesActionsUseCase @Inject constructor(
    private val folderController: FolderController,
    private val folderRoleUtils: FolderRoleUtils,
    private val localSettings: LocalSettings,
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val messageController: MessageController,
    private val threadController: ThreadController,
    private val sharedUtils: SharedUtils,
) {

    suspend fun toggleMessagesSpamStatus(
        messages: List<Message>,
        currentFolderId: String?,
        mailbox: Mailbox,
        callbacks: RefreshCallbacks? = null,
    ): MoveMessagesResult {
        val folder = if (currentFolderId != null) folderController.getFolder(currentFolderId) else null
        val folderRole = folderRoleUtils.getActionFolderRole(messages, folder)

        val destinationFolderRole = if (folderRole == FolderRole.SPAM) {
            FolderRole.INBOX
        } else {
            FolderRole.SPAM
        }
        val destinationFolder = folderController.getFolder(destinationFolderRole)!!

        val unscheduleMessages = messageController.getUnscheduledMessages(messages)

        return moveMessagesTo(destinationFolder, currentFolderId, mailbox, unscheduleMessages, callbacks)
    }

    suspend fun moveMessagesTo(
        destinationFolder: Folder,
        currentFolderId: String?,
        mailbox: Mailbox,
        messages: List<Message>,
        callbacks: RefreshCallbacks? = null,
    ): MoveMessagesResult {

        val movedThreads = moveOutThreadsLocally(messages, destinationFolder)
        val featureFlags = mailbox.featureFlags

        val apiResponses = moveMessages(
            mailbox = mailbox,
            messagesToMove = messages,
            destinationFolder = destinationFolder,
            alsoMoveReactionMessages = FeatureAvailability.isReactionsAvailable(featureFlags, localSettings),
        )

        if (apiResponses.atLeastOneSucceeded() && currentFolderId != null) {

            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(exception = destinationFolder.id),
                destinationFolderId = destinationFolder.id,
                currentFolderId = currentFolderId,
                callbacks = callbacks,
            )
        }

        if (apiResponses.atLeastOneFailed() && movedThreads.isNotEmpty()) {
            threadController.updateIsLocallyMovedOutStatus(movedThreads, hasBeenMovedOut = false)
        }

        return MoveMessagesResult(movedThreads, messages, apiResponses, destinationFolder)
    }

    private suspend fun refreshFoldersAsync(
        mailbox: Mailbox,
        messagesFoldersIds: ImpactedFolders,
        currentFolderId: String? = null,
        destinationFolderId: String? = null,
        callbacks: RefreshCallbacks? = null,
    ) {
        sharedUtils.refreshFolders(mailbox, messagesFoldersIds, destinationFolderId, currentFolderId, callbacks)
    }

    private suspend fun moveOutThreadsLocally(messages: List<Message>, destinationFolder: Folder): List<String> {
        val uidsToMove = mutableListOf<String>().apply {
            messages.flatMapTo(mutableSetOf(), Message::threads).forEach { thread ->
                val nbMessagesInCurrentFolder = thread.messages.count { it.folderId != destinationFolder.id }
                if (nbMessagesInCurrentFolder == 0) add(thread.uid)
            }
        }

        if (uidsToMove.isNotEmpty()) threadController.updateIsLocallyMovedOutStatus(uidsToMove, hasBeenMovedOut = true)
        return uidsToMove
    }


    private suspend fun moveMessages(
        mailbox: Mailbox,
        messagesToMove: List<Message>,
        destinationFolder: Folder,
        alsoMoveReactionMessages: Boolean,
    ): List<ApiResponse<MoveResult>> {
        val apiResponses = ApiRepository.moveMessages(
            mailboxUuid = mailbox.uuid,
            messagesUids = messagesToMove.getUids(),
            destinationId = destinationFolder.id,
            alsoMoveReactionMessages = alsoMoveReactionMessages,
        )

        // TODO: Will unsync permantly the mailbox if one message in one of the batches did succeed but some other messages in the
        //  same batch or in other batches that are target by emoji reactions did not
        if (alsoMoveReactionMessages && apiResponses.atLeastOneSucceeded()) deleteEmojiReactionMessagesLocally(messagesToMove)

        return apiResponses
    }

    /**
     * When deleting a message targeted by emoji reactions inside of a thread, the emoji reaction messages from another folder
     * that were targeting this message will display for a brief moment until we refresh their folders. This is because those
     * messages don't have a target message anymore and emoji reactions messages with no target in their thread need to be
     * displayed.
     *
     * Deleting them from the database in the first place will prevent them from being shown and the messages will be deleted by
     * the api at the same time anyway.
     */
    suspend fun deleteEmojiReactionMessagesLocally(messagesToMove: List<Message>) {
        for (messageToMove in messagesToMove) {
            if (messageToMove.emojiReactions.isEmpty()) continue

            mailboxContentRealm().write {
                messageToMove.emojiReactions.forEach { reaction ->
                    reaction.authors.forEach { author ->
                        MessageController.deleteMessageByUidBlocking(author.sourceMessageUid, this)
                    }
                }
            }
        }
    }

    suspend fun getMessagesFromThreadToSpamOrHam(threads: Set<Thread>): List<Message> {
        return threads.flatMap { messageController.getUnscheduledMessagesFromThread(it, includeDuplicates = false) }
    }


    // Delete Region
    suspend fun permanentlyDelete(
        messagesToDelete: List<Message>,
        currentFolder: Folder?,
        mailbox: Mailbox,
        onApiFinished: () -> Unit, // Callback for the loader reset
        refreshCallbacks: RefreshCallbacks?
    ): DeleteResult {
        val undoResources = emptyList<String>()
        val uids = messagesToDelete.getUids()

        val uidsToMove = moveOutThreadsLocally(messagesToDelete, folderController.getFolder(FolderRole.TRASH)!!)

        val apiResponses = ApiRepository.deleteMessages(
            mailboxUuid = mailbox.uuid,
            messagesUids = uids,
            alsoMoveReactionMessages = FeatureAvailability.isReactionsAvailable(mailbox.featureFlags, localSettings)
        )

        onApiFinished()

        if (apiResponses.atLeastOneSucceeded()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messagesToDelete.getFoldersIds(),
                currentFolderId = currentFolder?.id,
                callbacks = refreshCallbacks,
            )
        }

        if (apiResponses.atLeastOneFailed()) threadController.updateIsLocallyMovedOutStatus(
            threadsUids = uidsToMove,
            hasBeenMovedOut = false
        )

        val undoDestinationId = messagesToDelete.first().folderId
        val undoFoldersIds = messagesToDelete.getFoldersIds(exception = undoDestinationId)

        return DeleteResult(
            apiResponses = apiResponses,
            uidsToMove = uidsToMove,
            undoResources = undoResources,
            undoFoldersIds = undoFoldersIds,
            undoDestinationId = undoDestinationId,
        )
    }

    suspend fun getMessagesFromThreadToDelete(threads: List<Thread>): List<Message> {
        return threads.flatMap { messageController.getUnscheduledMessagesFromThread(it, includeDuplicates = true) }
    }

    suspend fun getMessagesToDelete(messages: List<Message>) = messageController.getMessagesAndDuplicates(messages)

    // End Region
    data class MoveMessagesResult(
        val movedThreads: List<String>,
        val messages: List<Message>,
        val apiResponses: List<ApiResponse<MoveResult>>,
        val destinationFolder: Folder
    )

    data class DeleteResult(
        val apiResponses: List<ApiResponse<*>>,
        val uidsToMove: List<String>,
        val undoResources: List<String>,
        val undoFoldersIds: ImpactedFolders,
        val undoDestinationId: String,
    )
}


