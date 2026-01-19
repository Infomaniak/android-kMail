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

class FolderPickerAdapter @Inject constructor() : ListAdapter<Any, MoveFolderViewHolder>(FolderDiffCallback()) {

    private var shouldDisplayIndent: Boolean = false
    private var selectedFolderId: String? = null
    private lateinit var onFolderClicked: (folder: Folder?) -> Unit

    operator fun invoke(onFolderClicked: (folder: Folder?) -> Unit): FolderPickerAdapter {
        this.onFolderClicked = onFolderClicked
        return this
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setFolders(newSelectedFolderId: String, newShouldDisplayIndent: Boolean, newFolders: List<Any>) =
        runCatchingRealm {

            selectedFolderId = newSelectedFolderId

            val shouldForceNotify = newShouldDisplayIndent != shouldDisplayIndent
            shouldDisplayIndent = newShouldDisplayIndent
            submitList(newFolders)
            if (shouldForceNotify) notifyDataSetChanged()
        }

    override fun getItemCount(): Int = runCatchingRealm { currentList.size }.getOrDefault(0)

    override fun getItemViewType(position: Int): Int = runCatchingRealm {
        return when (currentList[position]) {
            is FolderUi -> DisplayType.FOLDER.layout
            SearchFolderElement.ALL_FOLDERS -> DisplayType.FOLDER.layout
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
            if (getItemViewType(position) == DisplayType.FOLDER.layout) {
                val folderUi = if (currentList[position] is FolderUi) currentList[position] as FolderUi else null
                (this as ItemSelectableFolderBinding).root.displayFolder(folderUi)
            }
        }
    }

    private fun SelectableFolderItemView.displayFolder(folderUi: FolderUi?) {
        if (folderUi == null) {
            runCatchingRealm {
                text = context.getLocalizedNameOrAllFolders(null)
                icon = AppCompatResources.getDrawable(context, R.drawable.ic_all_folders)
                setSelectedState(selectedFolderId == null)
            }
            setOnClickListener { onFolderClicked.invoke(null) }
        } else {
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
                oldItem is FolderUi && newItem is FolderUi && oldItem.folder.id == newItem.folder.id -> true
                else -> false
            }
        }.getOrDefault(false)

        override fun areContentsTheSame(oldFolder: Any, newFolder: Any) = runCatchingRealm {
            oldFolder is FolderUi && newFolder is FolderUi &&
                    oldFolder.folder.name == newFolder.folder.name &&
                    oldFolder.folder.isFavorite == newFolder.folder.isFavorite &&
                    oldFolder.folder.path == newFolder.folder.path &&
                    oldFolder.folder.threads.count() == newFolder.folder.threads.count() &&
                    oldFolder.depth == newFolder.depth
        }.getOrDefault(false)
    }

    enum class SearchFolderElement(val itemId: Int) {
        ALL_FOLDERS(0)
    }
}
