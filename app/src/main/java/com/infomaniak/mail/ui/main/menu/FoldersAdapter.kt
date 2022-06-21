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
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.databinding.ItemFolderMenuDrawerBinding
import com.infomaniak.mail.ui.main.menu.FoldersAdapter.FolderViewHolder

class FoldersAdapter(private var folders: List<Folder> = emptyList(), private val openFolder: (folderName: String) -> Unit) :
    RecyclerView.Adapter<FolderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        return FolderViewHolder(ItemFolderMenuDrawerBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) = with(holder.binding) {
        val folder = folders[position]
        val badgeText = folder.getUnreadCountOrNull()

        when (folder.role) {
            Folder.FolderRole.INBOX -> setFolderUi(R.string.inboxFolder, R.drawable.ic_drawer_mailbox, badgeText)
            Folder.FolderRole.DRAFT -> setFolderUi(R.string.draftFolder, R.drawable.ic_edit_draft, badgeText)
            Folder.FolderRole.SENT -> setFolderUi(R.string.sentFolder, R.drawable.ic_sent_messages, badgeText)
            Folder.FolderRole.SPAM -> setFolderUi(R.string.spamFolder, R.drawable.ic_spam, badgeText)
            Folder.FolderRole.TRASH -> setFolderUi(R.string.trashFolder, R.drawable.ic_bin, badgeText)
            Folder.FolderRole.ARCHIVE -> setFolderUi(R.string.archiveFolder, R.drawable.ic_archive_folder, badgeText)
            Folder.FolderRole.COMMERCIAL -> setFolderUi(R.string.commercialFolder, R.drawable.ic_promotions, badgeText)
            Folder.FolderRole.SOCIALNETWORKS -> setFolderUi(R.string.socialNetworksFolder, R.drawable.ic_social_media, badgeText)
            null -> {
                val folderIcon = if (folder.isFavorite) R.drawable.ic_folder_star else R.drawable.ic_folder
                setFolderUi(folder.name, folderIcon, badgeText)
            }
        }
    }

    override fun getItemCount() = folders.size

    private fun ItemFolderMenuDrawerBinding.setFolderUi(
        @StringRes nameResource: Int,
        @DrawableRes iconId: Int,
        badgeText: String? = null
    ) {
        setFolderUi(root.context.getString(nameResource), iconId, badgeText)
    }

    private fun ItemFolderMenuDrawerBinding.setFolderUi(name: String, @DrawableRes iconId: Int, badgeText: String? = null) {
        folderName.text = name
        folderName.setCompoundDrawablesWithIntrinsicBounds(root.context.getDrawable(iconId), null, null, null)

        if (badgeText != null) folderBadge.text = badgeText

        root.setOnClickListener { openFolder.invoke(name) }
    }

    fun setFolders(newFolders: List<Folder>) {
        folders = newFolders
    }

    class FolderViewHolder(val binding: ItemFolderMenuDrawerBinding) : RecyclerView.ViewHolder(binding.root)
}
