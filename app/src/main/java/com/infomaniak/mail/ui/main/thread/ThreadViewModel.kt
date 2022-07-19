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
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.MailRealm
import com.infomaniak.mail.data.cache.MailboxContentController
import com.infomaniak.mail.data.cache.MailboxContentController.getLatestThread
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class ThreadViewModel : ViewModel() {

    private var listenToMessagesJob: Job? = null

    private val mutableUiMessagesFlow = MutableStateFlow<List<Message>?>(null)
    val uiMessagesFlow = mutableUiMessagesFlow.asStateFlow()

    fun setup() {
        listenToMessages()
    }

    private fun listenToMessages() {
        listenToMessagesJob?.cancel()
        listenToMessagesJob = viewModelScope.launch {
            MailData.messagesFlow.filterNotNull().collect { messages ->
                mutableUiMessagesFlow.value = messages
            }
        }
    }

    fun loadMessages(threadUid: String) {
        MailboxContentController.getThread(threadUid)?.let { thread ->
            MailData.selectThread(thread)
            markAsSeen(thread)
            MailData.loadMessages(thread)
        }
    }

    private fun markAsSeen(thread: Thread) {
        if (thread.unseenMessagesCount != 0) {

            val mailboxUuid = MailData.currentMailboxFlow.value?.uuid ?: return

            MailRealm.mailboxContent.writeBlocking {
                getLatestThread(thread.uid)?.let { latestThread ->

                    val apiResponse = ApiRepository.markMessagesAsSeen(mailboxUuid, latestThread.messages.map { it.uid })

                    if (apiResponse.isSuccess()) {
                        latestThread.apply {
                            messages.forEach { it.seen = true }
                            unseenMessagesCount = 0
                        }
                    }
                }
            }
        }
    }
}
