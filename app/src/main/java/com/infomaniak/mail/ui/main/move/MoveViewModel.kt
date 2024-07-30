/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.move

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.coroutineContext
import com.infomaniak.mail.utils.extensions.appContext
import com.infomaniak.mail.utils.extensions.flattenFolderChildren
import com.infomaniak.mail.utils.extensions.standardize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoveViewModel @Inject constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val folderController: FolderController,
    private val messageController: MessageController,
    private val threadController: ThreadController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    private var searchJob: Job? = null

    private val messageUid inline get() = savedStateHandle.get<String?>(MoveFragmentArgs::messageUid.name)
    private val threadsUids inline get() = savedStateHandle.get<Array<String>>(MoveFragmentArgs::threadsUids.name)!!

    private var allFolders = emptyList<Any>()
    val sourceFolderIdLiveData = MutableLiveData<String>()
    val filterResults = MutableLiveData<List<Any>>()
    var hasAlreadyTrackedSearch = false

    init {
        viewModelScope.launch(ioCoroutineContext) {

            fun List<Folder>.addDividerToFirstCustomFolder(): List<Any> {
                val folders = this
                val items = mutableListOf<Any>()
                var needsToAddDivider = true
                folders.forEach { folder ->
                    if (needsToAddDivider && folder.isRootAndCustom) {
                        needsToAddDivider = false
                        items.add(Unit)
                    }
                    items.add(folder)
                }
                return items
            }

            val sourceFolderId = messageUid?.let(messageController::getMessage)?.folderId
                ?: threadController.getThread(threadsUids.first())!!.folderId

            sourceFolderIdLiveData.postValue(sourceFolderId)

            allFolders = folderController.getMoveFolders()
                .flattenFolderChildren()
                .addDividerToFirstCustomFolder()
                .also(filterResults::postValue)
        }
    }

    fun filterFolders(query: CharSequence?, shouldDebounce: Boolean) {
        if (query?.isNotBlank() == true) {
            searchFolders(query, shouldDebounce)
        } else {
            filterResults.value = allFolders
        }
    }

    private fun searchFolders(query: CharSequence, shouldDebounce: Boolean) = viewModelScope.launch(ioCoroutineContext) {
        searchJob?.cancel()
        searchJob = launch {

            if (shouldDebounce) {
                delay(FILTER_DEBOUNCE_DURATION)
                ensureActive()
            }

            val filteredFolders = mutableListOf<Any>().apply {
                allFolders.forEach { folder ->
                    if (folder !is Folder) return@forEach
                    val folderName = folder.role?.folderNameRes?.let(appContext::getString) ?: folder.name
                    val isFound = folderName.standardize().contains(query.standardize())
                    if (isFound) add(folder)
                }
            }

            filterResults.postValue(filteredFolders)
        }
    }

    fun cancelSearch() {
        searchJob?.cancel()
    }

    override fun onCleared() {
        cancelSearch()
        super.onCleared()
    }

    companion object {
        private const val FILTER_DEBOUNCE_DURATION = 300L
    }
}
