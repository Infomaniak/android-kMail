/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.folderPicker

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.viewbinding.ViewBinding
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.FolderUi
import com.infomaniak.mail.databinding.ItemDividerHorizontalBinding
import com.infomaniak.mail.databinding.ItemSelectableFolderBinding
import com.infomaniak.mail.ui.main.folderPicker.FolderPickerAdapter.MoveFolderViewHolder
import com.infomaniak.mail.ui.main.menuDrawer.items.FolderViewHolder.Companion.MAX_SUB_FOLDERS_INDENT
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.extensions.getLocalizedNameOrAllFolders
import com.infomaniak.mail.views.itemViews.SelectableFolderItemView
import com.infomaniak.mail.views.itemViews.setFolderUi
import javax.inject.Inject
import kotlin.math.min

class FolderPickerAdapter @Inject constructor() : ListAdapter<FolderPickerItem, MoveFolderViewHolder>(FolderDiffCallback()) {

    private var shouldDisplayIndent: Boolean = false
    private var selectedFolderId: String? = null
    private lateinit var onFolderClicked: (folder: Folder?) -> Unit

    operator fun invoke(onFolderClicked: (folder: Folder?) -> Unit): FolderPickerAdapter {
        this.onFolderClicked = onFolderClicked
        return this
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setFolders(newSelectedFolderId: String?, newShouldDisplayIndent: Boolean, newFolders: List<FolderPickerItem>) {
        runCatchingRealm {
            selectedFolderId = newSelectedFolderId

            val shouldForceNotify = newShouldDisplayIndent != shouldDisplayIndent
            shouldDisplayIndent = newShouldDisplayIndent
            submitList(newFolders)
            if (shouldForceNotify) notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = runCatchingRealm { currentList.size }.getOrDefault(0)

    override fun getItemViewType(position: Int): Int = runCatchingRealm {
        return when (currentList[position]) {
            is FolderPickerItem.Folder -> DisplayType.FOLDER.layout
            is FolderPickerItem.AllFolders -> DisplayType.FOLDER.layout
            is FolderPickerItem.Divider -> DisplayType.DIVIDER.layout
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

    override fun onBindViewHolder(holder: MoveFolderViewHolder, position: Int): Unit = with(holder.binding) {
        runCatchingRealm {
            when (val item = getItem(position)) {
                is FolderPickerItem.Folder -> (this as ItemSelectableFolderBinding).root.displayFolder(item.folderUi)
                is FolderPickerItem.AllFolders -> (this as ItemSelectableFolderBinding).root.displayAllFoldersItem()
                else -> Unit
            }
        }
    }

    private fun SelectableFolderItemView.displayAllFoldersItem() {
        text = context.getLocalizedNameOrAllFolders(null)
        icon = AppCompatResources.getDrawable(context, R.drawable.ic_all_folders)
        setSelectedState(selectedFolderId == null)
        setOnClickListener { onFolderClicked.invoke(null) }
    }

    private fun SelectableFolderItemView.displayFolder(folderUi: FolderUi) {
        val folder = folderUi.folder
        val iconId = when {
            folder.role != null -> folder.role!!.folderIconRes
            folder.isFavorite -> R.drawable.ic_folder_star
            else -> R.drawable.ic_folder
        }
        setFolderUi(folder, iconId, isSelected = folder.id == selectedFolderId)

        val folderIndent = when {
            !shouldDisplayIndent -> 0
            else -> min(folderUi.depth, MAX_SUB_FOLDERS_INDENT)
        }
        setIndent(indent = folderIndent)
        setOnClickListener { if (folder.id != selectedFolderId) onFolderClicked.invoke(folder) }
    }

    class MoveFolderViewHolder(val binding: ViewBinding) : ViewHolder(binding.root)

    private enum class DisplayType(val layout: Int) {
        FOLDER(R.layout.item_selectable_folder),
        DIVIDER(R.layout.item_divider_horizontal),
    }

    private class FolderDiffCallback : DiffUtil.ItemCallback<FolderPickerItem>() {
        override fun areItemsTheSame(oldItem: FolderPickerItem, newItem: FolderPickerItem) = runCatchingRealm {
            return when (oldItem) {
                is FolderPickerItem.Divider if newItem is FolderPickerItem.Divider -> true // Dividers don't have any content, so always true.
                is FolderPickerItem.Folder if newItem is FolderPickerItem.Folder && oldItem.folderUi.folder.id == newItem.folderUi.folder.id -> true
                else -> false
            }
        }.getOrDefault(false)

        override fun areContentsTheSame(oldFolder: FolderPickerItem, newFolder: FolderPickerItem) = runCatchingRealm {
            oldFolder is FolderPickerItem.Folder && newFolder is FolderPickerItem.Folder &&
                    oldFolder.folderUi.folder.name == newFolder.folderUi.folder.name &&
                    oldFolder.folderUi.folder.isFavorite == newFolder.folderUi.folder.isFavorite &&
                    oldFolder.folderUi.folder.path == newFolder.folderUi.folder.path &&
                    oldFolder.folderUi.folder.threads.count() == newFolder.folderUi.folder.threads.count() &&
                    oldFolder.folderUi.depth == newFolder.folderUi.depth
        }.getOrDefault(false)
    }
}
