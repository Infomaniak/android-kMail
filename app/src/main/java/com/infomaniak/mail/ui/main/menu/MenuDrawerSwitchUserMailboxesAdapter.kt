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
package com.infomaniak.mail.ui.main.menu

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.databinding.ItemSwitchUserMailboxMenuDrawerBinding
import com.infomaniak.mail.ui.main.menu.MenuDrawerSwitchUserMailboxesAdapter.MenuDrawerSwitchUserMailboxViewHolder

class MenuDrawerSwitchUserMailboxesAdapter(
    private var mailboxes: List<Mailbox> = emptyList(),
    private val onMailboxSelected: (Mailbox) -> Unit,
) : RecyclerView.Adapter<MenuDrawerSwitchUserMailboxViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuDrawerSwitchUserMailboxViewHolder {
        return MenuDrawerSwitchUserMailboxViewHolder(
            ItemSwitchUserMailboxMenuDrawerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(
        holder: MenuDrawerSwitchUserMailboxViewHolder,
        position: Int,
    ): Unit = with(holder.binding.emailAddress) {
        val mailbox = mailboxes[position]
        text = mailbox.email
        badge = mailbox.inboxUnreadCount

        setOnClickListener { onMailboxSelected(mailbox) }
    }

    override fun getItemCount(): Int = mailboxes.count()

    fun setMailboxes(newMailboxes: List<Mailbox>) {
        mailboxes = newMailboxes
        notifyDataSetChanged()
    }

    class MenuDrawerSwitchUserMailboxViewHolder(
        val binding: ItemSwitchUserMailboxMenuDrawerBinding,
    ) : RecyclerView.ViewHolder(binding.root)
}
