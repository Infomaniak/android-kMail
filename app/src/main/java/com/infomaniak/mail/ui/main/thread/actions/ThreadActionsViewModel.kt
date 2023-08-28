/*
 * Infomaniak Mail - Android
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
package com.infomaniak.mail.ui.main.thread.actions

import androidx.lifecycle.*
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.coroutineContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

@HiltViewModel
class ThreadActionsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val messageController: MessageController,
    private val threadController: ThreadController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    private val threadUid inline get() = savedStateHandle.get<String>(ThreadActionsBottomSheetDialogArgs::threadUid.name)!!
    private val messageUidToReplyTo
        inline get() = savedStateHandle.get<String?>(ThreadActionsBottomSheetDialogArgs::messageUidToReplyTo.name)

    val threadLive: LiveData<Thread> = threadController.getThreadAsync(threadUid)
        .mapNotNull { it.obj }
        .asLiveData(ioCoroutineContext)

    fun getThreadAndMessageUidToReplyTo() = liveData(ioCoroutineContext) {
        val thread = threadController.getThread(threadUid) ?: run {
            emit(null)
            return@liveData
        }

        val uidToReplyTo = messageUidToReplyTo ?: messageController.getLastMessageToExecuteAction(thread).uid

        emit(thread to uidToReplyTo)
    }
}
