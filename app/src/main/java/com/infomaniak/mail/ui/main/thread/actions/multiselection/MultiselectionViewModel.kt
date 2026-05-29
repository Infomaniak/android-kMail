/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread.actions.multiselection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackMultiSelectionEvent
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.notifications.ResultsChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class MultiselectionViewModel @Inject constructor(
    application: Application,
) : AndroidViewModel(application) {
    private val _selectedThreads = mutableSetOf<Thread>()
    val isMultiSelectOnLiveData = MutableLiveData(false)
    var isMultiSelectOn: Boolean
        get() = isMultiSelectOnLiveData.value ?: false
        set(value) {
            isMultiSelectOnLiveData.value = value
        }
    val selectedThreadsLiveData = MutableLiveData(_selectedThreads)
    val selectedThreads: MutableSet<Thread>
        get() = _selectedThreads

    val selectedMessages: List<Message>
        get() = selectedThreads.flatMap { thread -> thread.messages }

    fun isEverythingSelected(currentThreadCount: Int): Boolean {
        return selectedThreads.count() == currentThreadCount
    }

    fun selectOrUnselectAll(currentThreadsLive: MutableLiveData<ResultsChange<Thread>>) {
        val isEverythingSelected = isEverythingSelected(currentThreadsLive.value?.list?.count() ?: 0)
        if (isEverythingSelected) {
            trackMultiSelectionEvent(MatomoName.None)
            selectedThreads.clear()
        } else {
            trackMultiSelectionEvent(MatomoName.All)
            currentThreadsLive.value?.list?.forEach { thread -> selectedThreads.add(thread) }
        }

        publishSelectedItems()
    }

    suspend fun selectOrUnselectAllSearchItems(searchResults: Flow<ResultsChange<Thread>>) {
        val results = searchResults.first().list
        val isEverythingSelected = isEverythingSelected(results.count())
        if (isEverythingSelected) {
            trackMultiSelectionEvent(MatomoName.None)
            selectedThreads.clear()
        } else {
            trackMultiSelectionEvent(MatomoName.All)
            results.forEach { thread -> selectedThreads.add(thread) }
        }

        publishSelectedItems()
    }

    fun publishSelectedItems() {
        selectedThreadsLiveData.value = _selectedThreads
    }
}
