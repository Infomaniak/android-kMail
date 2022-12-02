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
package com.infomaniak.mail.ui.main.user

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.databinding.ItemSwitchUserMailboxBinding
import com.infomaniak.mail.ui.main.user.SwitchUserMailboxesAdapter.SwitchUserMailboxViewHolder
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.formatUnreadCount
import com.infomaniak.mail.utils.getAttributeColor
import com.google.android.material.R as RMaterial

class SwitchUserMailboxesAdapter(
    private var mailboxes: List<Mailbox>,
    private var currentMailboxObjectId: String?,
    private val onMailboxSelected: (Mailbox) -> Unit,
) : RecyclerView.Adapter<SwitchUserMailboxViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwitchUserMailboxViewHolder {
        return SwitchUserMailboxViewHolder(
            ItemSwitchUserMailboxBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: SwitchUserMailboxViewHolder, position: Int): Unit = with(holder.binding) {
        val mailbox = mailboxes[position]
        emailAddress.text = mailbox.email

        val unreadCount = mailbox.inboxUnreadCount
        this.unreadCount.apply {
            isGone = unreadCount == 0
            text = formatUnreadCount(unreadCount)
        }

        setSelectedState(currentMailboxObjectId == mailbox.objectId)
        addressItemView.setOnClickListener { onMailboxSelected(mailbox) }
    }

    private fun ItemSwitchUserMailboxBinding.setSelectedState(isSelected: Boolean) {
        val (color, textStyle, badgeStyle) = if (isSelected) {
            Triple(context.getAttributeColor(RMaterial.attr.colorPrimary), R.style.H5_Accent, R.style.CalloutMedium_Accent)
        } else {
            Triple(ContextCompat.getColor(context, R.color.primaryTextColor), R.style.Body, R.style.Callout_Accent)
        }

        envelopeIcon.setColorFilter(color)
        emailAddress.setTextAppearance(textStyle)
        unreadCount.setTextAppearance(badgeStyle)
    }

    override fun getItemCount(): Int = mailboxes.count()

    class SwitchUserMailboxViewHolder(val binding: ItemSwitchUserMailboxBinding) : RecyclerView.ViewHolder(binding.root)
}
