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
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Thread
import com.infomaniak.mail.data.models.Thread.ThreadFilter
import com.infomaniak.mail.utils.AccountUtils
import kotlinx.coroutines.Dispatchers

class SearchViewModel : ViewModel() {

    private val searchFolder: Folder? = null
    private val lastSearch: String = ""

    private val searchQuery = MutableLiveData<String>()
    val selectedFilters = MutableLiveData(mutableSetOf<ThreadFilter>())

    private lateinit var currentFolderId: String
    private var selectedFolder: Folder? = null
    private var resourceNext: String? = null

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
        resourceNext = ""
        searchQuery(searchQuery.value ?: "")
    }

    fun searchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectFolder(folder: Folder?) {
        if (selectedFilters.value?.contains(ThreadFilter.FOLDER) == false) {
            selectedFilters.value = selectedFilters.value?.apply { add(ThreadFilter.FOLDER) }
        }
        selectedFolder = folder
    }

    fun toggleFilter(filter: ThreadFilter) {
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

    private fun fetchThreads(query: String?, filters: Set<ThreadFilter>) = liveData<List<Thread>>(Dispatchers.IO) {
        val currentMailbox = MailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)!!
        val folderId = selectedFolder?.id ?: currentFolderId
        val apiResponse = ApiRepository.searchThreads(currentMailbox.uuid, folderId, searchFilters(query, filters), resourceNext)

        resourceNext = apiResponse.data?.resourceNext ?: ""
        emit(apiResponse.data?.threads ?: emptyList())
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

}