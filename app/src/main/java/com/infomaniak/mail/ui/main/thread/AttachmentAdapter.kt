/*
 * Infomaniak Mail - Android
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
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.infomaniak.lib.core.utils.FormatterFileSize
import com.infomaniak.lib.core.utils.context
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.databinding.ItemAttachmentBinding
import com.infomaniak.mail.ui.main.thread.AttachmentAdapter.AttachmentViewHolder
import com.infomaniak.mail.utils.Utils.runCatchingRealm

class AttachmentAdapter(
    private val shouldDisplayCloseButton: Boolean = false,
    private val onDelete: ((position: Int, itemCountLeft: Int) -> Unit)? = null,
    private val onAttachmentClicked: ((Attachment) -> Unit)? = null,
) : RecyclerView.Adapter<AttachmentViewHolder>() {

    private var attachments: MutableList<Attachment> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttachmentViewHolder {
        return AttachmentViewHolder(ItemAttachmentBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: AttachmentViewHolder, position: Int, payloads: MutableList<Any>) {
        runCatchingRealm { super.onBindViewHolder(holder, position, payloads) }
    }

    override fun onBindViewHolder(holder: AttachmentViewHolder, position: Int): Unit = with(holder.binding) {
        val attachment = attachments[position]

        fileName.text = attachment.name
        fileDetails.text = FormatterFileSize.formatShortFileSize(context, attachment.size)
        icon.load(attachment.getFileTypeFromMimeType().icon)

        if (!shouldDisplayCloseButton) {
            root.setOnClickListener { onAttachmentClicked?.invoke(attachment) }
        } else {
            closeButton.apply {
                contentDescription = context.getString(R.string.contentDescriptionButtonDelete, attachment.name)
                setOnClickListener {
                    val index = attachments.indexOf(attachment)

                    // When clicking on the the attachment in order to delete it, we remove the attachment from the list
                    // successfully which means that during the fade out animation, if we click again on the little cross the
                    // attachment is not in the list anymore yet the cross has been clicked. This results in a negative index
                    // which is why this check is for.
                    if (index != -1) {
                        attachments.removeAt(index)
                        notifyItemRemoved(index)

                        onDelete?.invoke(index, itemCount)
                    }
                }
                isVisible = true
            }
        }
    }

    override fun getItemCount(): Int = runCatchingRealm { attachments.count() }.getOrDefault(0)

    fun setAttachments(newList: List<Attachment>) {
        attachments = newList.toMutableList()
    }

    fun addAll(newAttachments: List<Attachment>) {
        attachments.addAll(newAttachments)
        notifyItemRangeInserted(attachments.lastIndex, newAttachments.count())
    }

    class AttachmentViewHolder(val binding: ItemAttachmentBinding) : RecyclerView.ViewHolder(binding.root)
}
