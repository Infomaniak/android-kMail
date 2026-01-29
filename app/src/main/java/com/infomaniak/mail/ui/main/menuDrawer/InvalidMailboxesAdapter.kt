/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.menuDrawer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.databinding.ItemInvalidMailboxBinding
import com.infomaniak.mail.ui.main.menuDrawer.InvalidMailboxesAdapter.MailboxesViewHolder
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.views.itemViews.DecoratedItemView.SelectionStyle

class InvalidMailboxesAdapter(
    private var mailboxes: List<Mailbox> = emptyList(),
) : Adapter<MailboxesViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MailboxesViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return MailboxesViewHolder(ItemInvalidMailboxBinding.inflate(layoutInflater, parent, false))
    }

    override fun onBindViewHolder(holder: MailboxesViewHolder, position: Int) = runCatchingRealm {
        val mailbox = mailboxes[position]
        holder.binding.displayInvalidMailbox(mailbox)
    }.getOrDefault(Unit)

    override fun getItemCount(): Int = runCatchingRealm { mailboxes.count() }.getOrDefault(0)

    fun setMailboxes(newMailboxes: List<Mailbox>) {
        mailboxes = newMailboxes
        notifyDataSetChanged()
    }

    private fun ItemInvalidMailboxBinding.displayInvalidMailbox(mailbox: Mailbox) = with(root) {
        text = mailbox.emailIdn
        itemStyle = SelectionStyle.OTHER
        hasNoValidMailboxes = true
        computeEndIconVisibility()
    }

    class MailboxesViewHolder(val binding: ItemInvalidMailboxBinding) : ViewHolder(binding.root)
}
