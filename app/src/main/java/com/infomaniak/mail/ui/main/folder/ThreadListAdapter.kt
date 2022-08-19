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
import android.text.format.DateUtils.DAY_IN_MILLIS
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeAdapter
import com.ernestoyaquello.dragdropswiperecyclerview.util.DragDropSwipeDiffCallback
import com.google.android.material.card.MaterialCardView
import com.infomaniak.lib.core.utils.startOfTheDay
import com.infomaniak.lib.core.utils.startOfTheWeek
import com.infomaniak.lib.core.utils.toPx
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.CardviewThreadItemBinding
import com.infomaniak.mail.databinding.ItemThreadDateSeparatorBinding
import com.infomaniak.mail.databinding.ItemThreadSeeAllButtonBinding
import com.infomaniak.mail.utils.ModelsUtils.getFormattedThreadSubject
import com.infomaniak.mail.utils.UiUtils.fillInUserNameAndEmail
import io.realm.kotlin.ext.isValid
import java.util.*
import kotlin.math.abs

class ThreadListAdapter(dataSet: MutableList<Any> = mutableListOf()) :
    DragDropSwipeAdapter<Any, ThreadListAdapter.ThreadViewHolder>(dataSet) {

    @StringRes
    var previousSectionName: Int = -1
    private var displaySeeAllButton = false // TODO: Manage this for intelligent mailbox

    var onEmptyList: (() -> Unit)? = null
    var onThreadClicked: ((thread: Thread) -> Unit)? = null

    override fun getItemViewType(position: Int): Int = when {
        dataSet[position] is String -> DisplayType.DATE_SEPARATOR.layout
        displaySeeAllButton -> DisplayType.SEE_ALL_BUTTON.layout
        else -> DisplayType.THREAD.layout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = when (viewType) {
            R.layout.item_thread_date_separator -> ItemThreadDateSeparatorBinding.inflate(layoutInflater, parent, false)
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

    override fun getBehindSwipedItemSecondaryLayoutId(item: Any, viewHolder: ThreadViewHolder, position: Int): Int? {
        return when (getItemViewType(position)) {
            DisplayType.THREAD.layout -> if ((item as Thread).unseenMessagesCount > 0) R.layout.view_behind_swipe_read else R.layout.view_behind_swipe_unread
            else -> null
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

    fun notifyAdapter(newList: MutableList<Any>) {
        dataSet = newList
    }

    fun formatList(threads: List<Thread>, context: Context): MutableList<Any> {
        previousSectionName = -1
        val formattedList = mutableListOf<Any>()

        // TODO : Use realm to directly get the sorted list instead of sortedByDescending()
        threads.sortedByDescending { it.date }.forEach { thread ->
            val currentItemDateCategory = getDateCategories(thread.date.time).value
            when {
                currentItemDateCategory != previousSectionName -> {
                    previousSectionName = currentItemDateCategory
                    formattedList.add(context.getString(currentItemDateCategory))
                }
                // displaySeeAllButton -> formattedList.add(folder.threadCount - 3) // TODO: Handle Intelligent Mailbox
            }
            formattedList.add(thread)
        }

        return formattedList
    }

    private fun getDateCategories(dateTime: Long): DateFilter {
        return when {
            dateTime >= DateFilter.TODAY.start() -> DateFilter.TODAY
            dateTime >= DateFilter.CURRENT_WEEK.start() -> DateFilter.CURRENT_WEEK
            dateTime >= DateFilter.LAST_WEEK.start() -> DateFilter.LAST_WEEK
            else -> DateFilter.TWO_WEEKS
        }
    }

    private enum class DisplayType(val layout: Int) {
        THREAD(R.layout.cardview_thread_item),
        DATE_SEPARATOR(R.layout.item_thread_date_separator),
        SEE_ALL_BUTTON(R.layout.item_thread_see_all_button),
    }

    private enum class DateFilter(val start: () -> Long, @StringRes val value: Int) {
        TODAY({ Date().startOfTheDay().time }, R.string.threadListSectionToday),
        CURRENT_WEEK({ Date().startOfTheWeek().time }, R.string.threadListSectionThisWeek),
        LAST_WEEK(
            { Date().startOfTheWeek().time - DAY_IN_MILLIS * 7 },
            R.string.threadListSectionLastWeek
        ),
        TWO_WEEKS(
            { Date().startOfTheWeek().time - DAY_IN_MILLIS * 14 },
            R.string.threadListSectionTwoWeeks
        ),
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
    }

    class ThreadViewHolder(val binding: ViewBinding?) : DragDropSwipeAdapter.ViewHolder(binding!!.root) {
        var isSwippedOverHalf = false
    }
}
