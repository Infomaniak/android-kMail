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

import com.infomaniak.mail.data.models.SnoozeState
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.dataset.DummyFolders.FOLDER_DRAFT_ID
import com.infomaniak.mail.dataset.DummyFolders.FOLDER_INBOX_ID
import com.infomaniak.mail.dataset.DummyFolders.FOLDER_SENT_ID
import com.infomaniak.mail.utils.extensions.toRealmInstant
import java.util.Date

object DummyMessages {

    val messageInbox = Message().apply {
        uid = MESSAGE_INBOX_ID
        messageId = uid
        folderId = FOLDER_INBOX_ID
    }

    val messageSent = Message().apply {
        uid = MESSAGE_SENT_ID
        messageId = uid
        folderId = FOLDER_SENT_ID
    }

    val messageDraft = Message().apply {
        uid = MESSAGE_DRAFT_ID
        messageId = uid
        folderId = FOLDER_DRAFT_ID
    }

    val messageSnoozed = Message().apply {
        uid = MESSAGE_SNOOZED_ID
        messageId = uid
        folderId = FOLDER_INBOX_ID
        snoozeState = SnoozeState.Snoozed
        snoozeEndDate = Date().toRealmInstant()
        snoozeUuid = uid
    }

    private const val MESSAGE_INBOX_ID = "MESSAGE_INBOX_ID"
    private const val MESSAGE_SENT_ID = "MESSAGE_SENT_ID"
    private const val MESSAGE_DRAFT_ID = "MESSAGE_DRAFT_ID"
    private const val MESSAGE_SNOOZED_ID = "MESSAGE_SNOOZED_ID"
}
