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
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.databinding.ItemCustomFolderBinding
import com.infomaniak.mail.ui.main.menu.CustomFoldersAdapter.CustomFolderViewHolder

class CustomFoldersAdapter(private var customFolders: List<Folder> = emptyList()) :
    RecyclerView.Adapter<CustomFolderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomFolderViewHolder {
        return CustomFolderViewHolder(ItemCustomFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: CustomFolderViewHolder, position: Int) {
        holder.binding.customFolderName.text = customFolders[position].name
    }

    override fun getItemCount() = customFolders.size

    fun setCustomFolders(newCustomFolders: List<Folder>) {
        customFolders = newCustomFolders
    }

    class CustomFolderViewHolder(val binding: ItemCustomFolderBinding) : RecyclerView.ViewHolder(binding.root)
}
