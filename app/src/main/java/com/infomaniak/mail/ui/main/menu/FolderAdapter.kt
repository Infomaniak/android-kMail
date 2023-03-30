/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.utils.context
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.*
import com.infomaniak.mail.databinding.ItemFolderMenuDrawerBinding
import com.infomaniak.mail.ui.main.menu.FolderAdapter.FolderViewHolder
import com.infomaniak.mail.views.MenuDrawerItemView.*
import kotlin.math.min
import com.infomaniak.lib.core.R as RCore

class FolderAdapter(
    private var folders: List<Folder> = emptyList(),
    private var currentFolderId: String? = null,
    private val onClick: (folderId: String) -> Unit,
    private val isInMenuDrawer: Boolean = true,
) : RecyclerView.Adapter<FolderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        return FolderViewHolder(ItemFolderMenuDrawerBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.firstOrNull() == Unit) {
            val folder = folders[position]
            val isSelected = currentFolderId == folder.id
            holder.binding.item.setSelectedState(isSelected)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) = with(holder.binding) {

        val folder = folders[position]

        val folderName = folder.getLocalizedName(context)

        val badge = when (folder.role) {
            FolderRole.DRAFT -> folder.threads.count()
            FolderRole.SENT, FolderRole.TRASH -> 0
            else -> folder.unreadCount
        }

        folder.role?.let {
            setFolderUi(folder.id, folderName, it.folderIconRes, badge, it.matomoValue)
        } ?: run {
            val indentLevel = folder.path.split(folder.separator).size - 1
            setFolderUi(
                id = folder.id,
                name = folderName,
                iconId = if (folder.isFavorite) R.drawable.ic_folder_star else R.drawable.ic_folder,
                badgeText = badge,
                trackerName = "customFolder",
                trackerValue = indentLevel.toFloat(),
                folderIndent = min(indentLevel, MAX_SUB_FOLDERS_INDENT),
            )
        }
    }

    override fun getItemCount() = folders.size

    private fun ItemFolderMenuDrawerBinding.setFolderUi(
        id: String,
        name: String,
        @DrawableRes iconId: Int,
        badgeText: Int,
        trackerName: String,
        trackerValue: Float? = null,
        folderIndent: Int? = null,
    ) = with(item) {
        text = name
        icon = AppCompatResources.getDrawable(context, iconId)
        indent = context.resources.getDimension(RCore.dimen.marginStandard).toInt() * (folderIndent ?: 0)
        badge = if (isInMenuDrawer) badgeText else 0
        itemStyle = if (isInMenuDrawer) SelectionStyle.MENU_DRAWER else SelectionStyle.OTHER
        textWeight = if (isInMenuDrawer) TextWeight.MEDIUM else TextWeight.REGULAR

        setSelectedState(currentFolderId == id)

        setOnClickListener {
            if (isInMenuDrawer) context.trackMenuDrawerEvent(trackerName, value = trackerValue)
            onClick.invoke(id)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setFolders(newFolders: List<Folder>, newCurrentFolderId: String?) {
        // TODO: Temporary fix, we use a DiffUtil while waiting to realize it with realm or in async
        val diffResult = DiffUtil.calculateDiff(FolderDiffCallback(newFolders))
        currentFolderId = newCurrentFolderId
        folders = newFolders
        diffResult.dispatchUpdatesTo(this)
    }

    fun updateSelectedState(newCurrentFolderId: String) {
        val previousCurrentFolderId = currentFolderId
        currentFolderId = newCurrentFolderId
        previousCurrentFolderId?.let(::notifyCurrentItem)
        notifyCurrentItem(newCurrentFolderId)
    }

    private fun notifyCurrentItem(folderId: String) {
        val position = folders.indexOfFirst { it.id == folderId }
        notifyItemChanged(position)
    }

    private companion object {
        const val MAX_SUB_FOLDERS_INDENT = 2
    }

    class FolderViewHolder(val binding: ItemFolderMenuDrawerBinding) : RecyclerView.ViewHolder(binding.root)

    inner class FolderDiffCallback(private val newFolders: List<Folder>) : DiffUtil.Callback() {

        override fun getOldListSize() = folders.count()

        override fun getNewListSize() = newFolders.count()

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return folders[oldItemPosition].id == newFolders[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val (oldFolder, newFolder) = folders[oldItemPosition] to newFolders[newItemPosition]
            return oldFolder.isFavorite == newFolder.isFavorite &&
                    oldFolder.path.count() == newFolder.path.count() &&
                    oldFolder.role == newFolder.role &&
                    oldFolder.threads.count() == newFolder.threads.count() &&
                    oldFolder.unreadCount == newFolder.unreadCount &&
                    oldFolder.name == newFolder.name
        }

    }
}
