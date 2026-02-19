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
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshCallbacks
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
import com.infomaniak.mail.ui.main.SnackbarManager
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
    private val sharedUtils: SharedUtils,
) {

    // Move Region
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
            sharedUtils.refreshFolders(
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
    // End Region

    // Spam Region
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

    suspend fun getMessagesFromThreadToSpamOrHam(threads: Set<Thread>): List<Message> {
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
            sharedUtils.refreshFolders(
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

    // Seen Region
    suspend fun toggleThreadSeenStatus(
        threadsUids: List<String>,
        shouldRead: Boolean = true,
        currentFolderId: String?,
        mailbox: Mailbox,
        refreshCallbacks: RefreshCallbacks? = null
    ) {
        val threads = threadsUids.let { threadController.getThreads(threadsUids) }
        val isSeen = if (threads.count() == 1) threads.single().isSeen else !shouldRead
        val messagesToToggleSeen = getMessagesFromThreadsToMarkAsUnseen(threads, mailbox)

        handleToggleSeenStatus(messagesToToggleSeen, isSeen, currentFolderId, mailbox, refreshCallbacks)
    }

    suspend fun toggleMessagesSeenStatus(
        messages: List<Message>,
        shouldRead: Boolean = true,
        currentFolderId: String?,
        mailbox: Mailbox,
        refreshCallbacks: RefreshCallbacks?
    ) {
        val isSeen = if (messages.count() == 1) messages.single().isSeen else !shouldRead
        val messagesToToggleSeen = getMessagesToMarkAsUnseen(messages)

        handleToggleSeenStatus(messagesToToggleSeen, isSeen, currentFolderId, mailbox, refreshCallbacks)
    }

    private suspend fun handleToggleSeenStatus(
        messages: List<Message>,
        isSeen: Boolean,
        currentFolderId: String?,
        mailbox: Mailbox,
        refreshCallbacks: RefreshCallbacks? = null
    ) {
        if (isSeen) {
            markAsUnseen(messages, mailbox, refreshCallbacks)
        } else {
            sharedUtils.markMessagesAsSeen(
                messages = messages,
                currentFolderId = currentFolderId,
                mailbox = mailbox,
                callbacks = refreshCallbacks,
            )
        }
    }

    private suspend fun getMessagesFromThreadsToMarkAsUnseen(threads: List<Thread>, mailbox: Mailbox): List<Message> {
        return threads.flatMap { thread ->
            messageController.getLastMessageAndItsDuplicatesToExecuteAction(thread, mailbox.featureFlags)
        }
    }

    private suspend fun getMessagesToMarkAsUnseen(messages: List<Message>): List<Message> {
        return messageController.getMessagesAndDuplicates(messages)
    }

    private suspend fun markAsUnseen(messages: List<Message>, mailbox: Mailbox, callbacks: RefreshCallbacks?) {
        val messagesUids = messages.map { it.uid }

        sharedUtils.updateSeenStatus(messagesUids, isSeen = false)

        val apiResponses = ApiRepository.markMessagesAsUnseen(mailbox.uuid, messagesUids)

        if (apiResponses.atLeastOneSucceeded()) {
            sharedUtils.refreshFolders(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(),
                callbacks = callbacks,
            )
        } else {
            sharedUtils.updateSeenStatus(messagesUids, isSeen = true)
        }
    }
    // End Region

    // Favorites Region
    suspend fun toggleThreadFavorite(
        threadsUids: List<String>,
        shouldFavorite: Boolean = true,
        mailbox: Mailbox,
        callbacks: RefreshCallbacks? = null
    ) {
        val threads = threadsUids.let { threadController.getThreads(threadsUids) }
        val isFavorite = if (threads.count() == 1) threads.single().isFavorite else !shouldFavorite
        val messages = if (isFavorite) {
            getMessagesFromThreadToUnfavorite(threads)
        } else {
            getMessagesFromThreadToFavorite(threads, mailbox)
        }

        toggleMessagesFavoriteStatus(messages, isFavorite, mailbox, callbacks)
    }

    suspend fun toggleMessagesFavorite(
        messages: List<Message>,
        shouldFavorite: Boolean = true,
        mailbox: Mailbox,
        callbacks: RefreshCallbacks? = null
    ) {
        val isFavorite = if (messages.count() == 1) messages.single().isFavorite else !shouldFavorite

        val messages = if (isFavorite) {
            getMessagesToUnfavorite(messages)
        } else {
            getMessagesToFavorite(messages)
        }

        toggleMessagesFavoriteStatus(messages, isFavorite, mailbox, callbacks)
    }

    private suspend fun toggleMessagesFavoriteStatus(
        messages: List<Message>,
        isFavorite: Boolean,
        mailbox: Mailbox,
        callbacks: RefreshCallbacks?
    ) {
        val uids = messages.getUids()

        updateFavoriteStatus(messagesUids = uids, isFavorite = !isFavorite)

        val apiResponses = if (isFavorite) {
            ApiRepository.removeFromFavorites(mailbox.uuid, uids)
        } else {
            ApiRepository.addToFavorites(mailbox.uuid, uids)
        }

        if (apiResponses.atLeastOneSucceeded()) {
            sharedUtils.refreshFolders(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(),
                callbacks = callbacks,
            )
        } else {
            updateFavoriteStatus(messagesUids = uids, isFavorite = isFavorite)
        }
    }

    private suspend fun getMessagesToFavorite(messages: List<Message>): List<Message> {
        return messageController.getMessagesAndDuplicates(messages)
    }

    private suspend fun getMessagesToUnfavorite(messages: List<Message>): List<Message> {
        return messageController.getMessagesAndDuplicates(messages)
    }

    private suspend fun getMessagesFromThreadToFavorite(threads: List<Thread>, mailbox: Mailbox): List<Message> {
        return threads.flatMap { thread ->
            messageController.getLastMessageAndItsDuplicatesToExecuteAction(thread, mailbox.featureFlags)
        }
    }

    private suspend fun getMessagesFromThreadToUnfavorite(threads: List<Thread>): List<Message> {
        return threads.flatMap { messageController.getFavoriteMessages(it) }
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
        onReportSuccess: suspend () -> Unit
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
        mailbox: Mailbox
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
            restrictions.apply {
                blockedSenders.removeIf { it.email == email }
            }
            updateBlockedSenders(mailbox, restrictions)
            null
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
            sharedUtils.refreshFolders(mailbox, ImpactedFolders(mutableSetOf(FolderRole.SNOOZED)))
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

        if (result is BatchSnoozeResult.Success) {
            sharedUtils.refreshFolders(mailbox, result.impactedFolders)
        }

        return result
    }

    suspend fun unsnoozeThreads(
        threads: Collection<Thread>,
        mailbox: Mailbox,
    ): BatchSnoozeResult = coroutineScope {

        val result = SharedUtils.unsnoozeThreadsWithoutRefresh(
            mailbox = mailbox,
            threads = threads,
            scope = this
        )

        if (result is BatchSnoozeResult.Success) {
            sharedUtils.refreshFolders(mailbox, result.impactedFolders)
        }

        result
    }

    sealed class SnoozeResult {
        data class Success(val threadCount: Int, val date: Date) : SnoozeResult()
        data class Error(@StringRes val messageRes: Int) : SnoozeResult()
    }
    // End Region

    // Undo Region
    suspend fun undoAction(undoData: SnackbarManager.UndoData, mailbox: Mailbox): ApiCallResult {
        val (resources, foldersIds, destinationFolderId) = undoData    // 1. Execute the API calls for each resource
        val apiResponses = resources.map { ApiRepository.undoAction(it) }

        if (apiResponses.atLeastOneSucceeded()) {
            sharedUtils.refreshFolders(
                mailbox = mailbox,
                messagesFoldersIds = foldersIds,
                destinationFolderId = destinationFolderId,
            )
        }

        val failedCall = apiResponses.firstOrNull { it.data != true }

        return if (failedCall == null) {
            ApiCallResult.Success(R.string.snackbarMoveCancelled)
        } else {
            ApiCallResult.Error(failedCall.translateError())
        }
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


