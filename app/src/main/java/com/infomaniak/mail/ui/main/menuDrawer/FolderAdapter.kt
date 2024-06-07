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
package com.infomaniak.mail.ui.main.menuDrawer

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.viewbinding.ViewBinding
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.databinding.ItemFolderMenuDrawerBinding
import com.infomaniak.mail.databinding.ItemSelectableFolderBinding
import com.infomaniak.mail.ui.main.menuDrawer.FolderAdapter.FolderViewHolder
import com.infomaniak.mail.utils.UiUtils
import com.infomaniak.mail.utils.UnreadDisplay
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.views.itemViews.*
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.math.min

class FolderAdapter @Inject constructor(
    private val globalCoroutineScope: CoroutineScope,
) : ListAdapter<Folder, FolderViewHolder>(FolderDiffCallback()) {

    private inline val folders: List<Folder> get() = currentList

    private var setFoldersJob: Job? = null

    private var currentFolderId: String? = null
    private var hasCollapsableFolder: Boolean? = null
    private var isInMenuDrawer: Boolean = true
    private var shouldIndent: Boolean = true

    private lateinit var onFolderClicked: (folderId: String) -> Unit
    private var onCollapseClicked: ((folderId: String, shouldCollapse: Boolean) -> Unit)? = null
    private var onCollapseTransition: ((Boolean) -> Unit)? = null

    operator fun invoke(
        isInMenuDrawer: Boolean,
        shouldIndent: Boolean = true,
        onFolderClicked: (folderId: String) -> Unit,
        onCollapseClicked: ((folderId: String, shouldCollapse: Boolean) -> Unit)? = null,
        onCollapseTransition: ((Boolean) -> Unit)? = null,
    ): FolderAdapter {
        this.isInMenuDrawer = isInMenuDrawer
        this.shouldIndent = shouldIndent
        this.onFolderClicked = onFolderClicked
        this.onCollapseClicked = onCollapseClicked
        this.onCollapseTransition = onCollapseTransition

        return this
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = if (viewType == DisplayType.SELECTABLE_FOLDER.layout) {
            ItemSelectableFolderBinding.inflate(layoutInflater, parent, false)
        } else {
            ItemFolderMenuDrawerBinding.inflate(layoutInflater, parent, false)
        }

        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int, payloads: MutableList<Any>) = runCatchingRealm {
        if (payloads.firstOrNull() == Unit) {
            val folder = folders[position]
            if (getItemViewType(position) == DisplayType.SELECTABLE_FOLDER.layout) {
                (holder.binding as ItemSelectableFolderBinding).root.setSelectedState(currentFolderId == folder.id)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }.getOrDefault(Unit)

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) = with(holder.binding) {
        val folder = folders[position]

        when (getItemViewType(position)) {
            DisplayType.SELECTABLE_FOLDER.layout -> (this as ItemSelectableFolderBinding).root.displayFolder(folder)
            DisplayType.MENU_DRAWER.layout -> (this as ItemFolderMenuDrawerBinding).root.displayMenuDrawerFolder(folder)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (isInMenuDrawer) DisplayType.MENU_DRAWER.layout else DisplayType.SELECTABLE_FOLDER.layout
    }

    override fun getItemCount(): Int = runCatchingRealm { folders.size }.getOrDefault(0)

    private fun UnreadFolderItemView.displayMenuDrawerFolder(folder: Folder) {

        val unread = when (folder.role) {
            FolderRole.DRAFT -> UnreadDisplay(folder.threads.count())
            FolderRole.SENT, FolderRole.TRASH -> UnreadDisplay(0)
            else -> folder.unreadCountDisplay
        }

        displayFolder(folder, unread)
    }

    private fun SelectableItemView.displayFolder(folder: Folder, unread: UnreadDisplay? = null) {
        folder.role?.let {
            setFolderUi(folder, it.folderIconRes, unread, it.matomoValue)
        } ?: run {
            val indentLevel = if (shouldIndent) folder.path.split(folder.separator).size - 1 else 0
            setFolderUi(
                folder = folder,
                iconId = if (folder.isFavorite) R.drawable.ic_folder_star else R.drawable.ic_folder,
                unread = unread,
                trackerName = "customFolder",
                trackerValue = indentLevel.toFloat(),
                folderIndent = min(indentLevel, MAX_SUB_FOLDERS_INDENT),
            )
        }
    }

    private fun SelectableItemView.setFolderUi(
        folder: Folder,
        @DrawableRes iconId: Int,
        unread: UnreadDisplay?,
        trackerName: String,
        trackerValue: Float? = null,
        folderIndent: Int = 0,
    ) {
        val folderName = folder.getLocalizedName(context)

        tag = if (folder.shouldDisplayDivider) null else UiUtils.IGNORE_DIVIDER_TAG
        text = folderName
        icon = AppCompatResources.getDrawable(context, iconId)
        setSelectedState(currentFolderId == folder.id)

        when (this) {
            is SelectableFolderItemView -> setIndent(folderIndent)
            is UnreadFolderItemView -> {
                initOnCollapsableClickListener { onCollapseClicked?.invoke(folder.id, isCollapsed) }
                isPastilleDisplayed = unread?.shouldDisplayPastille ?: false
                unreadCount = unread?.count ?: 0
                isHidden = folder.isHidden
                isCollapsed = folder.isCollapsed
                canBeCollapsed = folder.canBeCollapsed
                setIndent(folderIndent, hasCollapsableFolder ?: false, canBeCollapsed)
                setCollapsingButtonContentDescription(folderName)
                onCollapseTransition?.invoke(false)
            }
            is SelectableMailboxItemView, is UnreadItemView -> {
                error("`${this::class.simpleName}` cannot exists here. Only Folder classes are allowed")
            }
        }

        setOnClickListener {
            if (isInMenuDrawer) context.trackMenuDrawerEvent(trackerName, value = trackerValue)
            onFolderClicked.invoke(folder.id)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setFolders(newFolders: List<Folder>, newCurrentFolderId: String? = null) = runCatchingRealm {

        fun foldersWithDivider(): List<Folder> {
            var isFirstCustomFolder = true
            return newFolders.map { folder ->
                folder.clone().apply {
                    shouldDisplayDivider = if (folder.role == null && isFirstCustomFolder) {
                        isFirstCustomFolder = false
                        true
                    } else {
                        false
                    }
                }
            }
        }

        /**
         * If there was no Folder with children, and then now there's at least one, we need to indent the whole Folders list.
         */
        fun notifyCollapsableFolders() {
            setFoldersJob?.cancel()
            setFoldersJob = globalCoroutineScope.launch {
                val newHasCollapsableFolder = newFolders.any { it.canBeCollapsed }

                val isFirstTime = hasCollapsableFolder == null
                val collapsableFolderExistenceHasChanged = newHasCollapsableFolder != hasCollapsableFolder
                hasCollapsableFolder = newHasCollapsableFolder

                if (!isFirstTime && collapsableFolderExistenceHasChanged) {
                    Dispatchers.Main { notifyDataSetChanged() }
                }
            }
        }

        newCurrentFolderId?.let { currentFolderId = it }
        submitList(foldersWithDivider())
        if (isInMenuDrawer) notifyCollapsableFolders()
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
        SELECTABLE_FOLDER(R.layout.item_selectable_folder),
        MENU_DRAWER(R.layout.item_folder_menu_drawer),
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
                    oldFolder.shouldDisplayDivider == newFolder.shouldDisplayDivider
        }.getOrDefault(false)
    }
}
