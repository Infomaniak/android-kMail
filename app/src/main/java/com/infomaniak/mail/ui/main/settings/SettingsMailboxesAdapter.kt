/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.settings

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView.*
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.databinding.ItemSettingsMailboxBinding
import com.infomaniak.mail.ui.main.settings.SettingsMailboxesAdapter.SettingsMailboxViewHolder

class SettingsMailboxesAdapter(
    private val onMailboxSelected: (Mailbox) -> Unit,
) : Adapter<SettingsMailboxViewHolder>() {

    private var mailboxes: List<Mailbox> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsMailboxViewHolder {
        return SettingsMailboxViewHolder(ItemSettingsMailboxBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: SettingsMailboxViewHolder, position: Int) = with(holder.binding.root) {
        val mailbox = mailboxes[position]
        setTitle(mailbox.email)

        setOnClickListener { onMailboxSelected(mailbox) }
        toggleMailboxBlockedState(!mailbox.isValid)
    }

    override fun getItemCount(): Int = mailboxes.count()

    @SuppressLint("NotifyDataSetChanged")
    fun setMailboxes(newMailboxes: List<Mailbox>) {
        mailboxes = newMailboxes
        notifyDataSetChanged()
    }

    class SettingsMailboxViewHolder(val binding: ItemSettingsMailboxBinding) : ViewHolder(binding.root)
}
