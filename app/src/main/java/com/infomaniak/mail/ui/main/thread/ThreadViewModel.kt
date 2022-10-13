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
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.toSharedFlow
import io.realm.kotlin.notifications.ListChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThreadViewModel : ViewModel() {

    fun getThread(uid: String): LiveData<Thread?> = liveData(Dispatchers.IO) {
        emit(ThreadController.getThread(uid))
    }

    fun listenToMessages(thread: Thread): LiveData<ListChange<Message>> = liveData(Dispatchers.IO) {
        emitSource(thread.messages.asFlow().toSharedFlow().asLiveData())
    }

    fun deleteThread(threadUid: String) = viewModelScope.launch(Dispatchers.IO) { ThreadController.deleteThread(threadUid) }

    fun fetchDraft(message: Message, completion: (draftUuid: String) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val parentMessageUid = message.uid
        ApiRepository.getDraft(message.draftResource).data?.let { draft ->
            draft.initLocalValues(parentMessageUid)
            RealmDatabase.mailboxContent().writeBlocking {
                DraftController.upsertDraft(draft, this)
                MessageController.updateMessage(parentMessageUid, this) {
                    it.draftUuid = draft.uuid
                }
            }
            withContext(Dispatchers.Main) { completion(draft.uuid) }
        }
    }
}
