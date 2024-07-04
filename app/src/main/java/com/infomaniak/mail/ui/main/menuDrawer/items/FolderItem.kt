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
package com.infomaniak.mail.ui.main.menuDrawer.items

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.viewbinding.ViewBinding
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.databinding.ItemMenuDrawerFolderBinding
import com.infomaniak.mail.utils.UnreadDisplay
import com.infomaniak.mail.views.itemViews.*
import kotlin.math.min

object FolderItem {

    @Suppress("MayBeConstant")
    val viewType = R.layout.item_menu_drawer_folder

    fun binding(inflater: LayoutInflater, parent: ViewGroup): ViewBinding {
        return ItemMenuDrawerFolderBinding.inflate(inflater, parent, false)
    }

    fun display(
        item: Any,
        binding: ViewBinding,
        currentFolderId: String?,
        hasCollapsableDefaultFolder: Boolean,
        hasCollapsableCustomFolder: Boolean,
        onFolderClicked: (folderId: String) -> Unit,
        onCollapseChildrenClicked: (folderId: String, shouldCollapse: Boolean) -> Unit,
    ) {
        item as Folder
        binding as ItemMenuDrawerFolderBinding

        Log.d("Bind", "Bind Folder : ${item.name}")
        binding.displayFolder(
            item,
            currentFolderId,
            hasCollapsableDefaultFolder,
            hasCollapsableCustomFolder,
            onFolderClicked,
            onCollapseChildrenClicked,
        )
    }

    private fun ItemMenuDrawerFolderBinding.displayFolder(
        folder: Folder,
        currentFolderId: String?,
        hasCollapsableDefaultFolder: Boolean,
        hasCollapsableCustomFolder: Boolean,
        onFolderClicked: (folderId: String) -> Unit,
        onCollapseChildrenClicked: (folderId: String, shouldCollapse: Boolean) -> Unit,
    ) = with(root) {

        data class RoleDependantParameters(
            var iconId: Int,
            var trackerName: String,
            var trackerValue: Float?,
            var folderIndent: Int,
        )

        val (iconId, trackerName, trackerValue, folderIndent) = folder.role?.let {
            RoleDependantParameters(
                iconId = it.folderIconRes,
                trackerName = it.matomoValue,
                trackerValue = null,
                folderIndent = 0,
            )
        } ?: run {
            val indentLevel = folder.path.split(folder.separator).size - 1
            RoleDependantParameters(
                iconId = if (folder.isFavorite) R.drawable.ic_folder_star else R.drawable.ic_folder,
                trackerName = "customFolder",
                trackerValue = indentLevel.toFloat(),
                folderIndent = min(indentLevel, Folder.MAX_SUB_FOLDERS_INDENT),
            )
        }

        val unread = when (folder.role) {
            FolderRole.DRAFT -> UnreadDisplay(folder.threads.count())
            FolderRole.SENT, FolderRole.TRASH -> UnreadDisplay(0)
            else -> folder.unreadCountDisplay
        }

        setFolderUi(
            folder,
            iconId,
            unread,
            currentFolderId,
            hasCollapsableDefaultFolder,
            hasCollapsableCustomFolder,
            onFolderClicked,
            onCollapseChildrenClicked,
            trackerName,
            trackerValue,
            folderIndent,
        )
    }

    private fun SelectableItemView.setFolderUi(
        folder: Folder,
        @DrawableRes iconId: Int,
        unread: UnreadDisplay?,
        currentFolderId: String?,
        hasCollapsableDefaultFolder: Boolean,
        hasCollapsableCustomFolder: Boolean,
        onFolderClicked: (folderId: String) -> Unit,
        onCollapseChildrenClicked: (folderId: String, shouldCollapse: Boolean) -> Unit,
        trackerName: String,
        trackerValue: Float?,
        folderIndent: Int,
    ) {
        val folderName = folder.getLocalizedName(context)

        text = folderName
        icon = AppCompatResources.getDrawable(context, iconId)
        setSelectedState(folder.id == currentFolderId)

        when (this) {
            is SelectableFolderItemView -> setIndent(folderIndent)
            is UnreadFolderItemView -> {
                initOnCollapsableClickListener { onCollapseChildrenClicked(folder.id, isCollapsed) }
                isPastilleDisplayed = unread?.shouldDisplayPastille ?: false
                unreadCount = unread?.count ?: 0
                isCollapsed = folder.isCollapsed
                canBeCollapsed = folder.canBeCollapsed
                val hasCollapsableFolder = if (folder.role == null) hasCollapsableCustomFolder else hasCollapsableDefaultFolder
                setIndent(
                    indent = folderIndent,
                    hasCollapsableFolder = hasCollapsableFolder,
                    canBeCollapsed = canBeCollapsed,
                )
                setCollapsingButtonContentDescription(folderName)
            }
            is SelectableMailboxItemView, is UnreadItemView -> {
                error("`${this::class.simpleName}` cannot exists here. Only Folder classes are allowed")
            }
        }

        setOnClickListener {
            context.trackMenuDrawerEvent(trackerName, value = trackerValue)
            onFolderClicked.invoke(folder.id)
        }
    }
}
