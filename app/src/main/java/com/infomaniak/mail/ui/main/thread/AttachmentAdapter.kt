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
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.infomaniak.lib.core.utils.FormatterFileSize
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.databinding.ItemAttachmentBinding
import com.infomaniak.mail.ui.main.thread.AttachmentAdapter.AttachmentViewHolder
import com.infomaniak.mail.utils.context

class AttachmentAdapter(
    private val onAttachmentClicked: ((Attachment) -> Unit)?,
) : RecyclerView.Adapter<AttachmentViewHolder>() {

    private var attachments: List<Attachment> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttachmentViewHolder {
        return AttachmentViewHolder(ItemAttachmentBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: AttachmentViewHolder, position: Int): Unit = with(holder.binding) {
        val attachment = attachments[position]

        fileName.text = attachment.name
        // TODO: Find how to add the FileType prefix before the file' size.
        fileDetails.text = /*item.mimeType + " - " + */FormatterFileSize.formatShortFileSize(context, attachment.size.toLong())
        icon.load(attachment.getFileTypeFromExtension().icon)
        root.setOnClickListener { onAttachmentClicked?.invoke(attachment) }
    }

    override fun getItemCount(): Int = attachments.count()

    fun setAttachments(newList: List<Attachment>) {
        attachments = newList
    }

    class AttachmentViewHolder(val binding: ItemAttachmentBinding) : RecyclerView.ViewHolder(binding.root)
}
