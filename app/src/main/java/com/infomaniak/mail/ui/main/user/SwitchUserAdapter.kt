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

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ItemSwitchUserAccountBinding
import com.infomaniak.mail.ui.main.user.SwitchUserAdapter.SwitchUserAccountViewHolder

@SuppressLint("NotifyDataSetChanged")
class SwitchUserAdapter(
    val currentUserId: Int,
    val onChangingUserAccount: ((User) -> Unit)
) : RecyclerView.Adapter<SwitchUserAccountViewHolder>() {

    private var accounts: List<User> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwitchUserAccountViewHolder {
        return SwitchUserAccountViewHolder(
            ItemSwitchUserAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: SwitchUserAccountViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.firstOrNull() is Unit) {
            holder.binding.updateSelectedUi(position)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: SwitchUserAccountViewHolder, position: Int): Unit = with(holder.binding) {
        val account = accounts[position]

        userAvatar.loadAvatar(account)
        userName.text = account.displayName
        userMailAddress.text = account.email
        updateSelectedUi(position)
        accountCardview.setOnClickListener { selectAccount(position) }
    }

    private fun ItemSwitchUserAccountBinding.updateSelectedUi(position: Int) {
        val isSelected = accounts[position].id == currentUserId
        val backgroundColorResource = if (isSelected) R.color.backgroundSecondaryColor else R.color.backgroundColor
        val backgroundColor = ContextCompat.getColor(root.context, backgroundColorResource)
        accountCardview.setCardBackgroundColor(backgroundColor)
        checkmark.isVisible = isSelected
    }

    private fun selectAccount(position: Int) = onChangingUserAccount(accounts[position])

    override fun getItemCount(): Int = accounts.count()

    fun initializeAccounts(newList: List<User>) {
        accounts = newList
        notifyItemRangeInserted(0, accounts.count())
    }

    class SwitchUserAccountViewHolder(val binding: ItemSwitchUserAccountBinding) : RecyclerView.ViewHolder(binding.root)
}
