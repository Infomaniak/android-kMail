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
import android.text.Html
import android.text.SpannedString
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
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
import com.infomaniak.mail.databinding.ItemMessageBinding
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.toDate
import com.infomaniak.mail.utils.toggleChevron

class ThreadAdapter : RecyclerView.Adapter<BindingViewHolder<ItemMessageBinding>>() {

    private var messageList: ArrayList<Message> = arrayListOf()

    var onContactClicked: ((contact: Recipient, isExpanded: Boolean) -> Unit)? = null
    var onDeleteDraftClicked: ((message: Message) -> Unit)? = null
    var onDraftClicked: ((message: Message) -> Unit)? = null

    override fun getItemCount() = messageList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder<ItemMessageBinding> {
        return BindingViewHolder(ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: BindingViewHolder<ItemMessageBinding>, position: Int): Unit = with(holder.binding) {
        val message = messageList[position]
        root.setOnClickListener(null)
        if ((position == messageList.size - 1 || !message.seen) && !message.isDraft) message.isExpanded = true
        if (!message.isExpanded) {
            root.setOnClickListener {
                if (message.isDraft) {
                    onDraftClicked?.invoke(message)
                } else {
                    message.isExpanded = true
                    displayMessage(message)
                }
            }
        }

        displayMessage(message)
    }

    fun removeMessage(message: Message) {
        val position = messageList.indexOf(message)
        messageList.removeAt(position)
        notifyItemRemoved(position)
    }

    fun notifyAdapter(newList: ArrayList<Message>) {
        DiffUtil.calculateDiff(MessageListDiffCallback(messageList, newList)).dispatchUpdatesTo(this)
        messageList = newList
    }

    private fun ItemMessageBinding.displayMessage(message: Message) = with(message) {
        displayHeader(message)
        hideAttachments()
        if (isExpanded) {
            displayAttachments(attachments)
            displayBody(body)
        }
    }

    private fun ItemMessageBinding.displayHeader(message: Message) = with(message) {
        deleteDraftButton.isVisible = isDraft
        deleteDraftButton.setOnClickListener { onDeleteDraftClicked?.invoke(message) }
        messageDate.text = if (isDraft) "" else date?.toDate()?.format("d MMM YYYY à HH:mm")
        expeditorName.setTextColor(root.context.getColor(if (isDraft) R.color.draftTextColor else R.color.primaryTextColor))
        expeditorName.text = if (isDraft) {
            root.context.getString(R.string.messageIsDraftOption)
        } else {
            from[0].displayedName(root.context)
        }

        expandHeaderButton.isVisible = isExpanded
        webViewFrameLayout.isVisible = isExpanded
        recipient.text = if (isExpanded) {
            formatRecipientsName(root.context, message)
        } else {
            Html.fromHtml(preview, Html.FROM_HTML_MODE_LEGACY)
        }
        expeditorEmail.text = if (isExpanded) from[0].email else ""

        if (isExpanded) {
            messageHeader.addExpandHeaderListener(this@displayHeader, message)
            expandHeaderButton.addExpandHeaderListener(this@displayHeader, message)
        }
    }

    private fun formatRecipientsName(context: Context, message: Message): SpannedString = with(message) {
        val to = recipientsToSpannedString(context, to)
        val cc = recipientsToSpannedString(context, cc)

        return buildSpannedString {
            if (isExpandedHeaderMode) scale(RECIPIENT_TEXT_SCALE_FACTOR) { append("${context.getString(R.string.toTitle)} ") }
            append(to)
            if (cc.isNotBlank()) append(cc)
        }.dropLast(2) as SpannedString
    }

    private fun Message.recipientsToSpannedString(context: Context, recipientsList: List<Recipient>) = buildSpannedString {
        recipientsList.forEach {
            append(
                if (isExpandedHeaderMode) {
                    buildSpannedString {
                        color(context.getColor(R.color.primaryTextColor)) { append(it.displayedName(context)) }
                        if (it.name.isBlank()) scale(RECIPIENT_TEXT_SCALE_FACTOR) { append(" (${it.email})") }
                        append(",\n")
                    }
                } else {
                    "${it.displayedName(context)}, "
                }
            )
        }
    }

    private fun View.addExpandHeaderListener(binding: ItemMessageBinding, message: Message) = with(message) {
        setOnClickListener {
            isExpandedHeaderMode = !isExpandedHeaderMode
            binding.expandHeader(message)
        }
    }

    private fun ItemMessageBinding.expandHeader(message: Message) = with(message) {

        expeditorEmail.isVisible = isExpandedHeaderMode
        expandHeaderButton.toggleChevron(!message.isExpandedHeaderMode)
        recipient.maxLines = if (isExpandedHeaderMode) Int.MAX_VALUE else 1
        recipient.changeSize(if (isExpandedHeaderMode) R.dimen.textSmallSize else R.dimen.textHintSize)

        recipient.text = formatRecipientsName(root.context, message)
        // TODO: Add listener to name and email of all recipient ?
        userAvatar.setOnClickListener { onContactClicked?.invoke(from[0], isExpandedHeaderMode) }
    }

    private fun TextView.changeSize(@DimenRes dimension: Int) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(dimension))
    }

    private fun ItemMessageBinding.displayAttachments(attachments: List<Attachment>) {
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
                attachments.forEach { addView(createChip(it.name)) }
            }

            attachmentsDownloadAllButton.setOnClickListener {
                // TODO attachmentList Fragment
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
        }.reduce { accumulator: Long, size: Long -> accumulator + size }

        return FormatterFileSize.formatShortFileSize(context, totalAttachmentsFileSizeInBytes)
    }

    private fun ItemMessageBinding.createChip(attachmentName: String): Chip {
        val layoutInflater = LayoutInflater.from(root.context)
        val chip = layoutInflater.inflate(R.layout.chip_attachment, attachmentsChipGroup, false) as Chip

        return chip.apply { text = attachmentName }
    }

    private fun ItemMessageBinding.displayBody(body: Body?) {
        // TODO make prettier webview, Add button to hide / display the conversation inside message body like webapp ?
        body?.let { messageBody.loadDataWithBaseURL("", it.value, it.type, "utf-8", "") }
    }

    private fun Recipient.displayedName(context: Context): String {
        return if (AccountUtils.currentUser?.email == email) context.getString(R.string.contactMe) else getNameOrEmail()
    }

    private fun Recipient.getNameOrEmail() = name.ifBlank { email }

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
