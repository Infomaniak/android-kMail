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
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.core.legacy.utils.SingleLiveEvent
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.emojicomponents.data.Reaction
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.ImpactedFolders
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshCallbacks
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.MoveResult
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.mailbox.SendersRestrictions
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.snooze.BatchSnoozeResult
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.SnackbarManager.UndoData
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.DraftInitManager
import com.infomaniak.mail.utils.EmojiReactionUtils.hasAvailableReactionSlot
import com.infomaniak.mail.utils.ErrorCode
import com.infomaniak.mail.utils.FeatureAvailability
import com.infomaniak.mail.utils.FolderRoleUtils
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.SharedUtils.Companion.unsnoozeThreadsWithoutRefresh
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.Utils.isPermanentDeleteFolder
import com.infomaniak.mail.utils.coroutineContext
import com.infomaniak.mail.utils.date.DateFormatUtils.dayOfWeekDateWithoutYear
import com.infomaniak.mail.utils.extensions.allFailed
import com.infomaniak.mail.utils.extensions.appContext
import com.infomaniak.mail.utils.extensions.atLeastOneFailed
import com.infomaniak.mail.utils.extensions.atLeastOneSucceeded
import com.infomaniak.mail.utils.extensions.getFirstTranslatedError
import com.infomaniak.mail.utils.extensions.getFoldersIds
import com.infomaniak.mail.utils.extensions.getUids
import com.infomaniak.mail.workers.DraftsActionsWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import com.infomaniak.core.legacy.R as RCore

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActionsViewModel @Inject constructor(
    application: Application,
    private val draftController: DraftController,
    private val draftInitManager: DraftInitManager,
    private val draftsActionsWorkerScheduler: DraftsActionsWorker.Scheduler,
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

    val activityDialogLoaderResetTrigger = SingleLiveEvent<Unit>()
    val reportPhishingTrigger = SingleLiveEvent<Unit>()

    //region Spam
    fun moveToSpamFolder(messagesUid: List<String>, currentFolderId: String?, mailbox: Mailbox) {
        viewModelScope.launch(ioCoroutineContext) {
            val messages = messageController.getMessages(messagesUid)
            toggleMessagesSpamStatus(messages, currentFolderId, mailbox)
        }
    }

    fun toggleThreadsOrMessagesSpamStatus(
        messages: List<Message>? = null,
        threads: Set<Thread>? = null,
        currentFolderId: String?,
        mailbox: Mailbox,
        displaySnackbar: Boolean = true
    ) = viewModelScope.launch(ioCoroutineContext) {
        val messagesToMarkAsSpam = when {
            threads != null -> getMessagesFromThreadToSpamOrHam(threads)
            messages != null -> messageController.getUnscheduledMessages(messages)
            else -> emptyList()
        }

        toggleMessagesSpamStatus(messagesToMarkAsSpam, currentFolderId, mailbox, displaySnackbar)
    }

    private fun toggleMessagesSpamStatus(
        messages: List<Message>,
        currentFolderId: String?,
        mailbox: Mailbox,
        displaySnackbar: Boolean = true,
    ) = viewModelScope.launch(ioCoroutineContext) {

        val folder = if (currentFolderId != null) folderController.getFolder(currentFolderId) else null
        val folderRole = folderRoleUtils.getActionFolderRole(messages, folder)

        val destinationFolderRole = if (folderRole == FolderRole.SPAM) {
            FolderRole.INBOX
        } else {
            FolderRole.SPAM
        }
        val destinationFolder = folderController.getFolder(destinationFolderRole)!!

        val messages = messageController.getUnscheduledMessages(messages)

        moveMessagesTo(destinationFolder, currentFolderId, mailbox, messages, displaySnackbar)
    }

    fun activateSpamFilter(mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        ApiRepository.setSpamFilter(
            mailboxHostingId = mailbox.hostingId,
            mailboxName = mailbox.mailboxName,
            activateSpamFilter = true,
        )
    }

    private suspend fun getMessagesFromThreadToSpamOrHam(threads: Set<Thread>): List<Message> {
        return threads.flatMap { messageController.getUnscheduledMessagesFromThread(it, includeDuplicates = false) }
    }

    fun unblockMail(email: String, mailbox: Mailbox?) = viewModelScope.launch(ioCoroutineContext) {
        if (mailbox == null) return@launch

        with(ApiRepository.getSendersRestrictions(mailbox.hostingId, mailbox.mailboxName)) {
            if (isSuccess()) {
                val updatedSendersRestrictions = data!!.apply {
                    blockedSenders.removeIf { it.email == email }
                }
                updateBlockedSenders(mailbox, updatedSendersRestrictions)
            }
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

    //endregion

    //region Move
    fun moveThreadsOrMessagesTo(
        destinationFolderId: String,
        threadsUids: List<String>? = null,
        messagesUid: List<String>? = null,
        currentFolderId: String?,
        mailbox: Mailbox,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val destinationFolder = folderController.getFolder(destinationFolderId) ?: return@launch
        val threads: List<Thread>? = threadsUids?.let { threadController.getThreads(threadsUids).toList() }
        val messages = messagesUid?.let { messageController.getMessages(it) }
        val messagesToMove = sharedUtils.getMessagesToMove(threads, messages, currentFolderId)

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
        messagesMoved: List<Message>,
        apiResponses: List<ApiResponse<MoveResult>>,
        destinationFolder: Folder,
    ) {

        val destination = destinationFolder.getLocalizedName(appContext)

        val snackbarTitle = when {
            apiResponses.allFailed() -> appContext.getString(apiResponses.first().translateError())
            threadsMoved.count() > 0 || messagesMoved.count() > 1 -> appContext.resources.getQuantityString(
                R.plurals.snackbarThreadMoved,
                threadsMoved.count(),
                destination
            )
            else -> appContext.getString(R.string.snackbarMessageMoved, destination)
        }

        val undoResources = apiResponses.mapNotNull { it.data?.undoResource }
        val undoData = if (undoResources.isEmpty()) {
            null
        } else {
            val undoDestinationId = destinationFolder.id
            val foldersIds = messagesMoved.getFoldersIds(exception = undoDestinationId)
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

    //region Delete
    fun deleteThreadsOrMessages(
        threads: List<Thread>? = null,
        messages: List<Message>? = null,
        currentFolder: Folder?,
        mailbox: Mailbox
    ) = viewModelScope.launch(ioCoroutineContext) {
        val messagesToDelete = getMessagesToDelete(threads, messages)
        deleteMessages(messagesToDelete, currentFolder, mailbox)
    }

    private fun deleteMessages(
        messages: List<Message>,
        currentFolder: Folder?,
        mailbox: Mailbox
    ) = viewModelScope.launch(ioCoroutineContext) {
        val shouldPermanentlyDelete = isPermanentDeleteFolder(folderRoleUtils.getActionFolderRole(messages, currentFolder))

        if (shouldPermanentlyDelete) {
            permanentlyDelete(messages, currentFolder, mailbox)
        } else {
            moveMessagesTo(
                destinationFolder = folderController.getFolder(FolderRole.TRASH)!!,
                messages = messages,
                currentFolderId = currentFolder?.id,
                mailbox = mailbox
            )
        }
    }

    private suspend fun permanentlyDelete(messagesToDelete: List<Message>, currentFolder: Folder?, mailbox: Mailbox) {
        val undoResources = emptyList<String>()
        val uids = messagesToDelete.getUids()

        val uidsToMove = moveOutThreadsLocally(messagesToDelete, folderController.getFolder(FolderRole.TRASH)!!)

        val apiResponses = ApiRepository.deleteMessages(
            mailboxUuid = mailbox.uuid,
            messagesUids = uids,
            alsoMoveReactionMessages = FeatureAvailability.isReactionsAvailable(mailbox.featureFlags, localSettings)
        )

        activityDialogLoaderResetTrigger.postValue(Unit)

        if (apiResponses.atLeastOneSucceeded()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messagesToDelete.getFoldersIds(),
                currentFolderId = currentFolder?.id,
                callbacks = RefreshCallbacks(onStart = ::onDownloadStart, onStop = { onDownloadStop(uidsToMove) }),
            )
        }

        if (apiResponses.atLeastOneFailed()) threadController.updateIsLocallyMovedOutStatus(
            threadsUids = uidsToMove,
            hasBeenMovedOut = false
        )

        val undoDestinationId = messagesToDelete.first().folderId
        val undoFoldersIds = messagesToDelete.getFoldersIds(exception = undoDestinationId)
        showDeleteSnackbar(
            apiResponses = apiResponses,
            messages = messagesToDelete,
            undoResources = undoResources,
            undoFoldersIds = undoFoldersIds,
            undoDestinationId = undoDestinationId,
            numberOfImpactedThreads = messagesToDelete.count(),
        )
    }

    private fun showDeleteSnackbar(
        apiResponses: List<ApiResponse<*>>,
        messages: List<Message>,
        undoResources: List<String>,
        undoFoldersIds: ImpactedFolders,
        undoDestinationId: String?,
        numberOfImpactedThreads: Int,
    ) {
        val snackbarTitle = if (apiResponses.atLeastOneSucceeded()) {
            if (messages.count() > 1) {
                appContext.resources.getQuantityString(
                    R.plurals.snackbarThreadDeletedPermanently,
                    numberOfImpactedThreads
                )
            } else {
                appContext.getString(R.string.snackbarMessageDeletedPermanently)
            }
        } else {
            appContext.getString(apiResponses.first().translateError())
        }

        val undoData = if (undoResources.isEmpty()) null else UndoData(undoResources, undoFoldersIds, undoDestinationId)

        snackbarManager.postValue(snackbarTitle, undoData)
    }

    private suspend fun getMessagesToDelete(threads: List<Thread>?, messages: List<Message>?) = when {
        threads != null -> threads.flatMap { messageController.getUnscheduledMessagesFromThread(it, includeDuplicates = true) }
        messages != null -> messageController.getMessagesAndDuplicates(messages)
        else -> emptyList()
    }

    //endregion

    //region Archive

    fun archiveThreadsOrMessages(
        threads: List<Thread>? = null,
        messages: List<Message>? = null,
        currentFolder: Folder?,
        mailbox: Mailbox
    ) = viewModelScope.launch(ioCoroutineContext) {
        val messagesToMove = sharedUtils.getMessagesToMove(threads, messages, currentFolder?.id)
        archiveMessages(messagesToMove, currentFolder, mailbox)
    }

    private fun archiveMessages(
        messages: List<Message>,
        currentFolder: Folder?,
        mailbox: Mailbox
    ) = viewModelScope.launch(ioCoroutineContext) {

        val role = folderRoleUtils.getActionFolderRole(messages, currentFolder)
        val isFromArchive = role == FolderRole.ARCHIVE
        val destinationFolderRole = if (isFromArchive) FolderRole.INBOX else FolderRole.ARCHIVE
        val destinationFolder = folderController.getFolder(destinationFolderRole) ?: return@launch

        moveMessagesTo(destinationFolder, currentFolder?.id, mailbox, messages)
    }

    private fun refreshFoldersAsync(
        mailbox: Mailbox,
        messagesFoldersIds: ImpactedFolders,
        currentFolderId: String? = null,
        destinationFolderId: String? = null,
        callbacks: RefreshCallbacks? = null,
    ) = viewModelScope.launch(ioCoroutineContext) {
        sharedUtils.refreshFolders(mailbox, messagesFoldersIds, destinationFolderId, currentFolderId, callbacks)
    }

    //region Seen

    fun toggleThreadsOrMessagesSeenStatus(
        threadsUids: List<String>? = null,
        messages: List<Message>? = null,
        shouldRead: Boolean = true,
        currentFolderId: String?,
        mailbox: Mailbox
    ) {
        toggleMessagesSeenStatus(threadsUids, messages, shouldRead = shouldRead, currentFolderId, mailbox)
    }

    private fun toggleMessagesSeenStatus(
        threadsUids: List<String>? = null,
        messages: List<Message>? = null,
        shouldRead: Boolean = true,
        currentFolderId: String?,
        mailbox: Mailbox
    ) = viewModelScope.launch(ioCoroutineContext) {
        val threads = threadsUids?.let { threadController.getThreads(threadsUids) }
        val isSeen = when {
            messages?.count() == 1 -> messages.single().isSeen
            threads?.count() == 1 -> threads.single().isSeen
            else -> !shouldRead
        }

        val messagesToToggleSeen = getMessagesToMarkAsUnseen(threads, messages, mailbox)

        if (isSeen) {
            markAsUnseen(messagesToToggleSeen, mailbox)
        } else {
            sharedUtils.markMessagesAsSeen(
                messages = messagesToToggleSeen,
                currentFolderId = currentFolderId,
                mailbox = mailbox,
                callbacks = RefreshCallbacks(::onDownloadStart, ::onDownloadStop),
            )
        }
    }

    private suspend fun markAsUnseen(
        messages: List<Message>,
        mailbox: Mailbox
    ) {
        val messagesUids = messages.map { it.uid }

        sharedUtils.updateSeenStatus(messagesUids, isSeen = false)

        val apiResponses = ApiRepository.markMessagesAsUnseen(mailbox.uuid, messagesUids)

        if (apiResponses.atLeastOneSucceeded()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(),
                callbacks = RefreshCallbacks(::onDownloadStart, ::onDownloadStop),
            )
        } else {
            sharedUtils.updateSeenStatus(messagesUids, isSeen = true)
        }
    }

    private suspend fun getMessagesToMarkAsUnseen(
        threads: List<Thread>?,
        messages: List<Message>?,
        mailbox: Mailbox
    ): List<Message> = when {
        threads != null -> threads.flatMap { thread ->
            messageController.getLastMessageAndItsDuplicatesToExecuteAction(thread, mailbox.featureFlags)
        }
        messages != null -> messageController.getMessagesAndDuplicates(messages)
        else -> emptyList() //this should never happen, we should always send a list of messages or threads.
    }

    //endregion

    //region Favorite

    fun toggleThreadsOrMessagesFavoriteStatus(
        threadsUids: List<String>? = null,
        messages: List<Message>? = null,
        shouldFavorite: Boolean = true,
        mailbox: Mailbox
    ) = viewModelScope.launch(ioCoroutineContext) {
        val threads = threadsUids?.let { threadController.getThreads(threadsUids) }

        val isFavorite = when {
            messages?.count() == 1 -> messages.single().isFavorite
            threads?.count() == 1 -> threads.single().isFavorite
            else -> !shouldFavorite
        }

        val messages = if (isFavorite) {
            getMessagesToUnfavorite(threads, messages)
        } else {
            getMessagesToFavorite(threads, messages, mailbox)
        }

        toggleMessagesFavoriteStatus(messages, isFavorite, mailbox)
    }

    private fun toggleMessagesFavoriteStatus(
        messages: List<Message>,
        isFavorite: Boolean,
        mailbox: Mailbox
    ) = viewModelScope.launch(ioCoroutineContext) {

        val uids = messages.getUids()

        updateFavoriteStatus(messagesUids = uids, isFavorite = !isFavorite)

        val apiResponses = if (isFavorite) {
            ApiRepository.removeFromFavorites(mailbox.uuid, uids)
        } else {
            ApiRepository.addToFavorites(mailbox.uuid, uids)
        }

        if (apiResponses.atLeastOneSucceeded()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(),
                callbacks = RefreshCallbacks(::onDownloadStart, ::onDownloadStop),
            )
        } else {
            updateFavoriteStatus(messagesUids = uids, isFavorite = isFavorite)
        }
    }

    private suspend fun getMessagesToFavorite(threads: List<Thread>?, messages: List<Message>?, mailbox: Mailbox) = when {
        threads != null -> threads.flatMap { thread ->
            messageController.getLastMessageAndItsDuplicatesToExecuteAction(thread, mailbox.featureFlags)
        }
        messages != null -> messageController.getMessagesAndDuplicates(messages)
        else -> emptyList() // this should never happen, we should always pass threads or messages
    }

    private suspend fun getMessagesToUnfavorite(threads: List<Thread>?, messages: List<Message>?) = when {
        threads != null -> threads.flatMap { messageController.getFavoriteMessages(it) }
        messages != null -> messageController.getMessagesAndDuplicates(messages)
        else -> emptyList()
    }

    private suspend fun updateFavoriteStatus(messagesUids: List<String>, isFavorite: Boolean) {
        mailboxContentRealm().write {
            MessageController.updateFavoriteStatus(messagesUids, isFavorite, realm = this)
        }
    }

    //endregion

    //region Phishing
    fun reportPhishing(messages: List<Message>, currentFolder: Folder?, mailbox: Mailbox) {
        viewModelScope.launch(ioCoroutineContext) {
            val mailboxUuid = mailbox.uuid
            val messagesUids: List<String> = messages.map { it.uid }

            if (messagesUids.isEmpty()) {
                snackbarManager.postValue(appContext.getString(RCore.string.anErrorHasOccurred))
                return@launch
            }

            with(ApiRepository.reportPhishing(mailboxUuid, messagesUids)) {
                val snackbarTitle = if (isSuccess()) {

                    if (folderRoleUtils.getActionFolderRole(messages, currentFolder) != FolderRole.SPAM) {
                        toggleThreadsOrMessagesSpamStatus(
                            messages = messages,
                            currentFolderId = currentFolder?.id,
                            mailbox = mailbox,
                            displaySnackbar = false
                        )
                    }

                    R.string.snackbarReportPhishingConfirmation
                } else {
                    translateError()
                }

                reportPhishingTrigger.postValue(Unit)
                snackbarManager.postValue(appContext.getString(snackbarTitle))
            }
        }
    }
    //endregion

    //region BlockUser
    fun blockUser(folderId: String, shortUid: Int, mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        val mailboxUuid = mailbox.uuid
        with(ApiRepository.blockUser(mailboxUuid, folderId, shortUid)) {
            val snackbarTitle = if (isSuccess()) R.string.snackbarBlockUserConfirmation else translateError()
            snackbarManager.postValue(appContext.getString(snackbarTitle))

            reportPhishingTrigger.postValue(Unit)
        }
    }
    //endregion

    //region Snooze
    // For now we only do snooze for Threads.
    suspend fun snoozeThreads(date: Date, threadUids: List<String>, currentFolderId: String?, mailbox: Mailbox?): Boolean {
        var isSuccess = false

        viewModelScope.launch {
            mailbox?.let { currentMailbox ->
                val threads = threadUids.mapNotNull { threadController.getThread(it) }

                val messageUids = threads.mapNotNull { thread ->
                    thread.getDisplayedMessages(currentMailbox.featureFlags, localSettings)
                        .lastOrNull { it.folderId == currentFolderId }?.uid
                }

                val responses = ioDispatcher { ApiRepository.snoozeMessages(currentMailbox.uuid, messageUids, date) }

                isSuccess = responses.atLeastOneSucceeded()
                val userFeedbackMessage = if (isSuccess) {
                    // Snoozing threads requires to refresh the snooze folder.
                    // It's the only folder that will update the snooze state of any message.
                    refreshFoldersAsync(currentMailbox, ImpactedFolders(mutableSetOf(FolderRole.SNOOZED)))

                    val formattedDate = appContext.dayOfWeekDateWithoutYear(date)
                    appContext.resources.getQuantityString(R.plurals.snackbarSnoozeSuccess, threads.count(), formattedDate)
                } else {
                    val errorMessageRes = responses.getFirstTranslatedError() ?: RCore.string.anErrorHasOccurred
                    appContext.getString(errorMessageRes)
                }

                snackbarManager.postValue(userFeedbackMessage)
            }
        }.join()

        return isSuccess
    }

    suspend fun rescheduleSnoozedThreads(date: Date, threadUids: List<String>, mailbox: Mailbox): BatchSnoozeResult {
        var rescheduleResult: BatchSnoozeResult = BatchSnoozeResult.Error.Unknown

        viewModelScope.launch(ioCoroutineContext) {
            val snoozedThreadUuids = threadUids.mapNotNull { threadUid ->
                val thread = threadController.getThread(threadUid) ?: return@mapNotNull null
                thread.snoozeUuid.takeIf { thread.isSnoozed() }
            }
            if (snoozedThreadUuids.isEmpty()) return@launch

            val result = rescheduleSnoozedThreads(mailbox, snoozedThreadUuids, date)

            val userFeedbackMessage = when (result) {
                is BatchSnoozeResult.Success -> {
                    refreshFoldersAsync(mailbox, result.impactedFolders)

                    val formattedDate = appContext.dayOfWeekDateWithoutYear(date)
                    appContext.resources.getQuantityString(R.plurals.snackbarSnoozeSuccess, threadUids.count(), formattedDate)
                }
                is BatchSnoozeResult.Error -> getRescheduleSnoozedErrorMessage(result)
            }

            snackbarManager.postValue(userFeedbackMessage)

            rescheduleResult = result
        }.join()

        return rescheduleResult
    }

    private suspend fun rescheduleSnoozedThreads(
        currentMailbox: Mailbox,
        snoozeUuids: List<String>,
        date: Date,
    ): BatchSnoozeResult {
        return SharedUtils.rescheduleSnoozedThreads(
            mailboxUuid = currentMailbox.uuid,
            snoozeUuids = snoozeUuids,
            newDate = date,
            impactedFolders = ImpactedFolders(mutableSetOf(FolderRole.SNOOZED)),
        )
    }

    private fun getRescheduleSnoozedErrorMessage(errorResult: BatchSnoozeResult.Error): String {
        val errorMessageRes = when (errorResult) {
            BatchSnoozeResult.Error.NoneSucceeded -> R.string.errorSnoozeFailedModify
            is BatchSnoozeResult.Error.ApiError -> errorResult.translatedError
            BatchSnoozeResult.Error.Unknown -> RCore.string.anErrorHasOccurred
        }
        return appContext.getString(errorMessageRes)
    }

    suspend fun unsnoozeThreads(threads: Collection<Thread>, mailbox: Mailbox?): BatchSnoozeResult {
        var unsnoozeResult: BatchSnoozeResult = BatchSnoozeResult.Error.Unknown

        viewModelScope.launch(ioCoroutineContext) {
            unsnoozeResult = if (mailbox == null) {
                BatchSnoozeResult.Error.Unknown
            } else {
                ioDispatcher { unsnoozeThreadsWithoutRefresh(scope = null, mailbox, threads) }
            }

            unsnoozeResult.let {
                val userFeedbackMessage = when (it) {
                    is BatchSnoozeResult.Success -> {
                        sharedUtils.refreshFolders(mailbox = mailbox!!, messagesFoldersIds = it.impactedFolders)
                        appContext.resources.getQuantityString(R.plurals.snackbarUnsnoozeSuccess, threads.count())
                    }
                    is BatchSnoozeResult.Error -> getUnsnoozeErrorMessage(it)
                }

                snackbarManager.postValue(userFeedbackMessage)
            }
        }.join()

        return unsnoozeResult
    }

    private fun getUnsnoozeErrorMessage(errorResult: BatchSnoozeResult.Error): String {
        val errorMessageRes = when (errorResult) {
            BatchSnoozeResult.Error.NoneSucceeded -> R.string.errorSnoozeFailedCancel
            is BatchSnoozeResult.Error.ApiError -> errorResult.translatedError
            BatchSnoozeResult.Error.Unknown -> RCore.string.anErrorHasOccurred
        }

        return appContext.getString(errorMessageRes)
    }
    //endregion

    //region Emoji reaction
    /**
     * Wrapper method to send an emoji reaction to the api. This method will check if the emoji reaction is allowed before
     * initiating an api call. This is the entry point to add an emoji reaction anywhere in the app.
     *
     * If sending is allowed, the caller place can fake the emoji reaction locally thanks to [onAllowed].
     * If sending is not allowed, it will display the error directly to the user and avoid doing the api call.
     */
    fun trySendEmojiReply(
        emoji: String,
        messageUid: String,
        reactions: Map<String, Reaction>,
        hasNetwork: Boolean,
        mailbox: Mailbox,
        onAllowed: () -> Unit = {},
    ) {
        viewModelScope.launch {
            when (val status = reactions.getEmojiSendStatus(emoji, hasNetwork)) {
                EmojiSendStatus.Allowed -> {
                    onAllowed()
                    sendEmojiReply(emoji, messageUid, mailbox)
                }
                is EmojiSendStatus.NotAllowed -> snackbarManager.postValue(appContext.getString(status.errorMessageRes))
            }
        }
    }

    private fun Map<String, Reaction>.getEmojiSendStatus(emoji: String, hasNetwork: Boolean): EmojiSendStatus = when {
        this[emoji]?.hasReacted == true -> EmojiSendStatus.NotAllowed.AlreadyUsed
        hasAvailableReactionSlot().not() -> EmojiSendStatus.NotAllowed.MaxReactionReached
        hasNetwork.not() -> EmojiSendStatus.NotAllowed.NoInternet
        else -> EmojiSendStatus.Allowed
    }

    /**
     * The actual logic of sending an emoji reaction to the api. This method initializes a [Draft] instance, stores it into the
     * database and schedules the [DraftsActionsWorker] so the draft is uploaded on the api.
     */
    private suspend fun sendEmojiReply(emoji: String, messageUid: String, mailbox: Mailbox) {
        val targetMessage = messageController.getMessage(messageUid) ?: return
        val (fullMessage, hasFailedFetching) = draftController.fetchHeavyDataIfNeeded(targetMessage)
        if (hasFailedFetching) return
        val draftMode = Draft.DraftMode.REPLY_ALL

        val draft = Draft().apply {
            with(draftInitManager) {
                setPreviousMessage(draftMode, fullMessage)
            }

            val quote = draftInitManager.createQuote(draftMode, fullMessage, attachments)
            body = EMOJI_REACTION_PLACEHOLDER + quote

            with(draftInitManager) {
                // We don't want to send the HTML code of the signature for an emoji reaction but we still need to send the
                // identityId stored in a Signature
                val signature = chooseSignature(mailbox.email, mailbox.signatures, draftMode, fullMessage)
                setSignatureIdentity(signature)
            }

            mimeType = Utils.TEXT_HTML

            action = Draft.DraftAction.SEND_REACTION
            emojiReaction = emoji
        }

        draftController.upsertDraft(draft)

        draftsActionsWorkerScheduler.scheduleWork(draft.localUuid, AccountUtils.currentMailboxId, AccountUtils.currentUserId)
    }

    private sealed interface EmojiSendStatus {
        data object Allowed : EmojiSendStatus

        sealed class NotAllowed(@StringRes val errorMessageRes: Int) : EmojiSendStatus {
            data object AlreadyUsed : NotAllowed(ErrorCode.EmojiReactions.alreadyUsed.translateRes)
            data object MaxReactionReached : NotAllowed(ErrorCode.EmojiReactions.maxReactionReached.translateRes)
            data object NoInternet : NotAllowed(RCore.string.noConnection)
        }
    }
    //endregion

    //region Undo action
    fun undoAction(undoData: UndoData, mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {

        fun List<ApiResponse<*>>.getFailedCall() = firstOrNull { it.data != true }

        val (resources, foldersIds, destinationFolderId) = undoData

        val apiResponses = resources.map { ApiRepository.undoAction(it) }

        if (apiResponses.atLeastOneSucceeded()) {
            // Don't use `refreshFoldersAsync` here, it will make the Snackbars blink.
            sharedUtils.refreshFolders(
                mailbox = mailbox,
                messagesFoldersIds = foldersIds,
                destinationFolderId = destinationFolderId,
            )
        }

        val failedCall = apiResponses.getFailedCall()

        val snackbarTitle = when {
            failedCall == null -> R.string.snackbarMoveCancelled
            else -> failedCall.translateError()
        }

        snackbarManager.postValue(appContext.getString(snackbarTitle))
    }
    //endregion

    private fun onDownloadStart() {
        isDownloadingChanges.postValue(true)
    }

    private fun onDownloadStop(threadsUids: List<String> = emptyList()) = viewModelScope.launch(ioCoroutineContext) {
        threadController.updateIsLocallyMovedOutStatus(threadsUids, hasBeenMovedOut = false)
        isDownloadingChanges.postValue(false)
    }

    companion object {
        private val TAG: String = ActionsViewModel::class.java.simpleName
        private const val EMOJI_REACTION_PLACEHOLDER = "<div>__REACTION_PLACEMENT__<br></div>"
    }
}
