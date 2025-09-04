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

import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Snoozable
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import javax.inject.Inject

// TODO: Handle this correctly if MultiSelect feature is added in the Search.
class FolderRoleUtils @Inject constructor(
    private val folderController: FolderController,
) {

    suspend fun getActionFolderRole(threads: Collection<Thread>): FolderRole? {
        val thread = threads.firstOrNull() ?: return null
        return getActionFolderRole(thread)
    }

    suspend fun getActionFolderRole(thread: Thread): FolderRole? {
        return getActionFolderRole(thread.folderId, thread)
    }

    suspend fun getActionFolderRole(message: Message): FolderRole? {
        return getActionFolderRole(message.folderId, message)
    }

    /**
     * Get the FolderRole of a Message or a list of Threads.
     *
     * @param threads The list of Threads to find the FolderRole. They should ALL be from the same Folder. For now, it's
     * always the case. But it could change in the future (for example, if the MultiSelect feature is added in the Search).
     */
    suspend fun getActionFolderRole(threads: Collection<Thread>, message: Message?): FolderRole? {
        val thread = threads.firstOrNull()
        return getActionFolderRole(
            folderId = message?.folderId ?: thread?.folderId ?: return null,
            snoozable = message ?: thread ?: return null,
        )
    }

    private suspend fun getActionFolderRole(folderId: String, snoozable: Snoozable): FolderRole? {
        val folderRole = folderController.getFolder(folderId)?.role
        return if (folderRole == FolderRole.INBOX && snoozable.isSnoozed()) FolderRole.SNOOZED else folderRole
    }
}
