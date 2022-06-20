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
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.utils.loadAvatar
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.databinding.ItemSwitchUserAccountBinding
import com.infomaniak.mail.ui.main.menu.user.SwitchUserAccountsAdapter.SwitchUserAccountViewHolder
import com.infomaniak.mail.utils.toggleChevron

class SwitchUserAccountsAdapter(
    private var accounts: List<UiAccount> = emptyList(),
    private val popBackStack: () -> Unit,
) : RecyclerView.Adapter<SwitchUserAccountViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwitchUserAccountViewHolder {
        return SwitchUserAccountViewHolder(
            ItemSwitchUserAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: SwitchUserAccountViewHolder, position: Int): Unit = with(holder.binding) {
        val account = accounts[position]
        expandFirstMailbox(account, position)
        userAvatarImage.loadAvatar(account.user)
        userName.text = account.user.displayName
        userMailAddress.text = account.user.email
        accountCardview.setOnClickListener { toggleMailboxes(account) }
        addressesList.adapter = SwitchUserMailboxesAdapter(mailboxes = account.mailboxes, popBackStack = popBackStack)
    }

    private fun ItemSwitchUserAccountBinding.expandFirstMailbox(account: UiAccount, position: Int) {
        if (position == 0) {
            chevron.rotation = 180.0f
            toggleMailboxes(account)
        }
    }

    private fun ItemSwitchUserAccountBinding.toggleMailboxes(account: UiAccount) {
        account.collapsed = !account.collapsed
        chevron.toggleChevron(account.collapsed)
        addressesList.isGone = account.collapsed
    }

    override fun getItemCount(): Int = accounts.count()

    fun setAccounts(newAccounts: List<UiAccount>) {
        accounts = newAccounts
    }

    class SwitchUserAccountViewHolder(val binding: ItemSwitchUserAccountBinding) : RecyclerView.ViewHolder(binding.root)

    data class UiAccount(
        val user: User,
        var mailboxes: List<Mailbox>,
        var collapsed: Boolean = true,
    )
}
