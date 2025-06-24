/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024-2025 Infomaniak Network SA
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

import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.annotation.DrawableRes
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.databinding.ItemMenuDrawerFolderBinding
import com.infomaniak.mail.ui.alertDialogs.ModifyNameFolderDialog
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.MenuDrawerViewHolder
import com.infomaniak.mail.utils.UnreadDisplay
import com.infomaniak.mail.views.itemViews.UnreadFolderItemView
import com.infomaniak.mail.views.itemViews.setFolderUi
import kotlin.math.min

class FolderViewHolder(
    inflater: LayoutInflater,
    parent: ViewGroup,
    val modifyNameFolderDialog: ModifyNameFolderDialog
) : MenuDrawerViewHolder(ItemMenuDrawerFolderBinding.inflate(inflater, parent, false)) {

    override val binding = super.binding as ItemMenuDrawerFolderBinding

    fun displayFolder(
        folder: Folder,
        currentFolderId: String?,
        hasCollapsableFolder: Boolean,
        onFolderClicked: (folderId: String) -> Unit,
        onCollapseChildrenClicked: (folderId: String, shouldCollapse: Boolean) -> Unit,
    ) {
        SentryLog.d("Bind", "Bind Folder : ${folder.name}")

        val roleDependantParameters = folder.role?.let {
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
                folderIndent = min(indentLevel, MAX_SUB_FOLDERS_INDENT),
            )
        }

        val unread = when (folder.role) {
            FolderRole.DRAFT, FolderRole.SCHEDULED_DRAFTS, FolderRole.SNOOZED -> UnreadDisplay(count = folder.threads.count())
            FolderRole.SENT, FolderRole.TRASH -> UnreadDisplay(count = 0)
            else -> folder.unreadCountDisplay
        }

        binding.root.setFolderUi(
            folder,
            roleDependantParameters,
            unread,
            currentFolderId,
            hasCollapsableFolder,
            onFolderClicked,
            onCollapseChildrenClicked,
        )
    }

    private fun UnreadFolderItemView.setFolderUi(
        folder: Folder,
        roleDependantParameters: RoleDependantParameters,
        unread: UnreadDisplay?,
        currentFolderId: String?,
        hasCollapsableFolder: Boolean,
        onFolderClicked: (folderId: String) -> Unit,
        onCollapseChildrenClicked: (folderId: String, shouldCollapse: Boolean) -> Unit,
    ) {

        val folderName = folder.getLocalizedName(context)
        val (iconId, trackerName, trackerValue, folderIndent) = roleDependantParameters

        setFolderUi(folder, iconId, isSelected = folder.id == currentFolderId)

        initOnCollapsableClickListener { onCollapseChildrenClicked(folder.id, isCollapsed) }

        isPastilleDisplayed = unread?.shouldDisplayPastille ?: false
        unreadCount = unread?.count ?: 0
        isCollapsed = folder.isCollapsed
        canBeCollapsed = folder.canBeCollapsed

        setIndent(
            indent = folderIndent,
            hasCollapsableFolder = hasCollapsableFolder,
            canBeCollapsed = canBeCollapsed,
        )

        setCollapsingButtonContentDescription(folderName)

        if(folder.role == null){
            setOnLongClickListener {
                val popup = PopupMenu(context, it)
                val inflater: MenuInflater = popup.menuInflater
                inflater.inflate(R.menu.item_menu_settings_folder, popup.menu)
                popup.show()

                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.modifySettingsFolder -> {
                            modifyNameFolderDialog.setFolderId(folder.id)
                            modifyNameFolderDialog.show(ranameFolderLastName = folder.name)
                            true
                        }
                        else -> false
                    }
                }
                true
            }
        }

        setOnClickListener {
            context.trackMenuDrawerEvent(trackerName, value = trackerValue)
            onFolderClicked.invoke(folder.id)
        }
    }

    private data class RoleDependantParameters(
        @DrawableRes var iconId: Int,
        var trackerName: String,
        var trackerValue: Float?,
        var folderIndent: Int,
    )

    companion object {
        const val MAX_SUB_FOLDERS_INDENT = 2
    }
}
