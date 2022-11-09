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
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeAdapter
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView
import com.ernestoyaquello.dragdropswiperecyclerview.util.DragDropSwipeDiffCallback
import com.google.android.material.card.MaterialCardView
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings.ThreadDensity
import com.infomaniak.mail.data.LocalSettings.ThreadDensity.COMPACT
import com.infomaniak.mail.data.LocalSettings.ThreadDensity.LARGE
import com.infomaniak.mail.data.models.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.CardviewThreadItemBinding
import com.infomaniak.mail.databinding.ItemThreadDateSeparatorBinding
import com.infomaniak.mail.databinding.ItemThreadSeeAllButtonBinding
import com.infomaniak.mail.ui.main.folder.ThreadListAdapter.ThreadViewHolder
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.ModelsUtils.getFormattedThreadSubject
import kotlin.math.abs

// TODO: Do we want to extract features from LoaderAdapter (in Core) and put them here?
// TODO: Same for all adapters in the app?
class ThreadListAdapter(
    private val threadDensity: ThreadDensity,
    private var contacts: Map<Recipient, MergedContact>,
    private val onSwipeFinished: () -> Unit,
) : DragDropSwipeAdapter<Any, ThreadViewHolder>(mutableListOf()), RealmChangesBinding.OnRealmChanged<Thread> {

    private lateinit var recyclerView: RecyclerView

    private var swipingIsAuthorized: Boolean = true
    private var displaySeeAllButton = false // TODO: Manage this for intelligent mailbox

    var onThreadClicked: ((thread: Thread) -> Unit)? = null

    init {
        setHasStableIds(true)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
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

    override fun onBindViewHolder(holder: ThreadViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.firstOrNull() is Unit && holder.itemViewType == DisplayType.THREAD.layout) {
            with(holder.binding as CardviewThreadItemBinding) {
                getDisplayedRecipient((dataSet[position] as Thread))?.let { expeditorAvatar.loadAvatar(it, contacts) }
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(item: Any, viewHolder: ThreadViewHolder, position: Int): Unit = with(viewHolder.binding) {
        when (getItemViewType(position)) {
            DisplayType.THREAD.layout -> (this as CardviewThreadItemBinding).displayThread(item as Thread)
            DisplayType.DATE_SEPARATOR.layout -> (this as ItemThreadDateSeparatorBinding).displayDateSeparator(item as String)
            DisplayType.SEE_ALL_BUTTON.layout -> (this as ItemThreadSeeAllButtonBinding).displaySeeAllButton(item)
        }
    }

    override fun getItemViewType(position: Int): Int = when {
        dataSet[position] is String -> DisplayType.DATE_SEPARATOR.layout
        displaySeeAllButton -> DisplayType.SEE_ALL_BUTTON.layout
        else -> DisplayType.THREAD.layout
    }

    override fun getItemId(position: Int): Long {
        return when (val item = dataSet[position]) {
            is Thread -> item.uid.hashCode().toLong()
            is String -> item.hashCode().toLong()
            else -> super.getItemId(position)
        }
    }

    private fun getDisplayedRecipient(thread: Thread): Recipient? = thread.messages.lastOrNull()?.from?.firstOrNull()

    private fun CardviewThreadItemBinding.displayThread(thread: Thread): Unit = with(thread) {

        expeditor.text = formatExpeditorNames(context)
        mailSubject.text = subject.getFormattedThreadSubject(root.context)
        mailBodyPreview.text = messages.lastOrNull()?.preview?.ifBlank { root.context.getString(R.string.noBodyTitle) }
        getDisplayedRecipient(this)?.let { expeditorAvatar.loadAvatar(it, contacts) }

        mailDate.text = formatDate(root.context)

        iconAttachment.isVisible = hasAttachments
        iconCalendar.isGone = true // TODO: See with API when we should display this icon
        iconFavorite.isVisible = isFavorite

        threadCount.text = uniqueMessagesCount.toString()
        threadCountCardview.isVisible = uniqueMessagesCount > 1

        if (unseenMessagesCount == 0) setThreadUiRead() else setThreadUiUnread()

        root.setOnClickListener { onThreadClicked?.invoke(this@with) }

        expeditorAvatar.isVisible = threadDensity == LARGE
        mailBodyPreview.isGone = threadDensity == COMPACT
        mailSubject.setMargins(top = if (threadDensity == COMPACT) 0 else 4.toPx())
    }

    private fun Thread.formatExpeditorNames(context: Context): String {
        return if (from.count() == 1) {
            from.first().displayedName(context)
        } else {
            from.joinToString(", ") {
                with(it.displayedName(context)) {
                    val delimiter = if (isEmail()) "@" else " "
                    substringBefore(delimiter)
                }
            }
        }
    }

    private fun CardviewThreadItemBinding.setThreadUiRead() {
        newMailBullet.isGone = true
        expeditor.setTextAppearance(R.style.H3_Secondary)
        mailSubject.setTextAppearance(R.style.Body_Secondary)
        mailDate.setTextAppearance(R.style.Callout_Secondary)
        iconAttachment.setDrawableColor(root.context, R.color.secondaryTextColor)
        iconCalendar.setDrawableColor(root.context, R.color.secondaryTextColor)
    }

    private fun CardviewThreadItemBinding.setThreadUiUnread() {
        newMailBullet.isVisible = true
        expeditor.setTextAppearance(R.style.H2)
        mailSubject.setTextAppearance(R.style.H4)
        mailDate.setTextAppearance(R.style.CalloutStrong)
        iconAttachment.setDrawableColor(root.context, R.color.primaryTextColor)
        iconCalendar.setDrawableColor(root.context, R.color.primaryTextColor)
    }

    private fun ImageView.setDrawableColor(context: Context, @ColorRes color: Int) = drawable.setTint(context.getColor(color))

    private fun ItemThreadDateSeparatorBinding.displayDateSeparator(title: String) {
        sectionTitle.text = title
    }

    private fun ItemThreadSeeAllButtonBinding.displaySeeAllButton(item: Any) {
        // TODO: Implement when we have intelligent mailbox
        // val threadsNumber = itemsList.size - NUMBER_OF_DISPLAYED_MAILS_OF_FOLDER
        // seeAllText.text = "See all $threadsNumber"
    }

    override fun onSwipeStarted(item: Any, viewHolder: ThreadViewHolder) {
        (recyclerView as DragDropSwipeRecyclerView).apply {
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
        isUserControlled: Boolean,
    ): Unit = with(viewHolder.binding) {
        val dx = abs(offsetX)
        val progress = dx.toFloat() / root.width

        if (progress < 0.5 && !viewHolder.isSwipedOverHalf) {
            viewHolder.isSwipedOverHalf = true
            root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } else if (progress > 0.5 && viewHolder.isSwipedOverHalf) {
            viewHolder.isSwipedOverHalf = false
            root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
        }

        val cardView = root as MaterialCardView
        cardView.cardElevation = cappedLinearInterpolator(CARD_ELEVATION, progress)
        cardView.radius = cappedLinearInterpolator(CARD_CORNER_RADIUS, progress)
    }

    private fun cappedLinearInterpolator(max: Float, progress: Float): Float {
        return if (progress < SWIPE_ANIMATION_THRESHOLD) max * progress / SWIPE_ANIMATION_THRESHOLD else max
    }

    override fun onSwipeAnimationFinished(viewHolder: ThreadViewHolder) {
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

    override fun getViewHolder(itemView: View): ThreadViewHolder = ThreadViewHolder { itemView }

    override fun getViewToTouchToStartDraggingItem(item: Any, viewHolder: ThreadViewHolder, position: Int): View? {
        return when (getItemViewType(position)) {
            DisplayType.THREAD.layout -> (viewHolder.binding as CardviewThreadItemBinding).goneHandle
            else -> null
        }
    }

    override fun canBeSwiped(item: Any, viewHolder: ThreadViewHolder, position: Int): Boolean {
        return getItemViewType(position) == DisplayType.THREAD.layout && swipingIsAuthorized
    }

    override fun createDiffUtil(oldList: List<Any>, newList: List<Any>): DragDropSwipeDiffCallback<Any>? = null

    override fun updateList(itemList: List<Thread>) {
        dataSet = formatList(itemList, recyclerView.context, threadDensity)
    }

    fun updateContacts(newContacts: Map<Recipient, MergedContact>) {
        contacts = newContacts
        notifyItemRangeChanged(0, itemCount, Unit)
    }

    private enum class DisplayType(val layout: Int) {
        THREAD(R.layout.cardview_thread_item),
        DATE_SEPARATOR(R.layout.item_thread_date_separator),
        SEE_ALL_BUTTON(R.layout.item_thread_see_all_button),
    }

    companion object {
        const val SWIPE_ANIMATION_THRESHOLD = 0.15f
        private val CARD_ELEVATION = 6.toPx().toFloat()
        private val CARD_CORNER_RADIUS = 12.toPx().toFloat()

        private const val FULL_MONTH = "MMMM"
        private const val MONTH_AND_YEAR = "MMMM yyyy"

        fun formatList(threads: List<Thread>, context: Context, threadDensity: ThreadDensity): MutableList<Any> {
            if (threadDensity == COMPACT) return threads.toMutableList()

            var previousSectionTitle = ""
            val formattedList = mutableListOf<Any>()

            // TODO: Use Realm to directly get the sorted list instead of sortedByDescending()
            threads.sortedByDescending { it.date }.forEachIndexed { _, thread ->
                val sectionTitle = thread.getSectionTitle(context)
                when {
                    sectionTitle != previousSectionTitle -> {
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
    }

    class ThreadViewHolder(val binding: ViewBinding) : ViewHolder(binding.root) {
        var isSwipedOverHalf = false
    }
}
