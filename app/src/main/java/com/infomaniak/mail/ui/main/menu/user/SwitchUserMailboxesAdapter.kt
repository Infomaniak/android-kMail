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
package com.infomaniak.mail.ui.main.menu.user

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.R
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.databinding.ItemSwitchUserMailboxBinding
import com.infomaniak.mail.ui.main.menu.user.SwitchUserMailboxesAdapter.SwitchUserMailboxViewHolder
import com.infomaniak.lib.core.R as RCore

class SwitchUserMailboxesAdapter(
    private var mailboxes: List<Mailbox> = emptyList(),
    private val displayIcon: Boolean = true,
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
        envelopeIcon.isVisible = displayIcon

        val unread = mailbox.unseenMessages
        var unreadText = unread.toString()
        if (unread >= 100) unreadText = "99+"
        unreadCount.apply {
            isGone = unread <= 0
            text = unreadText
        }

        setSelectedState(mailbox.objectId == MailData.currentMailboxFlow.value?.objectId)
        addressItemView.setOnClickListener { onMailboxSelected(mailbox) }
    }

    private fun ItemSwitchUserMailboxBinding.setSelectedState(isSelected: Boolean) {
        val (color, style) = computeStyle(isSelected)
        if (displayIcon) envelopeIcon.setColorFilter(color)
        emailAddress.apply {
            setTextColor(color)
            setTextAppearance(style)
        }
        unreadCount.setTextAppearance(style)
    }

    private fun ItemSwitchUserMailboxBinding.computeStyle(isSelected: Boolean) =
        if (isSelected) ContextCompat.getColor(root.context, R.color.emphasizedTextColor) to R.style.Callout_Highlighted_Strong
        else ContextCompat.getColor(root.context, RCore.color.title) to R.style.Callout

    override fun getItemCount(): Int = mailboxes.count()

    fun setMailboxes(newMailboxes: List<Mailbox>) {
        mailboxes = newMailboxes
    }

    class SwitchUserMailboxViewHolder(val binding: ItemSwitchUserMailboxBinding) : RecyclerView.ViewHolder(binding.root)
}
