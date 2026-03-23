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

import androidx.annotation.StringRes
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.ImpactedFolders
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.MoveResult
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.mailbox.SendersRestrictions
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.snooze.BatchSnoozeResult
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.ui.main.SnackbarManager.UndoData
import com.infomaniak.mail.utils.FeatureAvailability
import com.infomaniak.mail.utils.FolderRoleUtils
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.extensions.atLeastOneFailed
import com.infomaniak.mail.utils.extensions.atLeastOneSucceeded
import com.infomaniak.mail.utils.extensions.getFirstTranslatedError
import com.infomaniak.mail.utils.extensions.getFoldersIds
import com.infomaniak.mail.utils.extensions.getUids
import kotlinx.coroutines.coroutineScope
import java.util.Date
import javax.inject.Inject
import com.infomaniak.core.legacy.R as RCore

class MessagesActionsUseCase @Inject constructor(
    private val folderController: FolderController,
    private val folderRoleUtils: FolderRoleUtils,
    private val localSettings: LocalSettings,
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val mailboxController: MailboxController,
    private val messageController: MessageController,
    private val threadController: ThreadController,
) {

    // Move Region
    suspend fun moveMessagesTo(
        destinationFolder: Folder,
        mailbox: Mailbox,
        messages: List<Message>,
    ): MoveMessagesResult {

        val movedThreads = moveOutMessagesThreadsLocally(messages, destinationFolder)
        val featureFlags = mailbox.featureFlags

        val apiResponses = moveMessages(
            mailbox = mailbox,
            messagesToMove = messages,
            destinationFolder = destinationFolder,
            alsoMoveReactionMessages = FeatureAvailability.isReactionsAvailable(featureFlags, localSettings),
        )

        return MoveMessagesResult(movedThreads, messages, apiResponses, destinationFolder)
    }

    suspend fun getMessagesToMove(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap { messageController.getMovableMessages(it) }
        else -> listOf(message)
    }

    suspend fun getMessagesFromThreadsToMove(threads: List<Thread>): List<Message> {
        return threads.flatMap { messageController.getMovableMessages(it) }
    }

    fun getMessagesToMove(messages: List<Message>, currentFolderId: String?): List<Message> {
        return messages.filter { message -> message.folderId == currentFolderId && !message.isScheduledMessage }
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

    private suspend fun moveOutMessagesThreadsLocally(messages: List<Message>, destinationFolder: Folder): List<String> {
        val uidsToMove = mutableListOf<String>()
        mailboxContentRealm().run {
            messages.flatMapTo(mutableSetOf(), Message::threads).forEach { thread ->
                val realmThread = ThreadController.getThreadBlocking(thread.uid, realm = this) ?: return@forEach
                val nbMessagesInCurrentFolder = realmThread.messages.count { it.folderId != destinationFolder.id }
                if (nbMessagesInCurrentFolder == 0) uidsToMove.add(thread.uid)
            }
        }

        if (uidsToMove.isNotEmpty()) threadController.updateIsLocallyMovedOutStatus(uidsToMove, hasBeenMovedOut = true)
        return uidsToMove
    }

    suspend fun moveThreadsOrMessagesTo(
        destinationFolderId: String,
        threadsUids: List<String>,
        messagesUids: List<String>? = null,
        mailbox: Mailbox,
        currentFolderId: String?,
    ): MoveMessagesResult? {
        if (currentFolderId == null) return null

        val destinationFolder = folderController.getFolder(destinationFolderId) ?: return null

        var messagesToMove: List<Message>
        if (messagesUids != null) {
            messagesToMove = messagesUids.let { messageController.getMessages(it) }
        } else {
            val threads = threadController.getThreads(threadsUids).ifEmpty { return null }
            messagesToMove = getMessagesFromThreadsToMove(threads)
        }

        return moveMessagesTo(destinationFolder, mailbox, messagesToMove)
    }
    // End Region

    // Spam Region
    suspend fun toggleMessagesSpamStatus(
        messages: List<Message>,
        currentFolderId: String?,
        mailbox: Mailbox,
    ): MoveMessagesResult? {
        val folder = if (currentFolderId != null) folderController.getFolder(currentFolderId) else null
        val folderRole = folderRoleUtils.getActionFolderRole(messages, folder)

        val destinationFolderRole = if (folderRole == FolderRole.SPAM) FolderRole.INBOX else FolderRole.SPAM

        val destinationFolder = folderController.getFolder(destinationFolderRole) ?: return null
        val unscheduleMessages = messageController.getUnscheduledMessages(messages)

        return moveMessagesTo(destinationFolder, mailbox, unscheduleMessages)
    }

    suspend fun getMessagesFromThreadsToSpamOrHam(threads: Set<Thread>): List<Message> {
        return threads.flatMap { messageController.getUnscheduledMessagesFromThread(it, includeDuplicates = false) }
    }

    suspend fun activateSpamFilter(mailbox: Mailbox) {
        ApiRepository.setSpamFilter(
            mailboxHostingId = mailbox.hostingId,
            mailboxName = mailbox.mailboxName,
            activateSpamFilter = true,
        )
    }
    // End Region

    // Delete Region
    suspend fun permanentlyDelete(
        messagesToDelete: List<Message>,
        mailbox: Mailbox,
        onApiFinished: () -> Unit,
    ): DeleteResult? {
        if (messagesToDelete.isEmpty()) {
            return null
        }
        val uids = messagesToDelete.getUids()
        val destinationFolder = folderController.getFolder(FolderRole.TRASH) ?: return null
        val uidsToMove = moveOutMessagesThreadsLocally(messagesToDelete, destinationFolder)

        val apiResponses = ApiRepository.deleteMessages(
            mailboxUuid = mailbox.uuid,
            messagesUids = uids,
            alsoMoveReactionMessages = FeatureAvailability.isReactionsAvailable(mailbox.featureFlags, localSettings),
        )

        onApiFinished()

        return DeleteResult(
            apiResponses = apiResponses,
            uidsToMove = uidsToMove,
        )
    }

    suspend fun getMessagesFromThreadsToDelete(threads: List<Thread>): List<Message> {
        return threads.flatMap { messageController.getUnscheduledMessagesFromThread(it, includeDuplicates = true) }
    }

    suspend fun getMessagesToDelete(messages: List<Message>) = messageController.getMessagesAndDuplicates(messages)
    // End Region

    // Seen Region
    suspend fun toggleThreadsSeenStatus(
        threadsUids: List<String>,
        shouldRead: Boolean = true,
        mailbox: Mailbox,
    ): ToggleResult {
        val threads = threadsUids.let { threadController.getThreads(threadsUids) }
        val isSeen = if (threads.count() == 1) threads.single().isSeen else !shouldRead
        val messagesToToggle = if (isSeen) {
            threads.flatMap { thread ->
                messageController.getLastMessageAndItsDuplicatesToExecuteAction(thread, mailbox.featureFlags)
            }
        } else threads.flatMap { thread -> messageController.getUnseenMessages(thread) }

        return handleToggleSeenStatus(messagesToToggle, isSeen, mailbox, threadsUids)
    }

    suspend fun toggleMessagesSeenStatus(
        messages: List<Message>,
        shouldRead: Boolean = true,
        mailbox: Mailbox,
    ): ToggleResult {
        val isSeen = if (messages.count() == 1) messages.single().isSeen else !shouldRead
        val messagesToToggleSeen = messageController.getMessagesAndDuplicates(messages)

        return handleToggleSeenStatus(messagesToToggleSeen, isSeen, mailbox)
    }

    private suspend fun handleToggleSeenStatus(
        messages: List<Message>,
        isSeen: Boolean,
        mailbox: Mailbox,
        threadsUids: List<String>? = null
    ): ToggleResult {
        return if (isSeen) {
            markAsUnseen(
                messages = messages,
                mailbox = mailbox,
                threadsUids = threadsUids,
            )
        } else {
            markMessagesAsSeen(
                messages = messages,
                mailbox = mailbox,
                threadsUids = threadsUids,
            )
        }
    }

    /**
     * Mark a Message or some Threads as read
     * @param mailbox The Mailbox where the Threads & Messages are located
     * @param messages The Messages to mark as read
     */
    suspend fun markMessagesAsSeen(
        mailbox: Mailbox,
        messages: List<Message>,
        threadsUids: List<String>? = null,
    ): ToggleResult {

        val messagesUids = messages.getUids()

        updateSeenStatus(messagesUids, threadsUids, isSeen = true)

        val apiResponses = ApiRepository.markMessagesAsSeen(mailbox.uuid, messagesUids)

        if (apiResponses.atLeastOneFailed()) updateSeenStatus(messagesUids, isSeen = false)

        return ToggleResult(messages = messages, apiResponses = apiResponses)
    }

    suspend fun updateSeenStatus(messagesUids: List<String>, threadsUids: List<String>? = null, isSeen: Boolean) {

        mailboxContentRealm().write {
            if (threadsUids != null) {
                ThreadController.updateSeenStatus(threadsUids, isSeen, realm = this)
            }
            MessageController.updateSeenStatus(messagesUids, isSeen, realm = this)
        }
    }

    private suspend fun markAsUnseen(messages: List<Message>, mailbox: Mailbox, threadsUids: List<String>? = null): ToggleResult {
        val messagesUids = messages.getUids()

        updateSeenStatus(messagesUids, threadsUids, isSeen = false)

        val apiResponses = ApiRepository.markMessagesAsUnseen(mailbox.uuid, messagesUids)

        if (apiResponses.atLeastOneFailed()) updateSeenStatus(messagesUids, isSeen = true)

        return ToggleResult(messages = messages, apiResponses = apiResponses)
    }
    // End Region

    // Favorites Region
    suspend fun toggleThreadsFavorite(
        threadsUids: List<String>,
        shouldFavorite: Boolean = true,
        mailbox: Mailbox,
    ): ToggleResult {
        val threads = threadsUids.let { threadController.getThreads(threadsUids) }
        val isFavorite = if (threads.count() == 1) threads.single().isFavorite else !shouldFavorite
        val messages = if (isFavorite) {
            threads.flatMap { messageController.getFavoriteMessages(it) }
        } else {
            threads.flatMap { thread ->
                messageController.getLastMessageAndItsDuplicatesToExecuteAction(thread, mailbox.featureFlags)
            }
        }
        return toggleMessagesFavoriteStatus(messages, isFavorite, mailbox)
    }

    suspend fun toggleMessagesFavorite(
        messages: List<Message>,
        shouldFavorite: Boolean = true,
        mailbox: Mailbox,
    ): ToggleResult {
        val isFavorite = if (messages.count() == 1) messages.single().isFavorite else !shouldFavorite

        val messages = if (isFavorite) {
            getMessagesToUnfavorite(messages)
        } else {
            getMessagesToFavorite(messages)
        }

        return toggleMessagesFavoriteStatus(messages, isFavorite, mailbox)
    }

    private suspend fun toggleMessagesFavoriteStatus(
        messages: List<Message>,
        isFavorite: Boolean,
        mailbox: Mailbox,
    ): ToggleResult {
        val uids = messages.getUids()

        updateFavoriteStatus(messagesUids = uids, isFavorite = !isFavorite)

        val apiResponses = if (isFavorite) {
            ApiRepository.removeFromFavorites(mailbox.uuid, uids)
        } else {
            ApiRepository.addToFavorites(mailbox.uuid, uids)
        }

        if (apiResponses.atLeastOneFailed()) {
            updateFavoriteStatus(messagesUids = uids, isFavorite = isFavorite)
        }

        return ToggleResult(messages, apiResponses)
    }

    private suspend fun getMessagesToFavorite(messages: List<Message>): List<Message> {
        return messageController.getMessagesAndDuplicates(messages)
    }

    private suspend fun getMessagesToUnfavorite(messages: List<Message>): List<Message> {
        return messageController.getMessagesAndDuplicates(messages)
    }

    private suspend fun updateFavoriteStatus(messagesUids: List<String>, isFavorite: Boolean) {
        mailboxContentRealm().write {
            MessageController.updateFavoriteStatus(messagesUids, isFavorite, realm = this)
        }
    }
    // End Region

    // Phishing Region
    suspend fun reportPhishing(
        messages: List<Message>,
        currentFolder: Folder?,
        mailbox: Mailbox,
        onReportSuccess: suspend () -> Unit,
    ): ApiCallResult {
        val messagesUids = messages.map { it.uid }
        if (messagesUids.isEmpty()) return ApiCallResult.Error(RCore.string.anErrorHasOccurred)

        val response = ApiRepository.reportPhishing(mailbox.uuid, messagesUids)

        return if (response.isSuccess()) {
            if (folderRoleUtils.getActionFolderRole(messages, currentFolder) != FolderRole.SPAM) {
                onReportSuccess()
            }
            ApiCallResult.Success(R.string.snackbarReportPhishingConfirmation)
        } else {
            ApiCallResult.Error(response.translateError())
        }
    }
    // End Region

    // Block Region
    suspend fun blockUser(
        folderId: String,
        shortUid: Int,
        mailbox: Mailbox,
    ): ApiCallResult {
        val response = ApiRepository.blockUser(mailbox.uuid, folderId, shortUid)

        return if (response.isSuccess()) {
            ApiCallResult.Success(R.string.snackbarBlockUserConfirmation)
        } else {
            ApiCallResult.Error(response.translateError())
        }
    }

    suspend fun unblockMail(email: String, mailbox: Mailbox): ApiCallResult? {
        val response = ApiRepository.getSendersRestrictions(mailbox.hostingId, mailbox.mailboxName)
        return if (response.isSuccess()) {
            val restrictions = response.data ?: return ApiCallResult.Error(RCore.string.anErrorHasOccurred)
            restrictions.blockedSenders.removeIf { it.email == email }
            updateBlockedSenders(mailbox, restrictions)
            ApiCallResult.Success(R.string.unblockButton) // We don't show a snackbar on success. It's just a confirmation
        } else {
            ApiCallResult.Error(response.translateError())
        }
    }

    private suspend fun updateBlockedSenders(mailbox: Mailbox, updatedSendersRestrictions: SendersRestrictions) {
        with(ApiRepository.updateBlockedSenders(mailbox.hostingId, mailbox.mailboxName, updatedSendersRestrictions)) {
            if (isSuccess()) {
                mailboxController.updateMailbox(mailbox.objectId) {
                    it.sendersRestrictions = updatedSendersRestrictions
                }
            }
        }
    }
    // End Region

    // Snooze Region
    suspend fun snoozeThreads(
        date: Date,
        threadUids: List<String>,
        currentFolderId: String?,
        mailbox: Mailbox,
    ): SnoozeResult {
        val threads = threadUids.mapNotNull { threadController.getThread(it) }
        val messageUids = threads.mapNotNull { thread ->
            thread.getDisplayedMessages(mailbox.featureFlags, localSettings)
                .lastOrNull { it.folderId == currentFolderId }?.uid
        }

        val responses = ApiRepository.snoozeMessages(mailbox.uuid, messageUids, date)

        return if (responses.atLeastOneSucceeded()) {
            SnoozeResult.Success(threads.count(), date)
        } else {
            val errorRes = responses.getFirstTranslatedError() ?: RCore.string.anErrorHasOccurred
            SnoozeResult.Error(errorRes)
        }
    }

    suspend fun rescheduleSnoozedThreads(
        date: Date,
        threadUids: List<String>,
        mailbox: Mailbox,
    ): BatchSnoozeResult {
        val snoozedThreadUuids = threadUids.mapNotNull { threadUid ->
            val thread = threadController.getThread(threadUid) ?: return@mapNotNull null
            thread.snoozeUuid.takeIf { thread.isSnoozed() }
        }

        if (snoozedThreadUuids.isEmpty()) return BatchSnoozeResult.Error.Unknown

        val result = SharedUtils.rescheduleSnoozedThreads(
            mailboxUuid = mailbox.uuid,
            snoozeUuids = snoozedThreadUuids,
            newDate = date,
            impactedFolders = ImpactedFolders(mutableSetOf(FolderRole.SNOOZED)),
        )

        return result
    }

    suspend fun unsnoozeThreads(
        threads: Collection<Thread>,
        mailbox: Mailbox,
    ): BatchSnoozeResult = coroutineScope {
        SharedUtils.unsnoozeThreadsWithoutRefresh(
            mailbox = mailbox,
            threads = threads,
            scope = this
        )
    }

    sealed class SnoozeResult {
        data class Success(val threadCount: Int, val date: Date) : SnoozeResult()
        data class Error(@StringRes val messageRes: Int) : SnoozeResult()
    }
    // End Region

    // Undo Region
    suspend fun undoAction(undoData: UndoData): ApiCallResult {
        val resources = undoData.resources ?: return ApiCallResult.Error(RCore.string.anErrorHasOccurred)
        val apiResponses = resources.map { ApiRepository.undoAction(it) }
        val failedCall = apiResponses.firstOrNull { it.data != true }

        return if (failedCall == null) {
            ApiCallResult.Success(R.string.snackbarMoveCancelled)
        } else {
            ApiCallResult.Error(failedCall.translateError())
        }
    }

    fun getUndoData(
        messagesMoved: List<Message>,
        apiResponses: List<ApiResponse<MoveResult>>,
        destinationFolder: Folder,
    ): UndoData? {
        val undoResources = apiResponses.mapNotNull { it.data?.undoResource }
        val undoData = if (undoResources.isEmpty()) {
            null
        } else {
            val undoDestinationId = destinationFolder.id
            val foldersIds = messagesMoved.getFoldersIds(exception = undoDestinationId)
            foldersIds += destinationFolder.id
            UndoData(
                resources = undoResources,
                foldersIds = foldersIds,
                destinationFolderId = undoDestinationId,
            )
        }
        return undoData
    }
    // End Region

    // Draft Region
    suspend fun rescheduleDraft(draftResource: String, scheduleDate: Date): ApiResponse<Unit> {
        return ApiRepository.rescheduleDraft(draftResource, scheduleDate)
    }

    suspend fun unscheduleDraft(unscheduleDraftUrl: String): ApiResponse<Unit> {
        return ApiRepository.unscheduleDraft(unscheduleDraftUrl)
    }

    suspend fun deleteDraft(targetMailboxUuid: String, remoteDraftUuid: String): ApiResponse<Unit> {
        return ApiRepository.deleteDraft(targetMailboxUuid, remoteDraftUuid)
    }
    // End Region

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

    sealed class ApiCallResult {
        data class Success(val messageRes: Int) : ApiCallResult()
        data class Error(val messageRes: Int) : ApiCallResult()
    }

    data class MoveMessagesResult(
        val movedThreads: List<String>,
        val messages: List<Message>,
        val apiResponses: List<ApiResponse<MoveResult>>,
        val destinationFolder: Folder,
    )

    data class DeleteResult(
        val apiResponses: List<ApiResponse<*>>,
        val uidsToMove: List<String>,
    )

    data class ToggleResult(
        val messages: List<Message>,
        val apiResponses: List<ApiResponse<*>>,
    )
}
