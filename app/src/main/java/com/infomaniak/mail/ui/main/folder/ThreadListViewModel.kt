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
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.ui.MainViewModel
import io.realm.kotlin.ext.isValid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

class ThreadListViewModel : ViewModel() {

    private var updatedAtJob: Job? = null

    val isRecoveringFinished = MutableLiveData(true)
    val updatedAtTrigger = MutableLiveData<Unit>()

    val currentFolder = Transformations.switchMap(MainViewModel.currentFolderId) { folderId ->
        liveData(Dispatchers.IO) {
            if (folderId != null) emitSource(FolderController.getFolderAsync(folderId).mapNotNull { it.obj }.asLiveData())
        }
    }

    val currentFilter = SingleLiveEvent(ThreadFilter.ALL)

    val currentThreads = Transformations.switchMap(observeFolderAndFilter()) { (folder, filter) ->
        liveData(Dispatchers.IO) {
            if (folder != null && folder.isValid()) {
                emitSource(ThreadController.getThreads(folder.id, filter).asFlow().asLiveData())
            }
        }
    }

    private fun observeFolderAndFilter() = MediatorLiveData<Pair<Folder?, ThreadFilter>>().apply {
        value = currentFolder.value to currentFilter.value!!
        addSource(currentFolder) { value = it to value!!.second }
        addSource(currentFilter) { value = value?.first to it }
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

    fun toggleSeenStatus(thread: Thread) = viewModelScope.launch(Dispatchers.IO) {
        ThreadController.toggleSeenStatus(thread)
    }

    fun navigateToSelectedDraft(message: Message) = liveData(Dispatchers.IO) {
        val localUuid = DraftController.getDraftByMessageUid(message.uid)?.localUuid
        emit(SelectedDraft(localUuid, message.draftResource, message.uid))
    }

    data class SelectedDraft(
        val draftLocalUuid: String?,
        val draftResource: String?,
        val messageUid: String?,
    )
}
