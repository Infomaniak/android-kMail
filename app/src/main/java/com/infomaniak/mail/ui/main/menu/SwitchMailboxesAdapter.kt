/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.MatomoMail.SWITCH_MAILBOX_NAME
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.databinding.ItemSwitchMailboxBinding
import com.infomaniak.mail.ui.main.menu.SwitchMailboxesAdapter.SwitchMailboxesViewHolder
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.views.MenuDrawerItemView.SelectionStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SwitchMailboxesAdapter(
    private val isInMenuDrawer: Boolean,
    private val lifecycleScope: LifecycleCoroutineScope,
    private var mailboxes: List<Mailbox> = emptyList(),
) : RecyclerView.Adapter<SwitchMailboxesViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwitchMailboxesViewHolder {
        return SwitchMailboxesViewHolder(ItemSwitchMailboxBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: SwitchMailboxesViewHolder, position: Int) = with(holder.binding.emailAddress) {
        val mailbox = mailboxes[position]
        val isCurrentMailbox = mailbox.mailboxId == AccountUtils.currentMailboxId

        text = mailbox.email

        if (isInMenuDrawer) badge = mailbox.inboxUnreadCount else itemStyle = SelectionStyle.ACCOUNT

        holder.binding.root.setSelectedState(isCurrentMailbox)

        if (!isCurrentMailbox) {
            setOnClickListener {
                if (isInMenuDrawer) {
                    context.trackMenuDrawerEvent(SWITCH_MAILBOX_NAME)
                } else {
                    context.trackAccountEvent(SWITCH_MAILBOX_NAME)
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    AccountUtils.switchToMailbox(mailbox.mailboxId)
                }
            }
        }
    }

    override fun getItemCount(): Int = mailboxes.count()

    fun setMailboxes(newMailboxes: List<Mailbox>) {
        mailboxes = newMailboxes
        notifyDataSetChanged()
    }

    class SwitchMailboxesViewHolder(val binding: ItemSwitchMailboxBinding) : RecyclerView.ViewHolder(binding.root)
}
