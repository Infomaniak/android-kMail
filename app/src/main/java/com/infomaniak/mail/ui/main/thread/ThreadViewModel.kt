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
package com.infomaniak.mail.ui.main.thread

import android.app.Application
import androidx.lifecycle.*
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.SharedViewModelUtils
import com.infomaniak.mail.utils.getUids
import com.infomaniak.mail.utils.handlerIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ThreadViewModel(application: Application) : AndroidViewModel(application) {

    val quickActionBarClicks = MutableLiveData<Pair<Message, Int>>()

    private var fetchMessagesJob: Job? = null

    private val mailbox by lazy { MailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId) }

    fun threadLive(threadUid: String) = liveData(Dispatchers.IO) {
        emitSource(ThreadController.getThreadAsync(threadUid).map { it.obj }.asLiveData())
    }

    fun messagesLive(threadUid: String) = liveData(Dispatchers.IO) {
        emitSource(MessageController.getSortedMessages(threadUid).asFlow().asLiveData())
    }

    fun openThread(threadUid: String) = liveData(Dispatchers.IO) {

        val thread = ThreadController.getThread(threadUid)

        val expandedMap = mutableMapOf<String, Boolean>()
        thread.messages.forEachIndexed { index, message ->
            expandedMap[message.uid] = message.shouldBeExpanded(index, thread.messages.lastIndex)
        }
        emit(expandedMap)

        if (thread.unseenMessagesCount > 0) SharedViewModelUtils.markAsSeen(mailbox, thread)
    }

    fun fetchIncompleteMessages(messages: List<Message>) {
        fetchMessagesJob?.cancel()
        fetchMessagesJob = viewModelScope.launch(Dispatchers.IO) {
            ThreadController.fetchIncompleteMessages(messages, mailbox)
        }
    }

    fun deleteDraft(message: Message, threadUid: String, mailbox: Mailbox) = viewModelScope.launch(viewModelScope.handlerIO) {
        val thread = ThreadController.getThread(threadUid)
        val messages = MessageController.getMessageAndDuplicates(thread, message)
        val isSuccess = ApiRepository.deleteMessages(mailbox.uuid, messages.getUids()).isSuccess()
        if (isSuccess) MessageController.fetchCurrentFolderMessages(mailbox, message.folderId)
    }

    fun clickOnQuickActionBar(threadUid: String, menuId: Int) = viewModelScope.launch(Dispatchers.IO) {
        val thread = ThreadController.getThread(threadUid)
        val message = MessageController.getMessageToReplyTo(thread)
        quickActionBarClicks.postValue(message to menuId)
    }
}
