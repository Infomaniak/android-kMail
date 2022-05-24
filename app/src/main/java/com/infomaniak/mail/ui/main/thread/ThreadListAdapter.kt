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
package com.infomaniak.mail.ui.main.thread

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.views.ViewHolder
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.ItemThreadBinding
import com.infomaniak.mail.databinding.ItemThreadDateSeparatorBinding
import com.infomaniak.mail.databinding.ItemThreadSeeAllButtonBinding
import com.infomaniak.mail.utils.ModelsUtils.displayedSubject
import com.infomaniak.mail.utils.isToday
import com.infomaniak.mail.utils.toDate
import java.util.*

class ThreadListAdapter : RecyclerView.Adapter<ViewHolder>() { // TODO: Use LoaderAdapter from Core instead?

    private var itemsList: ArrayList<Any> = ArrayList()

    @StringRes
    var previousSectionName: Int = -1
    private var displaySeeAllButton = false // TODO manage this for intelligent mailbox

    var onEmptyList: (() -> Unit)? = null
    var onThreadClicked: ((thread: Thread) -> Unit)? = null

    override fun getItemCount(): Int = itemsList.size

    override fun getItemViewType(position: Int): Int {
        return when {
            itemsList[position] is String -> DisplayType.DATE_SEPARATOR.layout
            displaySeeAllButton -> DisplayType.SEE_ALL_BUTTON.layout
            else -> DisplayType.THREAD.layout
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = when (viewType) {
            R.layout.item_thread_date_separator -> ItemThreadDateSeparatorBinding.inflate(layoutInflater, parent, false)
            R.layout.item_thread_see_all_button -> ItemThreadSeeAllButtonBinding.inflate(layoutInflater, parent, false)
            else -> ItemThreadBinding.inflate(layoutInflater, parent, false)
        }
        return BindingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val viewHolder = holder as BindingViewHolder<*>
        when (getItemViewType(position)) {
            DisplayType.DATE_SEPARATOR.layout -> {
                (viewHolder.binding as ItemThreadDateSeparatorBinding).displayDateSeparator(position)
            }
            DisplayType.SEE_ALL_BUTTON.layout -> {
                (viewHolder.binding as ItemThreadSeeAllButtonBinding).displaySeeAllButton()
            }
            DisplayType.THREAD.layout -> {
                (viewHolder.binding as ItemThreadBinding).displayThread(position)
            }
        }
    }

    private fun ItemThreadDateSeparatorBinding.displayDateSeparator(position: Int) {
        sectionTitle.text = itemsList[position] as String
        sectionTitle.setTextAppearance(R.style.Callout)
        sectionTitle.setTextColor(root.context.getColor(R.color.sectionHeaderTextColor))
    }

    private fun ItemThreadSeeAllButtonBinding.displaySeeAllButton() {
        seeAllText.append(" (${itemsList.size - NUMBER_OF_DISPLAYED_MAILS_OF_FOLDER})")
    }

    private fun ItemThreadBinding.displayThread(position: Int) {
        val thread = itemsList[position] as Thread
        val context = itemThread.context

        expeditor.text = thread.from[0].name.ifEmpty { thread.from[0].email }
        mailSubject.text = thread.subject.displayedSubject(context)

        mailDate.text = formatDate(thread.date?.toDate() ?: Date(0))

        iconAttachment.isVisible = thread.hasAttachments
        iconCalendar.isGone = true // TODO: See with API when we should display this icon
        iconFavorite.isVisible = thread.flagged

        if (thread.unseenMessagesCount == 0) setThreadUiRead() else setThreadUiUnread()

        itemThread.setOnClickListener { onThreadClicked?.invoke(thread) }
    }

    private fun ItemThreadBinding.setThreadUiRead() {
        newMailBullet.isGone = true
        expeditor.setTextAppearance(R.style.H2_Secondary)
        mailSubject.setTextAppearance(R.style.Body_Secondary)
        mailDate.setTextAppearance(R.style.Callout_Secondary)
        iconAttachment.setDrawableColor(root.context, R.color.secondaryTextColor)
        iconCalendar.setDrawableColor(root.context, R.color.secondaryTextColor)
    }

    private fun ItemThreadBinding.setThreadUiUnread() {
        newMailBullet.isVisible = true
        expeditor.setTextAppearance(R.style.H2)
        mailSubject.setTextAppearance(R.style.H3)
        mailDate.setTextAppearance(R.style.Callout_Strong)
        iconAttachment.setDrawableColor(root.context, R.color.primaryTextColor)
        iconCalendar.setDrawableColor(root.context, R.color.primaryTextColor)
    }

    private fun formatDate(date: Date): String = with(date) {
        when {
            isToday() -> format(FORMAT_DATE_HOUR_MINUTE)
            year() == Date().year() -> format(FORMAT_DATE_SHORT_DAY_ONE_CHAR)
            else -> format(FORMAT_DATE_CLEAR_MONTH_DAY_ONE_CHAR)
        }
    }

    fun formatList(threads: List<Thread>, context: Context): ArrayList<Any> {
        previousSectionName = -1
        val formattedList = arrayListOf<Any>()

        threads.sortedByDescending { it.date }.forEach { thread ->
            val currentItemDateCategory = getDateCategories(thread.date?.toDate()?.time ?: 0L).value
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
            dateTime >= DateFilter.TODAY.start -> DateFilter.TODAY
            dateTime >= DateFilter.CURRENT_WEEK.start -> DateFilter.CURRENT_WEEK
            dateTime >= DateFilter.LAST_WEEK.start -> DateFilter.LAST_WEEK
            else -> DateFilter.TWO_WEEKS
        }
    }

    private fun ImageView.setDrawableColor(context: Context, @ColorRes color: Int) = drawable.setTint(context.getColor(color))

    fun notifyAdapter(newList: ArrayList<Any>) {
        DiffUtil.calculateDiff(ThreadListDiffCallback(itemsList, newList)).dispatchUpdatesTo(this)
        itemsList = newList
    }

    private enum class DisplayType(val layout: Int) {
        THREAD(R.layout.item_thread),
        DATE_SEPARATOR(R.layout.item_thread_date_separator),
        SEE_ALL_BUTTON(R.layout.item_thread_see_all_button),
    }

    private enum class DateFilter(val start: Long, @StringRes val value: Int) {
        TODAY(Date().startOfTheDay().time, R.string.threadListSectionToday),
        CURRENT_WEEK(Date().startOfTheWeek().time, R.string.threadListSectionThisWeek),
        LAST_WEEK(Date().startOfTheWeek().time - DAY_LENGTH_MS * 7, R.string.threadListSectionLastWeek),
        TWO_WEEKS(Date().startOfTheWeek().time - DAY_LENGTH_MS * 14, R.string.threadListSectionTwoWeeks),
    }

    private class ThreadListDiffCallback(
        private val oldList: List<Any>,
        private val newList: List<Any>,
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldIndex: Int, newIndex: Int): Boolean {
            val oldItem = oldList[oldIndex]
            val newItem = newList[newIndex]
            return when {
                oldItem.javaClass.name != newItem.javaClass.name -> false // Both aren't the same type
                oldItem is String && newItem is String -> oldItem == newItem // Both are Strings
                else -> (oldItem as Thread).uid == (newItem as Thread).uid // Both are Threads
            }
        }

        override fun areContentsTheSame(oldIndex: Int, newIndex: Int): Boolean {
            val oldItem = oldList[oldIndex]
            val newItem = newList[newIndex]
            return when {
                oldItem.javaClass.name != newItem.javaClass.name -> false // Both aren't the same type
                oldItem is String && newItem is String -> oldItem == newItem // Both are Strings
                else -> { // Both are Threads
                    if ((oldItem as Thread).uid == (newItem as Thread).uid) { // Same items
                        oldItem.subject == newItem.subject &&
                                oldItem.messagesCount == newItem.messagesCount &&
                                oldItem.unseenMessagesCount == newItem.unseenMessagesCount
                        // TODO: Add other fields checks
                    } else { // Not same items
                        false
                    }
                }
            }
        }
    }

    companion object {
        const val NUMBER_OF_DISPLAYED_MAILS_OF_FOLDER = 3
        const val DAY_LENGTH_MS = 1_000 * 3_600 * 24
    }
}
