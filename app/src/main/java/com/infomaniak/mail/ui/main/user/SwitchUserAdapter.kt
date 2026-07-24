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
package com.infomaniak.mail.ui.main.user

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.google.android.material.color.MaterialColors
import com.infomaniak.core.auth.models.user.User
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ItemSwitchUserAccountBinding
import com.infomaniak.mail.ui.main.user.SwitchUserAdapter.SwitchUserAccountViewHolder
import com.google.android.material.R as RMaterial

@SuppressLint("NotifyDataSetChanged")
class SwitchUserAdapter(
    val currentUserId: Int,
    val onChangingUserAccount: ((User) -> Unit),
    val onOpenContactCard: ((User) -> Unit)? = null,
) : Adapter<SwitchUserAccountViewHolder>() {

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

    override fun onBindViewHolder(holder: SwitchUserAccountViewHolder, position: Int) = with(holder.binding) {
        val account = accounts[position]

        userAvatar.loadUserAvatar(account)
        userName.text = account.displayName
        userMailAddress.text = account.email
        updateSelectedUi(position)
        accountCardview.setOnClickListener { selectAccount(position) }
        qrcode.setOnClickListener { if (account.id == currentUserId) onOpenContactCard?.invoke(account) }
    }

    private fun ItemSwitchUserAccountBinding.updateSelectedUi(position: Int) {
        val isCurrentUser = accounts[position].id == currentUserId
        qrcode.isVisible = isCurrentUser && onOpenContactCard != null
        qrcodeImage.background.alpha = (0.1 * 255).toInt()
        accountCardview.isSelected = isCurrentUser
        accountCardview.setCardBackgroundColor(
            if (isCurrentUser && accounts.size > 1) {
                MaterialColors.getColor(accountCardview, RMaterial.attr.colorPrimaryContainer)
            } else {
                accountCardview.context.getColor(R.color.backgroundColorSecondary)
            }
        )
    }

    private fun selectAccount(position: Int) = onChangingUserAccount(accounts[position])

    override fun getItemCount(): Int = accounts.count()

    fun initializeAccounts(newList: List<User>) {
        accounts = newList
        notifyDataSetChanged()
    }

    fun animateCurrentUserQrCode(recyclerView: RecyclerView) {
        recyclerView.post {
            for (i in 0 until recyclerView.childCount) {
                val child = recyclerView.getChildAt(i)
                val viewHolder = recyclerView.getChildViewHolder(child) as? SwitchUserAccountViewHolder ?: continue
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && accounts.getOrNull(position)?.id == currentUserId) {
                    ObjectAnimator.ofFloat(viewHolder.binding.qrcode, View.ROTATION_Y, 180f, 360f).apply {
                        duration = 800
                        interpolator = DecelerateInterpolator()
                        start()
                    }
                    break
                }
            }
        }
    }

    class SwitchUserAccountViewHolder(val binding: ItemSwitchUserAccountBinding) : ViewHolder(binding.root)
}
