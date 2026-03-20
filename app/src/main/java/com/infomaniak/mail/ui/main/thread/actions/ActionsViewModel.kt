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
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.ImpactedFolders
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshCallbacks
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode
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
import com.infomaniak.mail.useCases.MessagesActionsUseCase
import com.infomaniak.mail.utils.DownloadThreadsStatusManager
import com.infomaniak.mail.utils.FolderRoleUtils
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
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val messageController: MessageController,
    private val messagesActionsUseCase: MessagesActionsUseCase,
    private val refreshController: RefreshController,
    private val snackbarManager: SnackbarManager,
    private val threadController: ThreadController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    //region Scheduled Draft
    var draftResource: String? = null
    //endregion

    val activityDialogLoaderResetTrigger = SingleLiveEvent<Unit>()
    val reportPhishingTrigger = SingleLiveEvent<Unit>()

    //region Spam
    fun moveToSpamFolder(messagesUid: List<String>, currentFolderId: String?, mailbox: Mailbox) {
        viewModelScope.launch(ioCoroutineContext) {
            val messages = messageController.getMessages(messagesUid)
            handleToggleSpamMessages(messages, currentFolderId, mailbox, displaySnackbar = true)
        }
    }

    fun toggleThreadsSpamStatus(
        threads: Set<Thread>,
        currentFolderId: String?,
        mailbox: Mailbox,
        displaySnackbar: Boolean = true,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val messagesToMarkAsSpam = messagesActionsUseCase.getMessagesFromThreadsToSpamOrHam(threads)
        handleToggleSpamMessages(messagesToMarkAsSpam, currentFolderId, mailbox, displaySnackbar)
    }

    fun toggleMessagesSpamStatus(
        messages: List<Message>,
        currentFolderId: String?,
        mailbox: Mailbox,
        displaySnackbar: Boolean = true,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val messagesToMarkAsSpam = messageController.getUnscheduledMessages(messages)
        handleToggleSpamMessages(messagesToMarkAsSpam, currentFolderId, mailbox, displaySnackbar)
    }

    private suspend fun handleToggleSpamMessages(
        messages: List<Message>,
        currentFolderId: String?,
        mailbox: Mailbox,
        displaySnackbar: Boolean = true,
    ) {
        val result = messagesActionsUseCase.toggleMessagesSpamStatus(
            messages = messages,
            currentFolderId = currentFolderId,
            mailbox = mailbox,
        )

        if (result != null) {
            with(result) {
                if (apiResponses.atLeastOneSucceeded()) {
                    if (currentFolderId != null) {
                        refreshFoldersAsync(
                            mailbox = mailbox,
                            messagesFoldersIds = messages.getFoldersIds(exception = destinationFolder.id),
                            destinationFolderId = destinationFolder.id,
                            currentFolderId = currentFolderId,
                        )
                    }

                    if (displaySnackbar) showMoveSnackbar(movedThreads, messages, apiResponses, destinationFolder)
                }

                if (apiResponses.atLeastOneFailed()) {
                    viewModelScope.launch(ioCoroutineContext) {
                        threadController.updateIsLocallyMovedOutStatus(threadsUids = movedThreads, hasBeenMovedOut = false)
                    }
                }
            }
        }
    }

    fun activateSpamFilter(mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        messagesActionsUseCase.activateSpamFilter(mailbox)
    }

    fun unblockMail(email: String, mailbox: Mailbox?) = viewModelScope.launch(ioCoroutineContext) {
        if (mailbox == null) return@launch

        val result = messagesActionsUseCase.unblockMail(email, mailbox)
        if (result is MessagesActionsUseCase.ApiCallResult.Error) {
            snackbarManager.postValue(appContext.getString(result.messageRes))
        }
    }
    //endregion

    //region Move
    fun moveThreadsTo(
        destinationFolderId: String,
        threadsUids: List<String>,
        currentFolderId: String?,
        mailbox: Mailbox,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val threads: List<Thread> = threadController.getThreads(threadsUids).toList()
        val messagesToMove = messagesActionsUseCase.getMessagesFromThreadsToMove(threads)

        handleMessagesMove(destinationFolderId, messagesToMove, currentFolderId, mailbox)
    }

    fun moveMessagesTo(
        destinationFolderId: String,
        messagesUids: List<String>,
        currentFolderId: String?,
        mailbox: Mailbox,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val messages = messageController.getMessages(messagesUids)
        val messagesToMove = messagesActionsUseCase.getMessagesToMove(messages, currentFolderId)

        handleMessagesMove(destinationFolderId, messagesToMove, currentFolderId, mailbox)
    }

    private suspend fun handleMessagesMove(
        destinationFolderId: String,
        messages: List<Message>,
        currentFolderId: String?,
        mailbox: Mailbox,
    ) {
        val destinationFolder = folderController.getFolder(destinationFolderId) ?: return

        val result = messagesActionsUseCase.moveMessagesTo(
            destinationFolder = destinationFolder,
            mailbox = mailbox,
            messages = messages,
        )

        with(result) {
            if (apiResponses.atLeastOneSucceeded() && currentFolderId != null) {
                refreshFoldersAsync(
                    mailbox = mailbox,
                    messagesFoldersIds = messages.getFoldersIds(exception = destinationFolder.id),
                    destinationFolderId = destinationFolder.id,
                    currentFolderId = currentFolderId,
                )
            }

            if (apiResponses.atLeastOneFailed() && movedThreads.isNotEmpty()) {
                viewModelScope.launch(ioCoroutineContext) {
                    threadController.updateIsLocallyMovedOutStatus(movedThreads, hasBeenMovedOut = false)
                }
            }
        }

        showMoveSnackbar(result.movedThreads, result.messages, result.apiResponses, result.destinationFolder)
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
                destination,
            )
            else -> appContext.getString(R.string.snackbarMessageMoved, destination)
        }

        val undoData = messagesActionsUseCase.getUndoData(messagesMoved, apiResponses, destinationFolder)
        snackbarManager.postValue(snackbarTitle, undoData)
    }
    //endregion

    //region Delete
    fun deleteThreads(
        threads: List<Thread>,
        currentFolder: Folder?,
        mailbox: Mailbox,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val messagesToDelete = messagesActionsUseCase.getMessagesFromThreadsToDelete(threads)
        handleDeleteMessages(messagesToDelete, currentFolder, mailbox)
    }

    fun deleteMessages(
        messages: List<Message>,
        currentFolder: Folder?,
        mailbox: Mailbox,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val messagesToDelete = messagesActionsUseCase.getMessagesToDelete(messages)
        handleDeleteMessages(messagesToDelete, currentFolder, mailbox)
    }

    private suspend fun handleDeleteMessages(
        messagesToDelete: List<Message>,
        currentFolder: Folder?,
        mailbox: Mailbox,
    ) {
        val shouldPermanentlyDelete =
            isPermanentDeleteFolder(folderRoleUtils.getActionFolderRole(messagesToDelete, currentFolder))

        if (shouldPermanentlyDelete) {
            val result = messagesActionsUseCase.permanentlyDelete(
                messagesToDelete = messagesToDelete,
                mailbox = mailbox,
                onApiFinished = { activityDialogLoaderResetTrigger.postValue(Unit) },
            )

            if (result != null) {
                if (result.apiResponses.atLeastOneSucceeded()) {
                    refreshFoldersAsync(
                        mailbox = mailbox,
                        messagesFoldersIds = messagesToDelete.getFoldersIds(),
                        currentFolderId = currentFolder?.id,
                    )
                    showDeleteSnackbar(
                        apiResponses = result.apiResponses,
                        messages = messagesToDelete,
                        numberOfImpactedThreads = messagesToDelete.count(),
                    )
                }

                if (result.apiResponses.atLeastOneFailed()) {
                    viewModelScope.launch(ioCoroutineContext) {
                        threadController.updateIsLocallyMovedOutStatus(threadsUids = result.uidsToMove, hasBeenMovedOut = false)
                    }
                }
            }

        } else {
            val destinationFolder = folderController.getFolder(FolderRole.TRASH)
            if (destinationFolder == null) {
                snackbarManager.postValue(appContext.getString(RCore.string.anErrorHasOccurred))
                return
            }

            moveMessagesTo(
                destinationFolderId = destinationFolder.id,
                messagesUids = messagesToDelete.getUids(),
                currentFolderId = currentFolder?.id,
                mailbox = mailbox,
            )
        }
    }

    private fun showDeleteSnackbar(
        apiResponses: List<ApiResponse<*>>,
        messages: List<Message>,
        undoResources: List<String>? = null,
        undoFoldersIds: ImpactedFolders? = null,
        undoDestinationId: String? = null,
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

        val undoData = if (undoResources?.isNotEmpty() == true && undoFoldersIds != null) {
            UndoData(undoResources, undoFoldersIds, undoDestinationId)
        } else {
            null
        }

        snackbarManager.postValue(snackbarTitle, undoData)
    }

    //endregion

    //region Archive
    fun archiveThreads(
        threads: List<Thread>,
        currentFolder: Folder?,
        mailbox: Mailbox,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val messagesToMove = messagesActionsUseCase.getMessagesFromThreadsToMove(threads)
        handleArchiveMessage(messagesToMove, currentFolder, mailbox)
    }

    fun archiveMessages(
        messages: List<Message>,
        currentFolder: Folder?,
        mailbox: Mailbox
    ) = viewModelScope.launch(ioCoroutineContext) {
        val messagesToMove = messagesActionsUseCase.getMessagesToMove(messages, currentFolder?.id)
        handleArchiveMessage(messagesToMove, currentFolder, mailbox)
    }

    private suspend fun handleArchiveMessage(
        messages: List<Message>,
        currentFolder: Folder?,
        mailbox: Mailbox,
    ) {
        val role = folderRoleUtils.getActionFolderRole(messages, currentFolder)
        val isFromArchive = role == FolderRole.ARCHIVE
        val destinationFolderRole = if (isFromArchive) FolderRole.INBOX else FolderRole.ARCHIVE
        val destinationFolder = folderController.getFolder(destinationFolderRole) ?: return

        moveMessagesTo(destinationFolder.id, messages.getUids(), currentFolder?.id, mailbox)
    }

    //region Seen
    fun toggleThreadsSeenStatus(
        threadsUids: List<String>,
        shouldRead: Boolean = true,
        currentFolderId: String?,
        mailbox: Mailbox,
    ) = viewModelScope.launch(ioCoroutineContext) {

        val result = messagesActionsUseCase.toggleThreadsSeenStatus(threadsUids, shouldRead, mailbox)
        if (result.apiResponses.atLeastOneSucceeded()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = result.messages.getFoldersIds(),
                currentFolderId = currentFolderId,
            )
        }
    }

    fun toggleMessagesSeenStatus(
        messages: List<Message>,
        shouldRead: Boolean = true,
        currentFolderId: String?,
        mailbox: Mailbox,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val result = messagesActionsUseCase.toggleMessagesSeenStatus(messages, shouldRead, mailbox)
        if (result.apiResponses.atLeastOneSucceeded()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(),
                currentFolderId = currentFolderId,
            )
        }
    }

    //endregion

    //region Favorite
    fun toggleThreadsFavoriteStatus(
        threadsUids: List<String>,
        shouldFavorite: Boolean = true,
        mailbox: Mailbox,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val result = messagesActionsUseCase.toggleThreadsFavorite(threadsUids, shouldFavorite, mailbox)
        if (result.apiResponses.atLeastOneSucceeded()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = result.messages.getFoldersIds(),
            )
        }
    }

    fun toggleMessagesFavoriteStatus(
        messages: List<Message>,
        shouldFavorite: Boolean = true,
        mailbox: Mailbox,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val result = messagesActionsUseCase.toggleMessagesFavorite(messages, shouldFavorite, mailbox)
        if (result.apiResponses.atLeastOneSucceeded()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(),
            )
        }
    }
    //endregion

    //region Phishing
    fun reportPhishing(messages: List<Message>, currentFolder: Folder?, mailbox: Mailbox) {
        viewModelScope.launch(ioCoroutineContext) {
            val result = messagesActionsUseCase.reportPhishing(
                messages = messages,
                currentFolder = currentFolder,
                mailbox = mailbox,
                onReportSuccess = {
                    toggleMessagesSpamStatus(
                        messages = messages,
                        currentFolderId = currentFolder?.id,
                        mailbox = mailbox,
                        displaySnackbar = false,
                    )
                }
            )

            when (result) {
                is MessagesActionsUseCase.ApiCallResult.Success -> {
                    reportPhishingTrigger.postValue(Unit)
                    snackbarManager.postValue(appContext.getString(result.messageRes))
                }
                is MessagesActionsUseCase.ApiCallResult.Error -> {
                    snackbarManager.postValue(appContext.getString(result.messageRes))
                }
            }
        }
    }
    //endregion

    //region BlockUser
    fun blockUser(folderId: String, shortUid: Int, mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        when (val result = messagesActionsUseCase.blockUser(folderId, shortUid, mailbox)) {
            is MessagesActionsUseCase.ApiCallResult.Success -> {
                reportPhishingTrigger.postValue(Unit)
                snackbarManager.postValue(appContext.getString(result.messageRes))
            }
            is MessagesActionsUseCase.ApiCallResult.Error -> {
                snackbarManager.postValue(appContext.getString(result.messageRes))
            }
        }
    }
    //endregion

    //region Snooze
    // For now we only do snooze for Threads.
    suspend fun snoozeThreads(date: Date, threadUids: List<String>, currentFolderId: String?, mailbox: Mailbox?): Boolean {
        if (mailbox == null) return false

        val result = messagesActionsUseCase.snoozeThreads(
            date = date,
            threadUids = threadUids,
            currentFolderId = currentFolderId,
            mailbox = mailbox,
        )

        if (result is MessagesActionsUseCase.SnoozeResult.Success) {
            refreshFoldersAsync(mailbox, ImpactedFolders(mutableSetOf(FolderRole.SNOOZED)))
        }

        val message = when (result) {
            is MessagesActionsUseCase.SnoozeResult.Success -> {
                val formattedDate = appContext.dayOfWeekDateWithoutYear(result.date)
                appContext.resources.getQuantityString(R.plurals.snackbarSnoozeSuccess, result.threadCount, formattedDate)
            }
            is MessagesActionsUseCase.SnoozeResult.Error -> appContext.getString(result.messageRes)
        }

        snackbarManager.postValue(message)
        return result is MessagesActionsUseCase.SnoozeResult.Success
    }

    suspend fun rescheduleSnoozedThreads(date: Date, threadUids: List<String>, mailbox: Mailbox): BatchSnoozeResult {
        val result = messagesActionsUseCase.rescheduleSnoozedThreads(
            date = date,
            threadUids = threadUids,
            mailbox = mailbox,
        )

        if (result is BatchSnoozeResult.Success) {
            refreshFoldersAsync(mailbox, result.impactedFolders)
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
            val result = messagesActionsUseCase.unsnoozeThreads(threads, mailbox)

            if (result is BatchSnoozeResult.Success) {
                refreshFoldersAsync(mailbox, result.impactedFolders)
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
            with(ApiRepository.rescheduleDraft(resource, scheduleDate)) {
                if (isSuccess()) {
                    refreshFoldersAsync(mailbox, ImpactedFolders(mutableSetOf(FolderRole.SCHEDULED_DRAFTS)))
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
        mailbox: Mailbox
    ) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = mailbox
        val apiResponse = ApiRepository.unscheduleDraft(unscheduleDraftUrl)

        if (apiResponse.isSuccess()) {
            val scheduledDraftsFolderId = folderController.getFolder(FolderRole.SCHEDULED_DRAFTS)!!.id
            refreshFoldersAsync(mailbox, ImpactedFolders(mutableSetOf(scheduledDraftsFolderId)))
            onSuccess()
        } else {
            snackbarManager.postValue(title = appContext.getString(apiResponse.translateError()))
        }
    }

    fun unscheduleDraft(unscheduleDraftUrl: String, mailbox: Mailbox, openFolder: (folderId: String) -> Unit) =
        viewModelScope.launch(ioCoroutineContext) {
            val mailbox = mailbox
            val apiResponse = ApiRepository.unscheduleDraft(unscheduleDraftUrl)

            if (apiResponse.isSuccess()) {
                val scheduledDraftsFolderId = folderController.getFolder(FolderRole.SCHEDULED_DRAFTS)!!.id
                refreshFoldersAsync(mailbox, ImpactedFolders(mutableSetOf(scheduledDraftsFolderId)))
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
            val mailbox = mailbox
            val apiResponse = ApiRepository.deleteDraft(targetMailboxUuid, remoteDraftUuid)

            if (apiResponse.isSuccess() && mailbox.uuid == targetMailboxUuid) {
                val draftFolderId = folderController.getFolder(FolderRole.DRAFT)!!.id
                refreshFoldersAsync(mailbox, ImpactedFolders(mutableSetOf(draftFolderId)))
            }

            showDeletedDraftSnackbar(apiResponse)
        }

    private fun showDeletedDraftSnackbar(apiResponse: ApiResponse<Unit>) {
        val titleRes = if (apiResponse.isSuccess()) R.string.snackbarDraftDeleted else apiResponse.translateError()
        snackbarManager.postValue(appContext.getString(titleRes))
    }
    //endregion

    //endregion

    //region Undo action
    fun undoAction(undoData: UndoData?, mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        if (undoData == null) return@launch
        val result = messagesActionsUseCase.undoAction(undoData)
        if (result is MessagesActionsUseCase.ApiCallResult.Success) {
            with(undoData) {
                refreshFoldersAsync(
                    mailbox = mailbox,
                    messagesFoldersIds = foldersIds,
                    destinationFolderId = destinationFolderId,
                )
            }
        }

        val message = when (result) {
            is MessagesActionsUseCase.ApiCallResult.Success -> appContext.getString(result.messageRes)
            is MessagesActionsUseCase.ApiCallResult.Error -> appContext.getString(result.messageRes)
        }

        snackbarManager.postValue(message)
    }
    //endregion

    // region refresh

    fun refreshFoldersAsync(
        mailbox: Mailbox,
        messagesFoldersIds: ImpactedFolders,
        destinationFolderId: String? = null,
        currentFolderId: String? = null,
        threadsUids: List<String> = emptyList(),
    ) = viewModelScope.launch(ioCoroutineContext) {
        val realm = mailboxContentRealm()

        // We always want to refresh the `destinationFolder` last, to avoid any blink on the UI.
        val foldersIds = messagesFoldersIds.getFolderIds(realm).toMutableSet()
        destinationFolderId?.let(foldersIds::add)

        foldersIds.forEach { folderId ->
            refreshController.refreshThreads(
                refreshMode = RefreshMode.REFRESH_FOLDER,
                mailbox = mailbox,
                folderId = folderId,
                realm = realm,
                callbacks = if (folderId == currentFolderId) {
                    RefreshCallbacks(
                        onStart = ::onDownloadStart,
                        onStop = { onDownloadStop(threadsUids) })
                } else {
                    null
                },
            )
        }
    }

    private fun onDownloadStart() {
        downloadThreadsStatusManager.updateState(true)
    }

    private fun onDownloadStop(threadsUids: List<String> = emptyList()) = viewModelScope.launch(ioCoroutineContext) {
        threadController.updateIsLocallyMovedOutStatus(threadsUids, hasBeenMovedOut = false)
        downloadThreadsStatusManager.updateState(false)
    }
    //
}
