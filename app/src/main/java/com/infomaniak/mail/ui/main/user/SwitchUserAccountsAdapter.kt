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
package com.infomaniak.mail.ui.main.user

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isGone
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.databinding.ItemSwitchUserAccountBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.user.SwitchUserAccountsAdapter.SwitchUserAccountViewHolder
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.toggleChevron

class SwitchUserAccountsAdapter(
    private var accounts: List<UiAccount> = emptyList(),
    private var currentMailboxObjectId: String? = null,
    private val onMailboxSelected: (Mailbox) -> Unit,
) : RecyclerView.Adapter<SwitchUserAccountViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwitchUserAccountViewHolder {
        return SwitchUserAccountViewHolder(
            ItemSwitchUserAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: SwitchUserAccountViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.firstOrNull() is Unit) {
            holder.binding.updateAccountCardUiState(accounts[position].isCollapsed)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: SwitchUserAccountViewHolder, position: Int): Unit = with(holder.binding) {
        val account = accounts[position]

        if (!account.isCollapsed) chevron.rotation = ResourcesCompat.getFloat(context.resources, R.dimen.angleViewRotated)
        updateAccountCardUiState(account.isCollapsed)

        userAvatar.loadAvatar(account.user)
        userName.text = account.user.displayName
        userMailAddress.text = account.user.email
        accountCardview.setOnClickListener { toggleMailboxes(position) }

        addressesList.adapter = SwitchUserMailboxesAdapter(
            mailboxes = account.mailboxes,
            currentMailboxObjectId = currentMailboxObjectId,
            onMailboxSelected = onMailboxSelected,
        )
    }

    private fun ItemSwitchUserAccountBinding.toggleMailboxes(position: Int) {
        val account = accounts[position]
        if (account.isCollapsed) closeAllBut(position)
        account.isCollapsed = !account.isCollapsed

        updateAccountCardUiState(account.isCollapsed)
    }

    private fun closeAllBut(position: Int) {
        accounts.forEachIndexed { index, uiAccount ->
            if (index != position) {
                uiAccount.isCollapsed = true
                notifyItemChanged(index, Unit)
            }
        }
    }

    private fun ItemSwitchUserAccountBinding.updateAccountCardUiState(isCollapsed: Boolean) {
        chevron.toggleChevron(isCollapsed)
        addressesList.isGone = isCollapsed

        val backgroundColorResource = if (isCollapsed) R.color.backgroundColor else R.color.backgroundSecondaryColor
        val backgroundColor = ContextCompat.getColor(root.context, backgroundColorResource)
        accountCardview.setCardBackgroundColor(backgroundColor)
    }

    override fun getItemCount(): Int = accounts.count()

    fun notifyAdapter(newList: List<UiAccount>) {
        collapseAllExceptFirst(newList)
        currentMailboxObjectId = MainViewModel.currentMailboxObjectId.value
        DiffUtil.calculateDiff(UiAccountsListDiffCallback(accounts, newList)).dispatchUpdatesTo(this)
        accounts = newList
    }

    private fun collapseAllExceptFirst(newList: List<UiAccount>) {
        newList.forEachIndexed { index, uiAccount ->
            uiAccount.isCollapsed = index != 0
        }
    }

    class SwitchUserAccountViewHolder(val binding: ItemSwitchUserAccountBinding) : RecyclerView.ViewHolder(binding.root)

    private class UiAccountsListDiffCallback(
        private val oldList: List<UiAccount>,
        private val newList: List<UiAccount>,
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldIndex: Int, newIndex: Int): Boolean {
            return oldList[oldIndex].user.id == newList[newIndex].user.id
        }

        override fun areContentsTheSame(oldIndex: Int, newIndex: Int): Boolean {
            val oldItem = oldList[oldIndex]
            val newItem = newList[newIndex]

            return if (oldItem.mailboxes.size == newItem.mailboxes.size) {
                var areContentsTheSame = true
                oldItem.mailboxes.forEachIndexed { index, oldMailbox ->
                    if (oldMailbox.unseenMessages != newItem.mailboxes[index].unseenMessages) {
                        areContentsTheSame = false
                        return@forEachIndexed
                    }
                }
                areContentsTheSame
            } else {
                false
            }
        }
    }

    data class UiAccount(
        val user: User,
        var mailboxes: List<Mailbox>,
    ) {
        var isCollapsed = true
    }
}
