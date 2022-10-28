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
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.views.ViewHolder
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.message.Body
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.ItemMessageBinding
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.ThreadViewHolder
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.UiUtils.fillInUserNameAndEmail
import java.util.*

class ThreadAdapter : RecyclerView.Adapter<ThreadViewHolder>(), RealmChangesBinding.OnRealmChanged<Message> {

    private var messages = listOf<Message>()
    var contacts: Map<Recipient, MergedContact> = emptyMap()

    var onContactClicked: ((contact: Recipient) -> Unit)? = null
    var onDeleteDraftClicked: ((message: Message) -> Unit)? = null
    var onDraftClicked: ((message: Message) -> Unit)? = null
    var onAttachmentClicked: ((attachment: Attachment) -> Unit)? = null
    var onDownloadAllClicked: (() -> Unit)? = null
    var onReplyClicked: ((Message) -> Unit)? = null
    var onMenuClicked: ((Message) -> Unit)? = null

    override fun updateList(itemList: List<Message>) {
        messages = itemList.mapIndexed { index, message ->
            message.also { if ((index == itemList.lastIndex || !it.seen) && !it.isDraft) it.isExpanded = true }
        }
    }

    fun lastIndex() = messages.lastIndex

    override fun getItemCount() = messages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadViewHolder {
        return ThreadViewHolder(
            ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onContactClicked,
            onAttachmentClicked,
        )
    }

    override fun onBindViewHolder(holder: ThreadViewHolder, position: Int, payloads: MutableList<Any>) {
        val message = messages[position]
        if (payloads.firstOrNull() is Unit && !message.isDraft) {
            holder.binding.userAvatar.loadAvatar(message.from.first(), contacts)
        }
        super.onBindViewHolder(holder, position, payloads)
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
            userAvatar.loadAvatar(AccountUtils.currentUser!!)
            expeditorName.apply {
                text = context.getString(R.string.messageIsDraftOption)
                setTextColor(context.getColor(R.color.draftTextColor))
            }
            shortMessageDate.text = ""
        } else {
            val firstSender = message.from.first()
            userAvatar.loadAvatar(firstSender, contacts)
            expeditorName.apply {
                fillInUserNameAndEmail(firstSender, this)
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

    private fun Context.mostDetailedDate(date: Date): String {
        return getString(
            R.string.messageDetailsDateAt,
            date.format(FORMAT_EMAIL_DATE_LONG_DATE),
            date.format(FORMAT_EMAIL_DATE_HOUR),
        )
    }

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
            ccGroup.isVisible = isExpanded && message.cc.isNotEmpty()
            bccGroup.isVisible = isExpanded && message.bcc.isNotEmpty()
        }
    }

    private fun ThreadViewHolder.bindRecipientDetails(message: Message, messageDate: Date?) = with(binding) {
        fromAdapter.updateList(message.from.toList())
        toAdapter.updateList(message.to.toList())

        val ccIsNotEmpty = message.cc.isNotEmpty()
        ccGroup.isVisible = ccIsNotEmpty
        if (ccIsNotEmpty) ccAdapter.updateList(message.cc.toList())

        val bccIsNotEmpty = message.bcc.isNotEmpty()
        bccGroup.isVisible = bccIsNotEmpty
        if (bccIsNotEmpty) bccAdapter.updateList(message.bcc.toList())

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
        bccGroup.isGone = true
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
            setOnClickListener { onReplyClicked?.invoke(message) }
        }
        menuButton.apply {
            isVisible = isExpanded
            setOnClickListener { onMenuClicked?.invoke(message) }
        }

        recipient.text = if (isExpanded) getAllRecipientsFormatted(this@with) else subject
        recipientChevron.isVisible = isExpanded
        recipientOverlayedButton.isVisible = isExpanded
    }

    private fun ItemMessageBinding.getAllRecipientsFormatted(message: Message): String = with(message) {
        return listOf(*to.toTypedArray(), *cc.toTypedArray(), *bcc.toTypedArray()).joinToString { it.displayedName(context) }
    }

    fun updateContacts(newContacts: Map<Recipient, MergedContact>) {
        contacts = newContacts
        notifyItemRangeChanged(0, itemCount, Unit)
    }

    private companion object {
        const val FORMAT_EMAIL_DATE_HOUR = "HH:mm"
        const val FORMAT_EMAIL_DATE_SHORT_DATE = "d MMM"
        const val FORMAT_EMAIL_DATE_LONG_DATE = "d MMM yyyy"
    }

    class ThreadViewHolder(
        val binding: ItemMessageBinding,
        onContactClicked: ((contact: Recipient) -> Unit)?,
        onAttachmentClicked: ((attachment: Attachment) -> Unit)?,
    ) : ViewHolder(binding.root) {

        val fromAdapter = DetailedRecipientAdapter(onContactClicked)
        val toAdapter = DetailedRecipientAdapter(onContactClicked)
        val ccAdapter = DetailedRecipientAdapter(onContactClicked)
        val bccAdapter = DetailedRecipientAdapter(onContactClicked)
        val attachmentAdapter = AttachmentAdapter(onAttachmentClicked)

        init {
            with(binding) {
                fromRecyclerView.adapter = fromAdapter
                toRecyclerView.adapter = toAdapter
                ccRecyclerView.adapter = ccAdapter
                bccRecyclerView.adapter = bccAdapter
                attachmentsRecyclerView.adapter = attachmentAdapter
            }
        }
    }
}
