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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.databinding.ItemSwitchUserMailboxBinding
import com.infomaniak.mail.ui.main.MainViewModel
import com.infomaniak.mail.ui.main.menu.user.SwitchUserMailboxesAdapter.SwitchUserMailboxViewHolder
import com.infomaniak.mail.utils.UiUtils.formatUnreadCount
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.getAttributeColor
import com.google.android.material.R as RMaterial
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
        unreadCount.apply {
            isGone = unread == 0
            text = formatUnreadCount(unread)
        }

        setSelectedState(mailbox.objectId == MainViewModel.currentMailboxFlow.value?.objectId)
        addressItemView.setOnClickListener { onMailboxSelected(mailbox) }
    }

    private fun ItemSwitchUserMailboxBinding.setSelectedState(isSelected: Boolean) {
        val (color, textStyle, badgeStyle) = if (isSelected) {
            Triple(
                context.getAttributeColor(RMaterial.attr.colorPrimary),
                R.style.Callout_Highlighted_Strong,
                R.style.Callout_Highlighted_Strong
            )
        } else {
            Triple(
                ContextCompat.getColor(context, RCore.color.title),
                R.style.Callout,
                R.style.Callout_Highlighted
            )
        }

        if (displayIcon) envelopeIcon.setColorFilter(color)
        emailAddress.apply {
            setTextColor(color)
            setTextAppearance(textStyle)
        }
        unreadCount.setTextAppearance(badgeStyle)
    }

    override fun getItemCount(): Int = mailboxes.count()

    fun notifyAdapter(newList: List<Mailbox>) {
        DiffUtil.calculateDiff(MailboxesListDiffCallback(mailboxes, newList)).dispatchUpdatesTo(this)
        mailboxes = newList
    }

    private class MailboxesListDiffCallback(
        private val oldList: List<Mailbox>,
        private val newList: List<Mailbox>,
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldIndex: Int, newIndex: Int): Boolean {
            return oldList[oldIndex].mailboxId == newList[newIndex].mailboxId
        }

        override fun areContentsTheSame(oldIndex: Int, newIndex: Int): Boolean {
            return oldList[oldIndex].unseenMessages == newList[newIndex].unseenMessages
        }
    }

    class SwitchUserMailboxViewHolder(val binding: ItemSwitchUserMailboxBinding) : RecyclerView.ViewHolder(binding.root)
}
