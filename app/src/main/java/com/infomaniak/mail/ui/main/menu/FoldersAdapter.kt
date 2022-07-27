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
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.databinding.ItemFolderMenuDrawerBinding
import com.infomaniak.mail.ui.main.menu.FoldersAdapter.FolderViewHolder
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.setMargins
import com.infomaniak.lib.core.R as RCore

class FoldersAdapter(
    private var folders: List<Folder> = emptyList(),
    private val openFolder: (folderName: String) -> Unit,
) : RecyclerView.Adapter<FolderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        return FolderViewHolder(ItemFolderMenuDrawerBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) = with(holder.binding) {
        val folder = folders[position]
        val badgeText = folder.getUnreadCountOrNull()

        folder.role?.let {
            setFolderUi(context.getString(it.folderNameRes), it.folderIconRes, badgeText)
        } ?: setFolderUi(
            name = folder.name,
            iconId = if (folder.isFavorite) R.drawable.ic_folder_star else R.drawable.ic_folder,
            badgeText = badgeText,
            indent = folder.path.split(folder.separator).size - 1,
        )
    }

    override fun getItemCount() = folders.size

    private fun ItemFolderMenuDrawerBinding.setFolderUi(
        name: String,
        @DrawableRes iconId: Int,
        badgeText: String? = null,
        indent: Int? = null,
    ) {
        folderName.apply {
            text = name
            setCompoundDrawablesWithIntrinsicBounds(AppCompatResources.getDrawable(context, iconId), null, null, null)
            setMargins(left = resources.getDimension(RCore.dimen.marginStandard).toInt() * (indent ?: 0))
        }

        folderBadge.text = badgeText

        root.setOnClickListener { openFolder.invoke(name) }
    }

    fun setFolders(newFolders: List<Folder>) {
        folders = newFolders
        notifyDataSetChanged()
    }

    class FolderViewHolder(val binding: ItemFolderMenuDrawerBinding) : RecyclerView.ViewHolder(binding.root)
}
