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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.update
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.AccountUtils
import io.realm.kotlin.MutableRealm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

class ThreadViewModel(application: Application) : AndroidViewModel(application) {

    private val localSettings by lazy { LocalSettings.getInstance(application) }

    fun threadLive(threadUid: String) = liveData(Dispatchers.IO) {
        emitSource(ThreadController.getThreadAsync(threadUid).mapNotNull { it.obj }.asLiveData())
    }

    fun messagesLive(threadUid: String) = liveData(Dispatchers.IO) {
        ThreadController.getThread(threadUid)?.messages?.asFlow()?.asLiveData()?.let { emitSource(it) }
    }

    fun openThread(threadUid: String) = viewModelScope.launch(Dispatchers.IO) {
        val mailbox = MailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId) ?: return@launch
        ThreadController.getThread(threadUid)?.let { thread ->
            if (thread.unseenMessagesCount > 0) {
                ThreadController.markAsSeen(thread, mailbox.uuid)
                MessageController.fetchCurrentFolderMessages(mailbox, thread.folderId, localSettings.threadMode)
            }
            updateMessages(thread)
        }
    }

    private fun updateMessages(thread: Thread) {
        RealmDatabase.mailboxContent().writeBlocking {
            val remoteMessages = fetchMessages(thread)
            update(thread.messages, remoteMessages)
        }
    }

    private fun MutableRealm.fetchMessages(thread: Thread): List<Message> {
        return thread.messages.mapNotNull { localMessage ->
            if (localMessage.fullyDownloaded) {
                localMessage
            } else {
                ApiRepository.getMessage(localMessage.resource).data?.also {
                    it.messageIds = localMessage.messageIds

                    // If we've already got this Message's Draft beforehand, we need to save
                    // its `draftLocalUuid`, otherwise we'll lose the link between them.
                    if (it.isDraft) it.draftLocalUuid = DraftController.getDraftByMessageUid(it.uid, realm = this)?.localUuid

                    it.fullyDownloaded = true
                }
            }
        }
    }

    fun deleteDraft(message: Message, threadUid: String, mailbox: Mailbox) = viewModelScope.launch(Dispatchers.IO) {
        val thread = ThreadController.getThread(threadUid) ?: return@launch
        val uids = listOf(message.uid) + thread.getMessageDuplicates(message.messageId)

        if (ApiRepository.deleteMessages(mailbox.uuid, uids).isSuccess()) {
            MessageController.fetchCurrentFolderMessages(mailbox, message.folderId, localSettings.threadMode)
        }
    }
}
