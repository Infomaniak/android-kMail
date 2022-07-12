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
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.models.thread.Thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class ThreadListViewModel : ViewModel() {

    private var listenToThreadsJob: Job? = null

    private val mutableUiThreadsFlow: MutableStateFlow<List<Thread>?> = MutableStateFlow(null)
    val uiThreadsFlow = mutableUiThreadsFlow.asStateFlow()

    fun setup() {
        listenToThreads()
    }

    private fun listenToThreads() {
        if (listenToThreadsJob != null) listenToThreadsJob?.cancel()

        listenToThreadsJob = CoroutineScope(Dispatchers.IO).launch {
            MailData.threadsFlow.filterNotNull().collect { threads ->
                mutableUiThreadsFlow.value = threads
            }
        }
    }

    fun loadMailData() {
        MailData.loadMailData()
    }

    fun refreshThreads() {
        val folder = MailData.currentFolderFlow.value ?: return
        val mailbox = MailData.currentMailboxFlow.value ?: return
        MailData.fetchThreads(folder, mailbox)
    }

    override fun onCleared() {
        listenToThreadsJob?.cancel()
        listenToThreadsJob = null

        super.onCleared()
    }
}
