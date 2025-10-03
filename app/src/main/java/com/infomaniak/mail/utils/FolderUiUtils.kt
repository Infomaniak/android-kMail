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
import com.infomaniak.mail.utils.extensions.IK_FOLDER

/**
 * Returns a tree-like list of [com.infomaniak.mail.data.models.FolderUi] where each folder has a reference to its children. This method automatically
 * excludes .ik folders.
 *
 * @param isInDefaultFolderSection Do not return folders with role nor their children. This is used in the menu drawer to not display
 * role folders in the bottom custom folder section because they are already displayed on the upper section.
 */
fun List<Folder>.toFolderUi(isInDefaultFolderSection: Boolean): List<FolderUi> {
    val resultRoots = mutableListOf<FolderUi>()
    val folderToMenuFolder = mutableMapOf<Folder, FolderUi>()
    val excludeRoleFolder = isInDefaultFolderSection.not()

    // Step 1: Instantiate all FolderUi instances and identify root folders
    forEachNestedItem { folder, depth ->
        if (folder.shouldBeExcluded(excludeRoleFolder)) return@forEachNestedItem

        // Create placeholder (empty children for now)
        val menu = FolderUi(
            folder = folder,
            depth = depth,
            canBeCollapsed = false, // will compute below
            children = emptyList(),
            isInDefaultFolderSection = isInDefaultFolderSection,
        )
        folderToMenuFolder[folder] = menu

        if (depth == 0) resultRoots.add(menu)
    }

    // Step 2: Link children of FolderUi to existing instances + compute collapsibility
    folderToMenuFolder.forEach { (folder, menuFolder) ->
        val validChildren = folder.children
            .filter { !(it.shouldBeExcluded(excludeRoleFolder)) }
            .mapNotNull { folderToMenuFolder[it] }

        folderToMenuFolder[folder] = menuFolder.apply {
            canBeCollapsed = menuFolder.depth == 0 && validChildren.isNotEmpty()
            children = validChildren
        }
    }

    // Step 3: Only return root folders needed for traversal
    return resultRoots.map { folderToMenuFolder[it.folder]!! } // TODO: Remove!!
}

fun Folder.shouldBeExcluded(excludeRoleFolder: Boolean): Boolean {
    val isHiddenIkFolder = path.startsWith(IK_FOLDER) && role == null
    val isRoleFolder = role != null
    return isHiddenIkFolder || (excludeRoleFolder && isRoleFolder)
}

/**
 * @return A list of [FolderUi] with a single divider of the provided type
 */
fun MainViewModel.DisplayedFolders.flattenAndAddDividerBeforeFirstCustomFolder(
    dividerType: Any,
    excludedFolderRoles: Set<Folder.FolderRole> = emptySet(),
): List<Any> = buildList {
    default.forEachNestedItem { folder, _ -> if (folder.folder.role !in excludedFolderRoles) add(folder) }
    add(dividerType)
    custom.forEachNestedItem { folder, _ -> if (folder.folder.role !in excludedFolderRoles) add(folder) }
}
