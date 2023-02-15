/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.models.message.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ThreadListViewModel : ViewModel() {

    private val coroutineContext = viewModelScope.coroutineContext + Dispatchers.IO
    private var updatedAtJob: Job? = null

    val isRecoveringFinished = MutableLiveData(true)
    val updatedAtTrigger = MutableLiveData<Unit>()

    var currentFolderCursor: String? = null
    var currentThreadsCount: Int? = null

    fun startUpdatedAtJob() {
        updatedAtJob?.cancel()
        updatedAtJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(DateUtils.MINUTE_IN_MILLIS)
                updatedAtTrigger.postValue(Unit)
            }
        }
    }

    fun navigateToSelectedDraft(message: Message) = liveData(coroutineContext) {
        val localUuid = DraftController.getDraftByMessageUid(message.uid)?.localUuid
        emit(SelectedDraft(localUuid, message.draftResource, message.uid))
    }

    data class SelectedDraft(
        val draftLocalUuid: String?,
        val draftResource: String?,
        val messageUid: String?,
    )
}
