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
package com.infomaniak.mail.data.cache.mailboxContent

import com.infomaniak.mail.data.models.Folder
import io.realm.kotlin.TypedRealm

data class ImpactedFolders(
    private val folderIds: MutableSet<String> = mutableSetOf(),
    private val folderRoles: MutableSet<Folder.FolderRole> = mutableSetOf(),
) {
    operator fun plusAssign(folderId: String) {
        folderIds += folderId
    }

    operator fun plusAssign(folderRole: Folder.FolderRole) {
        folderRoles += folderRole
    }

    operator fun plusAssign(otherTarget: ImpactedFolders) {
        folderIds += otherTarget.folderIds
        folderRoles += otherTarget.folderRoles
    }

    /**
     * This method makes sure we have no duplicated folders by merging both lists into a single one
     */
    fun getFolderIds(realm: TypedRealm): Set<String> {
        folderRoles.forEach { folderRole ->
            FolderController.getFolder(folderRole, realm)?.id?.let(folderIds::add)
        }

        return folderIds
    }
}
