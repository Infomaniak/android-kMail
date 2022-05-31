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
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DimenRes
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.text.scale
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.infomaniak.lib.core.utils.FormatterFileSize
import com.infomaniak.lib.core.utils.format
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
        with(holder) {
            displayHeader(binding, message)
            displayAttachments(binding, message.attachments)
            displayBody(binding, message.body)
        }
    }

    private fun displayHeader(binding: ItemMessageBinding, message: Message) {
        with(binding.headerThread) {
            expeditorName.text = message.from[0].name.ifBlank { message.from[0].email }
            expeditorEmail.text = message.from[0].email
            recipient.text = formatRecipientsName(binding.root.context, message)
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
            if (attachments.isEmpty()) {
                hideAttachments()
            } else {
                showAttachments()

                val fileSize = formatAttachmentFileSize(root.context, attachments)
                attachmentsSizeText.text = root.context.resources.getQuantityString(
                    R.plurals.attachmentQuantity,
                    attachments.size,
                    attachments.size,
                    fileSize
                )

                with(attachmentsChipGroup) {
                    removeAllViews()
                    attachments.forEach { addView(createChip(binding.root.context, it.name)) }
                }

                attachmentsDownloadAllButton.setOnClickListener {
                    // TODO attachmentList Fragment
                }
            }
        }
    }

    private fun ItemMessageBinding.hideAttachments() {
        attachmentsGroup.isGone = true
        attachmentsScrollView.isGone = true
    }

    private fun ItemMessageBinding.showAttachments() {
        attachmentsGroup.isVisible = true
        attachmentsScrollView.isVisible = true
    }

    private fun formatAttachmentFileSize(context: Context, attachments: List<Attachment>): String {
        val totalAttachmentsFileSizeInBytes: Long = attachments.map { attachment ->
            attachment.size.toLong()
        }.reduce { acc: Long, size: Long -> acc + size }

        return FormatterFileSize.formatShortFileSize(context, totalAttachmentsFileSizeInBytes)
    }

    private fun displayBody(binding: ItemMessageBinding, body: Body?) = with(binding) {
        // TODO make prettier webview, Add button to hide / display the conversation inside message body like webapp ?
        // Log.e("TOTO", "body : ${body?.value} \n\n\n\n\n\ntype = ${body?.type}"  )
        body?.let { messageBody.loadDataWithBaseURL("", it.value, it.type, "utf-8", "") }
    }

    fun notifyAdapter(newList: ArrayList<Message>) {
        DiffUtil.calculateDiff(MessageListDiffCallback(messageList, newList)).dispatchUpdatesTo(this)
        messageList = newList
    }

    private fun formatRecipientsName(context: Context, message: Message): SpannedString = with(message) {
        val to = recipientsToSpannedString(context, to, isExpandedHeaderMode)
        val cc = recipientsToSpannedString(context, cc, isExpandedHeaderMode)

        return buildSpannedString {
            if (isExpandedHeaderMode) scale(RECIPIENT_TEXT_SCALE_FACTOR) { append("À : ") }
            append(to)
            if (cc.isNotBlank()) append(cc)
        }.dropLast(2) as SpannedString
    }

    private fun recipientsToSpannedString(
        context: Context,
        recipientsList: List<Recipient>,
        isExpandedHeaderMode: Boolean,
    ): SpannedString {

        return buildSpannedString {
            recipientsList.forEach {
                append(
                    if (isExpandedHeaderMode) {
                        buildSpannedString {
                            if (it.name.isNotBlank()) {
                                color(context.getColor(R.color.primaryTextColor)) { append(it.name) }
                                scale(RECIPIENT_TEXT_SCALE_FACTOR) { append(" (${it.email})") }
                            } else {
                                color(context.getColor(R.color.primaryTextColor)) { append(it.email) }
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
            val context = root.context
            if (message.isExpandedHeaderMode) {
                expandHeaderButton.rotation = 0.0f
                expeditorEmail.isVisible = true
                recipient.ellipsize = null
                recipient.maxLines = Int.MAX_VALUE
                changeViewTextSize(context, recipient, R.dimen.textSmallSize)
            } else {
                expandHeaderButton.rotation = 180.0f
                expeditorEmail.isGone = true
                recipient.ellipsize = TextUtils.TruncateAt.END
                recipient.maxLines = 1
                changeViewTextSize(context, recipient, R.dimen.textHintSize)
            }
            recipient.text = formatRecipientsName(context, message)
        }
    }

    private fun createChip(context: Context, attachmentName: String): Chip {
        return Chip(context).apply {
            ellipsize = TextUtils.TruncateAt.MIDDLE
            maxLines = 1
            maxWidth = context.resources.getDimensionPixelSize(R.dimen.attachmentChipMaxSize)
            text = attachmentName
            changeViewTextSize(context, this, R.dimen.textHintSize)
        }
    }

    private fun changeViewTextSize(context: Context, view: TextView, @DimenRes dimension: Int) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(dimension))
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

    companion object {
        const val RECIPIENT_TEXT_SCALE_FACTOR = 0.9f
    }
}
