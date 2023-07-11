/*
 * Infomaniak ikMail - Android
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
import android.content.Context
import android.util.Log
import androidx.lifecycle.*
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.MatomoMail.trackSearchEvent
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.data.models.thread.ThreadResult
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.search.SearchFragment.VisibilityMode
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.SearchUtils
import com.infomaniak.mail.utils.coroutineContext
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.notifications.ResultsChange
import io.sentry.Sentry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    application: Application,
    private val globalCoroutineScope: CoroutineScope,
    private val searchUtils: SearchUtils,
    private val messageController: MessageController,
    private val threadController: ThreadController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private inline val context: Context get() = getApplication()

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    private val _searchQuery = MutableLiveData("" to false)
    val searchQuery: String get() = _searchQuery.value!!.first

    private val _selectedFilters = MutableStateFlow(emptySet<ThreadFilter>())
    private inline val selectedFilters get() = _selectedFilters.value.toMutableSet()

    private val _selectedFolder = MutableStateFlow<Folder?>(null)
    val selectedFolder: Folder? get() = _selectedFolder.value

    /** `_onPaginationTrigger` & `shouldPaginate` are closely related. Modifying one will impact the other. Beware. */
    private val _onPaginationTrigger = MutableStateFlow(0L)
    private var shouldPaginate: Boolean = false
    private val _onRefreshTrigger = MutableStateFlow(0L)

    val visibilityMode = MutableLiveData(VisibilityMode.RECENT_SEARCHES)
    val history = SingleLiveEvent<String>()

    /** It's simply used as a default value for the API. */
    private lateinit var dummyFolderId: String

    private var resourceNext: String? = null
    private var isFirstPage: Boolean = true
    private val isLastPage get() = resourceNext.isNullOrBlank()

    val searchResults: LiveData<ResultsChange<Thread>> = observeSearchAndFilters()
        .flatMapLatest { (queryData, filters, folder, shouldGetNextPage) ->
            val (query, saveInHistory) = queryData
            if (!shouldGetNextPage) resetPaginationData()
            searchThreads(
                query = if (isLengthTooShort(query)) null else query,
                saveInHistory,
                shouldGetNextPage,
                filters,
                folder,
            )
        }
        .asLiveData(ioCoroutineContext)

    private fun observeSearchAndFilters(): Flow<NewSearchInfo> {
        return combine(
            _searchQuery.asFlow(),
            _selectedFilters,
            _selectedFolder,
            _onPaginationTrigger,
            _onRefreshTrigger,
        ) { queryData, filters, folder, paginationTime, refreshTime ->
            NewSearchInfo(queryData, filters, folder, shouldPaginate, paginationTime, refreshTime)
        }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_DURATION)
    }

    fun init(dummyFolderId: String) {
        this.dummyFolderId = dummyFolderId
    }

    fun refreshSearch() {
        resetPaginationIntent()
        _onRefreshTrigger.value = System.currentTimeMillis()
    }

    fun searchQuery(query: String, saveInHistory: Boolean = false) {
        resetPaginationIntent()
        _searchQuery.value = query to saveInHistory
    }

    fun selectFolder(folder: Folder?) {
        resetPaginationIntent()
        _selectedFolder.value = folder
    }

    fun setFilter(filter: ThreadFilter, isEnabled: Boolean = true) {
        resetPaginationIntent()
        if (isEnabled) {
            context.trackSearchEvent(filter.matomoValue)
            filter.select()
        } else {
            filter.unselect()
        }
    }

    fun unselectMutuallyExclusiveFilters() {
        resetPaginationIntent()
        _selectedFilters.value = selectedFilters.apply {
            removeAll(listOf(ThreadFilter.SEEN, ThreadFilter.UNSEEN, ThreadFilter.STARRED))
        }
    }

    fun nextPage() {
        if (isLastPage) return
        shouldPaginate = true
        _onPaginationTrigger.value = System.currentTimeMillis()
    }

    private fun ThreadFilter.select() {
        _selectedFilters.value = searchUtils.selectFilter(filter = this, selectedFilters)
    }

    private fun ThreadFilter.unselect() {
        _selectedFilters.value = selectedFilters.apply { remove(this@unselect) }
    }

    private fun resetPaginationIntent() {
        shouldPaginate = false
    }

    private fun resetPaginationData() {
        resourceNext = null
        isFirstPage = true
    }

    override fun onCleared() {
        globalCoroutineScope.launch(ioDispatcher) {
            searchUtils.deleteRealmSearchData()
            Log.i(TAG, "SearchViewModel>onCleared: called")
        }
        super.onCleared()
    }

    fun isLengthTooShort(query: String?) = query == null || query.length < MIN_SEARCH_QUERY

    private fun searchThreads(
        query: String?,
        saveInHistory: Boolean,
        shouldGetNextPage: Boolean,
        filters: Set<ThreadFilter>,
        folder: Folder?,
    ): Flow<ResultsChange<Thread>> = flow {
        getReadyForNewSearch(folder, filters, query)?.let { newFilters ->
            fetchThreads(folder, newFilters, query, shouldGetNextPage)
            emitThreads(saveInHistory, newFilters, query)
        }
    }

    private suspend fun getReadyForNewSearch(folder: Folder?, filters: Set<ThreadFilter>, query: String?): Set<ThreadFilter>? {

        val newFilters = if (folder == null) filters else (filters + ThreadFilter.FOLDER)

        if (isFirstPage && isLastPage) searchUtils.deleteRealmSearchData()

        return if (newFilters.isEmpty() && query.isNullOrBlank()) {
            visibilityMode.postValue(VisibilityMode.RECENT_SEARCHES)
            null
        } else {
            newFilters
        }
    }

    private suspend fun fetchThreads(
        folder: Folder?,
        newFilters: Set<ThreadFilter>,
        query: String?,
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

        val currentMailbox = MailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)!!
        val folderId = folder?.id ?: dummyFolderId
        val resource = if (shouldGetNextPage) resourceNext else null
        val searchFilters = searchUtils.searchFilters(query, newFilters, resource)
        val apiResponse = ApiRepository.searchThreads(currentMailbox.uuid, folderId, searchFilters, resource)

        if (apiResponse.isSuccess()) with(apiResponse) {
            initSearchFolderThreads()
            resourceNext = data?.resourceNext
            isFirstPage = data?.resourcePrevious == null
        } else if (isLastPage) {
            threadController.saveThreads(searchMessages = messageController.searchMessages(query, newFilters, folderId))
        }
    }

    private suspend fun FlowCollector<ResultsChange<Thread>>.emitThreads(
        saveInHistory: Boolean,
        newFilters: Set<ThreadFilter>,
        query: String?,
    ) {
        emitAll(threadController.getSearchThreadsAsync().mapLatest {
            if (saveInHistory) query?.let(history::postValue)

            it.also {
                val resultsVisibilityMode = when {
                    newFilters.isEmpty() && isLengthTooShort(searchQuery) -> VisibilityMode.RECENT_SEARCHES
                    it.list.isEmpty() -> VisibilityMode.NO_RESULTS
                    else -> VisibilityMode.RESULTS
                }
                visibilityMode.postValue(resultsVisibilityMode)
            }
        })
    }

    private data class NewSearchInfo(
        val queryData: Pair<String, Boolean>,
        val filters: Set<ThreadFilter>,
        val folder: Folder?,
        val shouldGetNextPage: Boolean,
        private val paginationTime: Long,
        private val refreshTime: Long,
    )

    private companion object {

        val TAG = SearchViewModel::class.simpleName

        /**
         * The minimum value allowed for a search query
         */
        const val MIN_SEARCH_QUERY = 3

        const val SEARCH_DEBOUNCE_DURATION = 500L
    }
}
