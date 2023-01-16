/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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

import com.infomaniak.mail.data.LocalSettings.ThreadMode
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread

object SharedViewModelUtils {

    fun markAsSeen(thread: Thread, mailbox: Mailbox, threadMode: ThreadMode) {
        val messages = ThreadController.getThreadUnseenMessages(thread)

        val isSuccess = ApiRepository.markMessagesAsSeen(mailbox.uuid, messages.map { it.uid }).isSuccess()
        if (isSuccess) refreshMessagesFolders(mailbox, threadMode, messages)
    }

    fun refreshMessagesFolders(
        mailbox: Mailbox,
        threadMode: ThreadMode,
        messages: List<Message>,
        destinationFolderId: String? = null,
    ) {
        mutableSetOf<String>().apply {
            addAll(messages.map { it.folderId })
            destinationFolderId?.let(::add)
        }.forEach { folderId ->
            FolderController.getFolder(folderId)?.let { folder ->
                MessageController.fetchFolderMessages(
                    mailbox = mailbox,
                    folder = folder,
                    threadMode = threadMode,
                    okHttpClient = null,
                    realm = null,
                )
            }
        }
    }
}
