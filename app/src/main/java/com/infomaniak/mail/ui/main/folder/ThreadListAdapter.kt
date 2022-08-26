/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import android.graphics.Canvas
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeAdapter
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView
import com.ernestoyaquello.dragdropswiperecyclerview.util.DragDropSwipeDiffCallback
import com.google.android.material.card.MaterialCardView
import com.infomaniak.lib.core.utils.capitalizeFirstChar
import com.infomaniak.lib.core.utils.format
import com.infomaniak.lib.core.utils.toPx
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.CardviewThreadItemBinding
import com.infomaniak.mail.databinding.ItemThreadDateSeparatorBinding
import com.infomaniak.mail.databinding.ItemThreadEmptySpaceBinding
import com.infomaniak.mail.databinding.ItemThreadSeeAllButtonBinding
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.ModelsUtils.getFormattedThreadSubject
import com.infomaniak.mail.utils.UiUtils.fillInUserNameAndEmail
import io.realm.kotlin.ext.isValid
import kotlin.math.abs

class ThreadListAdapter(dataSet: MutableList<Any> = mutableListOf()) :
    DragDropSwipeAdapter<Any, ThreadListAdapter.ThreadViewHolder>(dataSet) {

    private var parentRecycler: DragDropSwipeRecyclerView? = null

    private var previousSectionTitle: String = ""
    private var displaySeeAllButton = false // TODO: Manage this for intelligent mailbox

    var onEmptyList: (() -> Unit)? = null
    var onThreadClicked: ((thread: Thread) -> Unit)? = null

    override fun getItemViewType(position: Int): Int = when {
        dataSet[position] is String -> DisplayType.DATE_SEPARATOR.layout
        dataSet[position] is Unit -> DisplayType.EMPTY_SPACE.layout
        displaySeeAllButton -> DisplayType.SEE_ALL_BUTTON.layout
        else -> DisplayType.THREAD.layout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = when (viewType) {
            R.layout.item_thread_date_separator -> ItemThreadDateSeparatorBinding.inflate(layoutInflater, parent, false)
            R.layout.item_thread_empty_space -> ItemThreadEmptySpaceBinding.inflate(layoutInflater, parent, false)
            R.layout.item_thread_see_all_button -> ItemThreadSeeAllButtonBinding.inflate(layoutInflater, parent, false)
            else -> CardviewThreadItemBinding.inflate(layoutInflater, parent, false)
        }

        return ThreadViewHolder(binding)
    }

    override fun onBindViewHolder(item: Any, viewHolder: ThreadViewHolder, position: Int): Unit = with(viewHolder.binding!!) {
        when (getItemViewType(position)) {
            DisplayType.THREAD.layout -> (this as CardviewThreadItemBinding).displayThread(item as Thread)
            DisplayType.DATE_SEPARATOR.layout -> (this as ItemThreadDateSeparatorBinding).displayDateSeparator(item as String)
            DisplayType.SEE_ALL_BUTTON.layout -> (this as ItemThreadSeeAllButtonBinding).displaySeeAllButton(item)
        }
    }

    private fun CardviewThreadItemBinding.displayThread(thread: Thread): Unit = with(thread) {
        if (!isValid()) return // TODO: remove this when realm management will be refactored and stable

        fillInUserNameAndEmail(from.first(), expeditor)
        mailSubject.text = subject.getFormattedThreadSubject(root.context)
        mailBodyPreview.text = messages.last().preview.ifBlank { root.context.getString(R.string.noBodyTitle) }

        mailDate.text = formatDate(root.context)

        iconAttachment.isVisible = hasAttachments
        iconCalendar.isGone = true // TODO: See with API when we should display this icon
        iconFavorite.isVisible = isFavorite

        threadCount.text = messagesCount.toString()
        threadCountCardview.isVisible = messages.count() > 1

        if (unseenMessagesCount == 0) setThreadUiRead() else setThreadUiUnread()

        root.setOnClickListener {
            onThreadClicked?.invoke(this@with)
        }
    }

    private fun CardviewThreadItemBinding.setThreadUiRead() {
        newMailBullet.isGone = true
        expeditor.setTextAppearance(R.style.H2_Secondary)
        mailSubject.setTextAppearance(R.style.Body_Secondary)
        mailDate.setTextAppearance(R.style.Callout_Secondary)
        iconAttachment.setDrawableColor(root.context, R.color.secondaryTextColor)
        iconCalendar.setDrawableColor(root.context, R.color.secondaryTextColor)
    }

    private fun CardviewThreadItemBinding.setThreadUiUnread() {
        newMailBullet.isVisible = true
        expeditor.setTextAppearance(R.style.H2)
        mailSubject.setTextAppearance(R.style.H3)
        mailDate.setTextAppearance(R.style.Callout_Strong)
        iconAttachment.setDrawableColor(root.context, R.color.primaryTextColor)
        iconCalendar.setDrawableColor(root.context, R.color.primaryTextColor)
    }

    private fun ImageView.setDrawableColor(context: Context, @ColorRes color: Int) = drawable.setTint(context.getColor(color))

    private fun ItemThreadDateSeparatorBinding.displayDateSeparator(title: String) {
        sectionTitle.apply {
            text = title
            setTextAppearance(R.style.Callout)
            setTextColor(context.getColor(R.color.sectionHeaderTextColor))
        }
    }

    private fun ItemThreadSeeAllButtonBinding.displaySeeAllButton(item: Any) {
        // TODO: Implement when we have intelligent mailbox
        // val threadsNumber = itemsList.size - NUMBER_OF_DISPLAYED_MAILS_OF_FOLDER
        // seeAllText.text = "See all $threadsNumber"
    }

    override fun onSwipeStarted(item: Any, viewHolder: ThreadViewHolder) {
        parentRecycler?.apply {
            behindSwipedItemIconSecondaryDrawableId = if ((item as Thread).unseenMessagesCount > 0) {
                R.drawable.ic_envelope_open
            } else {
                R.drawable.ic_envelope
            }
        }
    }

    override fun onIsSwiping(
        item: Any?,
        viewHolder: ThreadViewHolder,
        offsetX: Int,
        offsetY: Int,
        canvasUnder: Canvas?,
        canvasOver: Canvas?,
        isUserControlled: Boolean
    ): Unit = with(viewHolder.binding!!)
    {
        val dx = abs(offsetX)
        val progress = dx.toFloat() / root.width

        if (progress < 0.5 && !viewHolder.isSwippedOverHalf) {
            viewHolder.isSwippedOverHalf = true
            root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } else if (progress > 0.5 && viewHolder.isSwippedOverHalf) {
            viewHolder.isSwippedOverHalf = false
            root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
        }

        val cardView = root as MaterialCardView
        cardView.cardElevation = cappedLinearInterpolator(CARD_ELEVATION, progress)
        cardView.radius = cappedLinearInterpolator(CARD_CORNER_RADIUS, progress)
    }

    private fun cappedLinearInterpolator(max: Float, progress: Float): Float {
        return if (progress < SWIPE_ANIMATION_THRESHOLD) {
            max * progress / SWIPE_ANIMATION_THRESHOLD
        } else {
            max
        }
    }

    override fun onSwipeAnimationFinished(viewHolder: ThreadViewHolder) {
        viewHolder.isSwippedOverHalf = false
    }

    override fun getViewHolder(itemView: View): ThreadViewHolder = ThreadViewHolder(null)

    override fun getViewToTouchToStartDraggingItem(item: Any, viewHolder: ThreadViewHolder, position: Int): View? {
        return when (getItemViewType(position)) {
            DisplayType.THREAD.layout -> (viewHolder.binding as CardviewThreadItemBinding).goneHandle
            else -> null
        }
    }

    override fun canBeSwiped(item: Any, viewHolder: ThreadViewHolder, position: Int): Boolean {
        return getItemViewType(position) == DisplayType.THREAD.layout
    }

    override fun createDiffUtil(oldList: List<Any>, newList: List<Any>): DragDropSwipeDiffCallback<Any> {
        return ThreadListDiffCallback(oldList, newList)
    }

    fun notifyAdapter(newList: MutableList<Any>, recyclerView: DragDropSwipeRecyclerView) {
        dataSet = newList
        parentRecycler = recyclerView
    }

    fun formatList(threads: List<Thread>, context: Context): MutableList<Any> {
        previousSectionTitle = ""
        val formattedList = mutableListOf<Any>()

        // TODO : Use realm to directly get the sorted list instead of sortedByDescending()
        threads.sortedByDescending { it.date }.forEachIndexed { index, thread ->
            val sectionTitle = thread.getSectionTitle(context)
            when {
                sectionTitle != previousSectionTitle -> {
                    if (index != 0) formattedList.add(Unit) // Adds a space before the next date separator
                    formattedList.add(sectionTitle)
                    previousSectionTitle = sectionTitle
                }
                // displaySeeAllButton -> formattedList.add(folder.threadCount - 3) // TODO: Handle Intelligent Mailbox
            }
            formattedList.add(thread)
        }

        return formattedList
    }

    private fun Thread.getSectionTitle(context: Context): String = with(date) {
        return when {
            isToday() -> context.getString(R.string.threadListSectionToday)
            isYesterday() -> context.getString(R.string.messageDetailsYesterday)
            isThisWeek() -> context.getString(R.string.threadListSectionThisWeek)
            isLastWeek() -> context.getString(R.string.threadListSectionLastWeek)
            isThisMonth() -> context.getString(R.string.threadListSectionThisMonth)
            isThisYear() -> format(FULL_MONTH).capitalizeFirstChar()
            else -> format(MONTH_AND_YEAR).capitalizeFirstChar()
        }
    }

    private enum class DisplayType(val layout: Int) {
        THREAD(R.layout.cardview_thread_item),
        DATE_SEPARATOR(R.layout.item_thread_date_separator),
        EMPTY_SPACE(R.layout.item_thread_empty_space),
        SEE_ALL_BUTTON(R.layout.item_thread_see_all_button),
    }

    private class ThreadListDiffCallback(
        private val oldList: List<Any>,
        private val newList: List<Any>,
    ) : DragDropSwipeDiffCallback<Any>(oldList, newList) {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun isSameContent(oldItem: Any, newItem: Any): Boolean {
            return when {
                oldItem.javaClass.name != newItem.javaClass.name -> false // Both aren't the same type
                oldItem is String && newItem is String -> oldItem == newItem // Both are Strings
                else -> { // Both are Threads
                    if ((oldItem as Thread).uid == (newItem as Thread).uid) { // Same items
                        oldItem.subject == newItem.subject &&
                                oldItem.messagesCount == newItem.messagesCount &&
                                oldItem.unseenMessagesCount == newItem.unseenMessagesCount &&
                                oldItem.isFavorite == newItem.isFavorite
                        // TODO: Add other fields checks
                    } else { // Not same items
                        false
                    }
                }
            }
        }

        override fun isSameItem(oldItem: Any, newItem: Any): Boolean {
            return when {
                oldItem is String && newItem is String -> oldItem == newItem // Both are Strings
                oldItem !is Thread || newItem !is Thread -> false // Both aren't Threads
                oldItem.isValid() && newItem.isValid() -> oldItem.uid == newItem.uid // Both are valid
                else -> false
            }
        }
    }

    companion object {
        const val SWIPE_ANIMATION_THRESHOLD = 0.15f
        private val CARD_ELEVATION = 6.toPx().toFloat()
        private val CARD_CORNER_RADIUS = 12.toPx().toFloat()

        private const val FULL_MONTH = "MMMM"
        private const val MONTH_AND_YEAR = "MMMM yyyy"
    }

    class ThreadViewHolder(val binding: ViewBinding?) : DragDropSwipeAdapter.ViewHolder(binding!!.root) {
        var isSwippedOverHalf = false
    }
}
