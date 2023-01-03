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
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.message.Message
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

    fun deleteDraft(message: Message, threadUid: String, mailbox: Mailbox) = viewModelScope.launch(Dispatchers.IO) {
        val thread = ThreadController.getThread(threadUid) ?: return@launch
        val uids = listOf(message.uid) + thread.getMessageDuplicatesUids(message.messageId)

        if (ApiRepository.deleteMessages(mailbox.uuid, uids).isSuccess()) {
            MessageController.fetchCurrentFolderMessages(mailbox, message.folderId, localSettings.threadMode)
        }
    }
}
