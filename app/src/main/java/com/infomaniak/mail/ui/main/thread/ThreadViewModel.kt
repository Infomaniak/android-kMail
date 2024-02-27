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
import com.infomaniak.mail.data.models.calendar.Attendee.AttendanceState
import com.infomaniak.mail.data.models.calendar.CalendarEventResponse
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.MainViewModel.ThreadBackup
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.SuperCollapsedBlock
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.MessageBodyUtils.SplitBody
import com.infomaniak.mail.utils.extensions.MergedContactDictionary
import com.infomaniak.mail.utils.extensions.context
import com.infomaniak.mail.utils.extensions.getUids
import com.infomaniak.mail.utils.extensions.indexOfFirstOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.query.RealmResults
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.collections.set

typealias ThreadAdapterItems = List<Any>
typealias MessagesWithoutHeavyData = List<Message>

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
    private var fetchCalendarEventJob: Job? = null

    val quickActionBarClicks = SingleLiveEvent<QuickActionBarResult>()

    //region Calendar Events
    private val treatedMessagesForCalendarEvent = mutableSetOf<String>()
    val isCalendarEventExpandedMap = mutableMapOf<String, Boolean>()
    //endregion

    var deletedMessagesUids = mutableSetOf<String>()
    val failedMessagesUids = SingleLiveEvent<List<String>>()

    val threadLive = MutableLiveData<Thread?>()
    val messagesLive = MutableLiveData<Pair<ThreadAdapterItems, MessagesWithoutHeavyData>>()

    private val cachedSplitBodies = mutableMapOf<String, SplitBody>()

    var shouldMarkThreadAsSeen: Boolean = false

    var superCollapsedBlock: SuperCollapsedBlock? = null

    private val mailbox by lazy { mailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)!! }

    private val currentMailboxLive = mailboxController.getMailboxAsync(
        AccountUtils.currentUserId,
        AccountUtils.currentMailboxId,
    ).map { it.obj }.asLiveData(ioCoroutineContext)

    fun resetMessagesRelatedCache() {
        treatedMessagesForCalendarEvent.clear()
        isCalendarEventExpandedMap.clear()
        cachedSplitBodies.clear()
        shouldMarkThreadAsSeen = false
        superCollapsedBlock = null
    }

    fun reassignThreadLive(threadUid: String) {
        threadLiveJob?.cancel()
        threadLiveJob = viewModelScope.launch(ioCoroutineContext) {
            threadController.getThreadAsync(threadUid).map { it.obj }.collect(threadLive::postValue)
        }
    }

    fun reassignMessagesLive(threadUid: String, withSuperCollapsedBlock: Boolean = true) {
        messagesLiveJob?.cancel()
        messagesLiveJob = viewModelScope.launch(ioCoroutineContext) {
            messageController.getSortedAndNotDeletedMessagesAsync(threadUid)
                ?.map { mapRealmMessagesResult(it.list, threadUid, withSuperCollapsedBlock) }
                ?.collect(messagesLive::postValue)
        }
    }

    private suspend fun mapRealmMessagesResult(
        messages: RealmResults<Message>,
        threadUid: String,
        withSuperCollapsedBlock: Boolean,
    ): Pair<ThreadAdapterItems, MessagesWithoutHeavyData> {

        superCollapsedBlock = superCollapsedBlock ?: SuperCollapsedBlock()

        val items = mutableListOf<Any>()
        val messagesToFetch = mutableListOf<Message>()
        val thread = messages.firstOrNull()?.threads?.firstOrNull { it.uid == threadUid } ?: return items to messagesToFetch
        val firstIndexAfterBlock = computeFirstIndexAfterBlock(thread, messages)
        superCollapsedBlock!!.shouldBeDisplayed =
            shouldBlockBeDisplayed(messages.count(), firstIndexAfterBlock, withSuperCollapsedBlock)

        suspend fun addMessage(message: Message) {
            splitBody(message).let {
                items += it
                if (!it.isFullyDownloaded()) messagesToFetch += it
            }
        }

        suspend fun mapListWithNewBlock() {
            messages.forEachIndexed { index, message ->
                when (index) {
                    0 -> { // First Message
                        addMessage(message)
                    }
                    in 1..<firstIndexAfterBlock -> { // All Messages that should go in block
                        superCollapsedBlock!!.messagesUids.add(message.uid)
                    }
                    firstIndexAfterBlock -> { // First Message not in block
                        items += superCollapsedBlock!!
                        addMessage(message.apply { shouldHideDivider = true })
                    }
                    else -> { // All following Messages
                        addMessage(message)
                    }
                }
            }
        }

        suspend fun mapListWithExistingBlock() {

            var isStillInBlock = true
            val previousBlock = superCollapsedBlock!!.messagesUids.toSet()

            superCollapsedBlock!!.messagesUids.clear()

            messages.forEachIndexed { index, message ->
                when {
                    index == 0 -> { // First Message
                        addMessage(message)
                    }
                    previousBlock.contains(message.uid) && isStillInBlock -> { // All Messages already in block
                        superCollapsedBlock!!.messagesUids.add(message.uid)
                    }
                    !previousBlock.contains(message.uid) && isStillInBlock -> { // First Message not in block
                        isStillInBlock = false
                        items += superCollapsedBlock!!
                        addMessage(message.apply { shouldHideDivider = true })
                    }
                    else -> { // All following Messages
                        addMessage(message)
                    }
                }
            }
        }

        suspend fun mapFullList() {
            messages.forEach { addMessage(it) }
        }

        if (superCollapsedBlock!!.shouldBeDisplayed) {
            if (superCollapsedBlock!!.isFirstTime()) mapListWithNewBlock() else mapListWithExistingBlock()
        } else {
            mapFullList()
        }

        return items to messagesToFetch
    }

    private fun computeFirstIndexAfterBlock(thread: Thread, list: RealmResults<Message>): Int {

        val firstDefaultIndex = list.count() - 2
        val firstUnreadIndex = if (thread.unseenMessagesCount > 0) list.indexOfFirstOrNull { !it.isSeen } else null
        val notNullFirstUnreadIndex = firstUnreadIndex ?: firstDefaultIndex

        return minOf(notNullFirstUnreadIndex, firstDefaultIndex)
    }

    /**
     * Before trying to create the SuperCollapsedBlock, we need these required Messages that will be displayed:
     * - The 1st Message will always be displayed.
     * - The last 2 Messages will always be displayed.
     * - If there's any unread Message in between, it will be displayed (hence, all following Messages will be displayed too).
     * After all these Messages are displayed, if there's at least 2 remaining Messages, they're gonna be collapsed in the Block.
     */
    private fun shouldBlockBeDisplayed(messagesCount: Int, firstIndexAfterBlock: Int, withSuperCollapsedBlock: Boolean): Boolean {
        return withSuperCollapsedBlock && // When we want to print a mail, we need the full list of Messages
                superCollapsedBlock?.shouldBeDisplayed == true && // If the Block was hidden for any reason, we mustn't ever display it again
                superCollapsedBlock?.hasBeenClicked == false && // Block hasn't been expanded by the user
                messagesCount >= SUPER_COLLAPSED_BLOCK_MINIMUM_MESSAGES_LIMIT && // At least 5 Messages in the Thread
                firstIndexAfterBlock >= SUPER_COLLAPSED_BLOCK_FIRST_INDEX_LIMIT  // At least 2 Messages in the Block
    }

    private suspend fun splitBody(message: Message): Message = withContext(ioDispatcher) {
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

    fun openThread(threadUid: String, threadBackup: ThreadBackup?) = liveData(ioCoroutineContext) {

        val thread = threadController.getThread(threadUid) ?: run {
            emit(null)
            return@liveData
        }

        sendMatomoAndSentryAboutThreadMessagesCount(thread)

        var isExpandedMap = mutableMapOf<String, Boolean>()
        var initialSetOfExpandedMessagesUids = mutableSetOf<String>()
        var isThemeTheSameMap = mutableMapOf<String, Boolean>()

        if (threadBackup != null) {
            isExpandedMap = threadBackup.isExpandedMapBackup
            initialSetOfExpandedMessagesUids = threadBackup.initialSetOfExpandedMessagesUidsBackup.toMutableSet()
            isThemeTheSameMap = threadBackup.isThemeTheSameMapBackup
            if (threadBackup.hasSuperCollapsedBlockBeenClicked) superCollapsedBlock = SuperCollapsedBlock(hasBeenClicked = true)
        } else {
            thread.messages.forEachIndexed { index, message ->
                isExpandedMap[message.uid] = message.shouldBeExpanded(index, thread.messages.lastIndex).also {
                    if (it) initialSetOfExpandedMessagesUids.add(message.uid)
                }
                isThemeTheSameMap[message.uid] = true
            }
        }

        shouldMarkThreadAsSeen = thread.unseenMessagesCount > 0

        emit(OpenThreadResult(thread, isExpandedMap, initialSetOfExpandedMessagesUids, isThemeTheSameMap))
    }

    fun markThreadAsSeen() = viewModelScope.launch(ioCoroutineContext) {
        threadLive.value?.let { sharedUtils.markAsSeen(mailbox, listOf(it)) }
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

    fun fetchCalendarEvents(items: List<Any>, forceFetch: Boolean = false) {
        fetchCalendarEventJob?.cancel()
        fetchCalendarEventJob = viewModelScope.launch(ioCoroutineContext) {
            mailboxContentRealm().writeBlocking {
                items.forEach { item -> if (item is Message) fetchCalendarEvent(item, forceFetch) }
            }
        }
    }

    private fun MutableRealm.fetchCalendarEvent(message: Message, forceFetch: Boolean) {
        if (!message.isFullyDownloaded()) return // Only treat message that have their Attachments downloaded

        if (!forceFetch) {
            val alreadyTreated = !treatedMessagesForCalendarEvent.add(message.uid)
            if (alreadyTreated) return
        }

        val icsAttachment = message.calendarAttachment ?: return

        val apiResponse = icsAttachment.resource?.let { resource ->
            ApiRepository.getAttachmentCalendarEvent(resource)
        } ?: return

        if (apiResponse.isSuccess()) {
            updateCalendarEvent(message, apiResponse.data!!)
        } else {
            Sentry.withScope { scope ->
                scope.setExtra("ics attachment mimeType", icsAttachment.mimeType)
                scope.setExtra("ics attachment size", icsAttachment.size.toString())
                scope.setExtra("error code", apiResponse.error?.code.toString())
                Sentry.captureMessage("Failed loading calendar event")
            }
        }
    }

    private fun MutableRealm.updateCalendarEvent(message: Message, calendarEventResponse: CalendarEventResponse) {
        MessageController.updateMessage(message.uid, realm = this) { localMessage ->
            localMessage?.let {
                it.latestCalendarEventResponse = calendarEventResponse
            } ?: run {
                Sentry.withScope { scope ->
                    scope.level = SentryLevel.ERROR
                    scope.setExtra("message.uid", message.uid)
                    val hasUserStoredEvent = calendarEventResponse.hasAssociatedInfomaniakCalendarEvent()
                    scope.setExtra("event has userStoredEvent", hasUserStoredEvent.toString())
                    scope.setExtra("event's isCanceled", calendarEventResponse.isCanceled.toString())
                    scope.setExtra("event has attachmentEvent", calendarEventResponse.hasAttachmentEvent().toString())
                    Sentry.captureMessage("Cannot find message by uid for fetched calendar event inside Realm")
                }
            }
        }
    }

    fun replyToCalendarEvent(attendanceState: AttendanceState, message: Message) = liveData(ioCoroutineContext) {
        val calendarEventResponse = message.latestCalendarEventResponse!!

        val response = ApiRepository.replyToCalendarEvent(
            attendanceState,
            useInfomaniakCalendarRoute = calendarEventResponse.hasAssociatedInfomaniakCalendarEvent(),
            calendarEventId = calendarEventResponse.calendarEvent!!.id,
            attachmentResource = message.calendarAttachment!!.resource ?: "",
        )

        emit(response.isSuccess())
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

    companion object {
        private const val SUPER_COLLAPSED_BLOCK_MINIMUM_MESSAGES_LIMIT = 5
        private const val SUPER_COLLAPSED_BLOCK_FIRST_INDEX_LIMIT = 3
    }
}
