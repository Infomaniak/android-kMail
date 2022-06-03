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
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.utils.loadAvatar
import com.infomaniak.mail.databinding.ItemSettingAccountBinding
import com.infomaniak.mail.utils.toggleChevron

class SettingAccountAdapter(
    private val accounts: List<UiAccount> = listOf()
) : RecyclerView.Adapter<SettingAccountAdapter.SettingAccountViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingAccountViewHolder {
        return SettingAccountViewHolder(ItemSettingAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: SettingAccountViewHolder, position: Int): Unit = with(holder.binding) {
        val account = accounts[position]
        userAvatarImage.loadAvatar(account.user)
        userName.text = account.user.displayName
        userMailAddress.text = account.user.email
        accountCardview.setOnClickListener { toggleMailboxes(account) }
        recyclerViewAddress.adapter = SettingAddressAdapter(account.mailboxes)
    }

    private fun ItemSettingAccountBinding.toggleMailboxes(account: UiAccount) {
        account.collapsed = !account.collapsed
        chevron.toggleChevron(account.collapsed)
        recyclerViewAddress.isGone = account.collapsed
    }

    override fun getItemCount(): Int = accounts.count()

    class SettingAccountViewHolder(val binding: ItemSettingAccountBinding) : RecyclerView.ViewHolder(binding.root)

    data class UiAccount(
        val user: User,
        var mailboxes: List<UiMailbox>,
        var collapsed: Boolean = true,
    )

    data class UiMailbox(
        val objectId: String,
        val email: String,
        val unreadCount: Int,
    )
}
