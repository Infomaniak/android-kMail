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

import androidx.lifecycle.*
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.update
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController.markAsSeen
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.ui.MainViewModel
import io.realm.kotlin.MutableRealm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ThreadViewModel : ViewModel() {

    private val currentThread = MutableLiveData<Thread>()
    val messages = Transformations.switchMap(currentThread) { thread ->
        liveData(Dispatchers.IO) { emitSource(thread.messages.asFlow().asLiveData()) }
    }

    fun openThread(threadUid: String) = viewModelScope.launch(Dispatchers.IO) {
        ThreadController.getThread(threadUid)?.let { thread ->
            selectThread(thread)
            currentThread.postValue(thread)
            markAsSeen(thread)
            updateMessages(thread)
        }
    }

    private fun selectThread(thread: Thread) {
        if (thread.uid != MainViewModel.currentThreadUid.value) MainViewModel.currentThreadUid.postValue(thread.uid)
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

                    // If we've already got this Message's Draft beforehand, we need to save
                    // its `draftLocalUuid`, otherwise we'll lose the link between them.
                    if (it.isDraft) it.draftLocalUuid = DraftController.getDraftByMessageUid(it.uid, realm = this)?.localUuid

                    it.fullyDownloaded = true
                }
            }
        }
    }

    fun deleteThread(threadUid: String) = viewModelScope.launch(Dispatchers.IO) { ThreadController.deleteThread(threadUid) }

    fun deleteDraft(message: Message) = viewModelScope.launch(Dispatchers.IO) { DraftController.deleteDraft(message) }
}
