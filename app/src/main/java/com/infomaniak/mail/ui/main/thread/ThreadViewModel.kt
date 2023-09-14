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
package com.infomaniak.mail.ui.main.thread

import android.app.Application
import androidx.lifecycle.*
import com.infomaniak.lib.core.utils.DownloadManagerUtils
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
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.MessageBodyUtils.SplitBody
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.ext.copyFromRealm
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.collections.set

@HiltViewModel
class ThreadViewModel @Inject constructor(
    application: Application,
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val mailboxController: MailboxController,
    private val messageController: MessageController,
    private val refreshController: RefreshController,
    private val savedStateHandle: SavedStateHandle,
    private val sharedUtils: SharedUtils,
    private val threadController: ThreadController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)
    private var fetchMessagesJob: Job? = null

    private val threadUid inline get() = savedStateHandle.get<String>(ThreadFragmentArgs::threadUid.name)!!
    private val mailbox by lazy { mailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)!! }

    val quickActionBarClicks = SingleLiveEvent<Pair<Message, Int>>()


    val threadLive = liveData(ioCoroutineContext) {
        emitSource(threadController.getThreadAsync(threadUid).map { it.obj }.asLiveData())
    }

    private val splitBodies = mutableMapOf<String, SplitBody>()

    val messagesLive = liveData(ioCoroutineContext) {

        suspend fun splitBody(message: Message): Message = withContext(ioDispatcher) {
            if (message.body == null) return@withContext message

            return@withContext message.copyFromRealm().apply {
                body?.let {
                    val isNotAlreadySplit = !splitBodies.contains(message.uid)
                    if (isNotAlreadySplit) splitBodies[message.uid] = MessageBodyUtils.splitContentAndQuote(it)
                    it.splitBody = splitBodies[message.uid]!!
                }
            }
        }

        messageController.getSortedMessagesAsync(threadUid)
            ?.map { results -> results.list.map { splitBody(it) } }
            ?.asLiveData()
            ?.let { emitSource(it) }
    }

    private val currentMailboxLive = mailboxController.getMailboxAsync(
        AccountUtils.currentUserId,
        AccountUtils.currentMailboxId,
    ).map { it.obj }.asLiveData(ioCoroutineContext)

    fun threadAndMergedContactAndMailboxMediator(
        mergedContactsLive: LiveData<MergedContactDictionary?>,
    ): LiveData<Triple<Thread?, MergedContactDictionary?, Mailbox?>> {
        return MediatorLiveData<Triple<Thread?, MergedContactDictionary?, Mailbox?>>().apply {
            addSource(threadLive) { first ->
                value = Triple(first, value?.second, value?.third)
            }

            addSource(mergedContactsLive) { second ->
                value = Triple(value?.first, second, value?.third)
            }

            addSource(currentMailboxLive) { third ->
                value = Triple(value?.first, value?.second, third)
            }
        }
    }

    fun openThread() = liveData(ioCoroutineContext) {

        val thread = threadController.getThread(threadUid) ?: run {
            emit(null)
            return@liveData
        }

        sendMatomoAndSentryAboutThreadMessagesCount(thread)

        val isExpandedMap = mutableMapOf<String, Boolean>()
        val isThemeTheSameMap = mutableMapOf<String, Boolean>()
        thread.messages.forEachIndexed { index, message ->
            isExpandedMap[message.uid] = message.shouldBeExpanded(index, thread.messages.lastIndex)
            isThemeTheSameMap[message.uid] = true
        }

        emit(Triple(thread, isExpandedMap, isThemeTheSameMap))

        if (thread.unseenMessagesCount > 0) sharedUtils.markAsSeen(mailbox, listOf(thread))
    }

    private fun sendMatomoAndSentryAboutThreadMessagesCount(thread: Thread) {

        val nbMessages = thread.messages.count()

        context.trackUserInfo("nbMessagesInThread", nbMessages)

        when (nbMessages) {
            0 -> SentryDebug.sendEmptyThread(thread)
            1 -> context.trackUserInfo("oneMessagesInThread")
            else -> context.trackUserInfo("multipleMessagesInThread", nbMessages)
        }
    }

    fun fetchMessagesHeavyData(messages: List<Message>) {
        fetchMessagesJob?.cancel()
        fetchMessagesJob = viewModelScope.launch(ioCoroutineContext) {
            threadController.fetchMessagesHeavyData(messages, mailbox, mailboxContentRealm())
        }
    }

    fun deleteDraft(message: Message, mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        val thread = threadController.getThread(threadUid) ?: return@launch
        val messages = messageController.getMessageAndDuplicates(thread, message)
        val isSuccess = ApiRepository.deleteMessages(mailbox.uuid, messages.getUids()).isSuccess()
        if (isSuccess) {
            refreshController.refreshThreads(
                refreshMode = RefreshMode.REFRESH_FOLDER_WITH_ROLE,
                mailbox = mailbox,
                folder = message.folder,
                realm = mailboxContentRealm(),
            )
        }
    }

    fun clickOnQuickActionBar(menuId: Int) = viewModelScope.launch(ioCoroutineContext) {
        val thread = threadController.getThread(threadUid) ?: return@launch
        val message = messageController.getLastMessageToExecuteAction(thread)
        quickActionBarClicks.postValue(message to menuId)
    }

    fun scheduleDownload(downloadUrl: String, filename: String) = viewModelScope.launch(ioCoroutineContext) {
        if (ApiRepository.ping().isSuccess()) {
            DownloadManagerUtils.scheduleDownload(context, downloadUrl, filename)
        }
    }
}
