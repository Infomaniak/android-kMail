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
package com.infomaniak.mail.ui.main.folder

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.os.Build
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
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeAdapter
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView
import com.ernestoyaquello.dragdropswiperecyclerview.util.DragDropSwipeDiffCallback
import com.google.android.material.card.MaterialCardView
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.MatomoMail.trackMultiSelectionEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.SwipeAction
import com.infomaniak.mail.data.LocalSettings.ThreadDensity
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.CardviewThreadItemBinding
import com.infomaniak.mail.databinding.ItemThreadDateSeparatorBinding
import com.infomaniak.mail.databinding.ItemThreadFlushFolderButtonBinding
import com.infomaniak.mail.databinding.ItemThreadLoadMoreButtonBinding
import com.infomaniak.mail.ui.main.folder.ThreadListAdapter.ThreadListViewHolder
import com.infomaniak.mail.ui.main.thread.SubjectFormatter
import com.infomaniak.mail.ui.main.thread.SubjectFormatter.TagColor
import com.infomaniak.mail.utils.RealmChangesBinding
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.extensions.*
import dagger.hilt.android.qualifiers.ActivityContext
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.math.abs
import com.google.android.material.R as RMaterial
import com.infomaniak.lib.core.R as RCore

// TODO: Do we want to extract features from LoaderAdapter (in Core) and put them here? Same for all adapters in the app?
class ThreadListAdapter @Inject constructor(
    @ActivityContext context: Context,
    private val localSettings: LocalSettings,
) : DragDropSwipeAdapter<Any, ThreadListViewHolder>(mutableListOf()), RealmChangesBinding.OnRealmChanged<Thread> {

    private var formatListJob: Job? = null
    private lateinit var recyclerView: RecyclerView

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
    private var threadListAdapterCallback: ThreadListAdapterCallback? = null

    private var previousThreadClickedPosition: Int? = null

    //region Tablet mode
    var openedThreadPosition: Int? = null
        private set
    var openedThreadUid: String? = null
        private set

    //endregion

    init {
        setHasStableIds(true)
    }

    operator fun invoke(
        folderRole: FolderRole?,
        threadListAdapterCallback: ThreadListAdapterCallback,
        multiSelection: MultiSelectionListener<Thread>? = null,
        isFolderNameVisible: Boolean = false,
    ) {
        this.folderRole = folderRole
        this.multiSelection = multiSelection
        this.isFolderNameVisible = isFolderNameVisible
        this.threadListAdapterCallback = threadListAdapterCallback
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun getItemViewType(position: Int): Int = runCatchingRealm {
        return when (dataSet[position]) {
            is String -> DisplayType.DATE_SEPARATOR.layout
            is FolderRole -> DisplayType.FLUSH_FOLDER_BUTTON.layout
            is Unit -> DisplayType.LOAD_MORE_BUTTON.layout
            else -> DisplayType.THREAD.layout
        }
    }.getOrDefault(super.getItemViewType(position))

    override fun getItemId(position: Int): Long = runCatchingRealm {
        return when (val item = dataSet[position]) {
            is Thread -> item.uid.hashCode().toLong()
            is String -> item.hashCode().toLong()
            else -> super.getItemId(position)
        }
    }.getOrDefault(super.getItemId(position))

    fun getItemPosition(threadUid: String): Int? {
        return dataSet
            .indexOfFirst { it is Thread && it.uid == threadUid }
            .takeIf { position -> position != -1 }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadListViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = when (viewType) {
            R.layout.item_thread_date_separator -> ItemThreadDateSeparatorBinding.inflate(layoutInflater, parent, false)
            R.layout.item_thread_flush_folder_button -> ItemThreadFlushFolderButtonBinding.inflate(layoutInflater, parent, false)
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
            val thread = dataSet[position] as Thread
            binding.updateSelectedUi(thread)
        }
    }.getOrDefault(Unit)

    override fun onBindViewHolder(item: Any, viewHolder: ThreadListViewHolder, position: Int) = with(viewHolder.binding) {
        when (getItemViewType(position)) {
            DisplayType.THREAD.layout -> (this as CardviewThreadItemBinding).displayThread(item as Thread, position)
            DisplayType.DATE_SEPARATOR.layout -> (this as ItemThreadDateSeparatorBinding).displayDateSeparator(item as String)
            DisplayType.FLUSH_FOLDER_BUTTON.layout -> (this as ItemThreadFlushFolderButtonBinding).displayFlushFolderButton(item as FolderRole)
            DisplayType.LOAD_MORE_BUTTON.layout -> (this as ItemThreadLoadMoreButtonBinding).displayLoadMoreButton()
        }
    }

    private fun CardviewThreadItemBinding.displayThread(thread: Thread, position: Int) {

        refreshCachedSelectedPosition(thread.uid, position) // If item changed position, update cached position.
        setupThreadDensityDependentUi()
        displayAvatar(thread)

        displayFolderName(thread)

        with(thread) {
            expeditor.text = formatRecipientNames(computeDisplayedRecipients())
            mailSubject.text = context.formatSubject(subject)
            mailBodyPreview.text = computePreview().ifBlank { context.getString(R.string.noBodyTitle) }
            mailDate.text = formatDate(context)

            draftPrefix.isVisible = hasDrafts

            iconAttachment.isVisible = hasAttachments
            iconCalendar.isGone = true // TODO: See with API when we should display this icon
            iconFavorite.isVisible = isFavorite

            val messagesCount = messages.count()
            threadCountText.text = messagesCount.toString()
            threadCountCard.isVisible = messagesCount > 1

            if (unseenMessagesCount == 0) setThreadUiRead() else setThreadUiUnread()
        }

        selectionCardView.setOnClickListener { onThreadClicked(thread, position) }

        multiSelection?.let { listener ->
            selectionCardView.setOnLongClickListener {
                onThreadClickWithAbilityToOpenMultiSelection(thread, listener, TrackerAction.LONG_PRESS)
                true
            }
            expeditorAvatar.apply {
                setOnClickListener { onThreadClickWithAbilityToOpenMultiSelection(thread, listener, TrackerAction.CLICK) }
                setOnLongClickListener {
                    onThreadClickWithAbilityToOpenMultiSelection(thread, listener, TrackerAction.LONG_PRESS)
                    true
                }
            }
        }

        updateSelectedUi(thread)
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
        val hasOpened = openMultiSelectionIfClosed(listener, action)
        toggleMultiSelectedThread(thread, shouldUpdateSelectedUi = !hasOpened)
    }

    private fun CardviewThreadItemBinding.openMultiSelectionIfClosed(
        listener: MultiSelectionListener<Thread>,
        action: TrackerAction,
    ): Boolean {
        val shouldOpen = !listener.isEnabled
        if (shouldOpen) {
            context.trackMultiSelectionEvent("enable", action)
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
            previousThreadClickedPosition?.let { previousPosition ->
                threadListAdapterCallback?.onPositionClickedChanged?.invoke(position, previousPosition)
            }

            previousThreadClickedPosition = position

            threadListAdapterCallback?.onThreadClicked?.invoke(thread)
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
        if (newPosition != null) notifyItemChanged(newPosition, NotificationType.SELECTED_STATE)
    }

    fun getNextThread(startingThreadIndex: Int, direction: Int): Pair<Thread, Int>? {
        var currentThreadIndex = startingThreadIndex + direction

        while (currentThreadIndex >= 0 && currentThreadIndex <= dataSet.lastIndex) {
            if (dataSet[currentThreadIndex] is Thread) return dataSet[currentThreadIndex] as Thread to currentThreadIndex
            currentThreadIndex += direction
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
        expeditorAvatar.loadAvatar(thread.computeAvatarRecipient())
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
        mailSubject.setBodyRegularSecondary()

        threadCountText.setTextAppearance(R.style.Label_Secondary)
        threadCountCard.strokeColor = context.getColor(R.color.threadCountBorderRead)
    }

    private fun TextView.setBodyRegularSecondary() {
        setTextAppearance(R.style.Body)
        setTextColor(context.getColor(R.color.secondaryTextColor))
    }

    private fun CardviewThreadItemBinding.setThreadUiUnread() {
        newMailBullet.isVisible = true

        expeditor.setBodyMediumPrimary()
        mailSubject.setBodyMediumPrimary()

        threadCountText.setTextAppearance(R.style.LabelMedium)
        threadCountCard.strokeColor = context.getColor(R.color.threadCountBorderUnread)
    }

    private fun TextView.setBodyMediumPrimary() {
        setTextAppearance(R.style.BodyMedium)
        setTextColor(context.getColor(R.color.primaryTextColor))
    }

    private fun ItemThreadDateSeparatorBinding.displayDateSeparator(title: String) {
        sectionTitle.text = title
    }

    private fun ItemThreadFlushFolderButtonBinding.displayFlushFolderButton(folderRole: FolderRole) {

        val (hintTextId, buttonTextId) = when (folderRole) {
            FolderRole.SPAM -> R.string.threadListSpamHint to R.string.threadListEmptySpamButton
            FolderRole.TRASH -> R.string.threadListTrashHint to R.string.threadListEmptyTrashButton
            else -> throw IllegalStateException("We are trying to flush a non-flushable folder.")
        }

        flushText.text = context.getString(hintTextId)

        flushButton.apply {
            val buttonText = context.getString(buttonTextId)
            text = buttonText
            setOnClickListener { threadListAdapterCallback?.onFlushClicked?.invoke(buttonText) }
        }
    }

    private fun ItemThreadLoadMoreButtonBinding.displayLoadMoreButton() {
        loadMoreButton.setOnClickListener {
            if (dataSet.last() is Unit) dataSet = dataSet.toMutableList().apply { removeLastOrNull() }
            threadListAdapterCallback?.onLoadMoreClicked?.invoke()
        }
    }

    override fun onSwipeStarted(item: Any, viewHolder: ThreadListViewHolder) {
        (item as Thread).updateDynamicIcons()
    }

    private fun Thread.updateDynamicIcons() {

        fun computeDynamicAction(folderRole: FolderRole, swipeAction: SwipeAction) = SwipeActionUiData(
            colorRes = if (folder.role == folderRole) R.color.swipeInbox else swipeAction.colorRes,
            iconRes = if (folder.role == folderRole) R.drawable.ic_drawer_inbox else swipeAction.iconRes,
        )

        fun getSwipeActionUiData(swipeAction: SwipeAction) = when (swipeAction) {
            SwipeAction.READ_UNREAD -> SwipeActionUiData(
                colorRes = swipeAction.colorRes,
                iconRes = if (unseenMessagesCount > 0) R.drawable.ic_envelope_open else swipeAction.iconRes,
            )
            SwipeAction.FAVORITE -> SwipeActionUiData(
                colorRes = swipeAction.colorRes,
                iconRes = if (isFavorite) R.drawable.ic_unstar else swipeAction.iconRes,
            )
            SwipeAction.ARCHIVE -> computeDynamicAction(FolderRole.ARCHIVE, swipeAction)
            SwipeAction.SPAM -> computeDynamicAction(FolderRole.SPAM, swipeAction)
            else -> null
        }

        (recyclerView as DragDropSwipeRecyclerView).apply {
            getSwipeActionUiData(localSettings.swipeLeft)?.let { (colorRes, iconRes) ->
                behindSwipedItemBackgroundColor = context.getColor(colorRes)
                behindSwipedItemIconDrawableId = iconRes
            }

            getSwipeActionUiData(localSettings.swipeRight)?.let { (colorRes, iconRes) ->
                behindSwipedItemBackgroundSecondaryColor = context.getColor(colorRes)
                behindSwipedItemIconSecondaryDrawableId = iconRes
            }
        }
    }

    override fun onIsSwiping(
        item: Any?,
        viewHolder: ThreadListViewHolder,
        offsetX: Int,
        offsetY: Int,
        canvasUnder: Canvas?,
        canvasOver: Canvas?,
        isUserControlled: Boolean,
    ) = with(viewHolder.binding) {
        val dx = abs(offsetX)
        val progress = dx.toFloat() / root.width

        if (progress < 0.5 && !viewHolder.isSwipedOverHalf) {
            viewHolder.isSwipedOverHalf = true
            root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } else if (progress > 0.5 && viewHolder.isSwipedOverHalf) {
            viewHolder.isSwipedOverHalf = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
            } else {
                root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }

        val cardView = root as MaterialCardView
        cardView.cardElevation = cappedLinearInterpolator(CARD_ELEVATION, progress)
        cardView.radius = cappedLinearInterpolator(cardCornerRadius, progress)
    }

    private fun cappedLinearInterpolator(max: Float, progress: Float): Float {
        return if (progress < SWIPE_ANIMATION_THRESHOLD) max * progress / SWIPE_ANIMATION_THRESHOLD else max
    }

    override fun onSwipeAnimationFinished(viewHolder: ThreadListViewHolder) {
        viewHolder.isSwipedOverHalf = false
        threadListAdapterCallback?.onSwipeFinished?.invoke()
        unblockOtherSwipes()
    }

    fun blockOtherSwipes() {
        swipingIsAuthorized = false
    }

    private fun unblockOtherSwipes() {
        swipingIsAuthorized = true
    }

    override fun getViewHolder(itemView: View): ThreadListViewHolder = ThreadListViewHolder { itemView }

    override fun getViewToTouchToStartDraggingItem(item: Any, viewHolder: ThreadListViewHolder, position: Int): View? {
        return when (getItemViewType(position)) {
            DisplayType.THREAD.layout -> (viewHolder.binding as CardviewThreadItemBinding).goneHandle
            DisplayType.FLUSH_FOLDER_BUTTON.layout -> (viewHolder.binding as ItemThreadFlushFolderButtonBinding).goneHandle
            DisplayType.LOAD_MORE_BUTTON.layout -> (viewHolder.binding as ItemThreadLoadMoreButtonBinding).goneHandle
            else -> null
        }
    }

    override fun canBeSwiped(item: Any, viewHolder: ThreadListViewHolder, position: Int): Boolean {
        return getItemViewType(position) == DisplayType.THREAD.layout && swipingIsAuthorized
    }

    override fun createDiffUtil(oldList: List<Any>, newList: List<Any>): DragDropSwipeDiffCallback<Any>? = null

    override fun updateList(itemList: List<Thread>, lifecycleScope: LifecycleCoroutineScope) {

        formatListJob?.cancel()
        formatListJob = lifecycleScope.launch {

            val formattedList = runCatchingRealm {
                formatList(itemList, recyclerView.context, folderRole, localSettings.threadDensity, scope = this)
            }.getOrDefault(emptyList())

            Dispatchers.Main {
                // Put back "Load more" button if it was already there
                dataSet = if (isLoadMoreDisplayed) formattedList + Unit else formattedList
            }
        }
    }

    private fun formatList(
        threads: List<Thread>,
        context: Context,
        folderRole: FolderRole?,
        threadDensity: ThreadDensity,
        scope: CoroutineScope,
    ) = mutableListOf<Any>().apply {

        if ((folderRole == FolderRole.TRASH || folderRole == FolderRole.SPAM) && threads.isNotEmpty()) {
            add(folderRole)
        }

        if (threadDensity == ThreadDensity.COMPACT) {
            if (multiSelection?.selectedItems?.let(threads::containsAll) == false) {
                multiSelection?.selectedItems?.removeAll {
                    scope.ensureActive()
                    !threads.contains(it)
                }
            }
            addAll(threads)
        } else {
            var previousSectionTitle = ""
            threads.forEach { thread ->
                scope.ensureActive()
                val sectionTitle = thread.getSectionTitle(context)
                when {
                    sectionTitle != previousSectionTitle -> {
                        add(sectionTitle)
                        previousSectionTitle = sectionTitle
                    }
                }
                add(thread)
            }
        }
    }

    private fun Thread.getSectionTitle(context: Context): String = with(date.toDate()) {
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
                shouldDisplayLoadMore && lastItem !is Unit -> add(Unit)
                !shouldDisplayLoadMore && lastItem is Unit -> removeIf { it is Unit }
                else -> false
            }

            if (shouldUpdateDataSet) dataSet = this
        }
    }

    private enum class DisplayType(val layout: Int) {
        THREAD(R.layout.cardview_thread_item),
        DATE_SEPARATOR(R.layout.item_thread_date_separator),
        FLUSH_FOLDER_BUTTON(R.layout.item_thread_flush_folder_button),
        LOAD_MORE_BUTTON(R.layout.item_thread_load_more_button),
    }

    enum class NotificationType {
        SELECTED_STATE,
    }

    private data class SwipeActionUiData(@ColorRes val colorRes: Int, @DrawableRes val iconRes: Int?)

    companion object {
        private const val SWIPE_ANIMATION_THRESHOLD = 0.15f
        private val CARD_ELEVATION = 6.toPx().toFloat()

        private const val FULL_MONTH = "MMMM"
        private const val MONTH_AND_YEAR = "MMMM yyyy"
    }

    class ThreadListViewHolder(val binding: ViewBinding) : ViewHolder(binding.root) {
        var isSwipedOverHalf = false
    }
}
