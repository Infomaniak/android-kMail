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
package com.infomaniak.mail.ui.main.menu

import android.app.Application
import androidx.lifecycle.*
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.coroutineContext
import com.infomaniak.mail.utils.getCustomMenuFolders
import com.infomaniak.mail.utils.standardize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
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

    var filterResults: MutableLiveData<List<Folder>> = MutableLiveData()

    fun cancelSearch() {
        filterJob?.cancel()
    }

    fun getFolderIdAndCustomFolders() = liveData(ioCoroutineContext) {

        val folderId = messageUid?.let { messageController.getMessage(it)!!.folderId }
            ?: threadController.getThread(threadsUids.first())!!.folderId

        val customFolders = folderController.getCustomFolders().getCustomMenuFolders()

        emit(folderId to customFolders)
    }

    fun filterFolders(query: String, folders: List<Folder>, shouldDebounce: Boolean) = viewModelScope.launch(ioCoroutineContext) {
        filterJob?.cancel()
        filterJob = launch {
            if (shouldDebounce) {
                delay(FILTER_DEBOUNCE_DURATION)
                ensureActive()
            }
            val filteredFolders = folders.filter { folder ->
                val folderName = folder.role?.folderNameRes?.let(context::getString) ?: folder.name
                folderName.standardize().contains(query.standardize())
            }

            filterResults.postValue(filteredFolders)
        }
    }

    override fun onCleared() {
        cancelSearch()
        super.onCleared()
    }

    companion object {
        private const val FILTER_DEBOUNCE_DURATION = 300L
    }
}
