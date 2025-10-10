/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.coroutineContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.invoke
import javax.inject.Inject

@HiltViewModel
class ThreadActionsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val messageController: MessageController,
    mailboxController: MailboxController,
    threadController: ThreadController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    private val threadUid inline get() = savedStateHandle.get<String>(ThreadActionsBottomSheetDialogArgs::threadUid.name)!!

    private val threadMessageToExecuteAction: Flow<ThreadMessageInteraction.Action> = threadController.getThreadAsync(threadUid)
        .mapNotNull { it.obj?.let { thread -> getThreadAndMessageUidToExecuteAction(thread) } }

    private val threadMessageToExecuteReaction: Flow<ThreadMessageInteraction.Reaction?> = threadController.getThreadAsync(threadUid)
        .mapNotNull { it.obj }
        .map { getMessageToExecuteReaction(it) }

    val threadMessagesWithActionAndReaction: SharedFlow<ThreadMessageToExecuteInteraction> =
        combine(threadMessageToExecuteAction, threadMessageToExecuteReaction) { messageToExecuteActions, messageToExecuteReaction ->
            ThreadMessageToExecuteInteraction(
                messageToExecuteActions.thread,
                messageToExecuteActions.messageUid,
                messageToExecuteReaction?.messageUid
            )
        }
        .shareIn(scope = viewModelScope, started = SharingStarted.Eagerly, replay = 1)

    private val currentMailboxLive = mailboxController.getMailboxAsync(
        AccountUtils.currentUserId,
        AccountUtils.currentMailboxId,
    ).mapNotNull { it.obj }.asLiveData(ioCoroutineContext)

    private val featureFlagsLive = currentMailboxLive.map { it.featureFlags }

    private suspend fun getThreadAndMessageUidToExecuteAction(thread: Thread): ThreadMessageInteraction.Action {
        val messageUid = ioDispatcher { messageController.getLastMessageToExecuteAction(thread, featureFlagsLive.value).uid }
        return ThreadMessageInteraction.Action(thread, messageUid)
    }

    private suspend fun getMessageToExecuteReaction(thread: Thread): ThreadMessageInteraction.Reaction? {
        val messageUid = ioDispatcher { messageController.getLastMessageToExecuteReaction(thread, featureFlagsLive.value)?.uid }
        return messageUid?.let(ThreadMessageInteraction::Reaction)
    }

    private sealed class ThreadMessageInteraction(open val messageUid: String) {
        data class Action(val thread: Thread, override val messageUid: String) : ThreadMessageInteraction(messageUid)
        data class Reaction(override val messageUid: String): ThreadMessageInteraction(messageUid)
    }

    data class ThreadMessageToExecuteInteraction(
        val thread: Thread,
        val messageUidToExecuteAction: String,
        val messageUidToExecuteReaction: String?
    )
}
