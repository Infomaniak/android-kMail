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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.ItemMessageBinding

class ThreadAdapter : RecyclerView.Adapter<BoundViewHolder<ItemMessageBinding>>() {

    var messageList: ArrayList<Message> = arrayListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoundViewHolder<ItemMessageBinding> {
        return BoundViewHolder(ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: BoundViewHolder<ItemMessageBinding>, position: Int) {
        with(holder.binding) {
            messageBody.text = messageList[position].body?.value
            headerThread.expeditorName.text = messageList[position].from.joinToString(
                ",",
                limit = HEADER_NAME_LIMIT,
                truncated = " + ${messageList[position].from.size - HEADER_NAME_LIMIT}"
            ) { it.name }
            headerThread.expeditorEmail.text = messageList[position].from[0].email
        }

    }

    override fun getItemCount() = messageList.size

    fun addAll(newMessageList: ArrayList<Message>) {
        val beforeItemCount = itemCount
        messageList.addAll(newMessageList)
        notifyItemRangeInserted(beforeItemCount, newMessageList.size)
    }

    fun clean() {
        messageList.clear()
        notifyItemRangeRemoved(0, messageList.size)
    }

    fun notifyAdapter(newList: ArrayList<Message>) {
        DiffUtil.calculateDiff(MessageListDiffCallback(messageList, newList)).dispatchUpdatesTo(this)
        messageList = newList
    }

    private class MessageListDiffCallback(
        private val oldList: List<Message>,
        private val newList: List<Message>,
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldIndex: Int, newIndex: Int): Boolean = oldList[oldIndex].uid == newList[newIndex].uid

        override fun areContentsTheSame(oldIndex: Int, newIndex: Int): Boolean {
            val oldItem = oldList[oldIndex]
            val newItem = newList[newIndex]

            return oldItem.uid == newItem.uid
                    && oldItem.from == newItem.from
                    && oldItem.date == newItem.date
                    && oldItem.attachments == newItem.attachments
                    && oldItem.subject == newItem.subject
                    && oldItem.body?.objectId == newItem.body?.objectId
        }
    }

    companion object {
        const val HEADER_NAME_LIMIT = 5
    }
}

