/*
 * Infomaniak kMail - Android
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

import androidx.lifecycle.*
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Thread
import com.infomaniak.mail.data.models.Thread.ThreadFilter
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.AccountUtils
import io.realm.kotlin.ext.toRealmList
import kotlinx.coroutines.*

class SearchViewModel : ViewModel() {

    private val searchQuery = MutableLiveData<String>()
    val selectedFilters = MutableLiveData(mutableSetOf<ThreadFilter>())

    private lateinit var currentFolderId: String
    private var selectedFolder: Folder? = null
    private var resourceNext: String? = null

    private var fetchThreadsJob: Job? = null

    val searchResults = observeSearchAndFilters().switchMap { (query, filters) -> fetchThreads(query, filters) }
    val hasNextPage get() = !resourceNext.isNullOrBlank()

    private fun observeSearchAndFilters() = MediatorLiveData<Pair<String?, Set<ThreadFilter>>>().apply {
        value = searchQuery.value to selectedFilters.value!!
        addSource(searchQuery) { value = it to value!!.second }
        addSource(selectedFilters) { value = value?.first to it }
    }

    fun init(currentFolderId: String) {
        this.currentFolderId = currentFolderId
    }

    fun refreshSearch() {
        resourceNext = null
        searchQuery(searchQuery.value ?: "")
    }

    fun searchQuery(query: String) {
        resourceNext = null
        searchQuery.value = query
    }

    fun selectFolder(folder: Folder?) {
        resourceNext = null
        if (selectedFilters.value?.contains(ThreadFilter.FOLDER) == false) {
            selectedFilters.value = selectedFilters.value?.apply { add(ThreadFilter.FOLDER) }
        }
        selectedFolder = folder
    }

    fun toggleFilter(filter: ThreadFilter) {
        resourceNext = null
        if (selectedFilters.value?.contains(filter) == true) {
            selectedFilters.value = selectedFilters.value?.apply { remove(filter) }
        } else {
            selectFilter(filter)
        }
    }

    fun nextPage() {
        if (resourceNext.isNullOrBlank()) return
        searchQuery(searchQuery.value ?: "")
    }

    override fun onCleared() {
        viewModelScope.launch {
            fetchThreadsJob?.cancelAndJoin()
            deleteRealmSearchData()
        }
        super.onCleared()
    }

    private fun fetchThreads(query: String?, filters: Set<ThreadFilter>): LiveData<List<Thread>> {
        fetchThreadsJob?.cancel()
        fetchThreadsJob = Job()
        return liveData(Dispatchers.IO + fetchThreadsJob!!) {
            if (!hasNextPage) deleteRealmSearchData()

            val currentMailbox = MailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)!!
            val folderId = selectedFolder?.id ?: currentFolderId
            val searchFilters = searchFilters(query, filters)
            val apiResponse = ApiRepository.searchThreads(currentMailbox.uuid, folderId, searchFilters, resourceNext)

            if (apiResponse.isSuccess()) {
                val threads = apiResponse.data?.threads?.let(ThreadController::getThreadsWithLocalMessages)
                emit(threads ?: emptyList())
                resourceNext = apiResponse.data?.resourceNext ?: ""
            } else if (resourceNext.isNullOrBlank()) {
                val threads = MessageController.searchMessages(query, filters, selectedFolder?.id).convertToThreads()
                ThreadController.saveThreads(threads)
                emit(threads)
            }
        }
    }

    private fun searchFilters(query: String?, filters: Set<ThreadFilter>): String {
        val filtersQuery = StringBuilder("severywhere=${if (filters.contains(ThreadFilter.FOLDER)) "0" else "1"}")
        if (!query.isNullOrBlank()) filtersQuery.append("&scontains=$query")

        with(filters) {
            when {
                contains(ThreadFilter.ATTACHMENTS) -> filtersQuery.append("&sattachments=yes")
                contains(ThreadFilter.SEEN) -> filtersQuery.append("&filters=seen")
                contains(ThreadFilter.UNSEEN) -> filtersQuery.append("&filters=unseen")
                contains(ThreadFilter.STARRED) -> filtersQuery.append("&filters=starred")
                else -> Unit
            }
        }
        return filtersQuery.toString()
    }

    private fun selectFilter(filter: ThreadFilter) {
        val selectedFilters = selectedFilters.value
        val filters = when (filter) {
            ThreadFilter.SEEN -> {
                selectedFilters?.apply {
                    removeAll(arrayOf(ThreadFilter.UNSEEN, ThreadFilter.STARRED))
                }
            }
            ThreadFilter.UNSEEN -> {
                selectedFilters?.apply {
                    removeAll(arrayOf(ThreadFilter.SEEN, ThreadFilter.STARRED))
                }
            }
            ThreadFilter.STARRED -> {
                selectedFilters?.apply {
                    removeAll(arrayOf(ThreadFilter.SEEN, ThreadFilter.UNSEEN))
                }
            }
            else -> selectedFilters
        }

        this.selectedFilters.value = filters?.apply { add(filter) }
    }

    private fun List<Message>.convertToThreads(): List<Thread> {
        return this.map { message ->
            Thread().apply {
                this.uid = "search-${message.uid}"
                this.messages = listOf(message).toRealmList()
                this.unseenMessagesCount = 0
                this.from = message.from
                this.to = message.to
                this.date = message.date
                this.hasAttachments = message.hasAttachments
                this.isFavorite = message.isFavorite
                this.isAnswered = message.isAnswered
                this.isForwarded = message.isForwarded
                this.size = message.size
                this.subject = message.subject
            }
        }
    }

    private suspend fun deleteRealmSearchData() = withContext(Dispatchers.IO) {
        RealmDatabase.mailboxContent().writeBlocking {
            MessageController.deleteSearchMessages(this)
            ThreadController.deleteSearchThreads(this)
        }
    }

}