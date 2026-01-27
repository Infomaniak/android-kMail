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
package com.infomaniak.mail.ui.main.thread.actions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
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
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.SnackbarManager.UndoData
import com.infomaniak.mail.utils.FeatureAvailability
import com.infomaniak.mail.utils.FolderRoleUtils
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.coroutineContext
import com.infomaniak.mail.utils.extensions.allFailed
import com.infomaniak.mail.utils.extensions.appContext
import com.infomaniak.mail.utils.extensions.atLeastOneFailed
import com.infomaniak.mail.utils.extensions.atLeastOneSucceeded
import com.infomaniak.mail.utils.extensions.getFoldersIds
import com.infomaniak.mail.utils.extensions.getUids
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActionsViewModel @Inject constructor(
    application: Application,
    private val folderController: FolderController,
    private val folderRoleUtils: FolderRoleUtils,
    private val localSettings: LocalSettings,
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val mailboxController: MailboxController,
    private val messageController: MessageController,
    private val sharedUtils: SharedUtils,
    private val snackbarManager: SnackbarManager,
    private val threadController: ThreadController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)
    val isDownloadingChanges: MutableLiveData<Boolean> = MutableLiveData(false)
    private val _currentMailboxObjectId = MutableStateFlow<String?>(null)
    val currentMailbox = _currentMailboxObjectId.mapLatest { id ->
        id?.let { mailboxController.getMailbox(it) }
    }.asLiveData(ioCoroutineContext)

    private val currentMailboxLive = _currentMailboxObjectId.filterNotNull().flatMapLatest { objectId ->
        mailboxController.getMailboxAsync(objectId).mapNotNull { it.obj }
    }.asLiveData(ioCoroutineContext)

    val featureFlagsLive = currentMailboxLive.map { it.featureFlags }

    //region Spam

    fun moveToSpamFolder(messagesUid: List<String>, currentFolderId: String?, mailbox: Mailbox) =
        viewModelScope.launch(ioCoroutineContext) {
            val messages = messageController.getMessages(messagesUid)
            toggleMessagesSpamStatus(messages, currentFolderId, mailbox)
        }

    fun toggleMessagesSpamStatus(
        messages: List<Message>,
        currentFolderId: String?,
        mailbox: Mailbox,
        displaySnackbar: Boolean = true
    ) {
        toggleThreadsOrMessageSpamStatus(messages, currentFolderId, mailbox, displaySnackbar)
    }

    private fun toggleThreadsOrMessageSpamStatus(
        messages: List<Message>,
        currentFolderId: String?,
        mailbox: Mailbox,
        displaySnackbar: Boolean = true,
    ) = viewModelScope.launch(ioCoroutineContext) {
        // we check only one message folder role because:
        // if we are in a specific folder all messages will have the same folder role,
        // if we are in search we don't show the messages that are already in SPAM so none of them will be in the SPAM folder

        val firstMessage = messages.first()

        val destinationFolderRole = if (folderRoleUtils.getActionFolderRole(firstMessage) == FolderRole.SPAM) {
            FolderRole.INBOX
        } else {
            FolderRole.SPAM
        }
        val destinationFolder = folderController.getFolder(destinationFolderRole)!!

        val messages = messageController.getUnscheduledMessages(messages)

        moveMessagesTo(destinationFolder, currentFolderId, mailbox, messages, displaySnackbar)
    }

    //endregion

    //region Move
    fun moveThreadsOrMessageTo(
        destinationFolderId: String,
        threadsUids: List<String>,
        messageUid: String? = null,
        currentFolderId: String,
        mailbox: Mailbox,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val destinationFolder = folderController.getFolder(destinationFolderId)!!
        val threads = threadController.getThreads(threadsUids).ifEmpty { return@launch }
        val message = messageUid?.let { messageController.getMessage(it)!! }
        val messagesToMove = sharedUtils.getMessagesToMove(threads, message)

        moveMessagesTo(destinationFolder, currentFolderId, mailbox, messagesToMove)
    }

    private suspend fun moveMessagesTo(
        destinationFolder: Folder,
        currentFolderId: String?,
        mailbox: Mailbox,
        messages: List<Message>,
        shouldDisplaySnackbar: Boolean = true,
    ) {

        val movedThreads = moveOutThreadsLocally(messages, destinationFolder)

        val apiResponses = moveMessages(
            mailbox = mailbox,
            messagesToMove = messages,
            destinationFolder = destinationFolder,
            alsoMoveReactionMessages = FeatureAvailability.isReactionsAvailable(featureFlagsLive.value, localSettings),
        )

        if (apiResponses.atLeastOneSucceeded() && currentFolderId != null) {

            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(exception = destinationFolder.id),
                destinationFolderId = destinationFolder.id,
                currentFolderId = currentFolderId,
                callbacks = RefreshCallbacks(onStart = ::onDownloadStart, onStop = { onDownloadStop(movedThreads) }),
            )
        }

        if (apiResponses.atLeastOneFailed() && movedThreads.isNotEmpty()) {
            threadController.updateIsLocallyMovedOutStatus(movedThreads, hasBeenMovedOut = false)
        }

        if (shouldDisplaySnackbar) showMoveSnackbar(movedThreads, messages, apiResponses, destinationFolder)
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
    private suspend fun deleteEmojiReactionMessagesLocally(messagesToMove: List<Message>) {
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

    private fun showMoveSnackbar(
        threadsMoved: List<String>,
        messages: List<Message>,
        apiResponses: List<ApiResponse<MoveResult>>,
        destinationFolder: Folder,
    ) {

        val destination = destinationFolder.getLocalizedName(appContext)

        val snackbarTitle = when {
            apiResponses.allFailed() -> appContext.getString(apiResponses.first().translateError())
            threadsMoved.count() > 0 -> appContext.resources.getQuantityString(
                R.plurals.snackbarThreadMoved,
                threadsMoved.count(),
                destination
            )
            //TODO: A MESSAGES MOVED QUANTITY STRING
            else -> appContext.getString(R.string.snackbarMessageMoved, destination)
        }

        val undoResources = apiResponses.mapNotNull { it.data?.undoResource }
        val undoData = if (undoResources.isEmpty()) {
            null
        } else {
            val undoDestinationId = destinationFolder.id
            val foldersIds = messages.getFoldersIds(exception = undoDestinationId)
            foldersIds += destinationFolder.id
            UndoData(
                resources = apiResponses.mapNotNull { it.data?.undoResource },
                foldersIds = foldersIds,
                destinationFolderId = undoDestinationId,
            )
        }

        snackbarManager.postValue(snackbarTitle, undoData)
    }
    //endregion

    private suspend fun moveOutThreadsLocally(
        messages: List<Message>,
        destinationFolder: Folder
    ): List<String> {
        val uidsToMove =
            mutableListOf<String>().apply {
                messages.flatMapTo(mutableSetOf(), Message::threads).forEach { thread ->
                    val nbMessagesInCurrentFolder = thread.messages.count { it.folderId != destinationFolder.id }
                    if (nbMessagesInCurrentFolder == 0) add(thread.uid)
                }
            }

        if (uidsToMove.isNotEmpty()) threadController.updateIsLocallyMovedOutStatus(uidsToMove, hasBeenMovedOut = true)
        return uidsToMove
    }

    private fun refreshFoldersAsync(
        mailbox: Mailbox,
        messagesFoldersIds: ImpactedFolders,
        currentFolderId: String,
        destinationFolderId: String? = null,
        callbacks: RefreshCallbacks? = null,
    ) = viewModelScope.launch(ioCoroutineContext) {
        sharedUtils.refreshFolders(mailbox, messagesFoldersIds, destinationFolderId, currentFolderId, callbacks)
    }

    private fun onDownloadStart() {
        isDownloadingChanges.postValue(true)
    }

    private fun onDownloadStop(threadsUids: List<String> = emptyList()) = viewModelScope.launch(ioCoroutineContext) {
        threadController.updateIsLocallyMovedOutStatus(threadsUids, hasBeenMovedOut = false)
        isDownloadingChanges.postValue(false)
    }

}
