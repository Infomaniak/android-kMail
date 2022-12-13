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
package com.infomaniak.mail.ui.main.thread

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.databinding.ItemDetailedContactBinding
import com.infomaniak.mail.ui.main.thread.DetailedRecipientAdapter.DetailedRecipientViewHolder
import com.infomaniak.mail.utils.fillInUserNameAndEmail

class DetailedRecipientAdapter(
    private val onContactClicked: ((contact: Recipient) -> Unit)?,
) : RecyclerView.Adapter<DetailedRecipientViewHolder>() {

    private var recipients = emptyList<Recipient>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailedRecipientViewHolder {
        return DetailedRecipientViewHolder(ItemDetailedContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: DetailedRecipientViewHolder, position: Int): Unit = with(holder.binding) {
        val recipient = recipients[position]

        fillInUserNameAndEmail(recipient, name, emailAddress)

        name.setOnClickListener { onContactClicked?.invoke(recipient) }
    }

    override fun getItemCount(): Int = recipients.count()

    fun updateList(newList: List<Recipient>) {
        recipients = newList
    }

    class DetailedRecipientViewHolder(val binding: ItemDetailedContactBinding) : RecyclerView.ViewHolder(binding.root)
}
