/*
 * Infomaniak ikMail - Android
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
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.MatomoMail.trackUserInfo
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.coroutineContext
import com.infomaniak.mail.utils.getUids
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.collections.set

@HiltViewModel
class ThreadViewModel @Inject constructor(
    application: Application,
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val messageController: MessageController,
    private val sharedUtils: SharedUtils,
    private val threadController: ThreadController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private inline val context: Context get() = getApplication()

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)
    private var fetchMessagesJob: Job? = null

    val quickActionBarClicks = SingleLiveEvent<Pair<Message, Int>>()

    private val mailbox by lazy { MailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)!! }

    fun threadLive(threadUid: String) = liveData(ioCoroutineContext) {
        emitSource(threadController.getThreadAsync(threadUid).map { it.obj }.asLiveData())
    }

    fun messagesLive(threadUid: String) = liveData(ioCoroutineContext) {
        messageController.getSortedMessages(threadUid)?.asFlow()?.asLiveData()?.let { emitSource(it) }
    }

    fun openThread(threadUid: String) = liveData(ioCoroutineContext) {

        val thread = threadController.getThread(threadUid) ?: run {
            emit(null)
            return@liveData
        }

        context.trackUserInfo("nbMessagesInThread", thread.messages.count())

        val isExpandedMap = mutableMapOf<String, Boolean>()
        val isThemeTheSameMap = mutableMapOf<String, Boolean>()
        thread.messages.forEachIndexed { index, message ->
            isExpandedMap[message.uid] = message.shouldBeExpanded(index, thread.messages.lastIndex)
            isThemeTheSameMap[message.uid] = true
        }

        emit(Triple(thread, isExpandedMap, isThemeTheSameMap))

        if (thread.unseenMessagesCount > 0) sharedUtils.markAsSeen(mailbox, listOf(thread))
    }

    fun fetchIncompleteMessages(messages: List<Message>) {
        fetchMessagesJob?.cancel()
        fetchMessagesJob = viewModelScope.launch(ioCoroutineContext) {
            threadController.fetchIncompleteMessages(messages, mailbox)
        }
    }

    fun deleteDraft(message: Message, threadUid: String, mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        val thread = threadController.getThread(threadUid) ?: return@launch
        val messages = messageController.getMessageAndDuplicates(thread, message)
        val isSuccess = ApiRepository.deleteMessages(mailbox.uuid, messages.getUids()).isSuccess()
        if (isSuccess) {
            RefreshController.refreshThreads(
                refreshMode = RefreshMode.REFRESH_FOLDER_WITH_ROLE,
                mailbox = mailbox,
                folder = message.folder,
                realm = mailboxContentRealm(),
            )
        }
    }

    fun clickOnQuickActionBar(threadUid: String, menuId: Int) = viewModelScope.launch(ioCoroutineContext) {
        val thread = threadController.getThread(threadUid) ?: return@launch
        val message = messageController.getLastMessageToExecuteAction(thread)
        quickActionBarClicks.postValue(message to menuId)
    }
}
