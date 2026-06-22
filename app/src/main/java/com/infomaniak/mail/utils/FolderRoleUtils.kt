/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025-2026 Infomaniak Network SA
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
import com.infomaniak.mail.data.models.FolderRole
import com.infomaniak.mail.data.models.Snoozable
import com.infomaniak.mail.data.models.extensions.folder
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import javax.inject.Inject

class FolderRoleUtils @Inject constructor(
    private val folderController: FolderController,
) {
    /**
     * Get the FolderRole of a list of Messages.
     *
     * @param messages The list of Messages to find the FolderRole. They can be from different folders because of the
     * Multiselect in the Search.
     *
     * If there is only one message, we will return the folder role of that message.
     * If there are multiple messages, they can be from different folders, i.e. even if the thread is in INBOX,
     * there can be a message that is in messages sent folder in the same thread. So we cannot choose the folder role of the opened folder.
     * In the cases that a draft, rescheduled message or spam are in a thread in a not permanently delete folder
     * (a reply of a mail in inbox for example), we had issues for deleting when we choose the role of the opened folder.
     */
    suspend fun getActionFolderRoles(messages: List<Message>): List<FolderRole> {
        val folderRoles = when {
            messages.count() == 1 -> listOfNotNull(getActionFolderRole(messages.first()))
            else -> messages.mapNotNull { message -> message.folder.role ?: getActionFolderRole(message) }
        }
        return folderRoles
    }

    suspend fun getActionFolderRole(message: Message): FolderRole? {
        return getActionFolderRole(message.folderId, message)
    }

    /**
     * There are times when the thread folder role is useful, i.e. when we need to know if the thread is in the archive folder
     * (to do the unarchive action). In this case we check where the thread is, not if every message is in the archive folder.
     */
    suspend fun getThreadsActionFolderRole(threads: Collection<Thread>): FolderRole? {
        val thread = threads.firstOrNull() ?: return null
        return getThreadActionFolderRole(thread)
    }

    suspend fun getThreadActionFolderRole(thread: Thread): FolderRole? {
        return getActionFolderRole(thread.folderId, thread)
    }

    private suspend fun getActionFolderRole(folderId: String, snoozable: Snoozable): FolderRole? {
        val folderRole = folderController.getFolder(folderId)?.role
        return if (folderRole == FolderRole.INBOX && snoozable.isSnoozed()) FolderRole.SNOOZED else folderRole
    }
}
