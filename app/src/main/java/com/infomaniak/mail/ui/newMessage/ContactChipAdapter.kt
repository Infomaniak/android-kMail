/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.newMessage

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.databinding.ChipContactBinding
import com.infomaniak.mail.ui.newMessage.RecipientFieldView.Companion.setChipStyle
import com.infomaniak.mail.ui.newMessage.encryption.EncryptionLockButtonView
import com.infomaniak.mail.ui.newMessage.encryption.EncryptionLockButtonView.EncryptionStatus

class ContactChipAdapter(
    val openContextMenu: (Recipient, BackspaceAwareChip) -> Unit,
    val onBackspace: (Recipient) -> Unit,
) : Adapter<ContactChipAdapter.ContactChipViewHolder>() {

    private val recipients = mutableSetOf<Recipient>()
    private var encryptionStatus = EncryptionStatus.Unencrypted

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactChipViewHolder {
        return ContactChipViewHolder(ChipContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ContactChipViewHolder, position: Int): Unit = with(holder.binding) {
        val recipient = recipients.elementAt(position)
        root.apply {
            text = recipient.getNameOrEmail()
            setOnClickListener { openContextMenu(recipient, root) }
            setOnBackspaceListener { onBackspace(recipient) }
            setChipStyle(recipient.isDisplayedAsExternal, encryptionStatus)
        }
    }

    override fun getItemCount(): Int = recipients.count()

    fun isEmpty() = itemCount == 0

    fun getRecipients() = recipients

    fun addChip(recipient: Recipient): Boolean {
        return recipients.add(recipient).also { addedSuccessfully ->
            if (addedSuccessfully) notifyItemInserted(itemCount - 1)
        }
    }

    fun removeChip(recipient: Recipient) {
        val index = recipients.indexOf(recipient)
        recipients.remove(recipient)
        notifyItemRemoved(index)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun toggleEncryption(encryptionStatus: EncryptionStatus) {
        this.encryptionStatus = encryptionStatus
        notifyDataSetChanged() // We need to recompute whole collection to set new style
    }

    class ContactChipViewHolder(val binding: ChipContactBinding) : ViewHolder(binding.root)
}
