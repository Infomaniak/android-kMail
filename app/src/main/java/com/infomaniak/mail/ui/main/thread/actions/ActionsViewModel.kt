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
import androidx.lifecycle.viewModelScope
import com.infomaniak.core.legacy.utils.SingleLiveEvent
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.ImpactedFolders
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.MoveResult
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.snooze.BatchSnoozeResult
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.SnackbarManager.UndoData
import com.infomaniak.mail.useCases.MessagesActions
import com.infomaniak.mail.utils.DownloadThreadsStatusManager
import com.infomaniak.mail.utils.FolderRoleUtils
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.Utils.isPermanentDeleteFolder
import com.infomaniak.mail.utils.coroutineContext
import com.infomaniak.mail.utils.date.DateFormatUtils.dayOfWeekDateWithoutYear
import com.infomaniak.mail.utils.extensions.allFailed
import com.infomaniak.mail.utils.extensions.appContext
import com.infomaniak.mail.utils.extensions.atLeastOneFailed
import com.infomaniak.mail.utils.extensions.atLeastOneSucceeded
import com.infomaniak.mail.utils.extensions.getFoldersIds
import com.infomaniak.mail.utils.extensions.getUids
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import com.infomaniak.core.legacy.R as RCore

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActionsViewModel @Inject constructor(
    application: Application,
    private val downloadThreadsStatusManager: DownloadThreadsStatusManager,
    private val folderController: FolderController,
    private val folderRoleUtils: FolderRoleUtils,
    private val messageController: MessageController,
    private val messagesActions: MessagesActions,
    private val sharedUtils: SharedUtils,
    private val snackbarManager: SnackbarManager,
    private val threadController: ThreadController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    //region Scheduled Draft
    var draftResource: String? = null
    //endregion

    //region AutoAdvance
    val calculateCurrentThreadPosition = SingleLiveEvent<Unit>()
    var currentThread: Pair<Int, String>? = null
    val tryToAutoAdvance = SingleLiveEvent<Unit>()
    //endregion

    val activityDialogLoaderResetTrigger = SingleLiveEvent<Unit>()
    val reportPhishingTrigger = SingleLiveEvent<Unit>()

    //region refreshSearch
    private val _searchRefreshEvents = Channel<Unit>(Channel.CONFLATED)
    val searchRefreshEvents = _searchRefreshEvents.receiveAsFlow()

    fun notifySearchRefresh() {
        _searchRefreshEvents.trySend(Unit)
    }
    //endregion

    //region AutoAdvance
    fun updateCurrentThreadPosition(currentPosition: Int, currentUid: String) {
        currentThread = currentPosition to currentUid
    }
    //endregion

    //region Spam
    fun moveToSpamFolder(messagesUid: List<String>, parentFolderId: String?, mailbox: Mailbox) {
        viewModelScope.launch(ioCoroutineContext) {
            val messages = messageController.getMessages(messagesUid)
            handleToggleSpamMessages(messages, parentFolderId, mailbox, displaySnackbar = true)
        }
    }

    fun toggleThreadsSpamStatus(
        threads: Set<Thread>,
        parentFolderId: String?,
        mailbox: Mailbox,
        displaySnackbar: Boolean = true,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val messagesToMarkAsSpam = messagesActions.getMessagesFromThreadsToSpamOrHam(threads)
        handleToggleSpamMessages(messagesToMarkAsSpam, parentFolderId, mailbox, displaySnackbar)
    }

    fun toggleMessagesSpamStatus(
        messages: List<Message>,
        parentFolderId: String?,
        mailbox: Mailbox,
        displaySnackbar: Boolean = true,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val messagesToMarkAsSpam = messageController.getUnscheduledMessages(messages)
        handleToggleSpamMessages(messagesToMarkAsSpam, parentFolderId, mailbox, displaySnackbar)
    }

    private suspend fun handleToggleSpamMessages(
        messages: List<Message>,
        parentFolderId: String?,
        mailbox: Mailbox,
        displaySnackbar: Boolean = true,
    ) {
        val result = messagesActions.toggleMessagesSpamStatus(
            messages = messages,
            mailbox = mailbox,
        ) ?: run {
            snackbarManager.postValue(appContext.getString(RCore.string.anErrorHasOccurred))
            return
        }

        with(result) {
            if (apiResponses.atLeastOneSucceeded()) {
                if (parentFolderId != null) {
                    refreshFoldersAsync(
                        mailbox = mailbox,
                        messagesFoldersIds = messages.getFoldersIds(exception = destinationFolder.id),
                        destinationFolderId = destinationFolder.id,
                        parentFolderId = parentFolderId,
                        threadsUids = movedThreads,
                    )
                }

                if (displaySnackbar) showMoveSnackbar(
                    movedThreads.count(),
                    messages.count(),
                    messages.getFoldersIds(exception = destinationFolder.id),
                    apiResponses,
                    destinationFolder
                )
            }

            if (apiResponses.atLeastOneFailed()) {
                viewModelScope.launch(ioCoroutineContext) {
                    threadController.updateIsLocallyMovedOutStatus(threadsUids = movedThreads, hasBeenMovedOut = false)
                }
            }

            notifySearchRefresh()
        }
    }

    fun activateSpamFilter(mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        messagesActions.activateSpamFilter(mailbox)
    }

    fun unblockMail(email: String, mailbox: Mailbox?) = viewModelScope.launch(ioCoroutineContext) {
        if (mailbox == null) return@launch

        val result = messagesActions.unblockMail(email, mailbox)
        if (result is MessagesActions.ApiCallResult.Error) {
            snackbarManager.postValue(appContext.getString(result.messageRes))
        }
    }
    //endregion

    //region Move
    fun moveThreadsTo(
        destinationFolderId: String,
        threadsUids: List<String>,
        parentFolderId: String?,
        mailbox: Mailbox,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val threads: List<Thread> = threadController.getThreads(threadsUids).toList()
        val messagesToMove = messagesActions.getMessagesFromThreadsToMove(threads)

        handleMessagesMove(destinationFolderId, messagesToMove, parentFolderId, mailbox)
    }

    fun moveMessagesTo(
        destinationFolderId: String,
        messagesUids: List<String>,
        parentFolderId: String?,
        mailbox: Mailbox,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val messages = messageController.getMessages(messagesUids)
        handleMessagesMove(destinationFolderId, messages, parentFolderId, mailbox)
    }

    private suspend fun handleMessagesMove(
        destinationFolderId: String,
        messages: List<Message>,
        parentFolderId: String?,
        mailbox: Mailbox,
    ) {
        val destinationFolder = folderController.getFolder(destinationFolderId)
        if (destinationFolder == null) {
            snackbarManager.postValue(appContext.getString(RCore.string.anErrorHasOccurred))
            return
        }

        calculateCurrentThreadPosition.postValue(Unit)

        val result = messagesActions.moveMessagesTo(
            destinationFolder = destinationFolder,
            mailbox = mailbox,
            messages = messages,
        )

        with(result) {
            if (apiResponses.atLeastOneSucceeded()) {
                currentThread?.let { (_, uid) ->
                    if (movedThreads.isNotEmpty() && movedThreads.contains(uid)) tryToAutoAdvance.postValue(Unit)
                }
                refreshFoldersAsync(
                    mailbox = mailbox,
                    messagesFoldersIds = messages.getFoldersIds(exception = destinationFolder.id),
                    destinationFolderId = destinationFolder.id,
                    parentFolderId = parentFolderId,
                    threadsUids = movedThreads,
                )
            }

            if (apiResponses.atLeastOneFailed() && movedThreads.isNotEmpty()) {
                viewModelScope.launch(ioCoroutineContext) {
                    threadController.updateIsLocallyMovedOutStatus(movedThreads, hasBeenMovedOut = false)
                }
            }

            notifySearchRefresh()
        }

        showMoveSnackbar(
            result.movedThreads.count(),
            result.messages.count(),
            result.messages.getFoldersIds(exception = destinationFolderId),
            result.apiResponses,
            result.destinationFolder
        )
    }

    private fun showMoveSnackbar(
        threadsMovedCount: Int,
        messagesMovedCount: Int,
        impactedFolders: ImpactedFolders,
        apiResponses: List<ApiResponse<MoveResult>>,
        destinationFolder: Folder,
    ) {
        val destination = destinationFolder.getLocalizedName(appContext)
        val snackbarTitle = when {
            apiResponses.allFailed() -> {
                if (apiResponses.isNotEmpty()) {
                    appContext.getString(apiResponses.first().translateError())
                } else {
                    appContext.getString(RCore.string.anErrorHasOccurred)
                }
            }
            threadsMovedCount > 0 || messagesMovedCount > 1 -> {
                appContext.resources.getQuantityString(
                    R.plurals.snackbarThreadMoved,
                    threadsMovedCount,
                    destination,
                )
            }
            else -> appContext.getString(R.string.snackbarMessageMoved, destination)
        }

        val undoData = messagesActions.getUndoData(impactedFolders, apiResponses, destinationFolder)
        snackbarManager.postValue(snackbarTitle, undoData)
    }
    //endregion

    //region Delete
    fun deleteThreads(
        threads: List<Thread>,
        parentFolderId: String?,
        mailbox: Mailbox,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val messagesToDelete = messagesActions.getMessagesFromThreadsToDelete(threads)
        handleDeleteMessages(messagesToDelete, parentFolderId, mailbox)
    }

    fun deleteMessages(
        messages: List<Message>,
        parentFolderId: String?,
        mailbox: Mailbox,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val messagesToDelete = messagesActions.getMessagesToDelete(messages)
        handleDeleteMessages(messagesToDelete, parentFolderId, mailbox)
    }

    private suspend fun handleDeleteMessages(
        messagesToDelete: List<Message>,
        parentFolderId: String?,
        mailbox: Mailbox,
    ) {
        val permanentlyDeleteMessages = messagesToDelete.filter { message ->
            isPermanentDeleteFolder(role = folderRoleUtils.getActionFolderRole(message))
        }

        val deleteMessages = messagesToDelete.filter { !permanentlyDeleteMessages.contains(it) }

        if (permanentlyDeleteMessages.isNotEmpty()) {
            val onlyPermanentlyDeleteMessages = deleteMessages.isEmpty()
            // If deleteMessages is empty we will do the auto advance after deleting permanently
            if (onlyPermanentlyDeleteMessages) calculateCurrentThreadPosition.postValue(Unit)
            handlePermanentlyDeleteMessages(
                permanentlyDeleteMessages = permanentlyDeleteMessages,
                mailbox = mailbox,
                parentFolderId = parentFolderId,
                shouldAutoAdvanceAndRefresh = onlyPermanentlyDeleteMessages,
                messagesToDelete = messagesToDelete,
            )
        }

        if (deleteMessages.isNotEmpty()) {
            calculateCurrentThreadPosition.postValue(Unit)
            val destinationFolder = folderController.getFolder(FolderRole.TRASH)
            if (destinationFolder == null) {
                snackbarManager.postValue(appContext.getString(RCore.string.anErrorHasOccurred))
                return
            }

            moveMessagesTo(
                destinationFolderId = destinationFolder.id,
                messagesUids = deleteMessages.getUids(),
                parentFolderId = parentFolderId,
                mailbox = mailbox,
            )
        }
    }

    private suspend fun handlePermanentlyDeleteMessages(
        permanentlyDeleteMessages: List<Message>,
        mailbox: Mailbox,
        parentFolderId: String?,
        shouldAutoAdvanceAndRefresh: Boolean,
        messagesToDelete: List<Message>
    ) {
        val result = messagesActions.permanentlyDelete(
            messagesToDelete = permanentlyDeleteMessages,
            mailbox = mailbox,
            onApiFinished = { activityDialogLoaderResetTrigger.postValue(Unit) },
        ) ?: run {
            snackbarManager.postValue(appContext.getString(RCore.string.anErrorHasOccurred))
            return
        }

        with(result) {
            // if there are only permanently delete messages, we should autoadvance and refresh the folder here.
            if (apiResponses.atLeastOneSucceeded() && shouldAutoAdvanceAndRefresh) {
                currentThread?.let { (_, uid) ->
                    if (uidsToMove.isNotEmpty() && uidsToMove.contains(uid)) tryToAutoAdvance.postValue(Unit)
                }
                refreshFoldersAsync(
                    mailbox = mailbox,
                    messagesFoldersIds = messagesToDelete.getFoldersIds(),
                    parentFolderId = parentFolderId,
                    threadsUids = uidsToMove,
                )
                notifySearchRefresh()
                val numberOfImpactedThreads = uidsToMove.distinct().count()
                showDeleteSnackbar(
                    apiResponses = apiResponses,
                    messages = messagesToDelete,
                    numberOfImpactedThreads = numberOfImpactedThreads,
                )
            }

            if (apiResponses.atLeastOneFailed() && uidsToMove.isNotEmpty()) {
                viewModelScope.launch(ioCoroutineContext) {
                    threadController.updateIsLocallyMovedOutStatus(threadsUids = uidsToMove, hasBeenMovedOut = false)
                }
            }
        }
    }

    private fun showDeleteSnackbar(
        apiResponses: List<ApiResponse<*>>,
        messages: List<Message>,
        numberOfImpactedThreads: Int,
    ) {
        val snackbarTitle = if (apiResponses.atLeastOneSucceeded()) {
            if (messages.count() > 1) {
                appContext.resources.getQuantityString(
                    R.plurals.snackbarThreadDeletedPermanently,
                    numberOfImpactedThreads,
                )
            } else {
                appContext.getString(R.string.snackbarMessageDeletedPermanently)
            }
        } else {
            appContext.getString(apiResponses.first().translateError())
        }
        // We send a null undoData, because this snackbar is only shown in a permanently delete and it is not possible
        // to undo this action.
        snackbarManager.postValue(snackbarTitle, null)
    }

    //endregion

    //region Archive
    fun archiveThreads(
        threads: List<Thread>,
        parentFolderId: String?,
        mailbox: Mailbox,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val messagesToMove = messagesActions.getMessagesFromThreadsToMove(threads)
        handleArchiveMessages(messagesToMove, parentFolderId, mailbox)
    }

    fun archiveMessages(
        messages: List<Message>,
        parentFolderId: String?,
        mailbox: Mailbox,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val messagesToMove = messagesActions.getMessagesToMove(messages, parentFolderId)
        handleArchiveMessages(messagesToMove, parentFolderId, mailbox)
    }

    private suspend fun handleArchiveMessages(
        messages: List<Message>,
        parentFolderId: String?,
        mailbox: Mailbox,
    ) {
        if (messages.isEmpty()) return
        val roles = folderRoleUtils.getActionFolderRoles(messages)
        val isFromArchive = roles.all { it == FolderRole.ARCHIVE }
        val destinationFolderRole = if (isFromArchive) FolderRole.INBOX else FolderRole.ARCHIVE
        val destinationFolder = folderController.getFolder(destinationFolderRole) ?: return

        moveMessagesTo(destinationFolder.id, messages.getUids(), parentFolderId, mailbox)
    }

    //region Seen
    fun toggleThreadsSeenStatus(
        threadsUids: List<String>,
        shouldRead: Boolean = true,
        parentFolderId: String?,
        mailbox: Mailbox,
        shouldRefreshSearch: Boolean = false,
    ) = viewModelScope.launch(ioCoroutineContext) {

        val result = messagesActions.toggleThreadsSeenStatus(threadsUids, shouldRead, mailbox)
        if (result.apiResponses.atLeastOneSucceeded()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = result.messages.getFoldersIds(),
                parentFolderId = parentFolderId,
            )
            if (shouldRefreshSearch) notifySearchRefresh()
        }
    }

    fun toggleMessagesSeenStatus(
        messages: List<Message>,
        shouldRead: Boolean = true,
        parentFolderId: String?,
        mailbox: Mailbox,
        shouldRefreshSearch: Boolean = false,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val result = messagesActions.toggleMessagesSeenStatus(messages, shouldRead, mailbox)
        if (result.apiResponses.atLeastOneSucceeded()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(),
                parentFolderId = parentFolderId,
            )
            if (shouldRefreshSearch) notifySearchRefresh()
        }
    }
    //endregion

    //region Favorite
    fun toggleThreadsFavoriteStatus(
        threadsUids: List<String>,
        shouldFavorite: Boolean = true,
        mailbox: Mailbox,
        shouldRefreshSearch: Boolean = false,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val result = messagesActions.toggleThreadsFavorite(threadsUids, shouldFavorite, mailbox)
        if (result.apiResponses.atLeastOneSucceeded()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = result.messages.getFoldersIds(),
            )
            if (shouldRefreshSearch) notifySearchRefresh()
        }
    }

    fun toggleMessagesFavoriteStatus(
        messages: List<Message>,
        shouldFavorite: Boolean = true,
        mailbox: Mailbox,
        shouldRefreshSearch: Boolean = false,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val result = messagesActions.toggleMessagesFavorite(messages, shouldFavorite, mailbox)
        if (result.apiResponses.atLeastOneSucceeded()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(),
            )
            if (shouldRefreshSearch) notifySearchRefresh()
        }
    }
    //endregion

    //region Phishing
    fun reportPhishing(
        messages: List<Message>,
        parentFolderId: String?,
        mailbox: Mailbox,
    ) {
        viewModelScope.launch(ioCoroutineContext) {
            val result = messagesActions.reportPhishing(
                messages = messages,
                mailbox = mailbox,
                onReportSuccess = {
                    toggleMessagesSpamStatus(
                        messages = messages,
                        parentFolderId = parentFolderId,
                        mailbox = mailbox,
                        displaySnackbar = false,
                    )
                }
            )

            when (result) {
                is MessagesActions.ApiCallResult.Success -> {
                    reportPhishingTrigger.postValue(Unit)
                    notifySearchRefresh()
                    snackbarManager.postValue(appContext.getString(result.messageRes))
                }
                is MessagesActions.ApiCallResult.Error -> {
                    snackbarManager.postValue(appContext.getString(result.messageRes))
                }
            }
        }
    }
    //endregion

    //region BlockUser
    fun blockUser(folderId: String, shortUid: Int, mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        when (val result = messagesActions.blockUser(folderId, shortUid, mailbox)) {
            is MessagesActions.ApiCallResult.Success -> {
                reportPhishingTrigger.postValue(Unit)
                snackbarManager.postValue(appContext.getString(result.messageRes))
            }
            is MessagesActions.ApiCallResult.Error -> {
                snackbarManager.postValue(appContext.getString(result.messageRes))
            }
        }
    }
    //endregion

    //region Snooze
    // For now we only do snooze for Threads.
    suspend fun snoozeThreads(date: Date, threadUids: List<String>, parentFolderId: String?, mailbox: Mailbox?): Boolean {
        if (mailbox == null) return false

        val result = messagesActions.snoozeThreads(
            date = date,
            threadUids = threadUids,
            parentFolderId = parentFolderId,
            mailbox = mailbox,
        )

        if (result is MessagesActions.SnoozeResult.Success) {
            refreshFoldersAsync(mailbox, ImpactedFolders(mutableSetOf(FolderRole.SNOOZED)))
            notifySearchRefresh()
        }

        val message = when (result) {
            is MessagesActions.SnoozeResult.Success -> {
                val formattedDate = appContext.dayOfWeekDateWithoutYear(result.date)
                appContext.resources.getQuantityString(R.plurals.snackbarSnoozeSuccess, result.threadCount, formattedDate)
            }
            is MessagesActions.SnoozeResult.Error -> appContext.getString(result.messageRes)
        }

        snackbarManager.postValue(message)
        return result is MessagesActions.SnoozeResult.Success
    }

    suspend fun rescheduleSnoozedThreads(date: Date, threadUids: List<String>, mailbox: Mailbox): BatchSnoozeResult {
        val result = messagesActions.rescheduleSnoozedThreads(
            date = date,
            threadUids = threadUids,
            mailbox = mailbox,
        )

        if (result is BatchSnoozeResult.Success) {
            refreshFoldersAsync(mailbox, result.impactedFolders)
            notifySearchRefresh()
        }

        val message = when (result) {
            is BatchSnoozeResult.Success -> {
                val formattedDate = appContext.dayOfWeekDateWithoutYear(date)
                appContext.resources.getQuantityString(R.plurals.snackbarSnoozeSuccess, threadUids.count(), formattedDate)
            }
            is BatchSnoozeResult.Error -> getRescheduleSnoozedErrorMessage(result)
        }

        snackbarManager.postValue(message)
        return result
    }

    fun unsnoozeThreads(threads: List<Thread>, mailbox: Mailbox?) {
        if (mailbox == null) {
            snackbarManager.postValue(appContext.getString(RCore.string.anErrorHasOccurred))
            return
        }

        viewModelScope.launch(ioCoroutineContext) {
            val result = messagesActions.unsnoozeThreads(threads, mailbox)

            if (result is BatchSnoozeResult.Success) {
                refreshFoldersAsync(mailbox, result.impactedFolders)
                notifySearchRefresh()
            }

            val message = when (result) {
                is BatchSnoozeResult.Success -> appContext.resources.getQuantityString(
                    R.plurals.snackbarUnsnoozeSuccess, threads.count()
                )
                is BatchSnoozeResult.Error -> getUnsnoozeErrorMessage(result)
            }
            snackbarManager.postValue(message)
        }
    }

    private fun getRescheduleSnoozedErrorMessage(errorResult: BatchSnoozeResult.Error): String {
        val errorMessageRes = when (errorResult) {
            BatchSnoozeResult.Error.NoneSucceeded -> R.string.errorSnoozeFailedModify
            is BatchSnoozeResult.Error.ApiError -> errorResult.translatedError
            BatchSnoozeResult.Error.Unknown -> RCore.string.anErrorHasOccurred
        }
        return appContext.getString(errorMessageRes)
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

    //region Drafts
    fun rescheduleDraft(scheduleDate: Date, mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        draftResource?.takeIf { it.isNotBlank() }?.let { resource ->
            with(messagesActions.rescheduleDraft(resource, scheduleDate)) {
                if (isSuccess()) {
                    refreshFoldersAsync(mailbox, ImpactedFolders(mutableSetOf(FolderRole.SCHEDULED_DRAFTS)))
                    notifySearchRefresh()
                } else {
                    snackbarManager.postValue(title = appContext.getString(translateError()))
                }
            }
        } ?: run {
            snackbarManager.postValue(title = appContext.getString(RCore.string.anErrorHasOccurred))
        }
    }

    fun modifyScheduledDraft(
        unscheduleDraftUrl: String,
        onSuccess: () -> Unit,
        mailbox: Mailbox,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val apiResponse = messagesActions.unscheduleDraft(unscheduleDraftUrl)
        if (apiResponse.isSuccess()) {
            val draftFolder = folderController.getFolder(FolderRole.SCHEDULED_DRAFTS) ?: run {
                snackbarManager.postValue(appContext.getString(RCore.string.anErrorHasOccurred))
                return@launch
            }

            refreshFoldersAsync(mailbox, ImpactedFolders(mutableSetOf(draftFolder.id)))
            notifySearchRefresh()
            onSuccess()
        } else {
            snackbarManager.postValue(title = appContext.getString(apiResponse.translateError()))
        }
    }

    fun unscheduleDraft(unscheduleDraftUrl: String, mailbox: Mailbox, openFolder: (folderId: String) -> Unit) =
        viewModelScope.launch(ioCoroutineContext) {
            val apiResponse = messagesActions.unscheduleDraft(unscheduleDraftUrl)
            if (apiResponse.isSuccess()) {
                val scheduledDraftsFolder = folderController.getFolder(FolderRole.SCHEDULED_DRAFTS) ?: run {
                    snackbarManager.postValue(appContext.getString(RCore.string.anErrorHasOccurred))
                    return@launch
                }

                refreshFoldersAsync(mailbox, ImpactedFolders(mutableSetOf(scheduledDraftsFolder.id)))
                notifySearchRefresh()
            }

            showUnscheduledDraftSnackbar(apiResponse, openFolder)
        }

    private fun showUnscheduledDraftSnackbar(apiResponse: ApiResponse<Unit>, openFolder: (folderId: String) -> Unit) {

        fun openDraftFolder() = viewModelScope.launch {
            val folderId = folderController.getFolder(FolderRole.DRAFT)?.id
            if (folderId != null) openFolder(folderId)
        }

        if (apiResponse.isSuccess()) {
            snackbarManager.postValue(
                title = appContext.getString(R.string.snackbarSaveInDraft),
                buttonTitle = R.string.draftFolder,
                customBehavior = ::openDraftFolder,
            )
        } else {
            snackbarManager.postValue(appContext.getString(apiResponse.translateError()))
        }
    }

    //region Delete

    fun deleteDraft(targetMailboxUuid: String, remoteDraftUuid: String, mailbox: Mailbox) =
        viewModelScope.launch(ioCoroutineContext) {
            val apiResponse = messagesActions.deleteDraft(targetMailboxUuid, remoteDraftUuid)

            if (apiResponse.isSuccess() && mailbox.uuid == targetMailboxUuid) {
                val draftFolder = folderController.getFolder(FolderRole.DRAFT) ?: run {
                    snackbarManager.postValue(appContext.getString(RCore.string.anErrorHasOccurred))
                    return@launch
                }

                refreshFoldersAsync(mailbox, ImpactedFolders(mutableSetOf(draftFolder.id)))
                notifySearchRefresh()
            }

            showDeletedDraftSnackbar(apiResponse)
        }

    private fun showDeletedDraftSnackbar(apiResponse: ApiResponse<Unit>) {
        val titleRes = if (apiResponse.isSuccess()) R.string.snackbarDraftDeleted else apiResponse.translateError()
        snackbarManager.postValue(appContext.getString(titleRes))
    }
    //endregion

    //region Undo action
    fun undoAction(undoData: UndoData?, mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        if (undoData == null) return@launch

        val result = messagesActions.undoAction(undoData)
        if (result is MessagesActions.ApiCallResult.Success) {
            with(undoData) {
                refreshFoldersAsync(
                    mailbox = mailbox,
                    messagesFoldersIds = foldersIds,
                    destinationFolderId = destinationFolderId,
                )
                notifySearchRefresh()
            }
        }

        val message = when (result) {
            is MessagesActions.ApiCallResult.Success -> appContext.getString(result.messageRes)
            is MessagesActions.ApiCallResult.Error -> appContext.getString(result.messageRes)
        }

        snackbarManager.postValue(message)
    }
    //endregion

    // region refresh

    private fun refreshFoldersAsync(
        mailbox: Mailbox,
        messagesFoldersIds: ImpactedFolders,
        destinationFolderId: String? = null,
        parentFolderId: String? = null,
        threadsUids: List<String> = emptyList(),
    ) = viewModelScope.launch(ioCoroutineContext) {
        if (parentFolderId == null) return@launch

        sharedUtils.refreshFolders(
            mailbox = mailbox,
            messagesFoldersIds = messagesFoldersIds,
            destinationFolderId = destinationFolderId,
            parentFolderId = parentFolderId,
            threadsUids = threadsUids,
            onDownloadStop = { threadsUids -> onDownloadStop(threadsUids) }
        )
    }

    private fun onDownloadStop(threadsUids: List<String> = emptyList()) = viewModelScope.launch(ioCoroutineContext) {
        threadController.updateIsLocallyMovedOutStatus(threadsUids, hasBeenMovedOut = false)
        downloadThreadsStatusManager.stop()
    }
    //endregion
}
