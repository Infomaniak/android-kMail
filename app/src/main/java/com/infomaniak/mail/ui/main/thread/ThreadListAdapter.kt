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
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.views.ViewHolder
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.ItemThreadBinding
import com.infomaniak.mail.databinding.SeeAllRecyclerButtonBinding
import com.infomaniak.mail.databinding.ThreadListRecyclerSectionBinding
import com.infomaniak.mail.utils.isToday
import com.infomaniak.mail.utils.toDate
import java.util.*

class ThreadListAdapter : RecyclerView.Adapter<ViewHolder>() { // TODO: Use LoaderAdapter from Core instead?

    var itemsList: ArrayList<Any> = ArrayList()

    @StringRes
    var previousSectionName: Int = -1
    var displaySeeAllButton = false // TODO manage this for intelligent mailbox

    var onEmptyList: (() -> Unit)? = null
    var onThreadClicked: ((thread: Thread) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = when (viewType) {
            R.layout.thread_list_recycler_section -> ThreadListRecyclerSectionBinding.inflate(layoutInflater, parent, false)
            R.layout.see_all_recycler_button -> SeeAllRecyclerButtonBinding.inflate(layoutInflater, parent, false)
            else -> ItemThreadBinding.inflate(layoutInflater, parent, false)
        }
        return ThreadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val viewHolder = holder as ThreadViewHolder<*>
        when (getItemViewType(position)) {
            DisplayType.SECTION_TITLE.layout -> with(viewHolder.binding as ThreadListRecyclerSectionBinding) {
                sectionTitle.text = itemsList[position] as String
            }
            DisplayType.SEE_ALL_BUTTON.layout -> with(viewHolder.binding as SeeAllRecyclerButtonBinding) {
                seeAllText.append("(${itemsList[position]})")
            }
            DisplayType.THREAD.layout -> with(viewHolder.binding as ItemThreadBinding) {
                with(itemsList[position] as Thread) {
                    expeditor.text = from[0].name.ifEmpty { from[0].email }
                    mailSubject.text = subject
                    mailDate.text = formatDate(date?.toDate() ?: Date(0))
                    viewHolder.binding.itemThread.setOnClickListener { onThreadClicked?.invoke(this) }
                }
            }
        }
    }

    override fun getItemCount() = itemsList.size

    override fun getItemViewType(position: Int): Int {
        return when {
            itemsList[position] is String -> DisplayType.SECTION_TITLE.layout
            displaySeeAllButton -> DisplayType.SEE_ALL_BUTTON.layout
            else -> DisplayType.THREAD.layout
        }
    }

    private fun getDateCategories(dateTime: Long): DateFilter {
        return when {
            dateTime >= DateFilter.TODAY.start -> DateFilter.TODAY
            dateTime >= DateFilter.CURRENT_WEEK.start -> DateFilter.CURRENT_WEEK
            dateTime >= DateFilter.LAST_WEEK.start -> DateFilter.LAST_WEEK
            else -> DateFilter.TWO_WEEKS
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

    private fun formatDate(date: Date): String = with(date) {
        when {
            isToday() -> format(FORMAT_DATE_HOUR_MINUTE)
            year() == Date().year() -> format(FORMAT_DATE_SHORT_DAY_ONE_CHAR)
            else -> format(FORMAT_DATE_CLEAR_MONTH_DAY_ONE_CHAR)
        }
    }

    fun notifyAdapter(newList: ArrayList<Any>) {
        DiffUtil.calculateDiff(ThreadListDiffCallback(itemsList, newList)).dispatchUpdatesTo(this)
        itemsList = newList
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
                                oldItem.messagesCount == newItem.messagesCount
                        // TODO: Add other fields checks
                    } else { // Not same items
                        false
                    }
                }
            }
        }
    }

    enum class DateFilter(val start: Long, @StringRes val value: Int) {
        TODAY(Date().startOfTheDay().time, R.string.threadListSectionToday),
        CURRENT_WEEK(Date().startOfTheWeek().time, R.string.threadListSectionThisWeek),
        LAST_WEEK(Date().startOfTheWeek().time - DAY_LENGTH_MS * 7, R.string.threadListSectionLastWeek),
        TWO_WEEKS(Date().startOfTheWeek().time - DAY_LENGTH_MS * 14, R.string.threadListSectionTwoWeeks),
    }

    enum class DisplayType(val layout: Int) {
        THREAD(R.layout.item_thread),
        SECTION_TITLE(R.layout.thread_list_recycler_section),
        SEE_ALL_BUTTON(R.layout.see_all_recycler_button),
    }

    companion object {
        const val DAY_LENGTH_MS = 1_000 * 3_600 * 24
    }
}
