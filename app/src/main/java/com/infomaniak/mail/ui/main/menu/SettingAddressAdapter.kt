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
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.R
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.databinding.ItemSettingAddressBinding
import com.infomaniak.mail.ui.main.menu.SettingAddressAdapter.SettingAddressViewHolder
import com.infomaniak.lib.core.R as RCore

class SettingAddressAdapter(
    private val mailboxes: List<Mailbox> = listOf(),
    private val popBackStack: () -> Unit,
) : RecyclerView.Adapter<SettingAddressViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingAddressViewHolder {
        return SettingAddressViewHolder(ItemSettingAddressBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: SettingAddressViewHolder, position: Int): Unit = with(holder.binding) {
        val mailbox = mailboxes[position]
        emailAddress.text = mailbox.email

        val unread = mailbox.unseenMessages
        var unreadText = unread.toString()
        if (unread >= 100) unreadText = "99+"
        unreadCount.apply {
            isGone = unread <= 0
            text = unreadText
        }

        setSelectedState(mailbox.objectId == MailData.currentMailbox?.objectId)
        addressCardview.setOnClickListener {
            MailData.selectMailbox(mailbox)
            popBackStack()
        }
    }

    private fun ItemSettingAddressBinding.setSelectedState(isSelected: Boolean) {
        val (color, style) = computeStyle(isSelected)
        envelopeIcon.setColorFilter(color)
        emailAddress.apply {
            setTextColor(color)
            setTextAppearance(style)
        }
        unreadCount.setTextAppearance(style)
    }

    private fun ItemSettingAddressBinding.computeStyle(isSelected: Boolean) =
        if (isSelected) ContextCompat.getColor(root.context, R.color.emphasizedTextColor) to R.style.Body_Highlighted
        else ContextCompat.getColor(root.context, RCore.color.title) to R.style.Body

    override fun getItemCount(): Int = mailboxes.count()

    class SettingAddressViewHolder(val binding: ItemSettingAddressBinding) : RecyclerView.ViewHolder(binding.root)
}
