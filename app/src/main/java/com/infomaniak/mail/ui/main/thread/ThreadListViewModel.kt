/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.ui.main.MainViewModel
import com.infomaniak.mail.utils.toSharedFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class ThreadListViewModel : ViewModel() {

    private var listenToThreadsJob: Job? = null

    val currentFolder = SingleLiveEvent<Folder?>()
    val threads = SingleLiveEvent<List<Thread>?>()

    fun listenToCurrentFolder() = viewModelScope.launch {
        MainViewModel.currentFolderFlow.collect {
            currentFolder.value = it
        }
    }

    fun listenToThreads() = viewModelScope.launch {
        MainViewModel.currentFolderFlow.filterNotNull().collect { folder ->
            listenToThreadsJob?.cancel()
            listenToThreadsJob = viewModelScope.launch {
                FolderController.getFolderSync(folder.id)?.threads?.asFlow()?.toSharedFlow()?.collect {
                    threads.value = it.list
                }
            }
        }
    }
}
