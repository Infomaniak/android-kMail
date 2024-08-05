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
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.ItemType.DIVIDER_ITEM
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.ItemType.EMPTY_CUSTOM_FOLDERS_ITEM
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.ItemType.FOLDERS_HEADER_ITEM
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.MenuDrawerViewHolder
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.ViewType.DIVIDER_VIEW
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.ViewType.EMPTY_FOLDERS_VIEW
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.ViewType.FOLDERS_HEADER_VIEW
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.ViewType.FOLDER_VIEW
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.ViewType.FOOTER_VIEW
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.ViewType.INVALID_MAILBOX_VIEW
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.ViewType.MAILBOXES_HEADER_VIEW
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.ViewType.MAILBOX_VIEW
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerFragment.MediatorContainer
import com.infomaniak.mail.ui.main.menuDrawer.items.DividerItemViewHolder
import com.infomaniak.mail.ui.main.menuDrawer.items.EmptyFoldersViewHolder
import com.infomaniak.mail.ui.main.menuDrawer.items.FolderViewHolder
import com.infomaniak.mail.ui.main.menuDrawer.items.FoldersHeaderViewHolder
import com.infomaniak.mail.ui.main.menuDrawer.items.FooterViewHolder
import com.infomaniak.mail.ui.main.menuDrawer.items.FooterViewHolder.MenuDrawerFooter
import com.infomaniak.mail.ui.main.menuDrawer.items.InvalidMailboxViewHolder
import com.infomaniak.mail.ui.main.menuDrawer.items.MailboxViewHolder
import com.infomaniak.mail.ui.main.menuDrawer.items.MailboxesHeaderViewHolder
import com.infomaniak.mail.ui.main.menuDrawer.items.MailboxesHeaderViewHolder.MailboxesHeader
import com.infomaniak.mail.utils.AccountUtils
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

    fun formatList(mediatorContainer: MediatorContainer) = buildList {
        runCatchingRealm {

            val (
                mailboxes,
                areMailboxesExpanded,
                allFolders,
                areCustomFoldersExpanded,
                permissions,
                quotas,
            ) = mediatorContainer

            var count = 0
            var temporaryHasCollapsableDefaultFolder = false
            var temporaryHasCollapsableCustomFolder = false

            // Mailboxes
            val currentMailboxIndex = mailboxes.indexOfFirst { it.mailboxId == AccountUtils.currentMailboxId }
            val otherMailboxes = mailboxes.toMutableList()
            val currentMailbox = otherMailboxes.removeAt(currentMailboxIndex)
            add(MailboxesHeader(currentMailbox, otherMailboxes.isNotEmpty(), areMailboxesExpanded))
            if (areMailboxesExpanded) addAll(otherMailboxes)

            // Default Folders
            add(DIVIDER_ITEM)
            while (count < allFolders.count() && (allFolders[count].role != null || !allFolders[count].isRoot)) {
                val defaultFolder = allFolders[count]
                if (defaultFolder.canBeCollapsed) temporaryHasCollapsableDefaultFolder = true
                add(defaultFolder)
                count++
            }

            // Custom Folders
            add(DIVIDER_ITEM)
            add(FOLDERS_HEADER_ITEM)
            if (areCustomFoldersExpanded) {
                if (count == allFolders.count()) {
                    add(EMPTY_CUSTOM_FOLDERS_ITEM)
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
            add(DIVIDER_ITEM)
            add(MenuDrawerFooter(permissions, quotas))
        }
    }

    fun notifySelectedFolder(currentFolder: Folder) {

        val oldId = currentFolderId
        val newId = currentFolder.id

        if (newId != oldId) {
            currentFolderId = newId

            var oldIsFound = false
            var newIsFound = false
            for (index in currentList.indices) {

                if (getItemViewType(index) != FOLDER_VIEW.ordinal) continue

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
            DIVIDER_ITEM -> DIVIDER_VIEW.ordinal
            is MailboxesHeader -> MAILBOXES_HEADER_VIEW.ordinal
            is Mailbox -> if (item.isValid) MAILBOX_VIEW.ordinal else INVALID_MAILBOX_VIEW.ordinal
            FOLDERS_HEADER_ITEM -> FOLDERS_HEADER_VIEW.ordinal
            is Folder -> FOLDER_VIEW.ordinal
            EMPTY_CUSTOM_FOLDERS_ITEM -> EMPTY_FOLDERS_VIEW.ordinal
            is MenuDrawerFooter -> FOOTER_VIEW.ordinal
            else -> error("Failed to find a viewType for MenuDrawer item")
        }
    }.getOrDefault(super.getItemViewType(position))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuDrawerViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            DIVIDER_VIEW.ordinal -> DividerItemViewHolder(inflater, parent)
            MAILBOXES_HEADER_VIEW.ordinal -> MailboxesHeaderViewHolder(inflater, parent)
            MAILBOX_VIEW.ordinal -> MailboxViewHolder(inflater, parent)
            INVALID_MAILBOX_VIEW.ordinal -> InvalidMailboxViewHolder(inflater, parent)
            FOLDERS_HEADER_VIEW.ordinal -> FoldersHeaderViewHolder(inflater, parent)
            FOLDER_VIEW.ordinal -> FolderViewHolder(inflater, parent)
            EMPTY_FOLDERS_VIEW.ordinal -> EmptyFoldersViewHolder(inflater, parent)
            FOOTER_VIEW.ordinal -> FooterViewHolder(inflater, parent)
            else -> error("Failed to find a binding for MenuDrawer viewType")
        }
    }

    override fun onBindViewHolder(holder: MenuDrawerViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.firstOrNull() == NotifyType.MAILBOXES_HEADER_CLICKED) {
            (holder as MailboxesHeaderViewHolder).updateCollapseState(
                header = currentList[position] as MailboxesHeader,
                binding = holder.binding as ItemMenuDrawerMailboxesHeaderBinding,
            )
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: MenuDrawerViewHolder, position: Int): Unit = with(holder.binding) {
        val item = currentList[position]

        when (holder) {
            is MailboxesHeaderViewHolder -> holder.displayMailboxesHeader(
                header = item as MailboxesHeader,
                binding = this as ItemMenuDrawerMailboxesHeaderBinding,
                onMailboxesHeaderClicked = callbacks.onMailboxesHeaderClicked,
            )
            is MailboxViewHolder -> holder.displayMailbox(
                mailbox = item as Mailbox,
                binding = this as ItemMenuDrawerMailboxBinding,
                onValidMailboxClicked = callbacks.onValidMailboxClicked,
            )
            is InvalidMailboxViewHolder -> holder.displayInvalidMailbox(
                mailbox = item as Mailbox,
                binding = this as ItemInvalidMailboxBinding,
                onLockedMailboxClicked = callbacks.onLockedMailboxClicked,
                onInvalidPasswordMailboxClicked = callbacks.onInvalidPasswordMailboxClicked,
            )
            is FoldersHeaderViewHolder -> holder.displayFoldersHeader(
                binding = this as ItemMenuDrawerCustomFoldersHeaderBinding,
                onFoldersHeaderClicked = callbacks.onFoldersHeaderClicked,
                onCreateFolderClicked = callbacks.onCreateFolderClicked,
            )
            is FolderViewHolder -> holder.displayFolder(
                folder = item as Folder,
                binding = this as ItemMenuDrawerFolderBinding,
                currentFolderId = currentFolderId,
                hasCollapsableFolder = if (item.role == null) hasCollapsableCustomFolder else hasCollapsableDefaultFolder,
                onFolderClicked = callbacks.onFolderClicked,
                onCollapseChildrenClicked = callbacks.onCollapseChildrenClicked,
            )
            is FooterViewHolder -> holder.displayFooter(
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

    abstract class MenuDrawerViewHolder(val binding: ViewBinding) : ViewHolder(binding.root)

    private enum class ItemType {
        DIVIDER_ITEM,
        FOLDERS_HEADER_ITEM,
        EMPTY_CUSTOM_FOLDERS_ITEM,
    }

    enum class ViewType {
        DIVIDER_VIEW,
        MAILBOXES_HEADER_VIEW,
        MAILBOX_VIEW,
        INVALID_MAILBOX_VIEW,
        FOLDERS_HEADER_VIEW,
        FOLDER_VIEW,
        EMPTY_FOLDERS_VIEW,
        FOOTER_VIEW,
    }

    private enum class NotifyType {
        MAILBOXES_HEADER_CLICKED,
    }

    private class FolderDiffCallback : DiffUtil.ItemCallback<Any>() {

        override fun areItemsTheSame(oldItem: Any, newItem: Any) = runCatchingRealm {
            when (oldItem) {
                DIVIDER_ITEM -> newItem == DIVIDER_ITEM
                is MailboxesHeader -> newItem is MailboxesHeader && newItem.mailbox?.objectId == oldItem.mailbox?.objectId
                is Mailbox -> newItem is Mailbox && newItem.objectId == oldItem.objectId
                FOLDERS_HEADER_ITEM -> newItem == FOLDERS_HEADER_ITEM
                is Folder -> newItem is Folder && newItem.id == oldItem.id
                EMPTY_CUSTOM_FOLDERS_ITEM -> newItem == EMPTY_CUSTOM_FOLDERS_ITEM
                is MenuDrawerFooter -> newItem is MenuDrawerFooter
                else -> error("oldItem wasn't any known item type (in MenuDrawer `areItemsTheSame`)")
            }
        }.getOrDefault(false)

        override fun areContentsTheSame(oldItem: Any, newItem: Any) = runCatchingRealm {
            when (oldItem) {
                DIVIDER_ITEM -> true
                is MailboxesHeader -> newItem is MailboxesHeader
                        && newItem.hasMoreThanOneMailbox == oldItem.hasMoreThanOneMailbox
                        && newItem.isExpanded == oldItem.isExpanded
                        && newItem.mailbox?.unreadCountDisplay?.count == oldItem.mailbox?.unreadCountDisplay?.count
                is Mailbox -> newItem is Mailbox && newItem.unreadCountDisplay.count == oldItem.unreadCountDisplay.count
                FOLDERS_HEADER_ITEM -> true
                is Folder -> newItem is Folder &&
                        newItem.name == oldItem.name &&
                        newItem.isFavorite == oldItem.isFavorite &&
                        newItem.path == oldItem.path &&
                        newItem.unreadCountDisplay == oldItem.unreadCountDisplay &&
                        newItem.threads.count() == oldItem.threads.count() &&
                        newItem.canBeCollapsed == oldItem.canBeCollapsed
                EMPTY_CUSTOM_FOLDERS_ITEM -> true
                is MenuDrawerFooter -> newItem is MenuDrawerFooter && newItem.quotas?.size == oldItem.quotas?.size
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
