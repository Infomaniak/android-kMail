/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.folder

import com.infomaniak.mail.data.models.FolderRole
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadSendingStatusTest {

    @Test
    fun sendingStatusFollowsMessageStateInDraftFolder() {
        val message = Message().apply { folderId = "drafts" }
        val thread = Thread().apply {
            folderId = "drafts"
            messages.add(message)
        }

        assertFalse(thread.shouldShowSendingStatus(FolderRole.DRAFT))
        message.isMessageWithSendDelay = true
        assertTrue(thread.shouldShowSendingStatus(FolderRole.DRAFT))
        message.isMessageWithSendDelay = false
        assertFalse(thread.shouldShowSendingStatus(FolderRole.DRAFT))
    }

    @Test
    fun sendingStatusIgnoresOtherFoldersAndMessages() {
        val thread = Thread().apply {
            folderId = "drafts"
            messages.add(Message().apply {
                folderId = "inbox"
                isMessageWithSendDelay = true
            })
        }

        assertFalse(thread.shouldShowSendingStatus(FolderRole.DRAFT))
        thread.messages.add(Message().apply {
            folderId = "drafts"
            isMessageWithSendDelay = true
        })
        assertTrue(thread.shouldShowSendingStatus(FolderRole.DRAFT))
        assertFalse(thread.shouldShowSendingStatus(FolderRole.INBOX))
        assertFalse(thread.shouldShowSendingStatus(null))
    }
}
