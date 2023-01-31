/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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

import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.Thread
import com.infomaniak.mail.data.models.message.Message

object SharedViewModelUtils {

    fun markAsSeen(mailbox: Mailbox, thread: Thread, message: Message? = null, withRefresh: Boolean = true): List<String>? {

        val messages = when {
            message != null -> MessageController.getMessageAndDuplicates(thread, message)
            else -> MessageController.getUnseenMessages(thread)
        }

        val isSuccess = ApiRepository.markMessagesAsSeen(mailbox.uuid, messages.getUids()).isSuccess()

        if (isSuccess) {
            val messagesFoldersIds = messages.getFoldersIds()
            if (withRefresh) {
                refreshFolders(mailbox, messagesFoldersIds)
            } else {
                return messagesFoldersIds
            }
        }

        return null
    }

    fun refreshFolders(mailbox: Mailbox, messagesFoldersIds: List<String>, destinationFolderId: String? = null) {

        // We always want to refresh the `destinationFolder` last, to avoid any blink on the UI.
        val foldersIds = mutableSetOf<String>().apply {
            addAll(messagesFoldersIds)
            destinationFolderId?.let(::add)
        }

        foldersIds.forEach { folderId ->
            val folder = FolderController.getFolder(folderId)
            MessageController.fetchFolderMessages(mailbox, folder, okHttpClient = null)
        }
    }
}
