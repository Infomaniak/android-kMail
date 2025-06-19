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
package com.infomaniak.mail.dataset

import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.dataset.DummyFolders.FOLDER_DRAFT_ID
import com.infomaniak.mail.dataset.DummyFolders.FOLDER_INBOX_ID
import com.infomaniak.mail.dataset.DummyFolders.FOLDER_SENT_ID
import com.infomaniak.mail.dataset.DummyMessages.messageDraft
import com.infomaniak.mail.dataset.DummyMessages.messageInbox
import com.infomaniak.mail.dataset.DummyMessages.messageSent
import com.infomaniak.mail.dataset.DummyMessages.messageSnoozed

object DummyThreads {
    val threadInbox = threadOf(messageInbox, messageSent).apply { folderId = FOLDER_INBOX_ID }
    val threadSent = threadOf(messageSent, messageInbox).apply { folderId = FOLDER_SENT_ID }
    val threadDraft = threadOf(messageDraft).apply { folderId = FOLDER_DRAFT_ID }
    val threadSnoozed = threadOf(messageSnoozed, messageSent).apply { folderId = FOLDER_INBOX_ID }
    val threadSearchInbox = threadOf(messageInbox, messageSent).apply { folderId = FOLDER_INBOX_ID }
    val threadSearchSnoozed = threadOf(messageSnoozed, messageSent).apply { folderId = FOLDER_INBOX_ID }
}

private val mailboxContentRealm = DummyMailboxContent()

private fun threadOf(vararg messages: Message): Thread {
    return messages.first().toThread().apply {
        messages.takeLast(messages.count() - 1).forEach { message ->
            addMessageWithConditions(message, mailboxContentRealm())
        }
        recomputeThread()
    }
}
