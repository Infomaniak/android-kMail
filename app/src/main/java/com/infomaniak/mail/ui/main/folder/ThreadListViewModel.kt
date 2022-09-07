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
package com.infomaniak.mail.ui.main.folder

import android.text.format.DateUtils
import androidx.lifecycle.*
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.utils.toSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

class ThreadListViewModel : ViewModel() {

    private var updatedAtJob: Job? = null

    val isRecovering = MutableLiveData(false)
    val updatedAtTrigger = MutableLiveData<Unit>()

    val currentFolder = MutableLiveData<Folder>()
    val currentFolderThreads = Transformations.switchMap(currentFolder) { folder ->
        liveData(Dispatchers.IO) { emitSource(folder.threads.asFlow().toSharedFlow().map { it.list }.asLiveData()) }
    }

    fun listenToFolder(folderId: String): LiveData<Folder> = liveData(Dispatchers.IO) {
        emitSource(
            FolderController.getFolderAsync(folderId)
                .mapNotNull { it.obj }
                .asLiveData()
        )
    }

    fun startUpdatedAtJob() {
        updatedAtJob?.cancel()
        updatedAtJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(DateUtils.MINUTE_IN_MILLIS)
                updatedAtTrigger.postValue(Unit)
            }
        }
    }
}
