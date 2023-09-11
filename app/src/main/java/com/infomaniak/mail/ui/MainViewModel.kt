/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
package com.infomaniak.mail.ui

import android.app.Application
import androidx.lifecycle.*
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.MatomoMail.trackMultiSelectionEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.*
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.cache.mailboxInfo.PermissionsController
import com.infomaniak.mail.data.cache.mailboxInfo.QuotasController
import com.infomaniak.mail.data.cache.userInfo.AddressBookController
import com.infomaniak.mail.data.cache.userInfo.MergedContactController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.MoveResult
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.SnackBarManager
import com.infomaniak.mail.ui.main.SnackBarManager.UndoData
import com.infomaniak.mail.ui.main.folder.ThreadListViewModel
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.ContactUtils.getPhoneContacts
import com.infomaniak.mail.utils.ContactUtils.mergeApiContactsIntoPhoneContacts
import com.infomaniak.mail.utils.NotificationUtils.Companion.cancelNotification
import com.infomaniak.mail.utils.SharedUtils.Companion.updateSignatures
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.ext.copyFromRealm
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Date
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import com.infomaniak.lib.core.R as RCore

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val addressBookController: AddressBookController,
    private val draftController: DraftController,
    private val folderController: FolderController,
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val mailboxController: MailboxController,
    private val mergedContactController: MergedContactController,
    private val messageController: MessageController,
    private val permissionsController: PermissionsController,
    private val quotasController: QuotasController,
    private val refreshController: RefreshController,
    private val sharedUtils: SharedUtils,
    private val threadController: ThreadController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)
    private var refreshMailboxesAndFoldersJob: Job? = null

    // First boolean is the download status, second boolean is if the LoadMore button should be displayed
    val isDownloadingChanges: MutableLiveData<Pair<Boolean, Boolean?>> = MutableLiveData(false to null)
    val isInternetAvailable = MutableLiveData<Boolean>()
    val isNewFolderCreated = SingleLiveEvent<Boolean>()
    val toggleLightThemeForMessage = SingleLiveEvent<Message>()

    val snackBarManager by lazy { SnackBarManager() }

    val mailboxesLive = mailboxController.getMailboxesAsync(AccountUtils.currentUserId).asLiveData(ioCoroutineContext)

    //region Multi selection
    val isMultiSelectOnLiveData = MutableLiveData(false)
    inline var isMultiSelectOn
        get() = isMultiSelectOnLiveData.value!!
        set(value) {
            isMultiSelectOnLiveData.value = value
        }

    val selectedThreadsLiveData = MutableLiveData(mutableSetOf<Thread>())
    inline val selectedThreads
        get() = selectedThreadsLiveData.value!!

    val isEverythingSelected
        get() = runCatchingRealm { selectedThreads.count() == currentThreadsLive.value?.list?.count() }.getOrDefault(false)
    //endregion

    //region Current Mailbox
    private val _currentMailboxObjectId = MutableStateFlow<String?>(null)

    val currentMailbox = _currentMailboxObjectId.mapLatest {
        it?.let(mailboxController::getMailbox)
    }.asLiveData(ioCoroutineContext)

    val currentDefaultFoldersLive = _currentMailboxObjectId.flatMapLatest { objectId ->
        objectId?.let { folderController.getDefaultFoldersAsync().map { it.list.getDefaultMenuFolders() } } ?: emptyFlow()
    }.asLiveData(ioCoroutineContext)

    val currentCustomFoldersLive = _currentMailboxObjectId.flatMapLatest { objectId ->
        objectId
            ?.let { folderController.getCustomFoldersAsync().map { it.list.getCustomMenuFolders(dismissHiddenChildren = true) } }
            ?: emptyFlow()
    }.asLiveData(ioCoroutineContext)

    val currentQuotasLive = _currentMailboxObjectId.flatMapLatest {
        it?.let(quotasController::getQuotasAsync) ?: emptyFlow()
    }.asLiveData(ioCoroutineContext)

    val currentPermissionsLive = _currentMailboxObjectId.flatMapLatest {
        it?.let(permissionsController::getPermissionsAsync) ?: emptyFlow()
    }.asLiveData(ioCoroutineContext)
    //endregion

    //region Current Folder
    private val _currentFolderId = MutableStateFlow<String?>(null)
    val currentFolderId get() = _currentFolderId.value

    val currentFolder = _currentFolderId.mapLatest {
        it?.let(folderController::getFolder)
    }.asLiveData(ioCoroutineContext)

    val currentFolderLive = _currentFolderId.flatMapLatest {
        it?.let(folderController::getFolderAsync) ?: emptyFlow()
    }.asLiveData(ioCoroutineContext)

    val currentFilter = SingleLiveEvent(ThreadFilter.ALL)

    val currentThreadsLive = observeFolderAndFilter().flatMapLatest { (folder, filter) ->
        folder?.let { threadController.getThreadsAsync(it, filter) } ?: emptyFlow()
    }.asLiveData(ioCoroutineContext)

    private fun observeFolderAndFilter() = MediatorLiveData<Pair<Folder?, ThreadFilter>>().apply {
        value = currentFolder.value to currentFilter.value!!
        addSource(currentFolder) { value = it to value!!.second }
        addSource(currentFilter) { value = value?.first to it }
    }.asFlow()

    override fun onCleared() {
        super.onCleared()
        RealmDatabase.closeOldRealms()
    }

    /**
     * Force update the `currentFilter` to its current value.
     * The sole effect will be to force the `currentThreadsLive` to trigger immediately.
     * The idea is to force the Threads adapter to trigger again, to update the sections headers (Today, Yesterday, etc...).
     */
    fun forceTriggerCurrentFolder() {
        currentFilter.apply { value = value }
    }
    //endregion

    //region Merged Contacts
    // Explanation of this Map: Map<Email, Map<Name, MergedContact>>
    val mergedContactsLive: LiveData<MergedContactDictionary?> = mergedContactController
        .getMergedContactsAsync()
        .mapLatest { ContactUtils.arrangeMergedContacts(it.list.copyFromRealm()) }
        .asLiveData(ioCoroutineContext)
    //endregion

    fun updateUserInfo() = viewModelScope.launch(ioCoroutineContext) {
        SentryLog.d(TAG, "Update user info")
        updateAddressBooks()
        updateContacts()
    }

    fun loadCurrentMailboxFromLocal() = liveData(ioCoroutineContext) {
        emit(openMailbox())
    }

    private fun openMailbox(): Mailbox? {
        SentryLog.d(TAG, "Load current mailbox from local")

        val mailbox = mailboxController.getMailboxWithFallback(
            userId = AccountUtils.currentUserId,
            mailboxId = AccountUtils.currentMailboxId,
        ) ?: return null

        if (mailbox.isLocked || !mailbox.isPasswordValid) {
            switchToValidMailbox()
            return null
        }

        selectMailbox(mailbox)

        if (currentFolderId == null) {
            folderController.getFolder(DEFAULT_SELECTED_FOLDER)?.let { folder ->
                selectFolder(folder.id)
            }
        }

        return mailbox
    }

    private fun switchToValidMailbox() = viewModelScope.launch(ioCoroutineContext) {
        mailboxController.getFirstValidMailbox(AccountUtils.currentUserId)?.let {
            AccountUtils.switchToMailbox(it.mailboxId)
        } ?: context.launchNoValidMailboxesActivity()
    }

    fun dismissCurrentMailboxNotifications() = viewModelScope.launch(ioCoroutineContext) {
        currentMailbox.value?.let {
            context.cancelNotification(it.notificationGroupId)
        }
    }

    fun refreshMailboxesFromRemote() {

        refreshMailboxesAndFoldersJob?.cancel()
        refreshMailboxesAndFoldersJob = viewModelScope.launch(ioCoroutineContext) {

            SentryLog.d(TAG, "Refresh mailboxes from remote")
            with(ApiRepository.getMailboxes()) {
                if (isSuccess()) {
                    mailboxController.updateMailboxes(data!!)

                    val shouldStop = AccountUtils.manageMailboxesEdgeCases(context, data!!)
                    if (shouldStop) return@launch
                }
            }

            val mailbox = currentMailbox.value ?: openMailbox() ?: return@launch

            updateQuotas(mailbox)
            updatePermissions(mailbox)
            updateSignatures(mailbox)
            updateFolders(mailbox)

            (currentFolderId?.let(folderController::getFolder) ?: folderController.getFolder(DEFAULT_SELECTED_FOLDER))
                ?.let { folder ->
                    selectFolder(folder.id)
                    viewModelScope.launch(ioCoroutineContext) {
                        refreshThreads(mailbox, folder.id)
                    }
                }
        }
    }

    private fun selectMailbox(mailbox: Mailbox) {
        if (mailbox.objectId != _currentMailboxObjectId.value) {
            SentryLog.d(TAG, "Select mailbox: ${mailbox.email}")
            if (mailbox.mailboxId != AccountUtils.currentMailboxId) AccountUtils.currentMailboxId = mailbox.mailboxId
            AccountUtils.currentMailboxEmail = mailbox.email
            _currentMailboxObjectId.value = mailbox.objectId
            _currentFolderId.value = null
        }
    }

    private fun selectFolder(folderId: String) {
        if (folderId != currentFolderId) {
            SentryLog.d(TAG, "Select folder: $folderId")
            _currentFolderId.value = folderId
        }
    }

    private fun updateQuotas(mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        SentryLog.d(TAG, "Force refresh Quotas")
        if (mailbox.isLimited) with(ApiRepository.getQuotas(mailbox.hostingId, mailbox.mailboxName)) {
            if (isSuccess()) mailboxController.updateMailbox(mailbox.objectId) {
                it.quotas = data
            }
        }
    }

    private fun updatePermissions(mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        SentryLog.d(TAG, "Force refresh Permissions")
        with(ApiRepository.getPermissions(mailbox.linkId, mailbox.hostingId)) {
            if (isSuccess()) mailboxController.updateMailbox(mailbox.objectId) {
                it.permissions = data
            }
        }
    }

    private fun updateSignatures(mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        mailboxContentRealm().writeBlocking { updateSignatures(mailbox) }
    }

    private fun updateFolders(mailbox: Mailbox) {
        SentryLog.d(TAG, "Force refresh Folders")
        ApiRepository.getFolders(mailbox.uuid).data?.let { folders ->
            if (!mailboxContentRealm().isClosed()) folderController.update(folders, mailboxContentRealm())
        }
    }

    fun openFolder(folderId: String) = viewModelScope.launch(ioCoroutineContext) {
        if (folderId == currentFolderId) return@launch

        if (currentFilter.value != ThreadFilter.ALL) currentFilter.postValue(ThreadFilter.ALL)

        selectFolder(folderId)
        refreshThreads(folderId = folderId)
    }

    fun flushFolder() = viewModelScope.launch(ioCoroutineContext) {
        val mailboxUuid = currentMailbox.value?.uuid ?: return@launch
        val folderId = currentFolderId ?: return@launch

        with(ApiRepository.flushFolder(mailboxUuid, folderId)) {
            if (isSuccess()) {
                forceRefreshThreads()
            } else {
                snackBarManager.postValue(context.getString(translatedError))
            }
        }
    }

    fun forceRefreshThreads(showSwipeRefreshLayout: Boolean = true) = viewModelScope.launch(ioCoroutineContext) {
        SentryLog.d(TAG, "Force refresh threads")
        refreshThreads(showSwipeRefreshLayout = showSwipeRefreshLayout)
    }

    fun getOnePageOfOldMessages() = viewModelScope.launch(ioCoroutineContext) {

        if (isDownloadingChanges.value?.first == true) return@launch

        refreshController.refreshThreads(
            refreshMode = RefreshMode.ONE_PAGE_OF_OLD_MESSAGES,
            mailbox = currentMailbox.value!!,
            folder = currentFolder.value!!,
            realm = mailboxContentRealm(),
            started = ::startedDownload,
            stopped = ::stoppedDownload,
        )
    }

    private fun updateAddressBooks() {
        ApiRepository.getAddressBooks().data?.addressBooks?.let(addressBookController::update)
    }

    private fun updateContacts() {
        ApiRepository.getContacts().data?.let { apiContacts ->
            val phoneMergedContacts = getPhoneContacts(context)
            mergeApiContactsIntoPhoneContacts(apiContacts, phoneMergedContacts)
            mergedContactController.update(phoneMergedContacts.values.toList())
        }
    }

    private suspend fun refreshThreads(
        mailbox: Mailbox? = currentMailbox.value,
        folderId: String? = currentFolderId,
        showSwipeRefreshLayout: Boolean = true,
    ) {
        if (mailbox == null || folderId == null) return
        val folder = folderController.getFolder(folderId) ?: return

        val (started, stopped) = if (showSwipeRefreshLayout) {
            ::startedDownload to ::stoppedDownload
        } else {
            null to null
        }

        refreshController.refreshThreads(
            refreshMode = RefreshMode.REFRESH_FOLDER_WITH_ROLE,
            mailbox = mailbox,
            folder = folder,
            realm = mailboxContentRealm(),
            started = started,
            stopped = stopped,
        )
    }

    //region Delete
    fun deleteMessage(threadUid: String, message: Message) {
        deleteThreadsOrMessage(threadsUids = listOf(threadUid), message = message)
    }

    fun deleteThread(threadUid: String) {
        deleteThreadsOrMessage(threadsUids = listOf(threadUid))
    }

    fun deleteThreads(threadsUids: List<String>) {
        deleteThreadsOrMessage(threadsUids = threadsUids)
    }

    private fun deleteThreadsOrMessage(
        threadsUids: List<String>,
        message: Message? = null,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value!!
        val threads = getActionThreads(threadsUids).ifEmpty { return@launch }
        var trashId: String? = null
        var undoResource: String? = null

        val role = getActionFolderRole(threads, message)
        val shouldPermanentlyDelete = role == FolderRole.DRAFT || role == FolderRole.SPAM || role == FolderRole.TRASH

        val messages = getMessagesToDelete(threads, message)
        val uids = messages.getUids()

        val apiResponse = if (shouldPermanentlyDelete) {
            ApiRepository.deleteMessages(mailbox.uuid, uids)
        } else {
            trashId = folderController.getFolder(FolderRole.TRASH)!!.id
            ApiRepository.moveMessages(mailbox.uuid, uids, trashId).also {
                undoResource = it.data?.undoResource
            }
        }

        if (apiResponse.isSuccess()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(exception = trashId),
                destinationFolderId = trashId,
                started = ::startedDownload,
                stopped = ::stoppedDownload,
            )
        }

        val undoDestinationId = message?.folderId ?: threads.first().folderId
        val undoFoldersIds = (messages.getFoldersIds(exception = undoDestinationId) + trashId).filterNotNull()
        showDeleteSnackbar(
            apiResponse,
            shouldPermanentlyDelete,
            message,
            undoResource,
            undoFoldersIds,
            undoDestinationId,
            threads.count(),
        )
    }

    private fun showDeleteSnackbar(
        apiResponse: ApiResponse<*>,
        shouldPermanentlyDelete: Boolean,
        message: Message?,
        undoResource: String?,
        undoFoldersIds: List<String>,
        undoDestinationId: String?,
        numberOfImpactedThreads: Int,
    ) {

        val snackbarTitle = if (apiResponse.isSuccess()) {
            val destination = context.getString(FolderRole.TRASH.folderNameRes)
            when {
                shouldPermanentlyDelete && message == null -> {
                    context.resources.getQuantityString(R.plurals.snackbarThreadDeletedPermanently, numberOfImpactedThreads)
                }
                shouldPermanentlyDelete && message != null -> {
                    context.getString(R.string.snackbarMessageDeletedPermanently)
                }
                !shouldPermanentlyDelete && message == null -> {
                    context.resources.getQuantityString(R.plurals.snackbarThreadMoved, numberOfImpactedThreads, destination)
                }
                else -> context.getString(R.string.snackbarMessageMoved, destination)
            }
        } else {
            context.getString(apiResponse.translatedError)
        }

        snackBarManager.postValue(snackbarTitle, undoResource?.let { UndoData(it, undoFoldersIds, undoDestinationId) })
    }

    private fun getMessagesToDelete(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap(messageController::getUnscheduledMessages)
        else -> messageController.getMessageAndDuplicates(threads.first(), message)
    }

    fun deleteDraft(targetMailboxUuid: String, remoteDraftUuid: String) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value!!
        val apiResponse = ApiRepository.deleteDraft(targetMailboxUuid, remoteDraftUuid)

        if (apiResponse.isSuccess() && mailbox.uuid == targetMailboxUuid) {
            val draftFolderId = folderController.getFolder(FolderRole.DRAFT)!!.id
            refreshFoldersAsync(mailbox, listOf(draftFolderId))
        }

        showDraftDeletedSnackBar(apiResponse)
    }

    private fun showDraftDeletedSnackBar(apiResponse: ApiResponse<Unit>) {
        val titleRes = if (apiResponse.isSuccess()) R.string.snackbarDraftDeleted else apiResponse.translateError()
        snackBarManager.postValue(context.getString(titleRes))
    }
    //endregion

    //region Move
    fun moveThreadsOrMessageTo(
        destinationFolderId: String,
        threadsUids: Array<String>,
        messageUid: String? = null,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value!!
        val destinationFolder = folderController.getFolder(destinationFolderId)!!
        val threads = getActionThreads(threadsUids.toList()).ifEmpty { return@launch }
        val message = messageUid?.let { messageController.getMessage(it)!! }

        val messages = sharedUtils.getMessagesToMove(threads, message)

        val apiResponse = ApiRepository.moveMessages(mailbox.uuid, messages.getUids(), destinationFolder.id)

        if (apiResponse.isSuccess()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(exception = destinationFolder.id),
                destinationFolderId = destinationFolder.id,
                started = ::startedDownload,
                stopped = ::stoppedDownload,
            )
        }

        showMoveSnackbar(threads, message, messages, apiResponse, destinationFolder)
    }

    private fun showMoveSnackbar(
        threads: List<Thread>,
        message: Message?,
        messages: List<Message>,
        apiResponse: ApiResponse<MoveResult>,
        destinationFolder: Folder,
    ) {
        val undoDestinationId = message?.folderId ?: threads.first().folderId
        val undoFoldersIds = messages.getFoldersIds(exception = undoDestinationId) + destinationFolder.id

        val destination = destinationFolder.getLocalizedName(context)

        val snackbarTitle = when {
            !apiResponse.isSuccess() -> context.getString(apiResponse.translatedError)
            message == null -> context.resources.getQuantityString(R.plurals.snackbarThreadMoved, threads.count(), destination)
            else -> context.getString(R.string.snackbarMessageMoved, destination)
        }

        val undoData = apiResponse.data?.undoResource?.let { UndoData(it, undoFoldersIds, undoDestinationId) }
        snackBarManager.postValue(snackbarTitle, undoData)
    }
    //endregion

    //region Archive
    fun archiveMessage(threadUid: String, message: Message) {
        archiveThreadsOrMessage(threadsUids = listOf(threadUid), message = message)
    }

    fun archiveThread(threadUid: String) {
        archiveThreadsOrMessage(threadsUids = listOf(threadUid))
    }

    fun archiveThreads(threadsUids: List<String>) {
        archiveThreadsOrMessage(threadsUids = threadsUids)
    }

    private fun archiveThreadsOrMessage(
        threadsUids: List<String>,
        message: Message? = null,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value!!
        val threads = getActionThreads(threadsUids).ifEmpty { return@launch }

        val role = getActionFolderRole(threads, message)
        val isArchived = role == FolderRole.ARCHIVE

        val destinationFolderRole = if (isArchived) FolderRole.INBOX else FolderRole.ARCHIVE
        val destinationFolder = folderController.getFolder(destinationFolderRole)!!

        val messages = sharedUtils.getMessagesToMove(threads, message)

        val apiResponse = ApiRepository.moveMessages(mailbox.uuid, messages.getUids(), destinationFolder.id)

        if (apiResponse.isSuccess()) {
            val messagesFoldersIds = messages.getFoldersIds(exception = destinationFolder.id)
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messagesFoldersIds,
                destinationFolderId = destinationFolder.id,
                started = ::startedDownload,
                stopped = ::stoppedDownload,
            )
        }

        showMoveSnackbar(threads, message, messages, apiResponse, destinationFolder)
    }
    //endregion

    //region Seen
    fun toggleMessageSeenStatus(threadUid: String, message: Message) {
        toggleThreadsOrMessageSeenStatus(threadsUids = listOf(threadUid), message = message)
    }

    fun toggleThreadSeenStatus(threadUid: String) {
        toggleThreadsOrMessageSeenStatus(threadsUids = listOf(threadUid))
    }

    fun toggleThreadsSeenStatus(threadsUids: List<String>, shouldRead: Boolean) {
        toggleThreadsOrMessageSeenStatus(threadsUids = threadsUids, shouldRead = shouldRead)
    }

    private fun toggleThreadsOrMessageSeenStatus(
        threadsUids: List<String>,
        message: Message? = null,
        shouldRead: Boolean = true,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value!!
        val threads = getActionThreads(threadsUids).ifEmpty { return@launch }

        val isSeen = when {
            message != null -> message.isSeen
            threads.count() == 1 -> threads.single().unseenMessagesCount == 0
            else -> !shouldRead
        }

        if (isSeen) {
            markAsUnseen(mailbox, threads, message)
        } else {
            sharedUtils.markAsSeen(mailbox, threads, message, ::startedDownload, ::stoppedDownload)
        }
    }

    private fun markAsUnseen(
        mailbox: Mailbox,
        threads: List<Thread>,
        message: Message? = null,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val messages = getMessagesToMarkAsUnseen(threads, message)
        val isSuccess = ApiRepository.markMessagesAsUnseen(mailbox.uuid, messages.getUids()).isSuccess()
        if (isSuccess) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(),
                started = ::startedDownload,
                stopped = ::stoppedDownload,
            )
        }
    }

    private fun getMessagesToMarkAsUnseen(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap(messageController::getLastMessageAndItsDuplicatesToExecuteAction)
        else -> messageController.getMessageAndDuplicates(threads.first(), message)
    }
    //endregion

    //region Favorite
    fun toggleMessageFavoriteStatus(threadUid: String, message: Message) {
        toggleThreadsOrMessageFavoriteStatus(threadsUids = listOf(threadUid), message = message)
    }

    fun toggleThreadFavoriteStatus(threadUid: String) {
        toggleThreadsOrMessageFavoriteStatus(threadsUids = listOf(threadUid))
    }

    fun toggleThreadsFavoriteStatus(threadsUids: List<String>, shouldFavorite: Boolean) {
        toggleThreadsOrMessageFavoriteStatus(threadsUids = threadsUids, shouldFavorite = shouldFavorite)
    }

    private fun toggleThreadsOrMessageFavoriteStatus(
        threadsUids: List<String>,
        message: Message? = null,
        shouldFavorite: Boolean = true,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value!!
        val threads = getActionThreads(threadsUids).ifEmpty { return@launch }

        val isFavorite = when {
            message != null -> message.isFavorite
            threads.count() == 1 -> threads.single().isFavorite
            else -> !shouldFavorite
        }

        val messages = if (isFavorite) {
            getMessagesToUnfavorite(threads, message)
        } else {
            getMessagesToFavorite(threads, message)
        }
        val uids = messages.getUids()

        val isSuccess = if (isFavorite) {
            ApiRepository.removeFromFavorites(mailbox.uuid, uids).isSuccess()
        } else {
            ApiRepository.addToFavorites(mailbox.uuid, uids).isSuccess()
        }

        if (isSuccess) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(),
                started = ::startedDownload,
                stopped = ::stoppedDownload,
            )
        }
    }

    private fun getMessagesToFavorite(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap(messageController::getLastMessageAndItsDuplicatesToExecuteAction)
        else -> messageController.getMessageAndDuplicates(threads.first(), message)
    }

    private fun getMessagesToUnfavorite(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap(messageController::getFavoriteMessages)
        else -> messageController.getMessageAndDuplicates(threads.first(), message)
    }
    //endregion

    //region Spam
    private fun toggleMessageSpamStatus(threadUid: String, message: Message) {
        toggleThreadsOrMessageSpamStatus(threadsUids = listOf(threadUid), message = message, displaySnackbar = false)
    }

    fun toggleThreadSpamStatus(threadUid: String) {
        toggleThreadsOrMessageSpamStatus(threadsUids = listOf(threadUid))
    }

    fun toggleThreadsSpamStatus(threadsUids: List<String>) {
        toggleThreadsOrMessageSpamStatus(threadsUids = threadsUids)
    }

    private fun toggleThreadsOrMessageSpamStatus(
        threadsUids: List<String>,
        message: Message? = null,
        displaySnackbar: Boolean = true,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value!!
        val threads = getActionThreads(threadsUids).ifEmpty { return@launch }

        val destinationFolderRole = if (message?.isSpam == true || getActionFolderRole(threads, message) == FolderRole.SPAM) {
            FolderRole.INBOX
        } else {
            FolderRole.SPAM
        }
        val destinationFolder = folderController.getFolder(destinationFolderRole)!!

        val messages = getMessagesToSpamOrHam(threads, message)

        val apiResponse = ApiRepository.moveMessages(mailbox.uuid, messages.getUids(), destinationFolder.id)

        if (apiResponse.isSuccess()) {
            refreshFoldersAsync(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(exception = destinationFolder.id),
                destinationFolderId = destinationFolder.id,
                started = ::startedDownload,
                stopped = ::stoppedDownload,
            )
        }

        if (displaySnackbar) showMoveSnackbar(threads, message, messages, apiResponse, destinationFolder)
    }

    private fun getMessagesToSpamOrHam(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap(messageController::getUnscheduledMessages)
        else -> messageController.getMessageAndDuplicates(threads.first(), message)
    }
    //endregion

    //region Phishing
    fun reportPhishing(threadUid: String, message: Message) = viewModelScope.launch(ioCoroutineContext) {
        val mailboxUuid = currentMailbox.value?.uuid!!

        with(ApiRepository.reportPhishing(mailboxUuid, message.folderId, message.shortUid)) {

            val snackbarTitle = if (isSuccess()) {
                if (getActionFolderRole(message.threads, message) != FolderRole.SPAM) toggleMessageSpamStatus(threadUid, message)
                R.string.snackbarReportPhishingConfirmation
            } else {
                translatedError
            }

            snackBarManager.postValue(context.getString(snackbarTitle))
        }
    }
    //endregion

    //region BlockUser
    fun blockUser(message: Message) = viewModelScope.launch(ioCoroutineContext) {
        val mailboxUuid = currentMailbox.value?.uuid!!

        with(ApiRepository.blockUser(mailboxUuid, message.folderId, message.shortUid)) {

            val snackbarTitle = if (isSuccess()) R.string.snackbarBlockUserConfirmation else translatedError

            snackBarManager.postValue(context.getString(snackbarTitle))
        }
    }
    //endregion

    //region Undo action
    fun undoAction(undoData: UndoData) = viewModelScope.launch(ioCoroutineContext) {
        val mailbox = currentMailbox.value!!
        val (resource, foldersIds, destinationFolderId) = undoData

        with(ApiRepository.undoAction(resource)) {

            val snackbarTitle = if (data == true) {
                // Don't use `refreshFoldersAsync` here, it will make the Snackbars blink.
                sharedUtils.refreshFolders(mailbox, foldersIds, destinationFolderId, ::startedDownload, ::stoppedDownload)
                R.string.snackbarMoveCancelled
            } else {
                if (translatedError == 0) RCore.string.anErrorHasOccurred else translatedError
            }

            snackBarManager.postValue(context.getString(snackbarTitle))
        }
    }
    //endregion

    //region New Folder
    private fun createNewFolderSync(name: String): String? {
        val mailbox = currentMailbox.value ?: return null
        val apiResponse = ApiRepository.createFolder(mailbox.uuid, name)

        return if (apiResponse.isSuccess()) {
            updateFolders(mailbox)
            apiResponse.data?.id
        } else {
            snackBarManager.postValue(context.getString(apiResponse.translateError()), null)
            null
        }
    }

    fun createNewFolder(name: String) = viewModelScope.launch(ioCoroutineContext) { createNewFolderSync(name) }

    fun moveToNewFolder(
        name: String,
        threadsUids: Array<String>,
        messageUid: String?,
    ) = viewModelScope.launch(ioCoroutineContext) {
        val newFolderId = createNewFolderSync(name) ?: return@launch
        moveThreadsOrMessageTo(newFolderId, threadsUids, messageUid)
        isNewFolderCreated.postValue(true)
    }
    //endregion

    private fun refreshFoldersAsync(
        mailbox: Mailbox,
        messagesFoldersIds: List<String>,
        destinationFolderId: String? = null,
        started: (() -> Unit)? = null,
        stopped: (() -> Unit)? = null,
    ) = viewModelScope.launch(ioCoroutineContext) {
        sharedUtils.refreshFolders(mailbox, messagesFoldersIds, destinationFolderId, started, stopped)
    }

    private fun startedDownload() {
        isDownloadingChanges.postValue(true to null)
    }

    private fun stoppedDownload() {

        val shouldDisplayLoadMore = currentFolderId?.let(folderController::getFolder)
            ?.let { it.cursor != null && !it.isHistoryComplete }
            ?: false

        isDownloadingChanges.postValue(false to shouldDisplayLoadMore)
    }

    private fun getActionThreads(threadsUids: List<String>): List<Thread> = threadsUids.mapNotNull(threadController::getThread)

    fun getActionFolderRole(thread: Thread?): FolderRole? {
        return getActionFolderRole(thread?.let(::listOf))
    }

    fun getActionFolderRole(message: Message): FolderRole? {
        return getActionFolderRole(message.threads, message)
    }

    private fun getActionFolderRole(threads: List<Thread>?, message: Message? = null): FolderRole? {
        return when {
            message != null -> {
                message.folder.role
            }
            threads?.firstOrNull()?.folder?.id == FolderController.SEARCH_FOLDER_ID -> {
                folderController.getFolder(threads.first().folderId)?.role
            }
            else -> {
                threads?.firstOrNull()?.folder?.role
            }
        }
    }

    fun addContact(recipient: Recipient) = viewModelScope.launch(ioCoroutineContext) {

        with(ApiRepository.addContact(addressBookController.getDefaultAddressBook().id, recipient)) {

            val snackbarTitle = if (isSuccess()) {
                updateUserInfo()
                R.string.snackbarContactSaved
            } else {
                translatedError
            }

            snackBarManager.postValue(context.getString(snackbarTitle))
        }
    }

    fun getMessage(messageUid: String) = liveData(ioCoroutineContext) {
        emit(messageController.getMessage(messageUid)!!)
    }

    fun navigateToSelectedDraft(message: Message) = liveData(ioCoroutineContext) {
        val localUuid = draftController.getDraftByMessageUid(message.uid)?.localUuid
        emit(ThreadListViewModel.SelectedDraft(localUuid, message.draftResource, message.uid))
    }

    fun selectOrUnselectAll() {
        if (isEverythingSelected) {
            context.trackMultiSelectionEvent("none")
            selectedThreads.clear()
        } else {
            context.trackMultiSelectionEvent("all")
            currentThreadsLive.value?.list?.forEach { thread -> selectedThreads.add(thread) }
        }

        publishSelectedItems()
    }

    fun publishSelectedItems() {
        selectedThreadsLiveData.value = selectedThreads
    }

    fun refreshDraftFolderWhenDraftArrives(scheduledDate: Long) = viewModelScope.launch(ioCoroutineContext) {
        val folder = folderController.getFolder(FolderRole.DRAFT)

        if (folder?.cursor != null) {

            val timeNow = Date().time
            val delay = REFRESH_DELAY + max(scheduledDate - timeNow, 0L)
            delay(min(delay, MAX_REFRESH_DELAY))

            refreshController.refreshThreads(
                refreshMode = RefreshMode.REFRESH_FOLDER_WITH_ROLE,
                mailbox = currentMailbox.value!!,
                folder = folder,
                realm = mailboxContentRealm(),
                started = ::startedDownload,
                stopped = ::stoppedDownload,
            )
        }
    }

    private companion object {
        val TAG: String = MainViewModel::class.java.simpleName

        val DEFAULT_SELECTED_FOLDER = FolderRole.INBOX

        // We add this delay because it doesn't always work if we just use the `etop`.
        const val REFRESH_DELAY = 2_000L
        const val MAX_REFRESH_DELAY = 6_000L
    }
}
