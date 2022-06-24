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

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.cache.MailboxContentController
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ThreadViewModel : ViewModel() {

    val messagesFromApi = MutableStateFlow<List<Message>?>(null)

    val isExpandedHeaderMode = false

    fun getMessagesFromRealmThenFetchFromApi(threadUid: String): List<Message> {
        return readThreadFromRealm(threadUid)?.also { thread ->
            thread.select()
            thread.markAsSeen()
            fetchThreadFromApi(thread)
        }?.messages ?: emptyList()
    }

    private fun readThreadFromRealm(threadUid: String): Thread? {
        Log.e("Realm", "Start reading thread")
        val thread = MailboxContentController.getLatestThread(threadUid)
        Log.e("Realm", "End of reading thread")
        return thread
    }

    private fun fetchThreadFromApi(thread: Thread) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.e("API", "Start fetching thread")
            thread.updateAndSelect()
            Log.e("API", "End of fetching thread")
            messagesFromApi.value = MailboxContentController.getThread(thread.uid)?.messages
        }
    }
}
