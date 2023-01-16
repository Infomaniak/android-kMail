/*
 * Infomaniak kMail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.databinding.ItemMailboxMyAccountBinding

class SimpleMailboxAdapter : RecyclerView.Adapter<SimpleMailboxAdapter.SimpleMailboxViewHolder>() {
    private var mailboxes = emptyList<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimpleMailboxViewHolder {
        return SimpleMailboxViewHolder(ItemMailboxMyAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: SimpleMailboxViewHolder, position: Int): Unit = with(holder.binding) {
        email.text = mailboxes[position]
    }

    override fun getItemCount(): Int = mailboxes.count()

    fun updateMailboxes(newList: List<String>) {
        mailboxes = newList
        notifyDataSetChanged()
    }

    class SimpleMailboxViewHolder(val binding: ItemMailboxMyAccountBinding) : RecyclerView.ViewHolder(binding.root)
}
