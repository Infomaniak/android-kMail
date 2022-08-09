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

import android.annotation.SuppressLint
import android.content.Context
import android.text.SpannedString
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.text.scale
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.findFragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.utils.FormatterFileSize
import com.infomaniak.lib.core.utils.firstOrEmpty
import com.infomaniak.lib.core.utils.format
import com.infomaniak.lib.core.utils.loadAvatar
import com.infomaniak.lib.core.views.ViewHolder
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Recipient
import com.infomaniak.mail.data.models.message.Body
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.ItemMessageBinding
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.ThreadViewHolder
import com.infomaniak.mail.utils.*
import java.util.*
import com.infomaniak.lib.core.R as RCore

class MessagesAdapter(
    private var messages: MutableList<Message> = mutableListOf(),
) : RecyclerView.Adapter<ThreadViewHolder>() {

    var onContactClicked: ((contact: Recipient) -> Unit)? = null
    var onDeleteDraftClicked: ((message: Message) -> Unit)? = null
    var onDraftClicked: ((message: Message) -> Unit)? = null
    var onAttachmentClicked: ((attachment: Attachment) -> Unit)? = null
    var onDownloadAllClicked: (() -> Unit)? = null

    override fun getItemCount() = messages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadViewHolder {
        return ThreadViewHolder(
            ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onContactClicked,
            onAttachmentClicked,
        )
    }

    override fun onBindViewHolder(holder: ThreadViewHolder, position: Int): Unit = with(holder.binding) {
        val message = messages[position]

        holder.bindHeader(message)
        holder.bindAttachment(message.attachments)
        loadBodyInWebView(message.body)

        displayExpandedCollapsedMessage(message)
    }

    private fun ItemMessageBinding.loadBodyInWebView(body: Body?) {
        // TODO: Make prettier webview, Add button to hide / display the conversation inside message body like webapp ?
        body?.let { messageBody.loadDataWithBaseURL("", it.value, it.type, "utf-8", "") }
    }

    private fun ThreadViewHolder.bindHeader(message: Message) = with(binding) {
        val messageDate = message.date?.toDate()

        if (message.isDraft) {
            userAvatarImage.loadAvatar(AccountUtils.currentUser!!)
            expeditorName.apply {
                text = context.getString(R.string.messageIsDraftOption)
                setTextColor(context.getColor(R.color.draftTextColor))
            }
            shortMessageDate.text = ""
        } else {
            val firstSender = message.from.first()
            userAvatarImage.loadAvatar(
                firstSender.email.hashCode(),
                null,
                firstSender.getNameOrEmail().firstOrEmpty().uppercase(),
            )
            expeditorName.apply {
                text = firstSender.displayedName(context)
                setTextColor(context.getColor(R.color.primaryTextColor))
            }
            shortMessageDate.text = messageDate?.let { context.mailFormattedDate(it) } ?: ""
        }

        userAvatar.setOnClickListener { onContactClicked?.invoke(message.from.first()) }

        handleHeaderClick(message)
        handleExpandDetailsClick(message)
        bindRecipientDetails(message, messageDate)
    }

    private fun Context.mailFormattedDate(date: Date): CharSequence = with(date) {
        return when {
            isToday() -> format(FORMAT_EMAIL_DATE_HOUR)
            isYesterday() -> getString(
                R.string.messageDetailsDateAt,
                getString(R.string.messageDetailsYesterday),
                format(FORMAT_EMAIL_DATE_HOUR),
            )
            isThisYear() -> getString(
                R.string.messageDetailsDateAt,
                format(FORMAT_EMAIL_DATE_SHORT_DATE),
                format(FORMAT_EMAIL_DATE_HOUR),
            )
            else -> this@mailFormattedDate.mostDetailedDate(this@with)
        }
    }

    private fun Context.mostDetailedDate(date: Date) =
        getString(R.string.messageDetailsDateAt, date.format(FORMAT_EMAIL_DATE_LONG_DATE), date.format(FORMAT_EMAIL_DATE_HOUR))

    private fun ItemMessageBinding.handleHeaderClick(message: Message) = with(message) {
        messageHeader.setOnClickListener {
            if (isExpanded) {
                isExpanded = false
                displayExpandedCollapsedMessage(this@with)
            } else {
                if (isDraft) {
                    onDraftClicked?.invoke(this@with)
                } else {
                    isExpanded = true
                    displayExpandedCollapsedMessage(this@with)
                }
            }
        }
    }

    private fun ItemMessageBinding.handleExpandDetailsClick(message: Message) {
        recipientOverlayedButton.setOnClickListener {
            message.detailsAreExpanded = !message.detailsAreExpanded
            val isExpanded = message.detailsAreExpanded
            recipientChevron.toggleChevron(!isExpanded)
            detailedFieldsGroup.isVisible = isExpanded
            ccGroup.isVisible = isExpanded && !message.cc.isEmpty()
        }
    }

    private fun ThreadViewHolder.bindRecipientDetails(message: Message, messageDate: Date?) = with(binding) {
        fromAdapter.updateList(message.from.toList())
        toAdapter.updateList(message.to.toList())

        val ccIsNotEmpty = !message.cc.isEmpty()
        ccGroup.isVisible = ccIsNotEmpty
        if (ccIsNotEmpty) ccAdapter.updateList(message.cc.toList())

        val isDateNotNull = messageDate != null
        detailedMessageDate.isVisible = isDateNotNull
        detailedMessagePrefix.isVisible = isDateNotNull
        if (isDateNotNull) detailedMessageDate.text = context.mostDetailedDate(messageDate!!)
    }

    private fun ThreadViewHolder.bindAttachment(attachments: List<Attachment>) = with(binding) {
        val fileSize = formatAttachmentFileSize(attachments)
        attachmentsSizeText.text = context.resources.getQuantityString(
            R.plurals.attachmentQuantity,
            attachments.size,
            attachments.size,
        ) + " ($fileSize)"
        attachmentAdapter.setAttachments(attachments)
        attachmentsDownloadAllButton.setOnClickListener { onDownloadAllClicked?.invoke() }
    }

    private fun ItemMessageBinding.formatAttachmentFileSize(attachments: List<Attachment>): String {
        if (attachments.isEmpty()) return ""

        val totalAttachmentsFileSizeInBytes: Long = attachments.map { attachment ->
            attachment.size.toLong()
        }.reduce { accumulator: Long, size: Long -> accumulator + size }

        return FormatterFileSize.formatShortFileSize(context, totalAttachmentsFileSizeInBytes)
    }

    private fun ItemMessageBinding.displayExpandedCollapsedMessage(message: Message) {
        collapseMessageDetails(message)
        setHeaderState(message)
        if (message.isExpanded) displayAttachments(message.attachments) else hideAttachments()
        webViewFrameLayout.isVisible = message.isExpanded
    }

    @SuppressLint("SetTextI18n")
    private fun ItemMessageBinding.displayAttachments(attachments: List<Attachment>) {
        if (attachments.isEmpty()) hideAttachments() else showAttachments()
    }

    private fun ItemMessageBinding.hideAttachments() {
        attachmentsGroup.isGone = true
        attachmentsRecyclerView.isGone = true
    }

    private fun ItemMessageBinding.showAttachments() {
        attachmentsGroup.isVisible = true
        attachmentsRecyclerView.isVisible = true
    }

    private fun ItemMessageBinding.collapseMessageDetails(message: Message) {
        message.detailsAreExpanded = false
        ccGroup.isGone = true
        detailedFieldsGroup.isGone = true
        recipientChevron.rotation = 0f
    }

    private fun ItemMessageBinding.setHeaderState(message: Message) = with(message) {
        deleteDraftButton.apply {
            isVisible = isDraft
            setOnClickListener { onDeleteDraftClicked?.invoke(this@with) }
        }
        replyButton.apply {
            isVisible = isExpanded
            setOnClickListener { findFragment<MessagesFragment>().notYetImplemented() }
        }
        menuButton.apply {
            isVisible = isExpanded
            setOnClickListener { findFragment<MessagesFragment>().notYetImplemented() }
        }

        recipient.text = if (isExpanded) formatRecipientsName(this@with) else subject
        recipientChevron.isVisible = isExpanded
        recipientOverlayedButton.isVisible = isExpanded
    }

    private fun ItemMessageBinding.formatRecipientsName(message: Message): SpannedString = with(message) {
        val to = recipientsToSpannedString(context, to)
        val cc = recipientsToSpannedString(context, cc)

        // TODO : Rewrite properly this part
        return buildSpannedString {
            if (isExpandedHeaderMode) scale(RECIPIENT_TEXT_SCALE_FACTOR) { append("${context.getString(R.string.toTitle)} ") }
            append(to)
            if (cc.isNotBlank()) append(cc)
        }.dropLast(2) as SpannedString
    }

    // TODO : Rewrite properly this part
    private fun Message.recipientsToSpannedString(context: Context, recipientsList: List<Recipient>) = buildSpannedString {
        recipientsList.forEach {
            if (isExpandedHeaderMode) {
                color(context.getColor(RCore.color.accent)) { append(it.displayedName(context)) }
                    .scale(RECIPIENT_TEXT_SCALE_FACTOR) { if (it.name.isNotBlank()) append(" (${it.email})") }
                    .append(",\n")
            } else {
                append("${it.displayedName(context)}, ")
            }
        }
    }

    fun removeMessage(message: Message) {
        val position = messages.indexOf(message)
        messages.removeAt(position)
        notifyItemRemoved(position)
    }

    fun notifyAdapter(newList: MutableList<Message>) {
        DiffUtil.calculateDiff(MessagesDiffCallback(messages, newList)).dispatchUpdatesTo(this)
        messages = newList
        messages.forEachIndexed { index, message ->
            if ((index == lastIndex() || !message.seen) && !message.isDraft) message.isExpanded = true
        }
    }

    fun lastIndex() = messages.lastIndex

    private fun Recipient.displayedName(context: Context): String {
        return if (email.isMe()) context.getString(R.string.contactMe) else getNameOrEmail()
    }

    private class MessagesDiffCallback(
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

    private companion object {
        const val FORMAT_EMAIL_DATE_HOUR = "HH:mm"
        const val FORMAT_EMAIL_DATE_SHORT_DATE = "d MMM"
        const val FORMAT_EMAIL_DATE_LONG_DATE = "d MMM yyyy"

        const val RECIPIENT_TEXT_SCALE_FACTOR = 0.9f
    }

    class ThreadViewHolder(
        val binding: ItemMessageBinding,
        onContactClicked: ((contact: Recipient) -> Unit)?,
        onAttachmentClicked: ((attachment: Attachment) -> Unit)?,
    ) : ViewHolder(binding.root) {

        val fromAdapter = DetailedRecipientAdapter(onContactClicked)
        val toAdapter = DetailedRecipientAdapter(onContactClicked)
        val ccAdapter = DetailedRecipientAdapter(onContactClicked)
        val attachmentAdapter = AttachmentAdapter(onAttachmentClicked)

        init {
            with(binding) {
                fromRecyclerView.adapter = fromAdapter
                toRecyclerView.adapter = toAdapter
                ccRecyclerView.adapter = ccAdapter
                attachmentsRecyclerView.adapter = attachmentAdapter
            }
        }
    }
}
