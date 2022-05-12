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
import android.text.SpannedString
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.scale
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.infomaniak.lib.core.utils.FormatterFileSize
import com.infomaniak.lib.core.utils.format
import com.infomaniak.lib.core.utils.toPx
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Recipient
import com.infomaniak.mail.data.models.message.Body
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.ItemHeaderThreadBinding
import com.infomaniak.mail.databinding.ItemMessageBinding
import com.infomaniak.mail.utils.toDate

class ThreadAdapter : RecyclerView.Adapter<BindingViewHolder<ItemMessageBinding>>() {

    private var messageList: ArrayList<Message> = arrayListOf()

    override fun getItemCount() = messageList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder<ItemMessageBinding> =
        BindingViewHolder(ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: BindingViewHolder<ItemMessageBinding>, position: Int) {
        val message = messageList[position]
        displayHeader(holder.binding, message)
        displayBody(holder.binding, message.body)
        displayAttachments(holder.binding, message.attachments)
    }

    private fun displayHeader(binding: ItemMessageBinding, message: Message) {
        with(binding.headerThread) {
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

    private fun displayAttachments(binding: ItemMessageBinding, attachments: List<Attachment>) {
        with(binding) {
            // TODO add attachment onClickListener and attachmentsListBottomSheetView
            if (attachments.isEmpty()) {
                attachmentsLayout.isGone = true
                attachmentsScrollView.isGone = true
            } else {
                attachmentsLayout.isVisible = true

                val fileSize = formatAttachmentFileSize(attachments, root.context)
                attachmentsCountText.text = root.context.resources.getQuantityString(
                    R.plurals.attachmentQuantity,
                    attachments.size,
                    attachments.size,
                    fileSize
                )

                with(attachmentsChipGroup) {
                    removeAllViews()
                    attachments.forEach {
                        addView(createChip(binding, it.name))
                    }
                }

                attachmentsSeeAllButton.setOnClickListener {
                    attachmentsScrollView.isVisible = true
                    it.isGone = true
                }

                attachmentsDownloadAllButton.setOnClickListener {
                    // TODO attachmentList Fragment
                }
            }
        }
    }

    private fun formatAttachmentFileSize(attachments: List<Attachment>, context: Context): String {
        val totalAttachmentsFileSizeInBytes: Long = attachments.map { attachment ->
            attachment.size.toLong()
        }.reduce { acc: Long, size: Long -> acc + size }

        return FormatterFileSize.formatShortFileSize(context, totalAttachmentsFileSizeInBytes)
    }

    private fun displayBody(binding: ItemMessageBinding, body: Body?) = with(binding) {
        // TODO make prettier webview, Add button to hide / display the conversation inside message body like webapp
        // Log.e("TOTO", "body : ${body?.value} \n\n\n\n\n\ntype = ${body?.type}"  )
        body?.let { messageBody.loadDataWithBaseURL("", it.value, it.type, "utf-8", "") }
    }

    fun notifyAdapter(newList: ArrayList<Message>) {
        DiffUtil.calculateDiff(MessageListDiffCallback(messageList, newList)).dispatchUpdatesTo(this)
        messageList = newList
    }

    private fun formatRecipientsName(message: Message): SpannedString = with(message) {
        val to = recipientsToSpannedString(to, isExpandedHeaderMode)
        val cc = recipientsToSpannedString(cc, isExpandedHeaderMode)

        return buildSpannedString {
            if (isExpandedHeaderMode) scale(0.9f) { append("À : ") }
            append(to)
            if (cc.isNotBlank()) {
                append(cc)
            }
        }.dropLast(2) as SpannedString
    }

    private fun recipientsToSpannedString(
        recipientsList: List<Recipient>,
        isExpandedHeaderMode: Boolean,
    ): SpannedString {

        return buildSpannedString {
            recipientsList.forEach {
                append(
                    if (isExpandedHeaderMode) {
                        buildSpannedString {
                            if (it.name.isNotBlank()) {
                                bold { append(it.name) }
                                scale(0.9f) { append(" (${it.email})") }
                            } else {
                                bold { append(it.email) }
                            }
                            append(",\n")
                        }
                    } else {
                        "${it.name.ifBlank { it.email }}, "
                    }
                )
            }
        }
    }

    private fun expandHeader(binding: ItemHeaderThreadBinding, message: Message) {
        with(binding) {
            if (message.isExpandedHeaderMode) {
                expeditorEmail.isVisible = true
                recipient.maxLines = Int.MAX_VALUE
                recipient.ellipsize = null
                recipient.textSize = 12.0f
                expandHeaderButton.rotation = 0.0f
            } else {
                expeditorEmail.isGone = true
                recipient.maxLines = 1
                recipient.textSize = 14.0f
                recipient.ellipsize = TextUtils.TruncateAt.END
                expandHeaderButton.rotation = 180.0f
            }
            recipient.text = formatRecipientsName(message)
        }
    }

    private fun createChip(binding: ItemMessageBinding, attachmentName: String): Chip {
        return Chip(binding.root.context).apply {
            text = attachmentName
            ellipsize = TextUtils.TruncateAt.MIDDLE
            // TODO put magic values in values/styles
            maxWidth = 160.toPx()
            textSize = 14.0f
            maxLines = 1
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
                    && oldItem.body == newItem.body
        }
    }
}
