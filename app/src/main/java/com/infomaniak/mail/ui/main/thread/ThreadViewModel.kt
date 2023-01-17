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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

class ThreadViewModel(application: Application) : AndroidViewModel(application) {

    val quickActionBarClicks = MutableLiveData<Pair<String, Int>>()

    private var fetchMessagesJob: Job? = null

    fun threadLive(threadUid: String) = liveData(Dispatchers.IO) {
        emitSource(ThreadController.getThreadAsync(threadUid).mapNotNull { it.obj }.asLiveData())
    }

    fun messagesLive(threadUid: String) = liveData(Dispatchers.IO) {
        ThreadController.getThread(threadUid)?.messages?.asFlow()?.asLiveData()?.let { emitSource(it) }
    }

    fun openThread(threadUid: String) = liveData(Dispatchers.IO) {

        val mailbox = MailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId) ?: run {
            emit(null)
            return@liveData
        }

        val thread = ThreadController.getThread(threadUid) ?: run {
            emit(null)
            return@liveData
        }

        val expandedMap = mutableMapOf<String, Boolean>()
        thread.messages.forEachIndexed { index, message ->
            expandedMap[message.uid] = message.shouldBeExpanded(index, thread.messages.lastIndex)
        }
        emit(expandedMap)

        if (thread.unseenMessagesCount > 0) SharedViewModelUtils.markAsSeen(thread, mailbox)
    }

    fun fetchIncompleteMessages(messages: List<Message>) {
        fetchMessagesJob?.cancel()
        fetchMessagesJob = viewModelScope.launch(Dispatchers.IO) {
            RealmDatabase.mailboxContent().writeBlocking {
                messages.forEach { localMessage ->
                    if (!localMessage.fullyDownloaded) {
                        ApiRepository.getMessage(localMessage.resource).data?.also { remoteMessage ->

                            // If we've already got this Message's Draft beforehand, we need to save
                            // its `draftLocalUuid`, otherwise we'll lose the link between them.
                            val draftLocalUuid = if (remoteMessage.isDraft) {
                                DraftController.getDraftByMessageUid(remoteMessage.uid, realm = this)?.localUuid
                            } else {
                                null
                            }

                            remoteMessage.initLocalValues(
                                fullyDownloaded = true,
                                messageIds = localMessage.messageIds,
                                isExpanded = localMessage.isExpanded,
                                draftLocalUuid,
                            )

                            MessageController.upsertMessage(remoteMessage, realm = this)
                        }
                    }
                }
            }
        }
    }

    fun deleteDraft(message: Message, threadUid: String, mailbox: Mailbox) = viewModelScope.launch(Dispatchers.IO) {
        val thread = ThreadController.getThread(threadUid) ?: return@launch
        val messages = thread.getMessageAndDuplicates(message)
        val uids = messages.map { it.uid }

        if (ApiRepository.deleteMessages(mailbox.uuid, uids).isSuccess()) {
            MessageController.fetchCurrentFolderMessages(mailbox, message.folderId)
        }
    }

    fun clickOnQuickActionBar(threadUid: String, menuId: Int) = viewModelScope.launch(Dispatchers.IO) {
        MessageController.getMessageUidToReplyTo(threadUid)?.let { messageUid ->
            quickActionBarClicks.postValue(messageUid to menuId)
        }
    }
}
