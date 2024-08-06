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
import androidx.lifecycle.liveData
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

    private var filterJob: Job? = null

    private val messageUid inline get() = savedStateHandle.get<String?>(MoveFragmentArgs::messageUid.name)
    private val threadsUids inline get() = savedStateHandle.get<Array<String>>(MoveFragmentArgs::threadsUids.name)!!

    var filterResults: MutableLiveData<Pair<List<Folder>, String>> = MutableLiveData()

    fun getCurrentFolderAndAllFolders() = liveData(ioCoroutineContext) {

        val currentFolderId = messageUid?.let(messageController::getMessage)?.folderId
            ?: threadController.getThread(threadsUids.first())!!.folderId

        var isFirstRootAndCustom = true
        val folders = folderController.getMoveFolders().flattenFolderChildren().map { folder ->
            folder.apply {
                if (isRootAndCustom && isFirstRootAndCustom) {
                    isFirstRootAndCustom = false
                    shouldDisplayDivider = true
                }
            }
        }

        emit(folders to currentFolderId)
    }

    fun filterFolders(
        query: String,
        folders: List<Folder>,
        currentFolderId: String,
        shouldDebounce: Boolean,
    ) = viewModelScope.launch(ioCoroutineContext) {
        filterJob?.cancel()
        filterJob = launch {
            if (shouldDebounce) {
                delay(FILTER_DEBOUNCE_DURATION)
                ensureActive()
            }

            var isFirstRootAndCustom = true
            val filteredFolders = folders.mapNotNull { folder ->
                val folderName = folder.role?.folderNameRes?.let(appContext::getString) ?: folder.name
                val isFound = folderName.standardize().contains(query.standardize())
                if (isFound) {
                    folder.clone().apply {
                        shouldDisplayDivider = if (isRootAndCustom && isFirstRootAndCustom) {
                            isFirstRootAndCustom = false
                            true
                        } else {
                            false
                        }
                        shouldDisplayIndent = false
                    }
                } else {
                    null
                }
            }

            filterResults.postValue(filteredFolders to currentFolderId)
        }
    }

    fun cancelSearch() {
        filterJob?.cancel()
    }

    override fun onCleared() {
        cancelSearch()
        super.onCleared()
    }

    companion object {
        private const val FILTER_DEBOUNCE_DURATION = 300L
    }
}
