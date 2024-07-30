/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.viewbinding.ViewBinding
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.databinding.ItemInvalidMailboxBinding
import com.infomaniak.mail.databinding.ItemMenuDrawerCustomFoldersHeaderBinding
import com.infomaniak.mail.databinding.ItemMenuDrawerFolderBinding
import com.infomaniak.mail.databinding.ItemMenuDrawerFooterBinding
import com.infomaniak.mail.databinding.ItemMenuDrawerMailboxBinding
import com.infomaniak.mail.databinding.ItemMenuDrawerMailboxesHeaderBinding
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.MenuDrawerViewHolder
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerFragment.MediatorContainer
import com.infomaniak.mail.ui.main.menuDrawer.items.DividerItem
import com.infomaniak.mail.ui.main.menuDrawer.items.EmptyFoldersItem
import com.infomaniak.mail.ui.main.menuDrawer.items.FolderItem
import com.infomaniak.mail.ui.main.menuDrawer.items.FoldersHeaderItem
import com.infomaniak.mail.ui.main.menuDrawer.items.FooterItem
import com.infomaniak.mail.ui.main.menuDrawer.items.FooterItem.MenuDrawerFooter
import com.infomaniak.mail.ui.main.menuDrawer.items.InvalidMailboxItem
import com.infomaniak.mail.ui.main.menuDrawer.items.MailboxItem
import com.infomaniak.mail.ui.main.menuDrawer.items.MailboxesHeaderItem
import com.infomaniak.mail.ui.main.menuDrawer.items.MailboxesHeaderItem.MailboxesHeader
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import javax.inject.Inject

class MenuDrawerAdapter @Inject constructor() : ListAdapter<Any, MenuDrawerViewHolder>(FolderDiffCallback()) {

    private var currentFolderId: String? = null
    private var hasCollapsableDefaultFolder = false
    private var hasCollapsableCustomFolder = false

    private lateinit var callbacks: MenuDrawerAdapterCallbacks

    operator fun invoke(callbacks: MenuDrawerAdapterCallbacks): MenuDrawerAdapter {
        this.callbacks = callbacks
        return this
    }

    fun setItems(mediatorContainer: MediatorContainer) = runCatchingRealm {

        val (
            currentMailbox,
            areMailboxesExpanded,
            otherMailboxes,
            allFolders,
            areCustomFoldersExpanded,
            permissions,
            quotas,
        ) = mediatorContainer

        val items = mutableListOf<Any>().apply {

            var count = 0
            var temporaryHasCollapsableDefaultFolder = false
            var temporaryHasCollapsableCustomFolder = false

            // Mailboxes
            add(MailboxesHeader(currentMailbox, otherMailboxes.isNotEmpty(), areMailboxesExpanded))
            if (areMailboxesExpanded) addAll(otherMailboxes)

            // Default Folders
            add(ItemType.DIVIDER)
            while (count < allFolders.count() && (allFolders[count].role != null || !allFolders[count].isRoot)) {
                val defaultFolder = allFolders[count]
                if (defaultFolder.canBeCollapsed) temporaryHasCollapsableDefaultFolder = true
                add(defaultFolder)
                count++
            }

            // Custom Folders
            add(ItemType.DIVIDER)
            add(ItemType.CUSTOM_FOLDERS_HEADER)
            if (areCustomFoldersExpanded) {
                if (count == allFolders.count()) {
                    add(ItemType.EMPTY_CUSTOM_FOLDERS)
                } else {
                    while (count < allFolders.count()) {
                        val customFolder = allFolders[count]
                        if (customFolder.canBeCollapsed) temporaryHasCollapsableCustomFolder = true
                        add(customFolder)
                        count++
                    }
                }
            }
            hasCollapsableDefaultFolder = temporaryHasCollapsableDefaultFolder
            hasCollapsableCustomFolder = temporaryHasCollapsableCustomFolder

            // Footer
            add(ItemType.DIVIDER)
            add(MenuDrawerFooter(permissions, quotas))
        }

        submitList(items)
    }

    fun notifySelectedFolder(currentFolder: Folder) {

        val oldId = currentFolderId
        val newId = currentFolder.id

        if (newId != oldId) {
            currentFolderId = newId

            var oldIsFound = false
            var newIsFound = false
            for (index in currentList.indices) {

                if (getItemViewType(index) != FolderItem.viewType) continue

                val itemId = (currentList[index] as Folder).id
                if (itemId == oldId) {
                    oldIsFound = true
                    notifyItemChanged(index)
                } else if (itemId == newId) {
                    newIsFound = true
                    notifyItemChanged(index)
                }

                if (oldIsFound && newIsFound) break
            }
        }
    }

    override fun getItemCount(): Int = currentList.size

    override fun getItemViewType(position: Int): Int = runCatchingRealm {
        return@runCatchingRealm when (val item = currentList[position]) {
            is MailboxesHeader -> MailboxesHeaderItem.viewType
            is Mailbox -> if (item.isValid) MailboxItem.viewType else InvalidMailboxItem.viewType
            is Folder -> FolderItem.viewType
            ItemType.CUSTOM_FOLDERS_HEADER -> FoldersHeaderItem.viewType
            ItemType.EMPTY_CUSTOM_FOLDERS -> EmptyFoldersItem.viewType
            is MenuDrawerFooter -> FooterItem.viewType
            ItemType.DIVIDER -> DividerItem.viewType
            else -> error("Failed to find a viewType for MenuDrawer item")
        }
    }.getOrDefault(super.getItemViewType(position))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuDrawerViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        val binding = when (viewType) {
            MailboxesHeaderItem.viewType -> MailboxesHeaderItem.binding(inflater, parent)
            MailboxItem.viewType -> MailboxItem.binding(inflater, parent)
            InvalidMailboxItem.viewType -> InvalidMailboxItem.binding(inflater, parent)
            FolderItem.viewType -> FolderItem.binding(inflater, parent)
            FoldersHeaderItem.viewType -> FoldersHeaderItem.binding(inflater, parent)
            EmptyFoldersItem.viewType -> EmptyFoldersItem.binding(inflater, parent)
            FooterItem.viewType -> FooterItem.binding(inflater, parent)
            DividerItem.viewType -> DividerItem.binding(inflater, parent)
            else -> error("Failed to find a binding for MenuDrawer viewType")
        }

        return MenuDrawerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MenuDrawerViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.firstOrNull() == NotifyType.MAILBOXES_HEADER_CLICKED) {
            MailboxesHeaderItem.updateCollapseState(
                header = currentList[position] as MailboxesHeader,
                binding = holder.binding as ItemMenuDrawerMailboxesHeaderBinding,
            )
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: MenuDrawerViewHolder, position: Int): Unit = with(holder.binding) {
        val item = currentList[position]

        when (getItemViewType(position)) {
            MailboxesHeaderItem.viewType -> MailboxesHeaderItem.displayMailboxesHeader(
                header = item as MailboxesHeader,
                binding = this as ItemMenuDrawerMailboxesHeaderBinding,
                onMailboxesHeaderClicked = callbacks.onMailboxesHeaderClicked,
            )
            MailboxItem.viewType -> MailboxItem.displayMailbox(
                mailbox = item as Mailbox,
                binding = this as ItemMenuDrawerMailboxBinding,
                onValidMailboxClicked = callbacks.onValidMailboxClicked,
            )
            InvalidMailboxItem.viewType -> InvalidMailboxItem.displayInvalidMailbox(
                mailbox = item as Mailbox,
                binding = this as ItemInvalidMailboxBinding,
                onLockedMailboxClicked = callbacks.onLockedMailboxClicked,
                onInvalidPasswordMailboxClicked = callbacks.onInvalidPasswordMailboxClicked,
            )
            FolderItem.viewType -> FolderItem.displayFolder(
                folder = item as Folder,
                binding = this as ItemMenuDrawerFolderBinding,
                currentFolderId = currentFolderId,
                hasCollapsableFolder = if (item.role == null) hasCollapsableCustomFolder else hasCollapsableDefaultFolder,
                onFolderClicked = callbacks.onFolderClicked,
                onCollapseChildrenClicked = callbacks.onCollapseChildrenClicked,
            )
            FoldersHeaderItem.viewType -> FoldersHeaderItem.displayCustomFoldersHeader(
                binding = this as ItemMenuDrawerCustomFoldersHeaderBinding,
                onCustomFoldersHeaderClicked = callbacks.onCustomFoldersHeaderClicked,
                onCreateFolderClicked = callbacks.onCreateFolderClicked,
            )
            FooterItem.viewType -> FooterItem.displayFooter(
                footer = item as MenuDrawerFooter,
                binding = this as ItemMenuDrawerFooterBinding,
                onSyncAutoConfigClicked = callbacks.onSyncAutoConfigClicked,
                onImportMailsClicked = callbacks.onImportMailsClicked,
                onRestoreMailsClicked = callbacks.onRestoreMailsClicked,
                onFeedbackClicked = callbacks.onFeedbackClicked,
                onHelpClicked = callbacks.onHelpClicked,
                onAppVersionClicked = callbacks.onAppVersionClicked,
            )
        }
    }

    class MenuDrawerViewHolder(val binding: ViewBinding) : ViewHolder(binding.root)

    private enum class ItemType {
        CUSTOM_FOLDERS_HEADER,
        EMPTY_CUSTOM_FOLDERS,
        DIVIDER,
    }

    private enum class NotifyType {
        MAILBOXES_HEADER_CLICKED,
    }

    private class FolderDiffCallback : DiffUtil.ItemCallback<Any>() {

        override fun areItemsTheSame(oldItem: Any, newItem: Any) = runCatchingRealm {
            when (oldItem) {
                is MailboxesHeader -> newItem is MailboxesHeader && newItem.mailbox?.objectId == oldItem.mailbox?.objectId
                is Mailbox -> newItem is Mailbox && newItem.objectId == oldItem.objectId
                is Folder -> newItem is Folder && newItem.id == oldItem.id
                ItemType.CUSTOM_FOLDERS_HEADER -> newItem == ItemType.CUSTOM_FOLDERS_HEADER
                ItemType.EMPTY_CUSTOM_FOLDERS -> newItem == ItemType.EMPTY_CUSTOM_FOLDERS
                is MenuDrawerFooter -> newItem is MenuDrawerFooter
                ItemType.DIVIDER -> newItem == ItemType.DIVIDER
                else -> error("oldItem wasn't any known item type (in MenuDrawer `areItemsTheSame`)")
            }
        }.getOrDefault(false)

        override fun areContentsTheSame(oldItem: Any, newItem: Any) = runCatchingRealm {
            when (oldItem) {
                is MailboxesHeader -> newItem is MailboxesHeader
                        && newItem.hasMoreThanOneMailbox == oldItem.hasMoreThanOneMailbox
                        && newItem.isExpanded == oldItem.isExpanded
                        && newItem.mailbox?.unreadCountDisplay?.count == oldItem.mailbox?.unreadCountDisplay?.count
                is Mailbox -> newItem is Mailbox && newItem.unreadCountDisplay.count == oldItem.unreadCountDisplay.count
                is Folder -> newItem is Folder &&
                        newItem.name == oldItem.name &&
                        newItem.isFavorite == oldItem.isFavorite &&
                        newItem.path == oldItem.path &&
                        newItem.unreadCountDisplay == oldItem.unreadCountDisplay &&
                        newItem.threads.count() == oldItem.threads.count() &&
                        newItem.canBeCollapsed == oldItem.canBeCollapsed
                ItemType.CUSTOM_FOLDERS_HEADER -> true
                ItemType.EMPTY_CUSTOM_FOLDERS -> true
                is MenuDrawerFooter -> newItem is MenuDrawerFooter && newItem.quotas?.size == oldItem.quotas?.size
                ItemType.DIVIDER -> false
                else -> error("oldItem wasn't any known item type (in MenuDrawer `areContentsTheSame`)")
            }
        }.getOrDefault(false)

        override fun getChangePayload(oldItem: Any, newItem: Any): Any? {
            return if (newItem is MailboxesHeader && oldItem is MailboxesHeader && newItem.isExpanded != oldItem.isExpanded) {
                NotifyType.MAILBOXES_HEADER_CLICKED
            } else {
                null
            }
        }
    }
}
