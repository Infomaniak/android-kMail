/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
import androidx.lifecycle.*
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.MatomoMail.trackSearchEvent
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.data.models.thread.ThreadResult
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.search.SearchFragment.VisibilityMode
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.SearchUtils
import com.infomaniak.mail.utils.coroutineContext
import com.infomaniak.mail.utils.extensions.context
import com.infomaniak.mail.utils.extensions.getMenuFolders
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sentry.Sentry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    application: Application,
    folderController: FolderController,
    private val globalCoroutineScope: CoroutineScope,
    private val mailboxController: MailboxController,
    private val messageController: MessageController,
    private val refreshController: RefreshController,
    private val savedStateHandle: SavedStateHandle,
    private val searchUtils: SearchUtils,
    private val threadController: ThreadController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    /** Needed to pass API request's validation, but won't be used by the API */
    private val dummyFolderId inline get() = savedStateHandle.get<String>(SearchFragmentArgs::dummyFolderId.name)!!

    var filterFolder: Folder? = null
        private set
    var currentSearchQuery: String = ""
        private set

    val foldersLive = folderController.getRootsFoldersAsync()
        .map { it.list.getMenuFolders() }
        .asLiveData(ioCoroutineContext)

    private var currentFilters = mutableSetOf<ThreadFilter>()

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

    fun cancelSearch() {
        searchJob?.cancel()
    }

    fun executePendingSearch() = viewModelScope.launch(ioCoroutineContext) {
        val hasPendingSearch = (lastExecutedSearchQuery != currentSearchQuery)
                || (lastExecutedFolder != filterFolder)
                || (lastExecutedFilters != currentFilters)

        if (hasPendingSearch) search()
    }

    fun refreshSearch() = viewModelScope.launch(ioCoroutineContext) {
        search()
    }

    fun searchQuery(query: String, saveInHistory: Boolean = false) = viewModelScope.launch(ioCoroutineContext) {
        if (query.isNotBlank() && isLengthTooShort(query)) return@launch
        search(query.trim().also { currentSearchQuery = it }, saveInHistory)
    }

    fun selectFolder(folder: Folder?) = viewModelScope.launch(ioCoroutineContext) {
        search(folder = folder.also { filterFolder = it })
    }

    fun setFilter(filter: ThreadFilter, isEnabled: Boolean = true) = viewModelScope.launch(ioCoroutineContext) {
        if (isEnabled && currentFilters.contains(filter)) return@launch
        if (isEnabled) {
            context.trackSearchEvent(filter.matomoValue)
            filter.select()
        } else {
            filter.unselect()
        }
    }

    fun unselectMutuallyExclusiveFilters() = viewModelScope.launch(ioCoroutineContext) {
        currentFilters.apply {
            removeAll(listOf(ThreadFilter.SEEN, ThreadFilter.UNSEEN, ThreadFilter.STARRED))
        }
        search(filters = currentFilters)
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
        searchJob?.cancel()
        searchJob = launch {
            delay(SEARCH_DEBOUNCE_DURATION)
            ensureActive()

            refreshController.cancelRefresh()

            if (!shouldGetNextPage) resetPaginationData()

            computeSearchFilters(folder, filters, query)?.let { newFilters ->
                fetchThreads(folder, newFilters, query, shouldGetNextPage)
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
    ) {

        suspend fun ApiResponse<ThreadResult>.initSearchFolderThreads() {
            runCatching {
                data?.threads?.let {
                    threadController.initAndGetSearchFolderThreads(
                        remoteThreads = it,
                        folderRole = folder?.role
                    )
                }
            }.getOrElse { exception ->
                exception.printStackTrace()
                Sentry.captureException(exception)
            }
        }

        visibilityMode.postValue(VisibilityMode.LOADING)

        val currentMailbox = mailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)!!
        val folderId = folder?.id ?: dummyFolderId
        val resource = if (shouldGetNextPage) resourceNext else null
        val searchFilters = searchUtils.searchFilters(query, newFilters, resource)
        val apiResponse = ApiRepository.searchThreads(currentMailbox.uuid, folderId, searchFilters, resource)

        searchJob?.ensureActive()

        if (isFirstPage && isLastPage) searchUtils.deleteRealmSearchData()

        if (apiResponse.isSuccess()) with(apiResponse) {
            initSearchFolderThreads()
            resourceNext = data?.resourceNext
            isFirstPage = data?.resourcePrevious == null
        } else if (isLastPage) {
            threadController.saveThreads(searchMessages = messageController.searchMessages(query, newFilters, folderId))
        }

        if (folder != lastExecutedFolder) lastExecutedFolder = folder
        if (newFilters != lastExecutedFilters) lastExecutedFilters = newFilters.toMutableSet()
        if (query != lastExecutedSearchQuery) lastExecutedSearchQuery = query

        val resultsVisibilityMode = when {
            newFilters.isEmpty() && isLengthTooShort(query) -> VisibilityMode.RECENT_SEARCHES
            threadController.getSearchThreadsCount() == 0L -> VisibilityMode.NO_RESULTS
            else -> VisibilityMode.RESULTS
        }

        visibilityMode.postValue(resultsVisibilityMode)
    }

    companion object {
        private val TAG: String = SearchViewModel::class.java.simpleName
        private const val MIN_SEARCH_QUERY = 3 // The minimum value allowed for a search query
        private const val SEARCH_DEBOUNCE_DURATION = 500L
    }
}
