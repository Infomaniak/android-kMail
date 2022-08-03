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
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isGone
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.utils.loadAvatar
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.databinding.ItemSwitchUserAccountBinding
import com.infomaniak.mail.ui.main.menu.user.SwitchUserAccountsAdapter.SwitchUserAccountViewHolder
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.toggleChevron

class SwitchUserAccountsAdapter(
    private var accounts: List<UiAccount> = emptyList(),
    private var currentMailboxObjectId: String? = null,
    private val onMailboxSelected: (Mailbox) -> Unit,
) : RecyclerView.Adapter<SwitchUserAccountViewHolder>() {

    private val mailboxesAdapter = mutableListOf<SwitchUserMailboxesAdapter>()
    private lateinit var isCollapsed: MutableList<Boolean>

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwitchUserAccountViewHolder {
        return SwitchUserAccountViewHolder(
            ItemSwitchUserAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: SwitchUserAccountViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.firstOrNull() is Unit) {
            holder.binding.updateAccountCardUiState(position)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: SwitchUserAccountViewHolder, position: Int): Unit = with(holder.binding) {
        val account = accounts[position]

        if (!isCollapsed[position]) chevron.rotation = ResourcesCompat.getFloat(context.resources, R.dimen.angleViewRotated)
        updateAccountCardUiState(position)

        userAvatarImage.loadAvatar(account.user)
        userName.text = account.user.displayName
        userMailAddress.text = account.user.email
        accountCardview.setOnClickListener { toggleMailboxes(position) }

        addressesList.adapter = if (position < mailboxesAdapter.size) {
            mailboxesAdapter[position]
                .also { it.notifyAdapter(account.mailboxes, currentMailboxObjectId) }
        } else {
            SwitchUserMailboxesAdapter(mailboxes = account.mailboxes, onMailboxSelected = onMailboxSelected)
                .also(mailboxesAdapter::add)
        }
    }

    private fun ItemSwitchUserAccountBinding.toggleMailboxes(position: Int) {
        if (isCollapsed[position]) closeAllBut(position)
        isCollapsed[position] = !isCollapsed[position]

        updateAccountCardUiState(position)
    }

    private fun closeAllBut(position: Int) {
        accounts.forEachIndexed { index, _ ->
            if (index != position) {
                isCollapsed[index] = true
                notifyItemChanged(index, Unit)
            }
        }
    }

    private fun ItemSwitchUserAccountBinding.updateAccountCardUiState(position: Int) {
        chevron.toggleChevron(isCollapsed[position])
        addressesList.isGone = isCollapsed[position]

        val backgroundColorResource = if (isCollapsed[position]) R.color.backgroundColor else R.color.selectedAccountCardviewColor
        val backgroundColor = ContextCompat.getColor(root.context, backgroundColorResource)
        accountCardview.setCardBackgroundColor(backgroundColor)
    }

    override fun getItemCount(): Int = accounts.count()

    fun notifyAdapter(newList: List<UiAccount>, newCurrentMailboxObjectId: String?) {
        isCollapsed = MutableList(newList.count()) { true }
        isCollapsed[0] = false
        currentMailboxObjectId = newCurrentMailboxObjectId
        DiffUtil.calculateDiff(UiAccountsListDiffCallback(accounts, newList)).dispatchUpdatesTo(this)
        accounts = newList
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
    )
}
