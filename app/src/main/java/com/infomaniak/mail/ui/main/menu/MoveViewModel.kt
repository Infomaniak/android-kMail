/*
 * Infomaniak ikMail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.menu

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.coroutineContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoveViewModel @Inject constructor(
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val messageController: MessageController,
    private val threadController: ThreadController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    private var searchJob: Job? = null

    private val draftFolderId: String? = FolderController.getFolder(FolderRole.DRAFT, mailboxContentRealm())?.id

    var searchResults: MutableLiveData<List<Folder>> = MutableLiveData()

    fun cancelSearch() {
        searchJob?.cancel()
    }

    fun getFolderIdByMessage(messageUid: String) = liveData(ioCoroutineContext) {
        emit(messageController.getMessage(messageUid)!!.folderId)
    }

    fun getFolderIdByThread(threadUid: String) = liveData(ioCoroutineContext) {
        emit(threadController.getThread(threadUid)!!.folderId)
    }

    fun searchQuery(query: String) = viewModelScope.launch(ioCoroutineContext) {
        searchJob?.cancel()
        searchJob = launch {
            delay(SEARCH_DEBOUNCE_DURATION)
            searchResults.postValue(FolderController.getFoldersByName(query.trim(), mailboxContentRealm()))
        }
    }

    override fun onCleared() {
        cancelSearch()
        super.onCleared()
    }

    private companion object {
        const val SEARCH_DEBOUNCE_DURATION = 300L
    }
}
