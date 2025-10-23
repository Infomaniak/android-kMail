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
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.FolderUi
import com.infomaniak.mail.databinding.ItemMenuDrawerFolderBinding
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.MenuDrawerViewHolder
import com.infomaniak.mail.utils.UnreadDisplay
import com.infomaniak.mail.views.itemViews.UnreadFolderItemView
import com.infomaniak.mail.views.itemViews.setFolderUi
import kotlin.math.min

class FolderViewHolder(
    inflater: LayoutInflater,
    parent: ViewGroup,
) : MenuDrawerViewHolder(ItemMenuDrawerFolderBinding.inflate(inflater, parent, false)) {

    override val binding = super.binding as ItemMenuDrawerFolderBinding

    fun displayFolder(
        folderUi: FolderUi,
        currentFolderId: String?,
        hasCollapsibleFolder: Boolean,
        onFolderClicked: (folderId: String) -> Unit,
        onFolderLongClicked: (folderId: String, folderName: String, view: View) -> Unit,
        onCollapseChildrenClicked: (folderId: String, shouldCollapse: Boolean) -> Unit,
    ) {
        val folder = folderUi.folder
        SentryLog.d("Bind", "Bind Folder : ${folder.name}")

        val roleDependantParameters = folder.role?.let {
            RoleDependantParameters(
                iconId = it.folderIconRes,
                trackerName = it.matomoName,
                trackerValue = null,
            )
        } ?: run {
            RoleDependantParameters(
                iconId = if (folder.isFavorite) R.drawable.ic_folder_star else R.drawable.ic_folder,
                trackerName = MatomoName.CustomFolder,
                trackerValue = folderUi.depth.toFloat(),
            )
        }

        val unread = when (folder.role) {
            FolderRole.DRAFT, FolderRole.SCHEDULED_DRAFTS, FolderRole.SNOOZED -> UnreadDisplay(count = folder.threads.count())
            FolderRole.SENT, FolderRole.TRASH -> UnreadDisplay(count = 0)
            else -> folder.unreadCountDisplay
        }

        val folderIndent = min(folderUi.depth, MAX_SUB_FOLDERS_INDENT)
        binding.root.setFolderUi(
            folderUi,
            roleDependantParameters,
            folderIndent,
            unread,
            currentFolderId,
            hasCollapsibleFolder,
            onFolderClicked,
            onFolderLongClicked,
            onCollapseChildrenClicked,
        )
    }

    private fun UnreadFolderItemView.setFolderUi(
        folderUi: FolderUi,
        roleDependantParameters: RoleDependantParameters,
        folderIndent: Int,
        unread: UnreadDisplay?,
        currentFolderId: String?,
        hasCollapsibleFolder: Boolean,
        onFolderClicked: (folderId: String) -> Unit,
        onFolderLongClicked: (folderId: String, folderName: String, view: View) -> Unit,
        onCollapseChildrenClicked: (folderId: String, shouldCollapse: Boolean) -> Unit,
    ) {
        val folder = folderUi.folder
        val folderName = folder.getLocalizedName(context)
        val (iconId, trackerName, trackerValue) = roleDependantParameters

        setFolderUi(folder, iconId, isSelected = folder.id == currentFolderId)

        initOnCollapsibleClickListener { onCollapseChildrenClicked(folderUi.folder.id, isCollapsed) }

        isPastilleDisplayed = unread?.shouldDisplayPastille ?: false
        unreadCount = unread?.count ?: 0
        isCollapsed = folder.isCollapsed
        canBeCollapsed = folderUi.canBeCollapsed

        setIndent(
            indent = folderIndent,
            hasCollapsibleFolder = hasCollapsibleFolder,
            canBeCollapsed = canBeCollapsed,
        )

        setCollapsingButtonContentDescription(folderName)

        if (folder.role == null) {
            setOnLongClickListener {
                onFolderLongClicked.invoke(folder.id, folder.name, it)
                true
            }
        }

        setOnClickListener {
            trackMenuDrawerEvent(trackerName, value = trackerValue)
            onFolderClicked.invoke(folder.id)
        }
    }

    private data class RoleDependantParameters(
        @DrawableRes var iconId: Int,
        var trackerName: MatomoName,
        var trackerValue: Float?,
    )

    companion object {
        const val MAX_SUB_FOLDERS_INDENT = 2
    }
}
