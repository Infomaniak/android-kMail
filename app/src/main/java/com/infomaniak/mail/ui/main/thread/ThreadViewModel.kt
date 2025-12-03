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
@file:OptIn(ExperimentalSplittiesApi::class, ExperimentalCoroutinesApi::class)

package com.infomaniak.mail.ui.main.thread

import android.app.Application
import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.infomaniak.core.legacy.utils.SingleLiveEvent
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.emojicomponents.data.ReactionDetail
import com.infomaniak.mail.MatomoMail.MatomoName
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
import com.infomaniak.mail.data.models.message.EmojiReactionState
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.di.DefaultDispatcher
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.SuperCollapsedBlock
import com.infomaniak.mail.ui.main.thread.models.EmojiReactionAuthorUi
import com.infomaniak.mail.ui.main.thread.models.EmojiReactionStateUi
import com.infomaniak.mail.ui.main.thread.models.MessageUi
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.FeatureAvailability
import com.infomaniak.mail.utils.FeatureAvailability.isSnoozeAvailable
import com.infomaniak.mail.utils.MessageBodyUtils
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.coroutineContext
import com.infomaniak.mail.utils.extensions.MergedContactDictionary
import com.infomaniak.mail.utils.extensions.appContext
import com.infomaniak.mail.utils.extensions.atLeastOneSucceeded
import com.infomaniak.mail.utils.extensions.getUids
import com.infomaniak.mail.utils.extensions.indexOfFirstOrNull
import com.infomaniak.mail.views.itemViews.AvatarMergedContactData
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmList
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import splitties.coroutines.suspendLazy
import splitties.experimental.ExperimentalSplittiesApi
import javax.inject.Inject

/* Please note that, for the moment, the logic that uses this list assumes that the items are necessarily messages.
If this were to change, it would be necessary to verify the types of the elements. */
typealias ThreadAdapterItems = List<Any>
typealias MessagesWithoutHeavyData = List<Message>

@HiltViewModel
class ThreadViewModel @Inject constructor(
    application: Application,
    private val avatarMergedContactData: AvatarMergedContactData,
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val mailboxController: MailboxController,
    private val messageController: MessageController,
    private val refreshController: RefreshController,
    private val sharedUtils: SharedUtils,
    private val snackbarManager: SnackbarManager,
    private val threadController: ThreadController,
    private val localSettings: LocalSettings,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    private var fetchMessagesJob: Job? = null
    private var fetchCalendarEventJob: Job? = null

    private val mailbox = viewModelScope.suspendLazy {
        mailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)!!
    }

    private val currentMailboxFlow = mailboxController.getMailboxAsync(
        AccountUtils.currentUserId,
        AccountUtils.currentMailboxId,
    ).mapNotNull { it.obj }

    private val currentMailboxLive = currentMailboxFlow.asLiveData()

    private val featureFlagsFlow = currentMailboxFlow.map { it.featureFlags }

    val threadState = ThreadState()

    @DoNotReadDirectly
    private val _threadOpeningModeFlow: MutableSharedFlow<ThreadOpeningMode> = MutableSharedFlow(replay = 1)
    @OptIn(DoNotReadDirectly::class)
    private val threadOpeningModeFlow = _threadOpeningModeFlow.distinctUntilChangedBy { it.threadUid }

    val threadFlow: Flow<Thread?> = threadOpeningModeFlow
        .map { mode -> mode.threadUid?.let { threadController.getThread(it) } }
        // replay = 1 is needed because the UI relies on this flow to set click listeners. If there's a config change but no
        // replay value, the click listeners won't ever be set
        .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val threadLive = threadOpeningModeFlow
        .mapNotNull { it.threadUid }
        .flatMapLatest(threadController::getThreadAsync)
        .map { it.obj }
        .asLiveData(ioCoroutineContext)

    // To each Message ID, we associate a set of emojis that are added for fake so we can
    // instantly apply clicked emojis without having to wait for the API call to return.
    private val fakeReactions = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    // Each time a message is unsubscribed from, it is added to the list and the list is sent again to update the MessageUi flow.
    // This unsubscribed message flow is collected in the combine to avoid losing the status of the unsubscribed message
    // if something else changes and rebuilds the MessageUi list.
    private val messageUidUnsubscribed = MutableStateFlow<List<String>>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val messagesFlow: Flow<Pair<ThreadAdapterItems, MessagesWithoutHeavyData>> =
        /**
         * Ideally, [ThreadState.hasSuperCollapsedBlockBeenClicked] should be passed directly to [ThreadOpeningMode.getMessages].
         *
         * However, due to the current high level of coupling in this code, direct integration is not feasible.
         * As a workaround, [ThreadState.hasSuperCollapsedBlockBeenClicked] is used solely to retrigger the computation.
         * The [ThreadOpeningMode.getMessages] method will independently determine the appropriate value to use.
         */
        combine(
            threadOpeningModeFlow,
            threadState.hasSuperCollapsedBlockBeenClicked,
            featureFlagsFlow,
            fakeReactions,
            messageUidUnsubscribed,
            transform = { mode, _, featureFlags, fakeReactions, messageUidUnsubscribed ->
                CombineMessageToBuildMessageUi(mode, featureFlags, fakeReactions, messageUidUnsubscribed)
            },
        ).flatMapLatest { (mode, featureFlags, fakeReactions, messageUidUnsubscribed) ->
            val isReactionsAvailable = FeatureAvailability.isReactionsAvailable(featureFlags, localSettings)
            mode.getMessages(featureFlags).mapLatest { (items, messagesToFetch) ->
                items.toUiMessages(fakeReactions, isReactionsAvailable, messageUidUnsubscribed) to messagesToFetch
            }
        }

    val messagesLive: LiveData<Pair<ThreadAdapterItems, MessagesWithoutHeavyData>> = messagesFlow.asLiveData(ioCoroutineContext)

    val messagesAreCollapsiblesFlow: StateFlow<Boolean> = messagesFlow
        .map { (items, _) -> items.count() > 1 }
        .stateIn(viewModelScope, SharingStarted.Lazily, initialValue = false)

    val quickActionBarClicks = SingleLiveEvent<QuickActionBarResult>()

    val failedMessagesUids = SingleLiveEvent<List<String>>()
    val deletedMessagesUids = mutableSetOf<String>()

    // Save the current scheduled date of the draft we're rescheduling to be able to pass it to the schedule bottom sheet
    var reschedulingCurrentlyScheduledEpochMillis: Long? = null

    val isThreadSnoozeHeaderVisible: LiveData<ThreadHeaderVisibility> = Utils
        .waitInitMediator(currentMailboxLive, threadLive)
        .map { (mailbox, thread) ->
            runCatchingRealm {
                when {
                    thread == null || thread.isSnoozed().not() -> ThreadHeaderVisibility.NONE
                    thread.shouldDisplayHeaderActions(mailbox) -> ThreadHeaderVisibility.MESSAGE_AND_ACTIONS
                    else -> ThreadHeaderVisibility.MESSAGE_ONLY
                }
            }.getOrElse { ThreadHeaderVisibility.NONE }
        }

    init {
        viewModelScope.launch {
            threadFlow.filterNotNull().collect { thread ->
                val featureFlags = featureFlagsFlow.first()

                // These 2 will always be empty or not all together at the same time.
                if (threadState.isExpandedMap.isEmpty() || threadState.isThemeTheSameMap.isEmpty()) {
                    val displayedMessages = thread.getDisplayedMessages(featureFlags, localSettings)
                    displayedMessages.forEachIndexed { index, message ->
                        threadState.isExpandedMap[message.uid] = message.shouldBeExpanded(index, displayedMessages.lastIndex)
                        threadState.isThemeTheSameMap[message.uid] = true
                    }
                }

                if (threadState.isFirstOpening) {
                    threadState.isFirstOpening = false
                    sendMatomoAboutThreadMessagesCount(thread, featureFlags)
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

        val newBlock = superCollapsedBlock?.let {
            it.copy(messagesUids = it.messagesUids.toMutableSet())
        } ?: SuperCollapsedBlock()

        val thread = messages.first().threads.single { it.uid == threadUid }
        val firstIndexAfterBlock = computeFirstIndexAfterBlock(thread, messages)
        newBlock.shouldBeDisplayed = shouldBlockBeDisplayed(messages.count(), newBlock, firstIndexAfterBlock)

        val result = if (newBlock.shouldBeDisplayed) {
            if (newBlock.isFirstTime()) {
                formatListWithNewBlock(messages, newBlock, firstIndexAfterBlock)
            } else {
                formatListWithExistingBlock(messages, newBlock)
            }
        } else {
            formatLists(messages, newBlock) { _, _ -> MessageBehavior.DISPLAYED }
        }

        currentCoroutineContext().ensureActive()
        superCollapsedBlock = newBlock

        return result
    }

    private suspend fun mapRealmMessagesResultWithoutSuperCollapsedBlock(
        message: Message,
    ): Pair<ThreadAdapterItems, MessagesWithoutHeavyData> {
        return formatLists(listOf(message), threadState.superCollapsedBlock) { _, _ -> MessageBehavior.DISPLAYED }
    }

    private fun computeFirstIndexAfterBlock(thread: Thread, list: RealmResults<Message>): Int {

        val firstDefaultIndex = list.count() - 2
        val firstUnreadIndex = if (thread.isSeen) null else list.indexOfFirstOrNull { it.hasUnreadContent() }
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
    private fun shouldBlockBeDisplayed(
        messagesCount: Int,
        block: SuperCollapsedBlock,
        firstIndexAfterBlock: Int,
    ): Boolean = block.shouldBeDisplayed && // If the Block was hidden, we mustn't ever display it again
            !threadState.hasSuperCollapsedBlockBeenClicked.value && // Block hasn't been expanded by the user
            messagesCount >= SUPER_COLLAPSED_BLOCK_MINIMUM_MESSAGES_LIMIT && // At least 5 Messages in the Thread
            firstIndexAfterBlock >= SUPER_COLLAPSED_BLOCK_FIRST_INDEX_LIMIT  // At least 2 Messages in the Block

    private suspend fun formatListWithNewBlock(
        messages: RealmResults<Message>,
        block: SuperCollapsedBlock,
        firstIndexAfterBlock: Int,
    ): Pair<ThreadAdapterItems, MessagesWithoutHeavyData> {
        return formatLists(messages, block) { index, _ ->
            when (index) {
                0 -> MessageBehavior.DISPLAYED // First Message
                in 1 until firstIndexAfterBlock -> MessageBehavior.COLLAPSED // All Messages that should go in block
                firstIndexAfterBlock -> MessageBehavior.FIRST_AFTER_BLOCK // First Message not in block
                else -> MessageBehavior.DISPLAYED // All following Messages
            }
        }
    }

    private suspend fun formatListWithExistingBlock(
        messages: RealmResults<Message>,
        block: SuperCollapsedBlock,
    ): Pair<ThreadAdapterItems, MessagesWithoutHeavyData> {

        var isStillInBlock = true
        val previousBlock = block.messagesUids.toSet()

        block.messagesUids.clear()

        return formatLists(messages, block) { index, messageUid ->
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

    private suspend fun formatLists(
        messages: List<Message>,
        block: SuperCollapsedBlock?,
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
            currentCoroutineContext().ensureActive()
            when (computeBehavior(index, message.uid)) {
                MessageBehavior.DISPLAYED -> addMessage(message)
                MessageBehavior.COLLAPSED -> block?.messagesUids?.add(message.uid)
                MessageBehavior.FIRST_AFTER_BLOCK -> {
                    block?.let { items += it }
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

    private fun markThreadAsSeen(thread: Thread) = viewModelScope.launch(ioCoroutineContext) {
        sharedUtils.markAsSeen(mailbox(), listOf(thread))
    }

    private fun sendMatomoAboutThreadMessagesCount(thread: Thread, featureFlags: Mailbox.FeatureFlagSet) {

        val nbMessages = thread.getDisplayedMessages(featureFlags, localSettings).count()

        trackUserInfo(MatomoName.NbMessagesInThread, nbMessages)

        when {
            nbMessages == 1 -> trackUserInfo(MatomoName.OneMessagesInThread)
            nbMessages > 1 -> trackUserInfo(MatomoName.MultipleMessagesInThread, nbMessages)
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

        val apiResponses = ApiRepository.deleteMessages(
            mailboxUuid = mailbox.uuid,
            messagesUids = messages.getUids(),
            alsoMoveReactionMessages = FeatureAvailability.isReactionsAvailable(featureFlagsFlow.first(), localSettings)
        )

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
        val message = messageController.getLastMessageToExecuteAction(thread, featureFlagsFlow.first())
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
                    updateCalendarEventBlocking(message, apiResponse)
                }
            }
        }
    }

    private suspend fun fetchCalendarEvent(item: Any, forceFetch: Boolean): Pair<Message, ApiResponse<CalendarEventResponse>>? {

        if (item !is MessageUi) return null
        val message: Message = item.message

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

    private fun MutableRealm.updateCalendarEventBlocking(message: Message, apiResponse: ApiResponse<CalendarEventResponse>) {

        if (!apiResponse.isSuccess()) {
            Sentry.captureMessage("Failed loading calendar event") { scope ->
                scope.setExtra("ics attachment mimeType", message.calendarAttachment!!.mimeType)
                scope.setExtra("ics attachment size", message.calendarAttachment!!.size.toString())
                scope.setExtra("error code", apiResponse.error?.code.toString())
            }
            return
        }

        MessageController.updateMessageBlocking(message.uid, realm = this) { localMessage ->
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

    @OptIn(DoNotReadDirectly::class)
    fun updateCurrentThreadUid(mode: ThreadOpeningMode) {
        viewModelScope.launch {
            _threadOpeningModeFlow.emit(mode)
        }
    }

    fun fakeEmojiReply(emoji: String, messageUid: String) {
        viewModelScope.launch {
            val messageId = messageController.getMessage(messageUid)?.messageId ?: return@launch

            // TODO: Optimize memory consumption
            fakeReactions.value = fakeReactions.value.toMutableMap().apply {
                set(messageId, getOrDefault(messageId, emptySet()).plus(emoji))
            }
        }
    }

    fun undoFakeEmojiReply(emoji: String, messageUid: String) {
        viewModelScope.launch {
            val messageId = messageController.getMessage(messageUid)?.messageId ?: return@launch

            // If the value isn't present, there's nothing to remove, so we can exit
            if (fakeReactions.value[messageId]?.contains(emoji) != true) return@launch

            // TODO: Optimize memory consumption
            fakeReactions.value = fakeReactions.value.toMutableMap().apply {
                set(messageId, getOrDefault(messageId, emptySet()).minus(emoji))
            }
        }
    }

    fun getLocalEmojiReactionsFor(messageUid: String) = (messagesLive.value
        ?.first
        ?.firstOrNull { it is MessageUi && it.message.uid == messageUid } as? MessageUi)
        ?.emojiReactionsState

    suspend fun getLocalEmojiReactionsDetailsFor(messageUid: String): Map<String, List<ReactionDetail>>? {
        return defaultDispatcher {
            val reactions = getLocalEmojiReactionsFor(messageUid) ?: return@defaultDispatcher null

            val reactionDetails: Map<String, List<ReactionDetail>> = buildMap {
                reactions.keys.forEach { emoji ->
                    val reactionDetail = reactions[emoji]?.computeReactionDetail(
                        emoji = emoji,
                        context = appContext,
                        mergedContactDictionary = avatarMergedContactData.mergedContactLiveData.value ?: emptyMap(),
                        isBimiEnabled = avatarMergedContactData.isBimiEnabledLiveData.value ?: false,
                    )
                    if (reactionDetail != null) put(emoji, reactionDetail)
                }
            }

            reactionDetails
        }
    }

    private suspend fun <E : Any> List<E>.toUiMessages(
        fakeReactions: Map<String, Set<String>>,
        isReactionsAvailable: Boolean,
        messageUidUnsubscribed: List<String>,
    ): List<Any> = map { item ->
        if (item is Message) {
            val localReactions = fakeReactions[item.messageId] ?: emptySet()
            val reactions = item.emojiReactions.toFakedReactions(localReactions)
            MessageUi(
                message = item,
                emojiReactionsState = reactions,
                isReactionsFeatureAvailable = isReactionsAvailable,
                hasUnsubscribeButton = item.hasUnsubscribeLink == true && messageUidUnsubscribed.none { it == item.uid }
            )
        } else {
            item
        }
    }

    private suspend fun RealmList<EmojiReactionState>.toFakedReactions(localReactions: Set<String>): Map<String, EmojiReactionStateUi> {
        val fakeReactions = mutableMapOf<String, EmojiReactionStateUi>()

        // Fake emojis that are already found on the message's reactions
        associateTo(fakeReactions) { state ->
            state.emoji to fakeEmojiReactionState(state, localReactions)
        }

        // Fake emojis that are only present as fake ones but are not present on the message's reactions
        localReactions.forEach { emoji ->
            if (emoji !in fakeReactions) {
                fakeReactions[emoji] = EmojiReactionStateUi(
                    emoji = emoji,
                    authors = listOf(EmojiReactionAuthorUi.FakeMe),
                    hasReacted = true,
                )
            }
        }

        return fakeReactions
    }

    private suspend fun fakeEmojiReactionState(state: EmojiReactionState, localReactions: Set<String>): EmojiReactionStateUi {
        val shouldFake = state.emoji in localReactions && !state.hasReacted

        val authors = state.authors.mapNotNullTo(mutableListOf<EmojiReactionAuthorUi>()) { author ->
            val bimi = messageController.getMessage(author.sourceMessageUid)?.bimi
            author.recipient?.let { recipient -> EmojiReactionAuthorUi.Real(recipient, bimi) }
        }
        val fakedReaction = EmojiReactionStateUi(
            emoji = state.emoji,
            authors = if (shouldFake) authors + EmojiReactionAuthorUi.FakeMe else authors,
            hasReacted = state.hasReacted || shouldFake,
        )

        return fakedReaction
    }

    //region Unsubscribe list diffusion
    fun unsubscribeMessage(message: Message) {
        viewModelScope.launch {
            with(ApiRepository.unsubscribe(message.resource)) {
                if (isSuccess()) {
                    snackbarManager.postValue(appContext.getString(R.string.snackbarUnsubscribeSuccess))
                    messageUidUnsubscribed.value += message.uid
                } else {
                    snackbarManager.postValue(appContext.getString(R.string.snackbarUnsubscribeFailure))
                }
            }
        }
    }
    //endregion

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

    private data class CombineMessageToBuildMessageUi(
        val mode: ThreadOpeningMode,
        val featureFlags: Mailbox.FeatureFlagSet,
        val fakeReactions: Map<String, Set<String>>,
        val messageUidUnsubscribed: List<String>
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
        fun getMessages(featureFlags: Mailbox.FeatureFlagSet): Flow<Pair<ThreadAdapterItems, MessagesWithoutHeavyData>>
    }

    inner class SingleMessage(val messageUid: String) : ThreadOpeningMode {
        override val threadUid: String? = null

        override fun getMessages(featureFlags: Mailbox.FeatureFlagSet): Flow<Pair<ThreadAdapterItems, MessagesWithoutHeavyData>> {
            return messageController.getMessageAsync(messageUid).mapNotNull {
                it.obj?.let { message -> mapRealmMessagesResultWithoutSuperCollapsedBlock(message) }
            }
        }
    }

    inner class AllMessages(override val threadUid: String) : ThreadOpeningMode {
        override fun getMessages(featureFlags: Mailbox.FeatureFlagSet): Flow<Pair<ThreadAdapterItems, MessagesWithoutHeavyData>> {
            return messageController
                .getSortedAndNotDeletedMessagesAsync(threadUid, featureFlags)
                .mapLatest { mapMessagesMutex.withLock { mapRealmMessagesResult(it.list, threadUid) } }
        }
    }

    @RequiresOptIn(
        level = RequiresOptIn.Level.ERROR,
        message = "Do not use this backing field directly, it will emit the same value multiple times"
    )
    @Retention(AnnotationRetention.BINARY)
    @Target(AnnotationTarget.PROPERTY)
    private annotation class DoNotReadDirectly

    companion object {
        private const val SUPER_COLLAPSED_BLOCK_MINIMUM_MESSAGES_LIMIT = 5
        private const val SUPER_COLLAPSED_BLOCK_FIRST_INDEX_LIMIT = 3

        private val mapMessagesMutex = Mutex()
    }
}
