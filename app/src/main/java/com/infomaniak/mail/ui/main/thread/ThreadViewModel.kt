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
package com.infomaniak.mail.ui.main.thread

import android.app.Application
import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.lifecycle.*
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.MatomoMail.trackUserInfo
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.calendar.Attendee.AttendanceState
import com.infomaniak.mail.data.models.calendar.CalendarEventResponse
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.SuperCollapsedBlock
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.SharedUtils.Companion.isSnoozeAvailable
import com.infomaniak.mail.utils.extensions.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.query.RealmResults
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

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
    private val localSettings: LocalSettings,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    private var fetchMessagesJob: Job? = null
    private var fetchCalendarEventJob: Job? = null

    val threadState = ThreadState()

    private val threadOpeningModeFlow: MutableSharedFlow<ThreadOpeningMode> = MutableSharedFlow()
    val threadFlow: Flow<Thread?> = threadOpeningModeFlow.map { mode -> mode.threadUid?.let(threadController::getThread) }

    // Could this directly collect threadOpeningModeFlow instead of collecting threadFlow?
    @OptIn(ExperimentalCoroutinesApi::class)
    val threadLive = threadFlow.filterNotNull().flatMapLatest { thread ->
        threadController.getThreadAsync(thread.uid).map { it.obj }
    }.asLiveData(ioCoroutineContext)

    @OptIn(ExperimentalCoroutinesApi::class)
    val messagesLive: LiveData<Pair<ThreadAdapterItems, MessagesWithoutHeavyData>> =
        combine(threadOpeningModeFlow, threadState.hasSuperCollapsedBlockBeenClicked) { mode, _ ->
            mode
        }.flatMapLatest { mode -> mode.getMessages() }.asLiveData(ioCoroutineContext)

    val batchedMessages = SingleLiveEvent<List<Any>>()

    val quickActionBarClicks = SingleLiveEvent<QuickActionBarResult>()

    val failedMessagesUids = SingleLiveEvent<List<String>>()
    var deletedMessagesUids = mutableSetOf<String>()

    private val mailbox by lazy { mailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)!! }

    private val currentMailboxLive = mailboxController.getMailboxAsync(
        AccountUtils.currentUserId,
        AccountUtils.currentMailboxId,
    ).map { it.obj }.asLiveData(ioCoroutineContext)

    // Save the current scheduled date of the draft we're rescheduling to be able to pass it to the schedule bottom sheet
    var reschedulingCurrentlyScheduledEpochMillis: Long? = null

    val isThreadSnoozeHeaderVisible = Utils.waitInitMediator(currentMailboxLive, threadLive).map { (mailbox, thread) ->
        when {
            thread == null || thread.isSnoozed().not() -> ThreadHeaderVisibility.NONE
            thread.shouldDisplayHeaderActions(mailbox) -> ThreadHeaderVisibility.MESSAGE_AND_ACTIONS
            else -> ThreadHeaderVisibility.MESSAGE_ONLY
        }
    }

    init {
        viewModelScope.launch {
            threadFlow.filterNotNull().collect { thread ->
                // These 2 will always be empty or not all together at the same time.
                if (threadState.isExpandedMap.isEmpty() || threadState.isThemeTheSameMap.isEmpty()) {
                    thread.messages.forEachIndexed { index, message ->
                        threadState.isExpandedMap[message.uid] = message.shouldBeExpanded(index, thread.messages.lastIndex)
                        threadState.isThemeTheSameMap[message.uid] = true
                    }
                }

                if (threadState.isFirstOpening) {
                    threadState.isFirstOpening = false
                    sendMatomoAndSentryAboutThreadMessagesCount(thread)
                    if (thread.isSeen.not()) markThreadAsSeen(thread)
                }
            }
        }
    }

    private fun Thread.shouldDisplayHeaderActions(mailbox: Mailbox?): Boolean {
        return isSnoozeAvailable(mailbox?.featureFlags, localSettings) && folder.role == FolderRole.SNOOZED
    }

    private suspend fun mapRealmMessagesResult(
        messages: RealmResults<Message>,
        threadUid: String,
    ): Pair<ThreadAdapterItems, MessagesWithoutHeavyData> = with(threadState) {

        if (messages.isEmpty()) return emptyList<Any>() to emptyList()

        superCollapsedBlock = superCollapsedBlock ?: SuperCollapsedBlock()

        val thread = messages.first().threads.single { it.uid == threadUid }
        val firstIndexAfterBlock = computeFirstIndexAfterBlock(thread, messages)
        superCollapsedBlock!!.shouldBeDisplayed = shouldBlockBeDisplayed(messages.count(), firstIndexAfterBlock)

        suspend fun formatListWithNewBlock(): Pair<ThreadAdapterItems, MessagesWithoutHeavyData> {
            return formatLists(messages) { index, _ ->
                when (index) {
                    0 -> MessageBehavior.DISPLAYED // First Message
                    in 1 until firstIndexAfterBlock -> MessageBehavior.COLLAPSED // All Messages that should go in block
                    firstIndexAfterBlock -> MessageBehavior.FIRST_AFTER_BLOCK // First Message not in block
                    else -> MessageBehavior.DISPLAYED // All following Messages
                }
            }
        }

        suspend fun formatListWithExistingBlock(): Pair<ThreadAdapterItems, MessagesWithoutHeavyData> {

            var isStillInBlock = true
            val previousBlock = superCollapsedBlock!!.messagesUids.toSet()

            superCollapsedBlock!!.messagesUids.clear()

            return formatLists(messages) { index, messageUid ->
                when {
                    index == 0 -> { // First Message
                        MessageBehavior.DISPLAYED
                    }
                    previousBlock.contains(messageUid) && isStillInBlock -> { // All Messages already in block
                        MessageBehavior.COLLAPSED
                    }
                    !previousBlock.contains(messageUid) && isStillInBlock -> { // First Message not in block
                        isStillInBlock = false
                        MessageBehavior.FIRST_AFTER_BLOCK
                    }
                    else -> { // All following Messages
                        MessageBehavior.DISPLAYED
                    }
                }
            }
        }

        return if (superCollapsedBlock!!.shouldBeDisplayed) {
            if (superCollapsedBlock!!.isFirstTime()) formatListWithNewBlock() else formatListWithExistingBlock()
        } else {
            formatLists(messages) { _, _ -> MessageBehavior.DISPLAYED }
        }
    }

    private suspend fun mapRealmMessagesResultWithoutSuperCollapsedBlock(
        message: Message,
    ): Pair<ThreadAdapterItems, MessagesWithoutHeavyData> {
        return formatLists(listOf(message)) { _, _ -> MessageBehavior.DISPLAYED }
    }

    private fun computeFirstIndexAfterBlock(thread: Thread, list: RealmResults<Message>): Int {

        val firstDefaultIndex = list.count() - 2
        val firstUnreadIndex = if (thread.isSeen) null else list.indexOfFirstOrNull { !it.isSeen }
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
    private fun shouldBlockBeDisplayed(messagesCount: Int, firstIndexAfterBlock: Int): Boolean = with(threadState) {
        return superCollapsedBlock?.shouldBeDisplayed == true && // If the Block was hidden, we mustn't ever display it again
                !hasSuperCollapsedBlockBeenClicked.value && // Block hasn't been expanded by the user
                messagesCount >= SUPER_COLLAPSED_BLOCK_MINIMUM_MESSAGES_LIMIT && // At least 5 Messages in the Thread
                firstIndexAfterBlock >= SUPER_COLLAPSED_BLOCK_FIRST_INDEX_LIMIT  // At least 2 Messages in the Block
    }

    private suspend fun formatLists(
        messages: List<Message>,
        computeBehavior: (Int, String) -> MessageBehavior,
    ): Pair<MutableList<Any>, MutableList<Message>> = with(threadState) {

        val items = mutableListOf<Any>()
        val messagesToFetch = mutableListOf<Message>()

        suspend fun addMessage(message: Message) {
            splitBody(message).let {
                items += it
                if (!it.isFullyDownloaded()) messagesToFetch += it
            }
        }

        messages.forEachIndexed { index, message ->
            when (computeBehavior(index, message.uid)) {
                MessageBehavior.DISPLAYED -> addMessage(message)
                MessageBehavior.COLLAPSED -> superCollapsedBlock!!.messagesUids.add(message.uid)
                MessageBehavior.FIRST_AFTER_BLOCK -> {
                    items += superCollapsedBlock!!
                    addMessage(message.apply { shouldHideDivider = true })
                }
            }
        }

        return items to messagesToFetch
    }

    private suspend fun splitBody(message: Message): Message = withContext(ioDispatcher) {
        if (message.body == null) return@withContext message

        message.apply {
            body?.let {
                val isNotAlreadySplit = !threadState.cachedSplitBodies.contains(message.uid)
                if (isNotAlreadySplit) threadState.cachedSplitBodies[message.uid] = MessageBodyUtils.splitContentAndQuote(it)
                splitBody = threadState.cachedSplitBodies[message.uid]
            }
        }

        return@withContext message
    }

    fun displayBatchedMessages(items: List<Any>) = viewModelScope.launch(ioCoroutineContext) {

        tailrec suspend fun sendBatchesRecursively(input: List<Any>, output: MutableList<Any>, batchSize: Int = 1) {

            val batch = input.take(batchSize)
            output.addAll(batch)

            // We need to post a different list each time, because the `submitList` function in AsyncListDiffer
            // won't trigger if we send the same list object (https://stackoverflow.com/questions/49726385).
            batchedMessages.postValue(ArrayList(output))

            if (batch.size < batchSize) return
            delay(DELAY_BETWEEN_EACH_BATCHED_MESSAGES)
            sendBatchesRecursively(input.subList(batchSize, input.size), output)
        }

        sendBatchesRecursively(input = items, output = mutableListOf(), batchSize = 2)
    }

    private fun markThreadAsSeen(thread: Thread) = viewModelScope.launch(ioCoroutineContext) {
        sharedUtils.markAsSeen(mailbox, listOf(thread))
    }

    private fun sendMatomoAndSentryAboutThreadMessagesCount(thread: Thread) {

        val nbMessages = thread.messages.count()

        appContext.trackUserInfo("nbMessagesInThread", nbMessages)

        when (nbMessages) {
            0 -> SentryDebug.sendEmptyThread(thread, "No Message in the Thread when opening it", mailboxContentRealm())
            1 -> appContext.trackUserInfo("oneMessagesInThread")
            else -> appContext.trackUserInfo("multipleMessagesInThread", nbMessages)
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
                //  Ideally, the adapter should process the 1st notify before the 2nd one, but occasionally the order is reversed.
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

        val apiResponses = ApiRepository.deleteMessages(mailbox.uuid, messages.getUids())

        if (apiResponses.atLeastOneSucceeded()) {
            refreshController.refreshThreads(
                refreshMode = RefreshMode.REFRESH_FOLDER_WITH_ROLE,
                mailbox = mailbox,
                folderId = message.folderId,
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

            val results = items.mapNotNull { item ->
                fetchCalendarEvent(item, forceFetch)
            }

            mailboxContentRealm().write {
                results.forEach { (message, apiResponse) ->
                    updateCalendarEvent(message, apiResponse)
                }
            }
        }
    }

    private fun fetchCalendarEvent(item: Any, forceFetch: Boolean): Pair<Message, ApiResponse<CalendarEventResponse>>? {

        if (item !is Message) return null
        val message: Message = item

        if (!message.isFullyDownloaded()) return null // Only process Messages with Attachments already downloaded

        if (!forceFetch) {
            val alreadyTreated = !threadState.treatedMessagesForCalendarEvent.add(message.uid)
            if (alreadyTreated) return null
        }

        val apiResponse = message.calendarAttachment?.resource?.let { resource ->
            ApiRepository.getAttachmentCalendarEvent(resource)
        } ?: return null

        return message to apiResponse
    }

    private fun MutableRealm.updateCalendarEvent(message: Message, apiResponse: ApiResponse<CalendarEventResponse>) {

        if (!apiResponse.isSuccess()) {
            Sentry.captureMessage("Failed loading calendar event") { scope ->
                scope.setExtra("ics attachment mimeType", message.calendarAttachment!!.mimeType)
                scope.setExtra("ics attachment size", message.calendarAttachment!!.size.toString())
                scope.setExtra("error code", apiResponse.error?.code.toString())
            }
            return
        }

        MessageController.updateMessage(message.uid, realm = this) { localMessage ->
            val calendarEventResponse = apiResponse.data!!
            localMessage?.let {
                it.latestCalendarEventResponse = calendarEventResponse
            } ?: run {
                Sentry.captureMessage(
                    "Cannot find message by uid for fetched calendar event inside Realm",
                    SentryLevel.ERROR,
                ) { scope ->
                    scope.setExtra("message.uid", message.uid)
                    val hasUserStoredEvent = calendarEventResponse.hasAssociatedInfomaniakCalendarEvent()
                    scope.setExtra("event has userStoredEvent", hasUserStoredEvent.toString())
                    scope.setExtra("event is canceled", calendarEventResponse.isCanceled.toString())
                    scope.setExtra("event has attachmentEvent", calendarEventResponse.hasAttachmentEvent().toString())
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

    fun updateCurrentThreadUid(mode: ThreadOpeningMode) {
        viewModelScope.launch {
            threadOpeningModeFlow.emit(mode)
        }
    }

    data class SubjectDataResult(
        val thread: Thread?,
        val mergedContacts: MergedContactDictionary?,
        val mailbox: Mailbox?,
    )

    data class QuickActionBarResult(
        val threadUid: String,
        val message: Message,
        val menuId: Int,
    )

    private enum class MessageBehavior {
        DISPLAYED, COLLAPSED, FIRST_AFTER_BLOCK,
    }

    sealed class SnoozeScheduleType(@StringRes val positiveButtonResId: Int) : Parcelable {
        abstract val threadUids: List<String>

        @Parcelize
        data class Snooze(override val threadUids: List<String>) : SnoozeScheduleType(R.string.buttonConfirm) {
            constructor(threadUid: String) : this(listOf(threadUid))
        }

        @Parcelize
        data class Modify(override val threadUids: List<String>) : SnoozeScheduleType(R.string.buttonModify) {
            constructor(threadUid: String) : this(listOf(threadUid))
        }
    }

    enum class ThreadHeaderVisibility {
        MESSAGE_AND_ACTIONS, MESSAGE_ONLY, NONE,
    }

    sealed interface ThreadOpeningMode {
        val threadUid: String?
        fun getMessages(): Flow<Pair<ThreadAdapterItems, MessagesWithoutHeavyData>>
    }

    inner class SingleMessage(val messageUid: String) : ThreadOpeningMode {
        override val threadUid: String? = null

        override fun getMessages(): Flow<Pair<ThreadAdapterItems, MessagesWithoutHeavyData>> {
            return messageController.getMessageAsync(messageUid).mapNotNull {
                it.obj?.let { message -> mapRealmMessagesResultWithoutSuperCollapsedBlock(message) }
            }
        }
    }

    inner class AllMessages(override val threadUid: String) : ThreadOpeningMode {
        override fun getMessages(): Flow<Pair<ThreadAdapterItems, MessagesWithoutHeavyData>> {
            return messageController.getSortedAndNotDeletedMessagesAsync(threadUid)
                ?.map { mapRealmMessagesResult(it.list, threadUid) } ?: emptyFlow()
        }
    }

    companion object {
        private const val SUPER_COLLAPSED_BLOCK_MINIMUM_MESSAGES_LIMIT = 5
        private const val SUPER_COLLAPSED_BLOCK_FIRST_INDEX_LIMIT = 3
        private const val DELAY_BETWEEN_EACH_BATCHED_MESSAGES = 50L
    }
}
