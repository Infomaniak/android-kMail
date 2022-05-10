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

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.utils.format
import com.infomaniak.mail.data.models.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.ItemHeaderThreadBinding
import com.infomaniak.mail.databinding.ItemMessageBinding
import com.infomaniak.mail.utils.toDate

class ThreadAdapter : RecyclerView.Adapter<BoundViewHolder<ItemMessageBinding>>() {

    private var messageList: ArrayList<Message> = arrayListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoundViewHolder<ItemMessageBinding> {
        return BoundViewHolder(ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: BoundViewHolder<ItemMessageBinding>, position: Int) {
        val message = messageList[position]

        with(holder.binding) {
            messageBody.text = message.body?.value
            with(headerThread) {
                expeditorName.text = message.from[0].name.ifBlank { message.from[0].email }
                expeditorEmail.text = message.from[0].email
                recipient.text = formatRecipientsName(message)
                expandHeaderButton.setOnClickListener {
                    val isExpanded = !message.isExpandedHeaderMode
                    message.isExpandedHeaderMode = isExpanded
                    expandHeader(this, message)
                }
                messageDate.text = message.date?.toDate()?.format("d MMM YYYY à HH:mm")
            }
        }
    }

    override fun getItemCount() = messageList.size

    fun notifyAdapter(newList: ArrayList<Message>) {
        DiffUtil.calculateDiff(MessageListDiffCallback(messageList, newList)).dispatchUpdatesTo(this)
        messageList = newList
    }

    private fun formatRecipientsName(message: Message): String = with(message) {
        val to = recipientsToString(to, isExpandedHeaderMode)
        val cc = recipientsToString(cc, isExpandedHeaderMode)

        return if (isExpandedHeaderMode) {
            "À : $to${if (cc.isBlank()) "" else ",\n$cc"}"
        } else {
            "$to${if (cc.isBlank()) "" else ", $cc"}"
        }
    }

    private fun recipientsToString(
        recipientsList: List<Recipient>,
        isExpandedHeaderMode: Boolean,
    ): String {

        return recipientsList.joinToString(",\n") {
            if (isExpandedHeaderMode) {
                if (it.name.isBlank()) it.email else "${it.name} (${it.email})"
            } else it.name.ifBlank { it.email }
        }
    }

    private fun expandHeader(binding: ItemHeaderThreadBinding, message: Message) {
        with(binding) {
            if (message.isExpandedHeaderMode) {
                expeditorEmail.isVisible = true
                recipient.maxLines = Int.MAX_VALUE
                recipient.ellipsize = null
                expandHeaderButton.rotation = 0f
            } else {
                expeditorEmail.isGone = true
                recipient.maxLines = 1
                recipient.ellipsize = TextUtils.TruncateAt.END
                expandHeaderButton.rotation = 180f
            }
            recipient.text = formatRecipientsName(message)
        }
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
}

