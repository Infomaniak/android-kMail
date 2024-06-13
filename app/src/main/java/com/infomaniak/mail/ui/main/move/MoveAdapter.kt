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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.viewbinding.ViewBinding
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.databinding.ItemSelectableFolderBinding
import com.infomaniak.mail.ui.main.move.MoveAdapter.FolderViewHolder
import com.infomaniak.mail.utils.UiUtils
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.views.itemViews.SelectableFolderItemView
import com.infomaniak.mail.views.itemViews.SelectableItemView
import javax.inject.Inject
import kotlin.math.min

class MoveAdapter @Inject constructor() : ListAdapter<Folder, FolderViewHolder>(FolderDiffCallback()) {

    private var currentFolderId: String? = null

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

    override fun getItemCount(): Int = runCatchingRealm { currentList.size }.getOrDefault(0)

    override fun getItemViewType(position: Int): Int = R.layout.item_selectable_folder

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = ItemSelectableFolderBinding.inflate(layoutInflater, parent, false)
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int, payloads: MutableList<Any>) = runCatchingRealm {
        if (payloads.firstOrNull() == Unit) {
            (holder.binding as ItemSelectableFolderBinding).root.setSelectedState(currentFolderId == currentList[position].id)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }.getOrDefault(Unit)

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) = with(holder.binding) {
        (this as ItemSelectableFolderBinding).root.displayFolder(currentList[position])
    }

    private fun SelectableItemView.displayFolder(folder: Folder) {
        folder.role?.let {
            setFolderUi(folder, it.folderIconRes)
        } ?: run {
            val indentLevel = if (folder.shouldDisplayIndent) folder.path.split(folder.separator).size - 1 else 0
            setFolderUi(
                folder = folder,
                iconId = if (folder.isFavorite) R.drawable.ic_folder_star else R.drawable.ic_folder,
                folderIndent = min(indentLevel, MAX_SUB_FOLDERS_INDENT),
            )
        }
    }

    private fun SelectableItemView.setFolderUi(folder: Folder, @DrawableRes iconId: Int, folderIndent: Int = 0) {
        tag = if (folder.shouldDisplayDivider) null else UiUtils.IGNORE_DIVIDER_TAG
        text = folder.getLocalizedName(context)
        icon = AppCompatResources.getDrawable(context, iconId)
        setSelectedState(currentFolderId == folder.id)
        if (this is SelectableFolderItemView) setIndent(folderIndent)
        setOnClickListener { onFolderClicked.invoke(folder.id) }
    }

    fun setFolders(newFolders: List<Folder>, newCurrentFolderId: String? = null) = runCatchingRealm {
        newCurrentFolderId?.let { currentFolderId = it }
        submitList(newFolders)
    }

    companion object {
        private const val MAX_SUB_FOLDERS_INDENT = 2
    }

    class FolderViewHolder(val binding: ViewBinding) : ViewHolder(binding.root)

    private class FolderDiffCallback : DiffUtil.ItemCallback<Folder>() {

        override fun areItemsTheSame(oldFolder: Folder, newFolder: Folder) = runCatchingRealm {
            return oldFolder.id == newFolder.id
        }.getOrDefault(false)

        override fun areContentsTheSame(oldFolder: Folder, newFolder: Folder) = runCatchingRealm {
            oldFolder.name == newFolder.name &&
                    oldFolder.isFavorite == newFolder.isFavorite &&
                    oldFolder.path == newFolder.path &&
                    oldFolder.unreadCountDisplay == newFolder.unreadCountDisplay &&
                    oldFolder.threads.count() == newFolder.threads.count() &&
                    oldFolder.isHidden == newFolder.isHidden &&
                    oldFolder.canBeCollapsed == newFolder.canBeCollapsed &&
                    oldFolder.shouldDisplayDivider == newFolder.shouldDisplayDivider &&
                    oldFolder.shouldDisplayIndent == newFolder.shouldDisplayIndent
        }.getOrDefault(false)
    }
}
