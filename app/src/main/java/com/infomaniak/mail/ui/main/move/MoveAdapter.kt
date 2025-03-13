/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.move

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.viewbinding.ViewBinding
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.databinding.ItemDividerHorizontalBinding
import com.infomaniak.mail.databinding.ItemSelectableFolderBinding
import com.infomaniak.mail.ui.main.menuDrawer.items.FolderViewHolder
import com.infomaniak.mail.ui.main.move.MoveAdapter.MoveFolderViewHolder
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.views.itemViews.SelectableFolderItemView
import com.infomaniak.mail.views.itemViews.setFolderUi
import javax.inject.Inject
import kotlin.math.min

class MoveAdapter @Inject constructor() : ListAdapter<Any, MoveFolderViewHolder>(FolderDiffCallback()) {

    var shouldDisplayIndent: Boolean = false

    private var selectedFolderId: String? = null

    private lateinit var onFolderClicked: (folderId: String) -> Unit
    private var onCollapseClicked: ((folderId: String, shouldCollapse: Boolean) -> Unit)? = null

    operator fun invoke(
        onFolderClicked: (folderId: String) -> Unit,
        onCollapseClicked: ((folderId: String, shouldCollapse: Boolean) -> Unit)? = null,
    ): MoveAdapter {
        this.onFolderClicked = onFolderClicked
        this.onCollapseClicked = onCollapseClicked

        return this
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setFolders(newSelectedFolderId: String, newShouldDisplayIndent: Boolean, newFolders: List<Any>) = runCatchingRealm {

        selectedFolderId = newSelectedFolderId

        val shouldForceNotify = newShouldDisplayIndent != shouldDisplayIndent
        shouldDisplayIndent = newShouldDisplayIndent

        submitList(newFolders)
        if (shouldForceNotify) notifyDataSetChanged()
    }

    override fun getItemCount(): Int = runCatchingRealm { currentList.size }.getOrDefault(0)

    override fun getItemViewType(position: Int): Int = runCatchingRealm {
        return when (currentList[position]) {
            is Folder -> DisplayType.FOLDER.layout
            else -> DisplayType.DIVIDER.layout
        }
    }.getOrDefault(super.getItemViewType(position))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoveFolderViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = if (viewType == DisplayType.FOLDER.layout) {
            ItemSelectableFolderBinding.inflate(layoutInflater, parent, false)
        } else {
            ItemDividerHorizontalBinding.inflate(layoutInflater, parent, false)
        }
        return MoveFolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MoveFolderViewHolder, position: Int, payloads: MutableList<Any>) = runCatchingRealm {
        if (payloads.firstOrNull() == Unit) {
            val isSelected = selectedFolderId == (currentList[position] as Folder).id
            (holder.binding as ItemSelectableFolderBinding).root.setSelectedState(isSelected)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }.getOrDefault(Unit)

    override fun onBindViewHolder(holder: MoveFolderViewHolder, position: Int) = with(holder.binding) {
        if (getItemViewType(position) == DisplayType.FOLDER.layout) {
            (this as ItemSelectableFolderBinding).root.displayFolder(currentList[position] as Folder)
        }
    }

    private fun SelectableFolderItemView.displayFolder(folder: Folder) {

        val iconId = when {
            folder.role != null -> folder.role!!.folderIconRes
            folder.isFavorite -> R.drawable.ic_folder_star
            else -> R.drawable.ic_folder
        }
        setFolderUi(folder, iconId, isSelected = folder.id == selectedFolderId)

        val folderIndent = when {
            !shouldDisplayIndent -> 0
            folder.role != null -> 0
            else -> min(folder.path.split(folder.separator).size - 1, FolderViewHolder.MAX_SUB_FOLDERS_INDENT)
        }
        setIndent(indent = folderIndent, hasCollapsableFolder = false, canBeCollapsed = false)

        setOnClickListener { if (folder.id != selectedFolderId) onFolderClicked.invoke(folder.id) }
    }

    class MoveFolderViewHolder(val binding: ViewBinding) : ViewHolder(binding.root)

    private enum class DisplayType(val layout: Int) {
        FOLDER(R.layout.item_selectable_folder),
        DIVIDER(R.layout.item_divider_horizontal),
    }

    private class FolderDiffCallback : DiffUtil.ItemCallback<Any>() {

        override fun areItemsTheSame(oldItem: Any, newItem: Any) = runCatchingRealm {
            return when {
                oldItem is Unit && newItem is Unit -> true // Unit is Divider item. They don't have any content, so always true.
                oldItem is Folder && newItem is Folder && oldItem.id == newItem.id -> true
                else -> false
            }
        }.getOrDefault(false)

        override fun areContentsTheSame(oldFolder: Any, newFolder: Any) = runCatchingRealm {
            oldFolder is Folder && newFolder is Folder &&
                    oldFolder.name == newFolder.name &&
                    oldFolder.isFavorite == newFolder.isFavorite &&
                    oldFolder.path == newFolder.path &&
                    oldFolder.threads.count() == newFolder.threads.count() &&
                    oldFolder.isHidden == newFolder.isHidden &&
                    oldFolder.canBeCollapsed == newFolder.canBeCollapsed
        }.getOrDefault(false)
    }
}
