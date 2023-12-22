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
package com.infomaniak.mail.ui.main.thread.calendar

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.data.models.calendar.Attendee
import com.infomaniak.mail.databinding.ItemAttendeeAvatarNameEmailBinding

class AttendeesAdapter(
    private val attendees: List<Attendee>
) : RecyclerView.Adapter<AttendeesAdapter.AttendeesViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendeesViewHolder {
        return AttendeesViewHolder(ItemAttendeeAvatarNameEmailBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: AttendeesViewHolder, position: Int): Unit = with(holder.binding) {
        val attendee = attendees[position]
        holder.binding.avatarNameEmailView.setCorrespondent(attendee)
    }

    override fun getItemCount(): Int = attendees.count()

    class AttendeesViewHolder(val binding: ItemAttendeeAvatarNameEmailBinding) : RecyclerView.ViewHolder(binding.root)
}
