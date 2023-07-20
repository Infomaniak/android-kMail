/*
 * Infomaniak ikMail - Android
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
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.*
import com.infomaniak.mail.databinding.*
import com.infomaniak.mail.ui.main.menu.FolderAdapter.FolderViewHolder
import com.infomaniak.mail.utils.UnreadDisplay
import com.infomaniak.mail.views.decoratedTextItemView.DecoratedTextItemView
import com.infomaniak.mail.views.decoratedTextItemView.FolderMenuDrawerItemView
import com.infomaniak.mail.views.decoratedTextItemView.SimpleFolderItemView
import kotlin.math.min
import com.infomaniak.lib.core.R as RCore

class FolderAdapter(
    private val isInMenuDrawer: Boolean,
    private var currentFolderId: String? = null,
    private val onClick: (folderId: String) -> Unit,
) : RecyclerView.Adapter<FolderViewHolder>() {

    private val foldersDiffer = AsyncListDiffer(this, FolderDiffCallback())

    private inline val folders get() = foldersDiffer.currentList

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = if (viewType == DisplayType.SIMPLE_FOLDER.layout) {
            ItemSimpleFolderBinding.inflate(layoutInflater, parent, false)
        } else {
            ItemFolderMenuDrawerBinding.inflate(layoutInflater, parent, false)
        }

        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.firstOrNull() == Unit) {
            val folder = folders[position]
            if (getItemViewType(position) == DisplayType.SIMPLE_FOLDER.layout) {
                (holder.binding as ItemSimpleFolderBinding).root.setSelectedState(currentFolderId == folder.id)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) = with(holder.binding) {
        val folder = folders[position]

        if (getItemViewType(position) == DisplayType.SIMPLE_FOLDER.layout) {
            (this as ItemSimpleFolderBinding).root.displayFolder(folder)
        } else {
            (this as ItemFolderMenuDrawerBinding).root.displayMenuDrawerFolder(folder)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (isInMenuDrawer) DisplayType.MENU_DRAWER.layout else DisplayType.SIMPLE_FOLDER.layout
    }

    override fun getItemCount() = folders.size

    private fun FolderMenuDrawerItemView.displayMenuDrawerFolder(folder: Folder) {
        val unread = when (folder.role) {
            FolderRole.DRAFT -> UnreadDisplay(folder.threads.count())
            FolderRole.SENT, FolderRole.TRASH -> UnreadDisplay(0)
            else -> folder.unreadCountDisplay
        }

        displayFolder(folder, unread)
    }

    private fun DecoratedTextItemView.displayFolder(folder: Folder, unread: UnreadDisplay? = null) {
        val folderName = folder.getLocalizedName(context)

        folder.role?.let {
            setFolderUi(folder.id, folderName, it.folderIconRes, unread, it.matomoValue)
        } ?: run {
            val indentLevel = folder.path.split(folder.separator).size - 1
            setFolderUi(
                folderId = folder.id,
                name = folderName,
                iconId = if (folder.isFavorite) R.drawable.ic_folder_star else R.drawable.ic_folder,
                unread = unread,
                trackerName = "customFolder",
                trackerValue = indentLevel.toFloat(),
                folderIndent = min(indentLevel, MAX_SUB_FOLDERS_INDENT),
            )
        }
    }

    private fun DecoratedTextItemView.setFolderUi(
        folderId: String,
        name: String,
        @DrawableRes iconId: Int,
        unread: UnreadDisplay?,
        trackerName: String,
        trackerValue: Float? = null,
        folderIndent: Int? = null,
    ) {
        text = name
        icon = AppCompatResources.getDrawable(context, iconId)

        when (this) {
            is FolderMenuDrawerItemView -> {
                indent = computeIndent(context, folderIndent)
                unreadCount = unread?.count ?: 0
                isPastilleDisplayed = unread?.shouldDisplayPastille ?: false
            }
            is SimpleFolderItemView -> {
                indent = computeIndent(context, folderIndent)
                setSelectedState(currentFolderId == folderId)
            }
        }

        setOnClickListener {
            if (isInMenuDrawer) context.trackMenuDrawerEvent(trackerName, value = trackerValue)
            onClick.invoke(folderId)
        }
    }

    private fun computeIndent(context: Context, folderIndent: Int?): Int {
        return context.resources.getDimension(RCore.dimen.marginStandard).toInt() * (folderIndent ?: 0)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setFolders(newFolders: List<Folder>, newCurrentFolderId: String?) {
        currentFolderId = newCurrentFolderId
        foldersDiffer.submitList(newFolders)
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

    private enum class DisplayType(val layout: Int) {
        SIMPLE_FOLDER(R.layout.item_simple_folder),
        MENU_DRAWER(R.layout.item_folder_menu_drawer),
    }

    private companion object {
        const val MAX_SUB_FOLDERS_INDENT = 2
    }

    class FolderViewHolder(val binding: ViewBinding) : RecyclerView.ViewHolder(binding.root)

    private class FolderDiffCallback : DiffUtil.ItemCallback<Folder>() {
        override fun areItemsTheSame(oldFolder: Folder, newFolder: Folder): Boolean {
            return oldFolder.id == newFolder.id
        }

        override fun areContentsTheSame(oldFolder: Folder, newFolder: Folder): Boolean {
            return oldFolder.name == newFolder.name &&
                    oldFolder.isFavorite == newFolder.isFavorite &&
                    oldFolder.path == newFolder.path &&
                    oldFolder.unreadCountDisplay == newFolder.unreadCountDisplay &&
                    oldFolder.threads.count() == newFolder.threads.count()
        }
    }
}
