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

import android.content.Context
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

    private val mutableUiThreadsFlow = MutableStateFlow<List<Thread>?>(null)
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
        MailData.loadInboxContent()
    }

    fun refreshThreads() {
        MailData.refreshThreads(
            folder = MailData.currentFolderFlow.value ?: return,
            mailbox = MailData.currentMailboxFlow.value ?: return,
        )
    }

    override fun onCleared() {
        listenToThreadsJob?.cancel()
        listenToThreadsJob = null

        super.onCleared()
    }

    fun openFolder(folderName: String, context: Context) {
        var job: Job? = null
        job = CoroutineScope(Dispatchers.IO).launch {
            MailData.foldersFlow.filterNotNull().collect { folders ->
                MailData.currentMailboxFlow.value?.let { mailbox ->
                    folders.find { it.getLocalizedName(context) == folderName }?.let { folder ->
                        MailData.selectFolder(folder)
                        MailData.loadThreads(folder, mailbox)
                    }
                }
                job?.cancel()
            }
        }
    }
}
