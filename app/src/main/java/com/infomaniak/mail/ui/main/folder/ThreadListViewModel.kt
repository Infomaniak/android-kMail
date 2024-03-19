/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.SearchUtils
import com.infomaniak.mail.utils.coroutineContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import javax.inject.Inject

@HiltViewModel
class ThreadListViewModel @Inject constructor(
    private val searchUtils: SearchUtils,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)
    private var updatedAtJob: Job? = null

    val isRecoveringFinished = MutableLiveData(true)
    val updatedAtTrigger = MutableLiveData<Unit>()

    var currentFolderCursor: String? = null
    var currentThreadsCount: Int? = null

    fun startUpdatedAtJob() {
        updatedAtJob?.cancel()
        updatedAtJob = viewModelScope.launch(ioCoroutineContext) {
            while (true) {
                delay(DateUtils.MINUTE_IN_MILLIS)
                ensureActive()
                updatedAtTrigger.postValue(Unit)
            }
        }
    }

    fun deleteSearchData() = viewModelScope.launch(ioCoroutineContext) {
        // Delete Search data in case they couldn't be deleted at the end of the previous Search.
        searchUtils.deleteRealmSearchData()
    }
}
