/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.folder

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.text.Spannable
import android.text.TextUtils.TruncateAt
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.google.android.material.card.MaterialCardView
import com.infomaniak.core.legacy.utils.capitalizeFirstChar
import com.infomaniak.core.legacy.utils.context
import com.infomaniak.core.legacy.utils.setMarginsRelative
import com.infomaniak.core.matomo.Matomo.TrackerAction
import com.infomaniak.core.ui.view.toPx
import com.infomaniak.core.common.utils.format
import com.infomaniak.core.common.utils.isInTheFuture
import com.infomaniak.core.common.utils.isThisMonth
import com.infomaniak.core.common.utils.isThisWeek
import com.infomaniak.core.common.utils.isThisYear
import com.infomaniak.core.common.utils.isToday
import com.infomaniak.core.common.utils.isYesterday
import com.infomaniak.dragdropswiperecyclerview.DragDropSwipeAdapter
import com.infomaniak.dragdropswiperecyclerview.DragDropSwipeRecyclerView
import com.infomaniak.mail.MainApplication
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackMultiSelectionEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.ThreadDensity
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.SwipeAction
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.CardviewThreadItemBinding
import com.infomaniak.mail.databinding.ItemBannerWithActionViewBinding
import com.infomaniak.mail.databinding.ItemThreadDateSeparatorBinding
import com.infomaniak.mail.databinding.ItemThreadLoadMoreButtonBinding
import com.infomaniak.mail.ui.main.folder.ThreadListAdapter.ThreadListViewHolder
import com.infomaniak.mail.ui.main.thread.SubjectFormatter
import com.infomaniak.mail.ui.main.thread.SubjectFormatter.TagColor
import com.infomaniak.mail.utils.RealmChangesBinding
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.extensions.formatSubject
import com.infomaniak.mail.utils.extensions.getAttributeColor
import com.infomaniak.mail.utils.extensions.isEmail
import com.infomaniak.mail.utils.extensions.isLastWeek
import com.infomaniak.mail.utils.extensions.postfixWithTag
import com.infomaniak.mail.utils.extensions.toDate
import dagger.hilt.android.qualifiers.ActivityContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch
import splitties.init.appCtx
import javax.inject.Inject
import kotlin.math.abs
import com.google.android.material.R as RMaterial
import com.infomaniak.core.legacy.R as RCore

// TODO: Do we want to extract features from LoaderAdapter (in Core) and put them here? Same for all adapters in the app?
class ThreadListAdapter @Inject constructor(
    @ActivityContext context: Context,
    private val localSettings: LocalSettings,
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
) : DragDropSwipeAdapter<ThreadListItem, ThreadListViewHolder>(mutableListOf()), RealmChangesBinding.OnRealmChanged<Thread> {

    private var formatListJob: Job? = null
    private lateinit var recyclerView: RecyclerView

    init {
        setHasStableIds(true) // See fun getItemId(position: Int) below.
    }

    override val realmAsyncListDiffer: AsyncListDiffer<Thread>? = null

    private val cardCornerRadius by lazy { context.resources.getDimension(R.dimen.alternativeMargin) }
    private val threadMarginCompact by lazy { context.resources.getDimension(RCore.dimen.marginStandardVerySmall).toInt() }
    private val threadMarginOther by lazy { context.resources.getDimension(RCore.dimen.marginStandardSmall).toInt() }
    private val checkMarkSizeLarge by lazy { context.resources.getDimension(R.dimen.userAvatarSizeLarge).toInt() }
    private val checkMarkSizeOther by lazy { context.resources.getDimension(R.dimen.largeIconSize).toInt() }

    private var swipingIsAuthorized: Boolean = true
    private var isLoadMoreDisplayed = false

    private var folderRole: FolderRole? = null
    private var multiSelection: MultiSelectionListener<Thread>? = null
    private var isFolderNameVisible: Boolean = false
    private var callbacks: ThreadListAdapterCallbacks? = null

    private var previousThreadClickedPosition: Int? = null

    //region Tablet mode
    var openedThreadPosition: Int? = null
        private set
    var openedThreadUid: String? = null
        private set
    //endregion

    private val isMultiselectDisabledInThisFolder: Boolean get() = folderRole == FolderRole.SCHEDULED_DRAFTS

    operator fun invoke(
        folderRole: FolderRole?,
        callbacks: ThreadListAdapterCallbacks,
        multiSelection: MultiSelectionListener<Thread>? = null,
        isFolderNameVisible: Boolean = false,
    ) {
        this.folderRole = folderRole
        this.multiSelection = multiSelection
        this.isFolderNameVisible = isFolderNameVisible
        this.callbacks = callbacks
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun getItemViewType(position: Int): Int = runCatchingRealm {
        return when (dataSet[position]) {
            is ThreadListItem.Content -> DisplayType.THREAD.layout
            is ThreadListItem.DateSeparator -> DisplayType.DATE_SEPARATOR.layout
            is ThreadListItem.FlushFolderButton -> DisplayType.FLUSH_FOLDER_BUTTON.layout
            ThreadListItem.LoadMore -> DisplayType.LOAD_MORE_BUTTON.layout
        }
    }.getOrDefault(super.getItemViewType(position))

    // We know this is not perfect since hashCode() can produce collisions, which can cause
    // sneaky issues (see https://stackoverflow.com/a/33084790/4433326),
    // but replacing this with proper asycnListDiffer currently leads to more frequent bugs,
    // including a systematic crash when viewing archived emails, most likely because of
    // how this is handled by the RecyclerView drag'N'drop + swipe library we depend on.
    // We are knowingly making the choice of the lowest impact crash, until a rewrite,
    // or other kind of fixing endeavor takes place.
    // NOTE: The commit that added this comment also removed the `ThreadDiffCallback` class,
    // that might be useful in the future, if a fix is done (rather than a partial rewrite).
    override fun getItemId(position: Int): Long = runCatchingRealm {
        return when (val item = dataSet[position]) {
            is ThreadListItem.Content -> item.thread.uid.hashCode().toLong()
            is ThreadListItem.DateSeparator -> item.title.hashCode().toLong()
            else -> super.getItemId(position)
        }
    }.getOrDefault(super.getItemId(position))

    fun getItemPosition(threadUid: String): Int? {
        return dataSet
            .indexOfFirst { it is ThreadListItem.Content && it.thread.uid == threadUid }
            .takeIf { position -> position != -1 }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadListViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = when (viewType) {
            R.layout.item_thread_date_separator -> ItemThreadDateSeparatorBinding.inflate(layoutInflater, parent, false)
            R.layout.item_banner_with_action_view -> ItemBannerWithActionViewBinding.inflate(layoutInflater, parent, false)
            R.layout.item_thread_load_more_button -> ItemThreadLoadMoreButtonBinding.inflate(layoutInflater, parent, false)
            else -> CardviewThreadItemBinding.inflate(layoutInflater, parent, false)
        }

        return ThreadListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ThreadListViewHolder, position: Int, payloads: MutableList<Any>) = runCatchingRealm {

        val payload = payloads.firstOrNull()
        if (payload !is NotificationType) {
            super.onBindViewHolder(holder, position, payloads)
            return@runCatchingRealm
        }

        if (payload == NotificationType.SELECTED_STATE && holder.itemViewType == DisplayType.THREAD.layout) {
            val binding = holder.binding as CardviewThreadItemBinding
            val thread = (dataSet[position] as ThreadListItem.Content).thread
            binding.updateSelectedUi(thread)
        }
    }.getOrDefault(Unit)

    override fun onBindViewHolder(
        item: ThreadListItem,
        viewHolder: ThreadListViewHolder,
        position: Int
    ) = with(viewHolder.binding) {
        when (getItemViewType(position)) {
            DisplayType.THREAD.layout -> {
                (this as CardviewThreadItemBinding).displayThread((item as ThreadListItem.Content).thread, position)
            }
            DisplayType.DATE_SEPARATOR.layout -> {
                (this as ItemThreadDateSeparatorBinding).displayDateSeparator((item as ThreadListItem.DateSeparator).title)
            }
            DisplayType.FLUSH_FOLDER_BUTTON.layout -> {
                (this as ItemBannerWithActionViewBinding)
                    .displayFlushFolderButton((item as ThreadListItem.FlushFolderButton).folderRole)
            }
            DisplayType.LOAD_MORE_BUTTON.layout -> {
                (this as ItemThreadLoadMoreButtonBinding).displayLoadMoreButton()
            }
        }
    }

    private fun CardviewThreadItemBinding.displayThread(thread: Thread, position: Int) {

        // If we are trying to display an empty Thread, don't. Just delete it.
        if (thread.messages.isEmpty()) {
            // TODO: Find why we are sometimes displaying empty Threads, and fix it instead of just deleting them.
            //  It's possibly because we are out of sync, and the situation will resolve by itself shortly?
            callbacks?.deleteThreadInRealm?.invoke(thread.uid)
            val mainApp = appCtx as MainApplication
            mainApp.globalCoroutineScope.launch(mainApp.ioDispatcher) {
                SentryDebug.sendEmptyThreadBlocking(
                    thread = thread,
                    message = "No Message in the Thread when displaying it in ThreadList",
                    realm = mailboxContentRealm(),
                )
            }
            return
        }

        refreshCachedSelectedPosition(thread.uid, position) // If item changed position, update cached position.
        setupThreadDensityDependentUi()
        displayAvatar(thread)
        displaySpamSpecificUi(thread)
        displayFolderName(thread)

        // This method is only useful for old Threads already stored in Realm, where they
        // could be both answered and forwarded (for new Threads, this is impossible).
        fun computeReplyAndForwardIcon(isAnswered: Boolean, isForwarded: Boolean): Pair<Boolean, Boolean> {
            return when {
                isAnswered -> true to false
                isForwarded -> false to true
                else -> false to false
            }
        }

        with(thread) {
            expeditor.text = formatRecipientNames(computeDisplayedRecipients())
            mailSubject.text = context.formatSubject(subject)
            mailBodyPreview.text = computePreview(context)

            val dateDisplay = computeThreadListDateDisplay(callbacks?.getFeatureFlags?.invoke(), localSettings)
            mailDate.text = dateDisplay.formatThreadDate(context, this)
            mailDateIcon.apply {
                isVisible = dateDisplay.iconRes != null
                dateDisplay.iconRes?.let(::setImageResource)
                dateDisplay.iconColorRes?.let { imageTintList = ColorStateList.valueOf(context.getColor(it)) }
            }
            draftPrefix.isVisible = hasDrafts

            val (isIconReplyVisible, isIconForwardVisible) = computeReplyAndForwardIcon(thread.isAnswered, thread.isForwarded)
            iconReply.isVisible = isIconReplyVisible
            iconForward.isVisible = isIconForwardVisible

            iconAttachment.isVisible = hasAttachable
            iconCalendar.isGone = true // TODO: See with API when we should display this icon
            iconFavorite.isVisible = isFavorite

            val messagesCount = getDisplayedMessages(callbacks?.getFeatureFlags?.invoke(), localSettings).count()
            threadCountText.text = "$messagesCount"
            threadCountCard.isVisible = messagesCount > 1

            if (isSeen) setThreadUiRead() else setThreadUiUnread()
        }

        selectionCardView.setOnClickListener { onThreadClicked(thread, position) }

        multiSelection?.let { listener ->
            selectionCardView.setOnLongClickListener {
                onThreadClickWithAbilityToOpenMultiSelection(thread, listener, TrackerAction.LONG_PRESS)
                true
            }
            expeditorAvatar.apply {
                setOnClickListener {
                    onThreadClickWithAbilityToOpenMultiSelection(thread, listener, TrackerAction.CLICK)
                }
                setOnLongClickListener {
                    onThreadClickWithAbilityToOpenMultiSelection(thread, listener, TrackerAction.LONG_PRESS)
                    true
                }
            }
        }

        updateSelectedUi(thread)
    }

    private fun CardviewThreadItemBinding.displaySpamSpecificUi(thread: Thread) {
        val (mailAddressText, isVisible) = if (folderRole == FolderRole.SPAM) {
            thread.from.first().quotedEmail() to true
        } else {
            "" to false
        }
        mailAddress.text = mailAddressText
        mailAddress.isVisible = isVisible
    }

    private fun CardviewThreadItemBinding.displayFolderName(thread: Thread) {
        val isCompactMode = localSettings.threadDensity == ThreadDensity.COMPACT

        fun setFolderNameVisibility(isVisible: Boolean) {
            folderNameExpandMode.isVisible = !isCompactMode && isVisible
            folderNameCompactMode.isVisible = isCompactMode && isVisible
        }

        val folderNameView = if (isCompactMode) folderNameCompactMode else folderNameExpandMode

        if (shouldDisplayFolderName(thread.folderName)) {
            folderNameView.text = computeFolderName(thread)
            setFolderNameVisibility(isVisible = true)
        } else {
            setFolderNameVisibility(isVisible = false)
        }
    }

    private fun shouldDisplayFolderName(folderName: String) = isFolderNameVisible && folderName.isNotEmpty()

    private fun CardviewThreadItemBinding.computeFolderName(thread: Thread): Spannable {
        return context.postfixWithTag(
            tag = thread.folderName,
            tagColor = TagColor(R.color.folderTagBackground, R.color.folderTagTextColor),
            ellipsizeConfiguration = SubjectFormatter.EllipsizeConfiguration(
                maxWidth = context.resources.getDimension(R.dimen.folderNameTagMaxSize),
                truncateAt = TruncateAt.END,
            ),
        )
    }

    private fun CardviewThreadItemBinding.onThreadClickWithAbilityToOpenMultiSelection(
        thread: Thread,
        listener: MultiSelectionListener<Thread>,
        action: TrackerAction,
    ) {
        if (isMultiselectDisabledInThisFolder) return

        val hasOpened = openMultiSelectionIfClosed(listener, action)
        toggleMultiSelectedThread(thread, shouldUpdateSelectedUi = !hasOpened)
    }

    private fun CardviewThreadItemBinding.openMultiSelectionIfClosed(
        listener: MultiSelectionListener<Thread>,
        action: TrackerAction,
    ): Boolean {
        val shouldOpen = !listener.isEnabled
        if (shouldOpen) {
            trackMultiSelectionEvent(MatomoName.Enable, action)
            listener.isEnabled = true
        }
        return shouldOpen
    }

    private fun refreshCachedSelectedPosition(threadUid: String, position: Int) {
        if (threadUid == openedThreadUid) openedThreadPosition = position
    }

    private fun CardviewThreadItemBinding.onThreadClicked(thread: Thread, position: Int) {
        if (multiSelection?.isEnabled == true) {
            toggleMultiSelectedThread(thread)
        } else {
            callbacks?.onThreadClicked?.invoke(thread)
            // If the Thread is `onlyOneDraft`, we'll directly navigate to the NewMessageActivity.
            // It means that we won't go to the ThreadFragment, so there's no need to select anything.
            if (thread.uid != openedThreadUid && !thread.isOnlyOneDraft) selectNewThread(position, thread.uid)
        }
    }

    fun selectNewThread(newPosition: Int?, threadUid: String?) {

        val oldPosition = openedThreadPosition

        openedThreadPosition = newPosition
        openedThreadUid = threadUid

        if (oldPosition != null && oldPosition < itemCount) notifyItemChanged(oldPosition, NotificationType.SELECTED_STATE)
        if (newPosition != null) {
            previousThreadClickedPosition?.let {
                callbacks?.onPositionClickedChanged?.invoke(newPosition, it)
            }
            previousThreadClickedPosition = newPosition
            notifyItemChanged(newPosition, NotificationType.SELECTED_STATE)
        }
    }

    fun getNextThread(startingThreadIndex: Int, direction: Int): Pair<Thread, Int>? {
        var currentThreadIndex = startingThreadIndex + direction

        while (currentThreadIndex >= 0 && currentThreadIndex <= dataSet.lastIndex) {
            when (val item = dataSet[currentThreadIndex]) {
                is ThreadListItem.Content -> return item.thread to currentThreadIndex
                else -> currentThreadIndex += direction
            }
        }

        return null
    }

    /**
     * Sometimes, we want to select a Thread before even having any Thread in the Adapter (example: coming from a Notification).
     * The selected Thread's UI will update when the Adapter triggers the next batch of `onBindViewHolder()`.
     */
    fun preselectNewThread(threadUid: String?) {
        selectNewThread(newPosition = null, threadUid)
    }

    private fun CardviewThreadItemBinding.toggleMultiSelectedThread(thread: Thread, shouldUpdateSelectedUi: Boolean = true) {
        with(multiSelection!!) {
            selectedItems.apply { if (contains(thread)) remove(thread) else add(thread) }
            publishSelectedItems()
        }
        if (shouldUpdateSelectedUi) updateSelectedUi(thread)
    }

    private fun CardviewThreadItemBinding.updateSelectedUi(targetThread: Thread) {

        val isMultiSelected = multiSelection?.selectedItems?.contains(targetThread) == true
        val isTabletSelected = targetThread.uid == openedThreadUid

        selectionCardView.backgroundTintList = when {
            isMultiSelected -> ColorStateList.valueOf(context.getAttributeColor(RMaterial.attr.colorPrimaryContainer))
            isTabletSelected -> context.getColorStateList(R.color.tabletSelectedBackground)
            else -> context.getColorStateList(R.color.backgroundColor)
        }

        multiSelection?.let {
            with(localSettings) {
                expeditorAvatar.isVisible = !isMultiSelected && threadDensity == ThreadDensity.LARGE
                checkMarkLayout.isVisible = it.isEnabled
                checkedState.isVisible = isMultiSelected
                uncheckedState.isVisible = !isMultiSelected && threadDensity != ThreadDensity.LARGE
            }
        }
    }

    private fun CardviewThreadItemBinding.setupThreadDensityDependentUi() = with(localSettings) {
        val margin = if (threadDensity == ThreadDensity.COMPACT) threadMarginCompact else threadMarginOther
        threadCard.setMarginsRelative(top = margin, bottom = margin)

        expeditorAvatar.isVisible = threadDensity == ThreadDensity.LARGE
        mailBodyPreview.isGone = threadDensity == ThreadDensity.COMPACT

        checkMarkBackground.reshapeToDensity()
        uncheckedState.reshapeToDensity()
    }

    private fun ImageView.reshapeToDensity() {
        val checkMarkSize = if (localSettings.threadDensity == ThreadDensity.LARGE) checkMarkSizeLarge else checkMarkSizeOther
        layoutParams.apply {
            width = checkMarkSize
            height = checkMarkSize
        }
        requestLayout()
    }

    private fun CardviewThreadItemBinding.displayAvatar(thread: Thread) {
        val (recipient, bimi) = thread.computeAvatarRecipient()
        expeditorAvatar.apply {
            loadAvatar(recipient, bimi)

            // Set `isFocusable` here instead of in XML file because setting it in the
            // XML doesn't trigger the overridden `setFocusable(boolean)` in AvatarView.
            isFocusable = false
        }
    }

    private fun CardviewThreadItemBinding.formatRecipientNames(recipients: List<Recipient>): String {

        fun everyone(): String {

            var everyone = ""
            var isFirstMe = true

            recipients.forEach { recipient ->

                val name = recipient.displayedName(context)

                val formattedName = when {
                    recipient.isMe() -> {
                        if (isFirstMe) isFirstMe = false else return@forEach
                        name
                    }
                    name.isEmail() -> name.substringBefore("@")
                    else -> recipient.computeFirstAndLastName().first
                }

                everyone += "${if (everyone.isNotEmpty()) ", " else ""}${formattedName}"
            }

            return everyone
        }

        return when (recipients.count()) {
            0 -> context.getString(R.string.unknownRecipientTitle)
            1 -> recipients.single().displayedName(context)
            else -> everyone()
        }
    }

    private fun CardviewThreadItemBinding.setThreadUiRead() {
        newMailBullet.isInvisible = true

        expeditor.setBodyRegularSecondary()
        mailAddress.setBodyRegularSecondary()
        mailSubject.setBodyRegularSecondary()

        threadCountText.setTextAppearance(R.style.Label_Secondary)
        threadCountCard.strokeColor = context.getColor(R.color.threadCountBorderRead)
    }

    private fun CardviewThreadItemBinding.setThreadUiUnread() {
        newMailBullet.isVisible = true

        expeditor.setBodyMediumPrimary()
        mailAddress.setBodyMediumPrimary()
        mailSubject.setBodyMediumPrimary()

        threadCountText.setTextAppearance(R.style.LabelMedium)
        threadCountCard.strokeColor = context.getColor(R.color.threadCountBorderUnread)
    }

    private fun TextView.setBodyRegularSecondary() {
        setTextAppearance(R.style.Body)
        setTextColor(context.getColor(R.color.secondaryTextColor))
    }

    private fun TextView.setBodyMediumPrimary() {
        setTextAppearance(R.style.BodyMedium)
        setTextColor(context.getColor(R.color.primaryTextColor))
    }

    private fun ItemThreadDateSeparatorBinding.displayDateSeparator(title: String) {
        sectionTitle.text = title
    }

    private fun ItemBannerWithActionViewBinding.displayFlushFolderButton(folderRole: FolderRole) {

        val (hintTextId, buttonTextId) = when (folderRole) {
            FolderRole.SPAM -> R.string.threadListSpamHint to R.string.threadListEmptySpamButton
            FolderRole.TRASH -> R.string.threadListTrashHint to R.string.threadListEmptyTrashButton
            else -> error("We are trying to flush a non-flushable folder.")
        }

        root.apply {
            description = context.getString(hintTextId)

            val buttonText = context.getString(buttonTextId)
            actionButtonText = buttonText
            setOnActionClickListener { callbacks?.onFlushClicked?.invoke(buttonText) }
        }
    }

    private fun ItemThreadLoadMoreButtonBinding.displayLoadMoreButton() {
        loadMoreButton.setOnClickListener {
            if (dataSet.last() is ThreadListItem.LoadMore) dataSet = dataSet.toMutableList().apply { removeLastOrNull() }
            callbacks?.onLoadMoreClicked?.invoke()
        }
    }

    override fun onSwipeStarted(item: ThreadListItem, viewHolder: ThreadListViewHolder) {
        if (item is ThreadListItem.Content) item.thread.updateDynamicIcons()
    }

    private fun Thread.updateDynamicIcons() {
        val featureFlags = callbacks?.getFeatureFlags?.invoke()

        (recyclerView as DragDropSwipeRecyclerView).apply {
            if (localSettings.swipeLeft.canDisplay(folderRole, featureFlags, localSettings)) {
                getSwipeActionUiData(localSettings.swipeLeft)?.let { (colorRes, iconRes) ->
                    behindSwipedItemBackgroundColor = context.getColor(colorRes)
                    behindSwipedItemIconDrawableId = iconRes
                }
            }

            if (localSettings.swipeRight.canDisplay(folderRole, featureFlags, localSettings)) {
                getSwipeActionUiData(localSettings.swipeRight)?.let { (colorRes, iconRes) ->
                    behindSwipedItemBackgroundSecondaryColor = context.getColor(colorRes)
                    behindSwipedItemIconSecondaryDrawableId = iconRes
                }
            }
        }
    }

    private fun Thread.getSwipeActionUiData(swipeAction: SwipeAction): SwipeActionUiData? {
        fun computeDynamicAction(folderRole: FolderRole, swipeAction: SwipeAction) = SwipeActionUiData(
            colorRes = if (folder.role == folderRole) R.color.swipeInbox else swipeAction.colorRes,
            iconRes = if (folder.role == folderRole) R.drawable.ic_drawer_inbox else swipeAction.iconRes,
        )

        return when (swipeAction) {
            SwipeAction.READ_UNREAD -> SwipeActionUiData(
                colorRes = swipeAction.colorRes,
                iconRes = if (isSeen) swipeAction.iconRes else R.drawable.ic_envelope_open,
            )
            SwipeAction.FAVORITE -> SwipeActionUiData(
                colorRes = swipeAction.colorRes,
                iconRes = if (isFavorite) R.drawable.ic_unstar else swipeAction.iconRes,
            )
            SwipeAction.ARCHIVE -> computeDynamicAction(FolderRole.ARCHIVE, swipeAction)
            SwipeAction.SPAM -> computeDynamicAction(FolderRole.SPAM, swipeAction)
            else -> null
        }
    }

    override fun onIsSwiping(
        item: ThreadListItem?,
        viewHolder: ThreadListViewHolder,
        offsetX: Int,
        offsetY: Int,
        canvasUnder: Canvas?,
        canvasOver: Canvas?,
        isUserControlled: Boolean,
    ) = with(viewHolder.binding) {
        val dx = abs(offsetX)
        val progress = dx.toFloat() / root.width

        if (progress < 0.5f && !viewHolder.isSwipedOverHalf) {
            viewHolder.isSwipedOverHalf = true
            root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } else if (progress > 0.5f && viewHolder.isSwipedOverHalf) {
            viewHolder.isSwipedOverHalf = false
            root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
        }

        val cardView = root as MaterialCardView
        val cardElevation = 6.toPx(cardView).toFloat()
        cardView.cardElevation = cappedLinearInterpolator(cardElevation, progress)
        cardView.radius = cappedLinearInterpolator(cardCornerRadius, progress)
    }

    private fun cappedLinearInterpolator(max: Float, progress: Float): Float {
        return if (progress < SWIPE_ANIMATION_THRESHOLD) max * progress / SWIPE_ANIMATION_THRESHOLD else max
    }

    override fun onSwipeAnimationFinished(viewHolder: ThreadListViewHolder) {
        viewHolder.isSwipedOverHalf = false
        callbacks?.onSwipeFinished?.invoke()
        unblockOtherSwipes()
    }

    fun blockOtherSwipes() {
        swipingIsAuthorized = false
    }

    private fun unblockOtherSwipes() {
        swipingIsAuthorized = true
    }

    override fun getViewHolder(itemView: View): ThreadListViewHolder = ThreadListViewHolder { itemView }

    override fun getViewToTouchToStartDraggingItem(item: ThreadListItem, viewHolder: ThreadListViewHolder, position: Int): View? {
        return when (getItemViewType(position)) {
            DisplayType.THREAD.layout -> (viewHolder.binding as CardviewThreadItemBinding).goneHandle
            DisplayType.FLUSH_FOLDER_BUTTON.layout -> (viewHolder.binding as ItemBannerWithActionViewBinding).root.getGoneHandle()
            DisplayType.LOAD_MORE_BUTTON.layout -> (viewHolder.binding as ItemThreadLoadMoreButtonBinding).goneHandle
            else -> null
        }
    }

    override fun canBeSwiped(item: ThreadListItem, viewHolder: ThreadListViewHolder, position: Int): Boolean {
        return getItemViewType(position) == DisplayType.THREAD.layout && swipingIsAuthorized
    }

    override fun updateList(itemList: List<Thread>, lifecycleScope: LifecycleCoroutineScope) {

        formatListJob?.cancel()
        formatListJob = lifecycleScope.launch {

            val formattedList = runCatchingRealm {
                formatList(itemList, recyclerView.context, folderRole, localSettings.threadDensity, scope = this)
            }.getOrDefault(emptyList())

            Dispatchers.Main {
                // Put back "Load more" button if it was already there
                dataSet = if (isLoadMoreDisplayed) formattedList + ThreadListItem.LoadMore else formattedList
            }
        }
    }

    private fun formatList(
        threads: List<Thread>,
        context: Context,
        folderRole: FolderRole?,
        threadDensity: ThreadDensity,
        scope: CoroutineScope,
    ) = mutableListOf<ThreadListItem>().apply {

        if ((folderRole == FolderRole.TRASH || folderRole == FolderRole.SPAM) && threads.isNotEmpty()) {
            add(ThreadListItem.FlushFolderButton(folderRole))
        }

        when {
            threadDensity == ThreadDensity.COMPACT -> {
                cleanMultiSelectionItems(threads, scope)
                addAll(threads.map { ThreadListItem.Content(it) })
            }
            folderRole?.groupMessagesBySection == false -> {
                addAll(threads.map { ThreadListItem.Content(it) })
            }
            else -> {
                var previousSectionTitle = ""
                threads.forEach { thread ->
                    scope.ensureActive()

                    val sectionTitle = thread.getSectionTitle(context)
                    if (sectionTitle != previousSectionTitle) {
                        add(ThreadListItem.DateSeparator(sectionTitle))
                        previousSectionTitle = sectionTitle
                    }

                    add(ThreadListItem.Content(thread))
                }
            }
        }
    }

    private fun cleanMultiSelectionItems(threads: List<Thread>, scope: CoroutineScope) {
        if (multiSelection?.selectedItems?.let(threads::containsAll) == false) {
            multiSelection?.selectedItems?.removeAll {
                scope.ensureActive()
                !threads.contains(it)
            }
        }
    }

    private fun Thread.getSectionTitle(context: Context): String = with(internalDate.toDate()) {
        return when {
            isInTheFuture() -> context.getString(R.string.comingSoon)
            isToday() -> context.getString(R.string.threadListSectionToday)
            isYesterday() -> context.getString(R.string.messageDetailsYesterday)
            isThisWeek() -> context.getString(R.string.threadListSectionThisWeek)
            isLastWeek() -> context.getString(R.string.threadListSectionLastWeek)
            isThisMonth() -> context.getString(R.string.threadListSectionThisMonth)
            isThisYear() -> format(FULL_MONTH).capitalizeFirstChar()
            else -> format(MONTH_AND_YEAR).capitalizeFirstChar()
        }
    }

    fun updateFolderRole(newRole: FolderRole?) {
        folderRole = newRole
    }

    fun updateSelection() {
        notifyItemRangeChanged(0, itemCount, NotificationType.SELECTED_STATE)
    }

    fun updateLoadMore(shouldDisplayLoadMore: Boolean) {

        isLoadMoreDisplayed = shouldDisplayLoadMore

        dataSet.toMutableList().apply {
            val lastItem = lastOrNull() ?: return@apply

            val shouldUpdateDataSet = when {
                shouldDisplayLoadMore && lastItem !is ThreadListItem.LoadMore -> add(ThreadListItem.LoadMore)
                !shouldDisplayLoadMore && lastItem is ThreadListItem.LoadMore -> removeIf { it is ThreadListItem.LoadMore }
                else -> false
            }

            if (shouldUpdateDataSet) dataSet = this
        }
    }

    private enum class DisplayType(val layout: Int) {
        THREAD(R.layout.cardview_thread_item),
        DATE_SEPARATOR(R.layout.item_thread_date_separator),
        FLUSH_FOLDER_BUTTON(R.layout.item_banner_with_action_view),
        LOAD_MORE_BUTTON(R.layout.item_thread_load_more_button),
    }

    enum class NotificationType {
        SELECTED_STATE,
    }

    private data class SwipeActionUiData(@ColorRes val colorRes: Int, @DrawableRes val iconRes: Int?)

    companion object {
        private const val SWIPE_ANIMATION_THRESHOLD = 0.15f

        private const val FULL_MONTH = "MMMM"
        private const val MONTH_AND_YEAR = "MMMM yyyy"
    }

    class ThreadListViewHolder(val binding: ViewBinding) : ViewHolder(binding.root) {
        var isSwipedOverHalf = false
    }
}
