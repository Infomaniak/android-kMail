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
package com.infomaniak.mail.ui.main.menu

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.coroutineContext
import com.infomaniak.mail.utils.standardize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoveViewModel @Inject constructor(
    application: Application,
    private val messageController: MessageController,
    private val threadController: ThreadController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private inline val context: Context get() = getApplication()

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    private var filterJob: Job? = null

    var filterResults: MutableLiveData<List<Folder>> = MutableLiveData()

    fun cancelSearch() {
        filterJob?.cancel()
    }

    fun getFolderIdByMessage(messageUid: String) = liveData(ioCoroutineContext) {
        emit(messageController.getMessage(messageUid)!!.folderId)
    }

    fun getFolderIdByThread(threadUid: String) = liveData(ioCoroutineContext) {
        emit(threadController.getThread(threadUid)!!.folderId)
    }

    fun filterFolders(query: String, folders: List<Folder>) = viewModelScope.launch(ioCoroutineContext) {
        filterJob?.cancel()
        filterJob = launch {
            delay(FILTER_DEBOUNCE_DURATION)
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

    private companion object {
        const val FILTER_DEBOUNCE_DURATION = 200L
    }
}
