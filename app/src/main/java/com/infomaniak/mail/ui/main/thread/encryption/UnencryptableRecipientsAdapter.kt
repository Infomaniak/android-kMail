/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread.encryption

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.databinding.ItemAvatarNameEmailBinding
import com.infomaniak.mail.ui.main.thread.encryption.UnencryptableRecipientsAdapter.UserNameEmailViewHolder

class UnencryptableRecipientsAdapter(private val recipients: Array<Recipient>) : Adapter<UserNameEmailViewHolder>() {

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserNameEmailViewHolder {
        return UserNameEmailViewHolder(
            ItemAvatarNameEmailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun getItemCount() = recipients.count()

    override fun onBindViewHolder(holder: UserNameEmailViewHolder, position: Int) = with(holder.binding) {
        avatarNameEmailView.setCorrespondent(recipients[position])
    }

    class UserNameEmailViewHolder(val binding: ItemAvatarNameEmailBinding) : ViewHolder(binding.root)
}

