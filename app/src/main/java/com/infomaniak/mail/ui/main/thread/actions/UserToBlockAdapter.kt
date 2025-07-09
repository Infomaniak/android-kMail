/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread.actions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.infomaniak.lib.core.utils.context
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackBlockUserAction
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.ItemContactBinding
import com.infomaniak.mail.ui.newMessage.ContactAdapter.ContactViewHolder

class UserToBlockAdapter(
    private val messagesToRecipients: List<Pair<Message, Recipient>>,
    private val onClickListener: (Message) -> Unit,
) : Adapter<ContactViewHolder>() {

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        return ContactViewHolder(ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount() = messagesToRecipients.count()

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) = with(holder.binding) {
        contactDetails.setCorrespondent(messagesToRecipients[position].second)
        root.setOnClickListener {
            context.trackBlockUserAction(MatomoName.SelectUser)
            onClickListener(messagesToRecipients[position].first)
        }
    }
}
