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
import com.infomaniak.mail.ui.newMessage.encryption.EncryptableView
import com.infomaniak.mail.ui.newMessage.encryption.EncryptionStatus

class ContactChipAdapter(
    val canChipsBeEnabled: Boolean = true,
    val openContextMenu: ((Recipient, BackspaceAwareChip) -> Unit)? = null,
    val onBackspace: ((Recipient) -> Unit)? = null,
) : Adapter<ContactChipAdapter.ContactChipViewHolder>(), EncryptableView {

    override var isEncryptionActivated = false
    override var unencryptableRecipients: Set<String>? = null
    override var encryptionPassword = ""

    private val recipients = mutableSetOf<Recipient>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactChipViewHolder {
        return ContactChipViewHolder(ChipContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ContactChipViewHolder, position: Int): Unit = with(holder.binding) {
        val recipient = recipients.elementAt(position)
        val encryptionStatus = when {
            !isEncryptionActivated -> EncryptionStatus.Unencrypted
            unencryptableRecipients == null -> EncryptionStatus.Loading
            recipient.isUnencryptable -> EncryptionStatus.PartiallyEncrypted
            else -> EncryptionStatus.Encrypted
        }

        root.apply {
            isEnabled = canChipsBeEnabled
            text = recipient.getNameOrEmail()
            setOnClickListener { openContextMenu?.invoke(recipient, root) }
            setOnBackspaceListener { onBackspace?.invoke(recipient) }
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
    fun toggleEncryption(isEncryptionActivated: Boolean, unencryptableRecipients: Set<String>?, encryptionPassword: String) {
        this.isEncryptionActivated = isEncryptionActivated
        this.unencryptableRecipients = unencryptableRecipients
        this.encryptionPassword = encryptionPassword
        notifyDataSetChanged() // We need to recompute whole collection to set new style
    }

    fun updateUnencryptableRecipients(unencryptableRecipients: Set<String>?) {
        val alreadyUnencryptableRecipients = this.unencryptableRecipients ?: emptySet()
        this.unencryptableRecipients = unencryptableRecipients
        // No need to recompute unencryptable items if a password is already set or the encryption is not activated
        if (!isEncryptionActivated || encryptionPassword.isNotBlank()) return

        unencryptableRecipients?.let { newUnencryptableRecipients ->
            recipients.forEachIndexed { index, recipient ->
                // Only recompute items that weren't already in the same state to avoid making them blink
                if (recipient.email in newUnencryptableRecipients && recipient.email !in alreadyUnencryptableRecipients) {
                    notifyItemChanged(index)
                }
            }
        }
    }

    class ContactChipViewHolder(val binding: ChipContactBinding) : ViewHolder(binding.root)
}
