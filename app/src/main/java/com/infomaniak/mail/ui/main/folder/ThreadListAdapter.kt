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
package com.infomaniak.mail.ui.main.folder

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
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
import com.infomaniak.mail.data.LocalSettings.ThreadDensity.COMPACT
import com.infomaniak.mail.data.LocalSettings.ThreadDensity.LARGE
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.*
import com.infomaniak.mail.ui.main.folder.ThreadListAdapter.ThreadListViewHolder
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import dagger.hilt.android.qualifiers.ActivityContext
import javax.inject.Inject
import kotlin.math.abs
import com.google.android.material.R as RMaterial
import com.infomaniak.lib.core.R as RCore

// TODO: Do we want to extract features from LoaderAdapter (in Core) and put them here? Same for all adapters in the app?
class ThreadListAdapter @Inject constructor(
    @ActivityContext context: Context,
    private val localSettings: LocalSettings,
) : DragDropSwipeAdapter<Any, ThreadListViewHolder>(mutableListOf()), RealmChangesBinding.OnRealmChanged<Thread> {

    private lateinit var recyclerView: RecyclerView

    override val realmAsyncListDiffer: AsyncListDiffer<Thread>? = null

    private val cardCornerRadius by lazy { context.resources.getDimension(R.dimen.alternativeMargin) }
    private val threadMarginCompact by lazy { context.resources.getDimension(RCore.dimen.marginStandardVerySmall).toInt() }
    private val threadMarginOther by lazy { context.resources.getDimension(RCore.dimen.marginStandardSmall).toInt() }
    private val checkMarkSizeLarge by lazy { context.resources.getDimension(R.dimen.userAvatarSizeLarge).toInt() }
    private val checkMarkSizeOther by lazy { context.resources.getDimension(R.dimen.checkMarkSizeOther).toInt() }

    private var swipingIsAuthorized: Boolean = true
    private var displaySeeAllButton = false // TODO: Manage this for intelligent mailbox
    private var isLoadMoreDisplayed = false

    var onThreadClicked: ((thread: Thread) -> Unit)? = null
    var onFlushClicked: ((dialogTitle: String) -> Unit)? = null
    var onLoadMoreClicked: (() -> Unit)? = null

    private var folderRole: FolderRole? = null
    private lateinit var contacts: MergedContactDictionary
    private lateinit var onSwipeFinished: () -> Unit
    private var multiSelection: MultiSelectionListener<Thread>? = null

    init {
        setHasStableIds(true)
    }

    operator fun invoke(
        folderRole: FolderRole?,
        contacts: MergedContactDictionary,
        onSwipeFinished: () -> Unit,
        multiSelection: MultiSelectionListener<Thread>? = null,
    ) {
        this.folderRole = folderRole
        this.contacts = contacts
        this.onSwipeFinished = onSwipeFinished
        this.multiSelection = multiSelection
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadListViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = when (viewType) {
            R.layout.item_thread_date_separator -> ItemThreadDateSeparatorBinding.inflate(layoutInflater, parent, false)
            R.layout.item_thread_flush_folder_button -> ItemThreadFlushFolderButtonBinding.inflate(layoutInflater, parent, false)
            R.layout.item_thread_load_more_button -> ItemThreadLoadMoreButtonBinding.inflate(layoutInflater, parent, false)
            R.layout.item_thread_see_all_button -> ItemThreadSeeAllButtonBinding.inflate(layoutInflater, parent, false)
            else -> CardviewThreadItemBinding.inflate(layoutInflater, parent, false)
        }

        return ThreadListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ThreadListViewHolder, position: Int, payloads: MutableList<Any>) = runCatchingRealm {
        val payload = payloads.firstOrNull()
        if (payload is NotificationType && holder.itemViewType == DisplayType.THREAD.layout) {
            val binding = holder.binding as CardviewThreadItemBinding
            val thread = dataSet[position] as Thread

            when (payload) {
                NotificationType.AVATAR -> binding.displayAvatar(thread)
                NotificationType.SELECTED_STATE -> binding.updateSelectedState(thread)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }.getOrDefault(Unit)

    override fun onBindViewHolder(item: Any, viewHolder: ThreadListViewHolder, position: Int) = with(viewHolder.binding) {
        when (getItemViewType(position)) {
            DisplayType.THREAD.layout -> (this as CardviewThreadItemBinding).displayThread(item as Thread)
            DisplayType.DATE_SEPARATOR.layout -> (this as ItemThreadDateSeparatorBinding).displayDateSeparator(item as String)
            DisplayType.FLUSH_FOLDER_BUTTON.layout -> (this as ItemThreadFlushFolderButtonBinding).displayFlushFolderButton(item as FolderRole)
            DisplayType.LOAD_MORE_BUTTON.layout -> (this as ItemThreadLoadMoreButtonBinding).displayLoadMoreButton()
            DisplayType.SEE_ALL_BUTTON.layout -> (this as ItemThreadSeeAllButtonBinding).displaySeeAllButton(item)
        }
    }

    override fun getItemViewType(position: Int): Int = runCatchingRealm {
        val item = dataSet[position]
        return when {
            item is String -> DisplayType.DATE_SEPARATOR.layout
            item is FolderRole -> DisplayType.FLUSH_FOLDER_BUTTON.layout
            item is Unit -> DisplayType.LOAD_MORE_BUTTON.layout
            displaySeeAllButton -> DisplayType.SEE_ALL_BUTTON.layout
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

    private fun CardviewThreadItemBinding.displayThread(thread: Thread) {
        setupThreadDensityDependentUi()

        displayAvatar(thread)

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

        selectionCardView.setOnClickListener {
            if (multiSelection?.isEnabled == true) toggleSelection(thread) else onThreadClicked?.invoke(thread)
        }

        multiSelection?.let { listener ->
            updateSelectedState(thread)

            selectionCardView.setOnLongClickListener {
                context.trackMultiSelectionEvent("enable", TrackerAction.LONG_PRESS)
                if (!listener.isEnabled) listener.isEnabled = true
                toggleSelection(thread)
                true
            }
        }
    }

    private fun CardviewThreadItemBinding.toggleSelection(selectedThread: Thread) = with(multiSelection!!) {
        with(selectedItems) {
            if (contains(selectedThread)) remove(selectedThread) else add(selectedThread)
            publishSelectedItems()
        }
        updateSelectedState(selectedThread)
    }

    private fun CardviewThreadItemBinding.updateSelectedState(selectedThread: Thread) {
        // TODO: Modify the UI accordingly
        val isSelected = multiSelection?.selectedItems?.contains(selectedThread) == true
        selectionCardView.backgroundTintList = if (isSelected) {
            ColorStateList.valueOf(context.getAttributeColor(RMaterial.attr.colorPrimaryContainer))
        } else {
            context.getColorStateList(R.color.backgroundColor)
        }

        with(localSettings) {
            expeditorAvatar.isVisible = !isSelected && threadDensity == LARGE
            checkMarkLayout.isVisible = multiSelection?.isEnabled == true

            checkedState.isVisible = isSelected
            uncheckedState.isVisible = threadDensity != LARGE && !isSelected
        }
    }

    private fun CardviewThreadItemBinding.setupThreadDensityDependentUi() = with(localSettings) {
        val margin = if (threadDensity == COMPACT) threadMarginCompact else threadMarginOther
        threadCard.setMarginsRelative(top = margin, bottom = margin)

        expeditorAvatar.isVisible = threadDensity == LARGE
        mailBodyPreview.isGone = threadDensity == COMPACT

        checkMarkBackground.reshapeToDensity()
        uncheckedState.reshapeToDensity()
    }

    private fun ImageView.reshapeToDensity() {
        val checkMarkSize = if (localSettings.threadDensity == LARGE) checkMarkSizeLarge else checkMarkSizeOther
        layoutParams.apply {
            width = checkMarkSize
            height = checkMarkSize
        }
        requestLayout()
    }

    private fun CardviewThreadItemBinding.displayAvatar(thread: Thread) {
        expeditorAvatar.loadAvatar(thread.computeAvatarRecipient(), contacts)
    }

    private fun CardviewThreadItemBinding.formatRecipientNames(recipients: List<Recipient>): String {
        return when (recipients.count()) {
            0 -> context.getString(R.string.unknownRecipientTitle)
            1 -> recipients.single().displayedName(context)
            else -> {
                recipients.joinToString { recipient ->
                    val name = recipient.displayedName(context)
                    when {
                        recipient.isMe() -> name
                        name.isEmail() -> name.substringBefore("@")
                        else -> recipient.computeFirstAndLastName().first
                    }
                }
            }
        }
    }

    private fun CardviewThreadItemBinding.setThreadUiRead() {
        newMailBullet.isInvisible = true
        threadCountText.setTextAppearance(R.style.Label_Secondary)
        threadCountCard.strokeColor = context.getColor(R.color.threadCountBorderRead)
    }

    private fun CardviewThreadItemBinding.setThreadUiUnread() {
        newMailBullet.isVisible = true
        threadCountText.setTextAppearance(R.style.LabelMedium)
        threadCountCard.strokeColor = context.getColor(R.color.threadCountBorderUnread)
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
            setOnClickListener { onFlushClicked?.invoke(buttonText) }
        }
    }

    private fun ItemThreadLoadMoreButtonBinding.displayLoadMoreButton() {
        loadMoreButton.setOnClickListener {
            if (dataSet.last() is Unit) dataSet = dataSet.toMutableList().apply { removeIf { it is Unit } }
            onLoadMoreClicked?.invoke()
        }
    }

    private fun ItemThreadSeeAllButtonBinding.displaySeeAllButton(item: Any) {
        // TODO: Implement when we have intelligent mailbox
        // val threadsNumber = itemsList.size - NUMBER_OF_DISPLAYED_MAILS_OF_FOLDER
        // seeAllText.text = "See all $threadsNumber"
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
        onSwipeFinished()
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

    override fun updateList(itemList: List<Thread>) = runCatchingRealm {
        dataSet = formatList(itemList, recyclerView.context, folderRole, localSettings.threadDensity, isLoadMoreDisplayed)
    }.getOrDefault(Unit)

    fun updateContacts(newContacts: MergedContactDictionary) {
        contacts = newContacts
        notifyItemRangeChanged(0, itemCount, NotificationType.AVATAR)
    }

    fun updateFolderRole(newRole: FolderRole?) {
        folderRole = newRole
    }

    fun updateSelection() {
        notifyItemRangeChanged(0, itemCount, NotificationType.SELECTED_STATE)
    }

    fun updateLoadMore(shouldDisplayLoadMore: Boolean) {

        isLoadMoreDisplayed = shouldDisplayLoadMore

        if (shouldDisplayLoadMore) {
            if (dataSet.lastOrNull() !is Unit) {
                dataSet = dataSet.toMutableList().apply { add(Unit) }
            }
        } else {
            if (dataSet.lastOrNull() is Unit) {
                dataSet = dataSet.toMutableList().apply { removeIf { it is Unit } }
            }
        }
    }

    private enum class DisplayType(val layout: Int) {
        THREAD(R.layout.cardview_thread_item),
        DATE_SEPARATOR(R.layout.item_thread_date_separator),
        FLUSH_FOLDER_BUTTON(R.layout.item_thread_flush_folder_button),
        LOAD_MORE_BUTTON(R.layout.item_thread_load_more_button),
        SEE_ALL_BUTTON(R.layout.item_thread_see_all_button),
    }

    private enum class NotificationType {
        AVATAR,
        SELECTED_STATE,
    }

    private data class SwipeActionUiData(@ColorRes val colorRes: Int, @DrawableRes val iconRes: Int?)

    private companion object {
        const val SWIPE_ANIMATION_THRESHOLD = 0.15f
        val CARD_ELEVATION = 6.toPx().toFloat()

        const val FULL_MONTH = "MMMM"
        const val MONTH_AND_YEAR = "MMMM yyyy"

        fun formatList(
            threads: List<Thread>,
            context: Context,
            folderRole: FolderRole?,
            threadDensity: ThreadDensity,
            isLoadMoreDisplayed: Boolean,
        ) = mutableListOf<Any>().apply {

            if ((folderRole == FolderRole.TRASH || folderRole == FolderRole.SPAM) && threads.isNotEmpty()) {
                add(folderRole)
            }

            if (threadDensity == COMPACT) {
                addAll(threads)
            } else {
                var previousSectionTitle = ""
                threads.forEach { thread ->
                    val sectionTitle = thread.getSectionTitle(context)
                    when {
                        sectionTitle != previousSectionTitle -> {
                            add(sectionTitle)
                            previousSectionTitle = sectionTitle
                        }
                        // displaySeeAllButton -> formattedList.add(folder.threadCount - 3) // TODO: Handle Intelligent Mailbox
                    }
                    add(thread)
                }
            }

            // Add "Load more" button
            if (isLoadMoreDisplayed) add(Unit)
        }

        fun Thread.getSectionTitle(context: Context): String = with(date.toDate()) {
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
    }

    class ThreadListViewHolder(val binding: ViewBinding) : ViewHolder(binding.root) {
        var isSwipedOverHalf = false
    }
}
