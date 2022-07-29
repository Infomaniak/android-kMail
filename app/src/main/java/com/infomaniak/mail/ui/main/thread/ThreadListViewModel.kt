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
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.api.ApiRepository.OFFSET_FIRST_PAGE
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class ThreadListViewModel : ViewModel() {

    private var listenToCurrentMailboxJob: Job? = null
    private var listenToCurrentFolderJob: Job? = null
    private var listenToThreadsJob: Job? = null

    val currentMailbox = SingleLiveEvent<Mailbox?>()
    val currentFolder = SingleLiveEvent<Folder?>()
    val threads = SingleLiveEvent<List<Thread>?>()

    var lastMailboxId: String? = null
    var lastFolderRole: Folder.FolderRole? = null
    var lastUnreadCount = MailData.currentFolderFlow.value?.unreadCount ?: 0
    var filter: ThreadFilter = ThreadFilter.ALL

    var currentOffset = OFFSET_FIRST_PAGE
    var isDownloadingChanges = false

    fun listenToCurrentMailbox() {
        listenToCurrentMailboxJob?.cancel()
        listenToCurrentMailboxJob = viewModelScope.launch {
            MailData.currentMailboxFlow.filterNotNull().collect {
                currentMailbox.value = it
            }
        }
    }

    fun listenToCurrentFolder() {
        listenToCurrentFolderJob?.cancel()
        listenToCurrentFolderJob = viewModelScope.launch {
            MailData.currentFolderFlow.collect {
                currentFolder.value = it
            }
        }
    }

    fun listenToThreads() {
        listenToThreadsJob?.cancel()
        listenToThreadsJob = viewModelScope.launch {
            MailData.threadsFlow.collect {
                threads.value = it
            }
        }
    }

    fun loadMailData() {
        MailData.loadMailboxContent(filter)
    }

    fun loadThreads(folder: Folder, mailbox: Mailbox, offset: Int) {
        MailData.loadThreads(folder, mailbox, offset, filter)
    }

    fun refreshThreads() {
        currentOffset = OFFSET_FIRST_PAGE
        isDownloadingChanges = true
        MailData.refreshThreads(
            MailData.currentFolderFlow.value ?: return,
            MailData.currentMailboxFlow.value ?: return,
            filter
        )
    }
}
