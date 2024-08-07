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
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.MenuDrawerViewHolder
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerFragment.MediatorContainer
import com.infomaniak.mail.ui.main.menuDrawer.items.ActionViewHolder
import com.infomaniak.mail.ui.main.menuDrawer.items.ActionViewHolder.MenuDrawerAction
import com.infomaniak.mail.ui.main.menuDrawer.items.ActionViewHolder.MenuDrawerAction.ActionType
import com.infomaniak.mail.ui.main.menuDrawer.items.ActionsHeaderViewHolder
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
                areActionsExpanded,
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
            add(ItemType.DIVIDER)
            while (count < allFolders.count() && (allFolders[count].role != null || !allFolders[count].isRoot)) {
                val defaultFolder = allFolders[count]
                if (defaultFolder.canBeCollapsed) temporaryHasCollapsableDefaultFolder = true
                add(defaultFolder)
                count++
            }

            // Custom Folders
            add(ItemType.DIVIDER)
            add(ItemType.FOLDERS_HEADER)
            if (areCustomFoldersExpanded) {
                if (count == allFolders.count()) {
                    add(ItemType.EMPTY_FOLDERS)
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

            // Actions
            add(ItemType.DIVIDER)
            add(ItemType.ACTIONS_HEADER)
            if (areActionsExpanded) {
                add(syncAutoConfigAction)
                add(importMailsAction)
                if (permissions?.canRestoreEmails == true) add(restoreMailsAction)
            }

            // Footer
            add(MenuDrawerFooter(quotas))
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

                if (getItemViewType(index) != ItemType.FOLDER.ordinal) continue

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
            ItemType.DIVIDER -> ItemType.DIVIDER.ordinal
            is MailboxesHeader -> ItemType.MAILBOXES_HEADER.ordinal
            is Mailbox -> if (item.isValid) ItemType.MAILBOX.ordinal else ItemType.INVALID_MAILBOX.ordinal
            ItemType.FOLDERS_HEADER -> ItemType.FOLDERS_HEADER.ordinal
            is Folder -> ItemType.FOLDER.ordinal
            ItemType.EMPTY_FOLDERS -> ItemType.EMPTY_FOLDERS.ordinal
            ItemType.ACTIONS_HEADER -> ItemType.ACTIONS_HEADER.ordinal
            is MenuDrawerAction -> ItemType.ACTION.ordinal
            is MenuDrawerFooter -> ItemType.FOOTER.ordinal
            else -> error("Failed to find a viewType for MenuDrawer item")
        }
    }.getOrDefault(super.getItemViewType(position))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuDrawerViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            ItemType.DIVIDER.ordinal -> DividerItemViewHolder(inflater, parent)
            ItemType.MAILBOXES_HEADER.ordinal -> MailboxesHeaderViewHolder(inflater, parent)
            ItemType.MAILBOX.ordinal -> MailboxViewHolder(inflater, parent)
            ItemType.INVALID_MAILBOX.ordinal -> InvalidMailboxViewHolder(inflater, parent)
            ItemType.FOLDERS_HEADER.ordinal -> FoldersHeaderViewHolder(inflater, parent)
            ItemType.FOLDER.ordinal -> FolderViewHolder(inflater, parent)
            ItemType.EMPTY_FOLDERS.ordinal -> EmptyFoldersViewHolder(inflater, parent)
            ItemType.ACTIONS_HEADER.ordinal -> ActionsHeaderViewHolder(inflater, parent)
            ItemType.ACTION.ordinal -> ActionViewHolder(inflater, parent)
            ItemType.FOOTER.ordinal -> FooterViewHolder(inflater, parent)
            else -> error("Failed to find a binding for MenuDrawer viewType")
        }
    }

    override fun onBindViewHolder(holder: MenuDrawerViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.firstOrNull() == NotifyType.MAILBOXES_HEADER_CLICKED) {
            (holder as MailboxesHeaderViewHolder).updateCollapseState(header = currentList[position] as MailboxesHeader)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: MenuDrawerViewHolder, position: Int) {
        val item = currentList[position]

        when (holder) {
            is MailboxesHeaderViewHolder -> holder.displayMailboxesHeader(
                header = item as MailboxesHeader,
                onMailboxesHeaderClicked = callbacks.onMailboxesHeaderClicked,
            )
            is MailboxViewHolder -> holder.displayMailbox(
                mailbox = item as Mailbox,
                onValidMailboxClicked = callbacks.onValidMailboxClicked,
            )
            is InvalidMailboxViewHolder -> holder.displayInvalidMailbox(
                mailbox = item as Mailbox,
                onLockedMailboxClicked = callbacks.onLockedMailboxClicked,
                onInvalidPasswordMailboxClicked = callbacks.onInvalidPasswordMailboxClicked,
            )
            is FoldersHeaderViewHolder -> holder.displayFoldersHeader(
                onFoldersHeaderClicked = callbacks.onFoldersHeaderClicked,
                onCreateFolderClicked = callbacks.onCreateFolderClicked,
            )
            is FolderViewHolder -> holder.displayFolder(
                folder = item as Folder,
                currentFolderId = currentFolderId,
                hasCollapsableFolder = if (item.role == null) hasCollapsableCustomFolder else hasCollapsableDefaultFolder,
                onFolderClicked = callbacks.onFolderClicked,
                onCollapseChildrenClicked = callbacks.onCollapseChildrenClicked,
            )
            is ActionsHeaderViewHolder -> holder.displayActionsHeader(
                onActionsHeaderClicked = callbacks.onActionsHeaderClicked,
            )
            is ActionViewHolder -> holder.displayAction(
                action = item as MenuDrawerAction,
                onActionClicked = callbacks.onActionClicked,
            )
            is FooterViewHolder -> holder.displayFooter(
                footer = item as MenuDrawerFooter,
                onFeedbackClicked = callbacks.onFeedbackClicked,
                onHelpClicked = callbacks.onHelpClicked,
                onAppVersionClicked = callbacks.onAppVersionClicked,
            )
        }
    }

    abstract class MenuDrawerViewHolder(open val binding: ViewBinding) : ViewHolder(binding.root)

    enum class ItemType {
        DIVIDER,
        MAILBOXES_HEADER,
        MAILBOX,
        INVALID_MAILBOX,
        FOLDERS_HEADER,
        FOLDER,
        EMPTY_FOLDERS,
        ACTIONS_HEADER,
        ACTION,
        FOOTER,
    }

    private enum class NotifyType {
        MAILBOXES_HEADER_CLICKED,
    }

    private class FolderDiffCallback : DiffUtil.ItemCallback<Any>() {

        override fun areItemsTheSame(oldItem: Any, newItem: Any) = runCatchingRealm {
            when (oldItem) {
                ItemType.DIVIDER -> newItem == ItemType.DIVIDER
                is MailboxesHeader -> newItem is MailboxesHeader && newItem.mailbox?.objectId == oldItem.mailbox?.objectId
                is Mailbox -> newItem is Mailbox && newItem.objectId == oldItem.objectId
                ItemType.FOLDERS_HEADER -> newItem == ItemType.FOLDERS_HEADER
                is Folder -> newItem is Folder && newItem.id == oldItem.id
                ItemType.EMPTY_FOLDERS -> newItem == ItemType.EMPTY_FOLDERS
                ItemType.ACTIONS_HEADER -> newItem == ItemType.ACTIONS_HEADER
                is MenuDrawerAction -> newItem is MenuDrawerAction && newItem.type == oldItem.type
                is MenuDrawerFooter -> newItem is MenuDrawerFooter
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
                is MenuDrawerFooter -> newItem is MenuDrawerFooter && newItem.quotas?.size == oldItem.quotas?.size
                ItemType.DIVIDER,
                ItemType.FOLDERS_HEADER,
                ItemType.EMPTY_FOLDERS,
                ItemType.ACTIONS_HEADER,
                is MenuDrawerAction -> true
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

    companion object {
        private val syncAutoConfigAction = MenuDrawerAction(
            type = ActionType.SYNC_AUTO_CONFIG,
            icon = R.drawable.ic_synchronize,
            text = R.string.syncCalendarsAndContactsTitle,
            maxLines = 2,
        )
        private val importMailsAction = MenuDrawerAction(
            type = ActionType.IMPORT_MAILS,
            icon = R.drawable.ic_drawer_download,
            text = R.string.buttonImportEmails,
            maxLines = 1,
        )
        private val restoreMailsAction = MenuDrawerAction(
            type = ActionType.RESTORE_MAILS,
            icon = R.drawable.ic_restore_arrow,
            text = R.string.buttonRestoreEmails,
            maxLines = 1,
        )
    }
}
