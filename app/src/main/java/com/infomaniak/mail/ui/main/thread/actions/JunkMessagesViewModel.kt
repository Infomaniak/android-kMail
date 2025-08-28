/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.di.DefaultDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.MessageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JunkMessagesViewModel @Inject constructor(
    application: Application,
    mailboxController: MailboxController,
    private val messageController: MessageController,
    private val threadController: ThreadController,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    val messageOfUserToBlock = SingleLiveEvent<Message>()
    val potentialBlockedUsers = SingleLiveEvent<Map<Recipient, Message>>()
    var junkMessages: SingleLiveEvent<List<Message>> = SingleLiveEvent()
    var threadsUids: List<String> = emptyList()
        set(value) {
            field = value
            getJunkMessagesAndUsersToBlock(value)
        }

    private val currentMailboxFlow = mailboxController.getMailboxAsync(
        AccountUtils.currentUserId,
        AccountUtils.currentMailboxId,
    ).mapNotNull { it.obj }

    private val featureFlagsFlow = currentMailboxFlow.map { it.featureFlags }

    private fun getJunkMessagesAndUsersToBlock(threadUids: List<String>) = viewModelScope.launch(defaultDispatcher) {
        val (messages, potentialMessagesToBlock) = MessageUtils.getJunkMessagesAndMessagesToBlockUsers(
            threadController = threadController,
            messageController = messageController,
            featureFlagsLive = featureFlagsFlow.first(),
            threadsUids = threadUids,
        )
        junkMessages.postValue(messages)
        potentialBlockedUsers.postValue(potentialMessagesToBlock)
    }
}
