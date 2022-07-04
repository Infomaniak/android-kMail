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
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.utils.startOfTheDay
import com.infomaniak.lib.core.utils.startOfTheWeek
import com.infomaniak.lib.core.views.ViewHolder
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.CardviewThreadItemBinding
import com.infomaniak.mail.databinding.ItemThreadDateSeparatorBinding
import com.infomaniak.mail.databinding.ItemThreadSeeAllButtonBinding
import com.infomaniak.mail.utils.ModelsUtils.getFormattedThreadSubject
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.toDate
import io.realm.kotlin.ext.isValid
import java.util.*

// TODO: Use LoaderAdapter from Core instead?
class ThreadListAdapter(private var itemsList: MutableList<Any> = mutableListOf()) : RecyclerView.Adapter<ViewHolder>() {

    @StringRes
    var previousSectionName: Int = -1
    private var displaySeeAllButton = false // TODO: Manage this for intelligent mailbox

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
            else -> CardviewThreadItemBinding.inflate(layoutInflater, parent, false)
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
                (viewHolder.binding as CardviewThreadItemBinding).displayThread(position)
            }
        }
    }

    private fun ItemThreadDateSeparatorBinding.displayDateSeparator(position: Int) {
        sectionTitle.apply {
            text = itemsList[position] as String
            setTextAppearance(R.style.Callout)
            setTextColor(context.getColor(R.color.sectionHeaderTextColor))
        }
    }

    private fun ItemThreadSeeAllButtonBinding.displaySeeAllButton() {
        // TODO: Implement when we have intelligent mailbox
        // val threadsNumber = itemsList.size - NUMBER_OF_DISPLAYED_MAILS_OF_FOLDER
        // seeAllText.text = "See all $threadsNumber"
    }

    private fun CardviewThreadItemBinding.displayThread(position: Int) = with(itemsList[position] as Thread) {
        expeditor.text = formatExpeditorField(context, this)
        mailSubject.text = subject.getFormattedThreadSubject(context)

        mailDate.text = displayedDate

        iconAttachment.isVisible = hasAttachments
        iconCalendar.isGone = true // TODO: See with API when we should display this icon
        iconFavorite.isVisible = flagged

        if (unseenMessagesCount == 0) setThreadUiRead() else setThreadUiUnread()

        root.setOnClickListener { onThreadClicked?.invoke(this) }
    }

    private fun formatExpeditorField(context: Context, thread: Thread) = with(thread) {
        buildSpannedString {
            if (hasDrafts) {
                color(context.getColor(R.color.draftTextColor)) {
                    append("(${context.getString(R.string.messageIsDraftOption)}) ")
                }
            }
            from.forEach { append("${if (it.name.isNullOrEmpty()) it.email else it.name}, ") }
        }.removeSuffix(", ")
    }

    private fun CardviewThreadItemBinding.setThreadUiRead() {
        newMailBullet.isGone = true
        expeditor.setTextAppearance(R.style.H2_Secondary)
        mailSubject.setTextAppearance(R.style.Body_Secondary)
        mailDate.setTextAppearance(R.style.Callout_Secondary)
        iconAttachment.setDrawableColor(context, R.color.secondaryTextColor)
        iconCalendar.setDrawableColor(context, R.color.secondaryTextColor)
    }

    private fun CardviewThreadItemBinding.setThreadUiUnread() {
        newMailBullet.isVisible = true
        expeditor.setTextAppearance(R.style.H2)
        mailSubject.setTextAppearance(R.style.H3)
        mailDate.setTextAppearance(R.style.Callout_Strong)
        iconAttachment.setDrawableColor(context, R.color.primaryTextColor)
        iconCalendar.setDrawableColor(context, R.color.primaryTextColor)
    }

    fun formatList(threads: List<Thread>, context: Context): MutableList<Any> {
        previousSectionName = -1
        val formattedList = mutableListOf<Any>()

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
            dateTime >= DateFilter.TODAY.start() -> DateFilter.TODAY
            dateTime >= DateFilter.CURRENT_WEEK.start() -> DateFilter.CURRENT_WEEK
            dateTime >= DateFilter.LAST_WEEK.start() -> DateFilter.LAST_WEEK
            else -> DateFilter.TWO_WEEKS
        }
    }

    private fun ImageView.setDrawableColor(context: Context, @ColorRes color: Int) = drawable.setTint(context.getColor(color))

    fun notifyAdapter(newList: MutableList<Any>) {
        DiffUtil.calculateDiff(ThreadListDiffCallback(itemsList, newList)).dispatchUpdatesTo(this)
        itemsList = newList
    }

    private enum class DisplayType(val layout: Int) {
        THREAD(R.layout.cardview_thread_item),
        DATE_SEPARATOR(R.layout.item_thread_date_separator),
        SEE_ALL_BUTTON(R.layout.item_thread_see_all_button),
    }

    private enum class DateFilter(val start: () -> Long, @StringRes val value: Int) {
        TODAY({ Date().startOfTheDay().time }, R.string.threadListSectionToday),
        CURRENT_WEEK({ Date().startOfTheWeek().time }, R.string.threadListSectionThisWeek),
        LAST_WEEK({ Date().startOfTheWeek().time - DAY_LENGTH_MS * 7 }, R.string.threadListSectionLastWeek),
        TWO_WEEKS({ Date().startOfTheWeek().time - DAY_LENGTH_MS * 14 }, R.string.threadListSectionTwoWeeks),
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
                oldItem is String && newItem is String -> oldItem == newItem // Both are Strings
                oldItem !is Thread || newItem !is Thread -> false // Both aren't Threads
                oldItem.isValid() && newItem.isValid() -> oldItem.uid == newItem.uid // Both are valid
                else -> false
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
                                oldItem.unseenMessagesCount == newItem.unseenMessagesCount &&
                                oldItem.displayedDate == newItem.displayedDate
                        // TODO: Add other fields checks
                    } else { // Not same items
                        false
                    }
                }
            }
        }
    }

    private companion object {
        const val NUMBER_OF_DISPLAYED_MAILS_OF_FOLDER = 3
        const val DAY_LENGTH_MS = 1_000 * 3_600 * 24
    }
}
