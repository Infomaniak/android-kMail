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
import com.infomaniak.mail.data.cache.AppSettingsController
import com.infomaniak.mail.data.cache.MailboxInfoController
import com.infomaniak.mail.databinding.ItemSettingAddressBinding
import com.infomaniak.mail.ui.main.menu.SettingAccountAdapter.UiMailbox
import com.infomaniak.lib.core.R as RCore

class SettingAddressAdapter(
    private val uiMailboxes: List<UiMailbox> = listOf()
) : RecyclerView.Adapter<SettingAddressAdapter.SettingAddressViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingAddressViewHolder {
        return SettingAddressViewHolder(ItemSettingAddressBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: SettingAddressViewHolder, position: Int): Unit = with(holder.binding) {
        val uiMailbox = uiMailboxes[position]
        emailAddress.text = uiMailbox.email

        val unread = uiMailbox.unreadCount
        var unreadText = unread.toString()
        if (unread > 100) unreadText = "99+"
        unreadCount.isGone = unread == 0
        unreadCount.text = unreadText

        // TODO: Adapt to new select method not in mailbox anymore
        val mailbox = MailboxInfoController.getMailbox(uiMailbox.objectId)
        setSelectedState(mailbox?.mailboxId == AppSettingsController.getAppSettings().currentMailboxId, uiMailbox)
        addressCardview.setOnClickListener { mailbox?.select() }
    }

    private fun ItemSettingAddressBinding.setSelectedState(isSelected: Boolean, mailbox: UiMailbox) {
        val (color, style) = computeStyle(isSelected)
        envelopeIcon.setColorFilter(color)
        emailAddress.setTextColor(color)
        emailAddress.setTextAppearance(style)
        unreadCount.setTextAppearance(style)
    }

    private fun ItemSettingAddressBinding.computeStyle(isSelected: Boolean) =
        if (isSelected) ContextCompat.getColor(root.context, R.color.emphasizedTextColor) to R.style.Body_Highlighted
        else ContextCompat.getColor(root.context, RCore.color.title) to R.style.Body

    override fun getItemCount(): Int = uiMailboxes.count()

    class SettingAddressViewHolder(val binding: ItemSettingAddressBinding) : RecyclerView.ViewHolder(binding.root)
}
