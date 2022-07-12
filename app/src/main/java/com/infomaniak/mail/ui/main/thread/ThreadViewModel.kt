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
import com.infomaniak.mail.data.cache.MailboxContentController
import com.infomaniak.mail.data.models.message.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class ThreadViewModel : ViewModel() {

    private var listenToMessagesJob: Job? = null

    private val mutableUiMessagesFlow: MutableStateFlow<List<Message>?> = MutableStateFlow(null)
    val uiMessagesFlow = mutableUiMessagesFlow.asStateFlow()

    val isExpandedHeaderMode = false

    fun setup() {
        listenToMessages()
    }

    private fun listenToMessages() {
        if (listenToMessagesJob != null) listenToMessagesJob?.cancel()

        listenToMessagesJob = CoroutineScope(Dispatchers.IO).launch {
            MailData.messagesFlow.filterNotNull().collect { messages ->
                mutableUiMessagesFlow.value = messages
            }
        }
    }

    fun loadMessages(threadUid: String) {
        val thread = MailboxContentController.getThread(threadUid) ?: return
        MailData.selectThread(thread)
        thread.markAsSeen()
        MailData.loadMessages(thread)
    }

    override fun onCleared() {
        listenToMessagesJob?.cancel()
        listenToMessagesJob = null

        super.onCleared()
    }
}
