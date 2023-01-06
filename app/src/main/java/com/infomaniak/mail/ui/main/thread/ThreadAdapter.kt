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
import com.google.android.material.card.MaterialCardView
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
import com.infomaniak.mail.utils.Utils
import java.util.*
import com.infomaniak.lib.core.R as RCore

class ThreadAdapter : RecyclerView.Adapter<ThreadViewHolder>(), RealmChangesBinding.OnRealmChanged<Message> {

    var messages = listOf<Message>()
        private set
    var expandedList = mutableListOf<Boolean>()
    var contacts: Map<Recipient, MergedContact> = emptyMap()

    var onContactClicked: ((contact: Recipient) -> Unit)? = null
    var onDeleteDraftClicked: ((message: Message) -> Unit)? = null
    var onDraftClicked: ((message: Message) -> Unit)? = null
    var onAttachmentClicked: ((attachment: Attachment) -> Unit)? = null
    var onDownloadAllClicked: ((message: Message) -> Unit)? = null
    var onReplyClicked: ((Message) -> Unit)? = null
    var onMenuClicked: ((Message) -> Unit)? = null

    override fun updateList(itemList: List<Message>) {
        messages = itemList
    }

    private fun lastIndex() = messages.lastIndex

    override fun getItemCount() = messages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadViewHolder {
        return ThreadViewHolder(
            ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onContactClicked,
            onAttachmentClicked,
        )
    }

    // Add here everything in a Message that can be updated in the UI.
    override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
        return newItem.body?.value == oldItem.body?.value &&
                newItem.seen == oldItem.seen &&
                newItem.isFavorite == oldItem.isFavorite
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

        root.setStyleIfSingleMail(position)

        holder.bindHeader(message, position)
        holder.bindAttachment(message)
        loadBodyInWebView(message.body)

        displayExpandedCollapsedMessage(message, position)
    }

    private fun MaterialCardView.setStyleIfSingleMail(position: Int) {
        @Suppress("LiftReturnOrAssignment")
        if (itemCount == 1) {
            setMarginsRelative(0, 0, 0, 0)
            radius = 0f
        } else {
            val vertical = resources.getDimension(RCore.dimen.marginStandardVerySmall).toInt()
            val horizontal = resources.getDimension(RCore.dimen.marginStandardSmall).toInt()
            val topMargin = if (position == 0) 2 * vertical else vertical
            val bottomMargin = if (position == lastIndex()) 2 * vertical else vertical
            setMarginsRelative(horizontal, topMargin, horizontal, bottomMargin)

            radius = 8.toPx().toFloat()
        }
    }

    private fun ItemMessageBinding.loadBodyInWebView(body: Body?) {
        // TODO: Make prettier webview, Add button to hide / display the conversation inside message body like webapp ?
        body?.let { messageBody.loadDataWithBaseURL("", it.value, it.type, Utils.UTF_8, "") }
    }

    private fun ThreadViewHolder.bindHeader(message: Message, position: Int) = with(binding) {
        val messageDate = message.date?.toDate()

        if (message.isDraft) {
            userAvatar.loadAvatar(AccountUtils.currentUser!!)
            expeditorName.apply {
                text = context.getString(R.string.messageIsDraftOption)
                setTextAppearance(R.style.H5_Error)
            }
            shortMessageDate.text = ""
        } else {
            val firstSender = message.from.first()
            userAvatar.loadAvatar(firstSender, contacts)
            expeditorName.apply {
                UiUtils.fillInUserNameAndEmail(firstSender, this)
                setTextAppearance(R.style.H5)
            }
            shortMessageDate.text = messageDate?.let { context.mailFormattedDate(it) } ?: ""
        }

        userAvatar.setOnClickListener { onContactClicked?.invoke(message.from.first()) }

        handleHeaderClick(message, position)
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
            else -> this@mailFormattedDate.mostDetailedDate(date = this@with)
        }
    }

    private fun Context.mostDetailedDate(date: Date): String {
        return getString(
            R.string.messageDetailsDateAt,
            date.format(FORMAT_EMAIL_DATE_LONG_DATE),
            date.format(FORMAT_EMAIL_DATE_HOUR),
        )
    }

    private fun ItemMessageBinding.handleHeaderClick(message: Message, position: Int) = with(message) {
        messageHeader.setOnClickListener {
            if (expandedList[position]) {
                expandedList[position] = false
                displayExpandedCollapsedMessage(message = this@with, position)
            } else {
                if (isDraft) {
                    onDraftClicked?.invoke(this@with)
                } else {
                    expandedList[position] = true
                    displayExpandedCollapsedMessage(message = this@with, position)
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

    private fun ThreadViewHolder.bindAttachment(message: Message) = with(binding) {
        val attachments = message.attachments
        val fileSize = formatAttachmentFileSize(attachments)
        attachmentsSizeText.text = context.resources.getQuantityString(
            R.plurals.attachmentQuantity,
            attachments.size,
            attachments.size,
        ) + " ($fileSize)"
        attachmentAdapter.setAttachments(attachments)
        attachmentsDownloadAllButton.setOnClickListener { onDownloadAllClicked?.invoke(message) }
    }

    private fun ItemMessageBinding.formatAttachmentFileSize(attachments: List<Attachment>): String {
        if (attachments.isEmpty()) return ""

        val totalAttachmentsFileSizeInBytes: Long = attachments.map { attachment ->
            attachment.size.toLong()
        }.reduce { accumulator: Long, size: Long -> accumulator + size }

        return FormatterFileSize.formatShortFileSize(context, totalAttachmentsFileSizeInBytes)
    }

    private fun ItemMessageBinding.displayExpandedCollapsedMessage(message: Message, position: Int) {
        val isExpanded = expandedList[position]
        collapseMessageDetails(message)
        setHeaderState(message, isExpanded)
        if (isExpanded) displayAttachments(message.attachments) else hideAttachments()

        if (message.body?.value == null) {
            messageLoader.isVisible = isExpanded
            webViewFrameLayout.isVisible = false
        } else {
            messageLoader.isVisible = false
            webViewFrameLayout.isVisible = isExpanded
        }
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

    private fun ItemMessageBinding.setHeaderState(message: Message, isExpanded: Boolean) = with(message) {
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

        recipient.text = if (isExpanded) getAllRecipientsFormatted(message = this@with) else getFormattedSubject(context)
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
        val attachmentAdapter = AttachmentAdapter { onAttachmentClicked?.invoke(it) }

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
