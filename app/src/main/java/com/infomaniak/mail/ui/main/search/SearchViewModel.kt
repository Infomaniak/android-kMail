/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.core.legacy.utils.SingleLiveEvent
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.MatomoMail.trackSearchEvent
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.ThreadMode
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.cache.userInfo.MergedContactController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.Companion.DUMMY_FOLDER_ID
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.search.SearchFragment.VisibilityMode
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.SearchUtils
import com.infomaniak.mail.utils.coroutineContext
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import javax.inject.Inject


@HiltViewModel
class SearchViewModel @Inject constructor(
    application: Application,
    private val globalCoroutineScope: CoroutineScope,
    private val mailboxController: MailboxController,
    private val messageController: MessageController,
    private val mergedContactController: MergedContactController,
    private val refreshController: RefreshController,
    private val savedStateHandle: SavedStateHandle,
    private val searchUtils: SearchUtils,
    private val threadController: ThreadController,
    private val localSettings: LocalSettings,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    /** Needed to pass API request's validation, but won't be used by the API */
    private val dummyFolderId
        inline get() = savedStateHandle.get<String>(SearchFragmentArgs::dummyFolderId.name) ?: DUMMY_FOLDER_ID

    var filterFolder: Folder? = null
        private set
    var currentSearchQuery: String = ""
        private set

    val uiState = MutableLiveData<SearchUiState>(SearchUiState.IDLE)

    val contactsResults = MutableLiveData<List<MergedContact>>()
    private var currentFilters = mutableSetOf<ThreadFilter>()
    var isAllFoldersSelected: Boolean = false

    private var lastExecutedFolder: Folder? = null
    private var lastExecutedSearchQuery: String = ""
    private var lastExecutedFilters = mutableSetOf<ThreadFilter>()

    val visibilityMode = MutableLiveData(VisibilityMode.RECENT_SEARCHES)
    val history = SingleLiveEvent<String>()

    private var resourceNext: String? = null
    private var isFirstPage: Boolean = true
    private val isLastPage get() = resourceNext.isNullOrBlank()

    private var searchJob: Job? = null
    val searchResults = threadController.getSearchThreadsAsync().asLiveData(ioCoroutineContext)

    private val currentMailboxFlow = mailboxController.getMailboxAsync(
        AccountUtils.currentUserId,
        AccountUtils.currentMailboxId,
    ).mapNotNull { it.obj }

    private val featureFlagsFlow = currentMailboxFlow.map { it.featureFlags }

    fun cancelSearch() {
        searchJob?.cancel()
    }

    fun executePendingSearch() = viewModelScope.launch(ioCoroutineContext) {
        val hasPendingSearch = (lastExecutedSearchQuery != currentSearchQuery)
                || (lastExecutedFolder != filterFolder)
                || (lastExecutedFilters != currentFilters)

        if (hasPendingSearch) search()
    }

    private fun shouldShowContacts(): Boolean {
        val state = uiState.value
        val hasQuery = currentSearchQuery.isNotBlank()
        val hasNoFilters = currentFilters.isEmpty()
        val notValidated = state != SearchUiState.VALIDATED

        return state == SearchUiState.TYPING && hasQuery && hasNoFilters && notValidated
    }

    fun resetFolderFilter() {
        filterFolder = null
        isAllFoldersSelected = false
        unselectAllChipFilters()
    }

    fun refreshSearch() = viewModelScope.launch(ioCoroutineContext) {
        search()
    }

    fun searchQuery(query: String, saveInHistory: Boolean = false) = viewModelScope.launch(ioCoroutineContext) {
        if (query.isNotBlank() && isLengthTooShort(query)) return@launch
        if (saveInHistory) {
            uiState.postValue(SearchUiState.VALIDATED)
            contactsResults.postValue(emptyList())
        } else {
            uiState.postValue(SearchUiState.TYPING)
        }
        search(query.trim().also { currentSearchQuery = it }, saveInHistory)
    }

    fun selectAllFoldersFilter(isSelected: Boolean) {
        isAllFoldersSelected = isSelected
    }

    fun selectFolder(folder: Folder?) {
        filterFolder = folder
        viewModelScope.launch(ioCoroutineContext) {
            search(folder = folder)
        }
    }

    fun setFilter(filter: ThreadFilter, isEnabled: Boolean = true) = viewModelScope.launch(ioCoroutineContext) {
        if (isEnabled && currentFilters.contains(filter)) return@launch

        uiState.postValue(SearchUiState.FILTERING)
        contactsResults.postValue(emptyList())

        if (isEnabled) {
            trackSearchEvent(filter.matomoName)
            filter.select()
        } else {
            filter.unselect()
            if (currentFilters.isEmpty()) {
                val newState = if (currentSearchQuery.isNotBlank()) SearchUiState.TYPING else SearchUiState.IDLE
                uiState.postValue(newState)
            }
        }
    }

    fun unselectMutuallyExclusiveFilters() = viewModelScope.launch(ioCoroutineContext) {
        currentFilters.removeAll(setOf(ThreadFilter.SEEN, ThreadFilter.UNSEEN, ThreadFilter.STARRED))
        val newState = if (currentFilters.isEmpty() && currentSearchQuery.isNotBlank()) {
            SearchUiState.TYPING
        } else if (currentFilters.isEmpty()) {
            SearchUiState.IDLE
        } else {
            SearchUiState.FILTERING
        }
        uiState.postValue(newState)
        search(filters = currentFilters)
    }

    fun unselectAllChipFilters() {
        currentFilters.removeAll(ThreadFilter.entries)
    }

    fun nextPage() = viewModelScope.launch(ioCoroutineContext) {
        if (isLastPage) return@launch
        search(shouldGetNextPage = true)
    }

    private suspend fun ThreadFilter.select() {
        search(filters = searchUtils.selectFilter(filter = this, currentFilters).also { currentFilters = it })
    }

    private suspend fun ThreadFilter.unselect() {
        search(filters = currentFilters.apply { remove(this@unselect) }.also { currentFilters = it })
    }

    private fun resetPaginationData() {
        resourceNext = null
        isFirstPage = true
    }

    override fun onCleared() {
        contactsResults.value = emptyList()
        cancelSearch()
        globalCoroutineScope.launch(ioDispatcher) {
            searchUtils.deleteRealmSearchData()
            SentryLog.i(TAG, "SearchViewModel>onCleared: called")
        }
        super.onCleared()
    }

    fun isLengthTooShort(query: String) = query.length < MIN_SEARCH_QUERY

    private suspend fun search(
        query: String = currentSearchQuery,
        saveInHistory: Boolean = false,
        filters: Set<ThreadFilter> = currentFilters,
        folder: Folder? = filterFolder,
        shouldGetNextPage: Boolean = false,
    ) = withContext(ioCoroutineContext) {
        cancelSearch()

        searchJob = launch {
            delay(SEARCH_DEBOUNCE_DURATION)
            ensureActive()

            val showContacts = shouldShowContacts() &&
                    query.isNotBlank() &&
                    !query.contains("\"") &&
                    !isLengthTooShort(query)

            val contacts = if (showContacts) {
                val queryClean = Normalizer.normalize(query, Normalizer.Form.NFD)
                    .replace("\\p{M}".toRegex(), "")
                val contactsList = mergedContactController.searchMergedContacts(queryClean)

                contactsResults.postValue(contactsList)
                contactsList
            } else {
                contactsResults.postValue(emptyList())
                emptyList()
            }


            mailboxController
                .getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)
                ?.objectId
                ?.let(refreshController::cancelRefresh)

            if (!shouldGetNextPage) resetPaginationData()

            computeSearchFilters(folder, filters, query)?.let { newFilters ->
                fetchThreads(folder, newFilters, query, shouldGetNextPage, contacts.isNotEmpty())
                if (saveInHistory) query.let(history::postValue)
            }
        }
    }

    private suspend fun computeSearchFilters(
        folder: Folder?,
        filters: Set<ThreadFilter>,
        query: String?,
    ): Set<ThreadFilter>? {

        val newFilters = if (folder == null) filters else (filters + ThreadFilter.FOLDER)

        return if (newFilters.isEmpty() && query.isNullOrBlank() && filterFolder == null) {
            searchUtils.deleteRealmSearchData()
            visibilityMode.postValue(VisibilityMode.RECENT_SEARCHES)
            null
        } else {
            newFilters
        }
    }

    private suspend fun fetchThreads(
        folder: Folder?,
        newFilters: Set<ThreadFilter>,
        query: String,
        shouldGetNextPage: Boolean,
        hasContacts: Boolean = false,
    ) {
        visibilityMode.postValue(VisibilityMode.LOADING)

        val currentMailbox = mailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)!!
        val folderId = folder?.id ?: dummyFolderId
        val resource = if (shouldGetNextPage) resourceNext else null
        val searchFilters = searchUtils.searchFilters(query, newFilters, resource)
        val apiResponse = ApiRepository.searchThreads(
            mailboxUuid = currentMailbox.uuid,
            folderId = folderId,
            filters = searchFilters,
            hasDisplayModeThread = localSettings.threadMode == ThreadMode.CONVERSATION,
            resource = resource
        )

        currentCoroutineContext().ensureActive()

        if (isFirstPage && isLastPage) searchUtils.deleteRealmSearchData()

        if (apiResponse.isSuccess()) {
            with(apiResponse) {
                data?.let { createSearchThreadsFromRemote(it.threads, folder) }
                resourceNext = data?.resourceNext
                isFirstPage = data?.resourcePrevious == null
            }
        } else if (isLastPage) {
            createSearchThreadsFromLocal(query, newFilters, folderId)
        }

        if (folder != lastExecutedFolder) lastExecutedFolder = folder
        if (newFilters != lastExecutedFilters) lastExecutedFilters = newFilters.toMutableSet()
        if (query != lastExecutedSearchQuery) lastExecutedSearchQuery = query

        val resultsVisibilityMode = when {
            newFilters.isEmpty() && isLengthTooShort(query) -> VisibilityMode.RECENT_SEARCHES
            threadController.getSearchThreadsCount() == 0L && !hasContacts -> VisibilityMode.NO_RESULTS
            else -> VisibilityMode.RESULTS
        }

        visibilityMode.postValue(resultsVisibilityMode)
    }

    private suspend fun createSearchThreadsFromRemote(remoteThreads: List<Thread>, folder: Folder?) {
        runCatching {
            val searchThreads = searchUtils.convertRemoteThreadsToSearchThreads(remoteThreads, folder)
            threadController.saveSearchThreads(searchThreads)
        }.getOrElse { exception ->
            exception.printStackTrace()
            Sentry.captureException(exception)
        }
    }

    private suspend fun createSearchThreadsFromLocal(
        query: String,
        newFilters: Set<ThreadFilter>,
        folderId: String,
    ) {
        val searchMessages = messageController.searchMessages(
            searchQuery = query,
            filters = newFilters,
            folderId = folderId,
            featureFlags = featureFlagsFlow.first(),
            localSettings = localSettings
        )
        val searchThreads = searchUtils.convertLocalMessagesToSearchThreads(searchMessages)
        threadController.saveSearchThreads(searchThreads)
    }

    enum class SearchUiState {
        IDLE,
        TYPING,
        FILTERING,
        VALIDATED
    }

    companion object {
        private val TAG: String = SearchViewModel::class.java.simpleName
        private const val MIN_SEARCH_QUERY = 2 // The minimum value allowed for a search query
        private const val SEARCH_DEBOUNCE_DURATION = 500L
    }
}
