/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.mail.utils

import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.FolderUi
import com.infomaniak.mail.data.models.forEachNestedItem
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.folderPicker.FolderPickerAdapter
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.extensions.IK_FOLDER

/**
 * Returns a tree-like list of [FolderUi] where each folder has a reference to its children. This method automatically
 * excludes .ik folders.
 *
 * @param isInDefaultFolderSection Are these [FolderUi] in the upper or lower section of the UI. This is also used in the menu
 * drawer to not display role folders in the bottom custom folder section because they are already displayed on the upper section.
 */
fun List<Folder>.toFolderUiTree(isInDefaultFolderSection: Boolean): List<FolderUi> {
    val folderToFolderUi = mutableMapOf<Pair<Folder, Int>, FolderUi>()
    val excludeRoleFolder = isInDefaultFolderSection.not()

    // Step 1: Instantiate all FolderUi instances and compute visibility
    forEachNestedItem { folder, depth ->
        if (folder.shouldBeExcluded(excludeRoleFolder)) return@forEachNestedItem

        // Create placeholder (empty children for now)
        val folderUi = FolderUi(
            folder = folder,
            depth = depth,
            canBeCollapsed = false, // will compute below
            children = emptyList(), // will compute below
            isHidden = false, // will compute below
        )
        folderToFolderUi[folder to depth] = folderUi
    }

    // Step 2: Link children of FolderUi to existing instances + compute collapsibility + identify root folders
    val resultRoots = mutableListOf<FolderUi>()
    folderToFolderUi.forEach { (key, folderUi) ->
        val (folder, parentDepth) = key

        val validChildren = folder.children
            .filter { !it.shouldBeExcluded(excludeRoleFolder = true) }
            .mapNotNull { folderToFolderUi[it to parentDepth + 1] } // children are stored at the parent's depth +1

        for (child in validChildren) {
            child.isHidden = folder.isCollapsed || folderUi.isHidden
        }

        folderUi.apply {
            canBeCollapsed = validChildren.isNotEmpty()
            children = validChildren
        }

        if (folderUi.isRoot) resultRoots.add(folderUi)
    }

    // Step 3: Only return root folders needed for traversal
    return resultRoots
}

private fun Folder.shouldBeExcluded(excludeRoleFolder: Boolean): Boolean {
    val isHiddenIkFolder = path.startsWith(IK_FOLDER) && role == null
    val isRoleFolder = role != null
    return isHiddenIkFolder || (excludeRoleFolder && isRoleFolder)
}

/**
 * @return A list of [FolderUi] with a single divider of the provided type
 */
fun MainViewModel.DisplayedFolders.flattenAndAddDividerBeforeFirstCustomFolder(
    dividerType: FolderPickerAdapter.FolderPickerItem.Divider,
    excludedFolderRoles: Set<Folder.FolderRole> = emptySet(),
): List<FolderPickerAdapter.FolderPickerItem> = buildList {
    runCatchingRealm {
        default.forEachNestedItem { folderUi, _ ->
            if (folderUi.folder.role !in excludedFolderRoles) add(FolderPickerAdapter.FolderPickerItem.Folder(folderUi))
        }
        add(dividerType)
        custom.forEachNestedItem { folderUi, _ ->
            if (folderUi.folder.role !in excludedFolderRoles) add(FolderPickerAdapter.FolderPickerItem.Folder(folderUi))
        }
    }
}
