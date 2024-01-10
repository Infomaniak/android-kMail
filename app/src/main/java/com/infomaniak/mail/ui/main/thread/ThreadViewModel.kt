/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.collections.set

@HiltViewModel
class ThreadViewModel @Inject constructor(
    application: Application,
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val mailboxController: MailboxController,
    private val messageController: MessageController,
    private val refreshController: RefreshController,
    private val sharedUtils: SharedUtils,
    private val threadController: ThreadController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    private var threadLiveJob: Job? = null
    private var messagesLiveJob: Job? = null
    private var fetchMessagesJob: Job? = null

    val quickActionBarClicks = SingleLiveEvent<QuickActionBarResult>()

    var deletedMessagesUids = mutableSetOf<String>()
    val failedMessagesUids = SingleLiveEvent<List<String>>()

    val threadLive = MutableLiveData<Thread?>()
    val messagesLive = MutableLiveData<List<Message>>()

    private val mailbox by lazy { mailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)!! }

    private val currentMailboxLive = mailboxController.getMailboxAsync(
        AccountUtils.currentUserId,
        AccountUtils.currentMailboxId,
    ).map { it.obj }.asLiveData(ioCoroutineContext)

    fun reassignThreadLive(threadUid: String) {
        threadLiveJob?.cancel()
        threadLiveJob = viewModelScope.launch(ioCoroutineContext) {
            threadController.getThreadAsync(threadUid).map { it.obj }.collect(threadLive::postValue)
        }
    }

    fun reassignMessagesLive(threadUid: String) {
        messagesLiveJob?.cancel()
        messagesLiveJob = viewModelScope.launch(ioCoroutineContext) {

            val cachedSplitBodies = mutableMapOf<String, SplitBody>()

            suspend fun splitBody(message: Message): Message = withContext(ioDispatcher) {
                if (message.body == null) return@withContext message

                message.apply {
                    body?.let {
                        val isNotAlreadySplit = !cachedSplitBodies.contains(message.uid)
                        if (isNotAlreadySplit) cachedSplitBodies[message.uid] = MessageBodyUtils.splitContentAndQuote(it)
                        splitBody = cachedSplitBodies[message.uid]
                    }
                }

                return@withContext message
            }

            messageController.getSortedAndNotDeletedMessagesAsync(threadUid)
                ?.map { results -> results.list.map { splitBody(it) } }
                ?.collect(messagesLive::postValue)
        }
    }

    fun openThread(threadUid: String) = liveData(ioCoroutineContext) {

        val thread = threadController.getThread(threadUid) ?: run {
            emit(null)
            return@liveData
        }

        sendMatomoAndSentryAboutThreadMessagesCount(thread)

        val isExpandedMap = mutableMapOf<String, Boolean>()
        val isThemeTheSameMap = mutableMapOf<String, Boolean>()
        val initialSetOfExpandedMessagesUids = mutableSetOf<String>()
        thread.messages.forEachIndexed { index, message ->
            isExpandedMap[message.uid] = message.shouldBeExpanded(index, thread.messages.lastIndex).also {
                if (it) initialSetOfExpandedMessagesUids.add(message.uid)
            }
            isThemeTheSameMap[message.uid] = true
        }

        emit(OpenThreadResult(thread, isExpandedMap, initialSetOfExpandedMessagesUids, isThemeTheSameMap))

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

    fun assembleSubjectData(mergedContactsLive: LiveData<MergedContactDictionary>): LiveData<SubjectDataResult> {

        return MediatorLiveData<SubjectDataResult>().apply {

            addSource(threadLive) { thread ->
                value = SubjectDataResult(thread, value?.mergedContacts, value?.mailbox)
            }

            addSource(mergedContactsLive) { mergedContacts ->
                value = SubjectDataResult(value?.thread, mergedContacts, value?.mailbox)
            }

            addSource(currentMailboxLive) { mailbox ->
                value = SubjectDataResult(value?.thread, value?.mergedContacts, mailbox)
            }
        }
    }

    fun fetchMessagesHeavyData(messages: List<Message>) {
        fetchMessagesJob?.cancel()
        fetchMessagesJob = viewModelScope.launch(ioCoroutineContext) {
            val (deleted, failed) = ThreadController.fetchMessagesHeavyData(messages, mailboxContentRealm())
            if (deleted.isNotEmpty() || failed.isNotEmpty()) {

                // TODO: A race condition exists between the two notify in the ThreadAdapter.
                //  The 1st notify involves sending Messages to the adapter, while the 2nd notify entails retrieving
                //  Messages' heavy data and subsequently notifying the adapter with the `uids` of failed Messages.
                //  Ideally, the adapter should process the 1st notify before the 2nd one, but occasionally, the order is reversed.
                //  Consequently, it appears that the adapter disregards the 2nd notify,
                //  leading to an infinite shimmering effect that we cannot escape from.
                delay(100L)

                deletedMessagesUids.apply {
                    clear()
                    addAll(deleted)
                }

                failedMessagesUids.postValue(failed)
            }
        }
    }

    fun deleteDraft(message: Message, mailbox: Mailbox) = viewModelScope.launch(ioCoroutineContext) {
        val realm = mailboxContentRealm()
        val thread = threadLive.value ?: return@launch
        val messages = messageController.getMessageAndDuplicates(thread, message)
        val isSuccess = ApiRepository.deleteMessages(mailbox.uuid, messages.getUids()).isSuccess()
        if (isSuccess) {
            refreshController.refreshThreads(
                refreshMode = RefreshMode.REFRESH_FOLDER_WITH_ROLE,
                mailbox = mailbox,
                folder = message.folder,
                realm = realm,
            )
        }
    }

    fun clickOnQuickActionBar(menuId: Int) = viewModelScope.launch(ioCoroutineContext) {
        val thread = threadLive.value ?: return@launch
        val message = messageController.getLastMessageToExecuteAction(thread)
        quickActionBarClicks.postValue(QuickActionBarResult(thread.uid, message, menuId))
    }

    data class SubjectDataResult(
        val thread: Thread?,
        val mergedContacts: MergedContactDictionary?,
        val mailbox: Mailbox?,
    )

    data class OpenThreadResult(
        val thread: Thread,
        val isExpandedMap: MutableMap<String, Boolean>,
        val initialSetOfExpandedMessagesUids: Set<String>,
        val isThemeTheSameMap: MutableMap<String, Boolean>,
    )

    data class QuickActionBarResult(
        val threadUid: String,
        val message: Message,
        val menuId: Int,
    )
}
