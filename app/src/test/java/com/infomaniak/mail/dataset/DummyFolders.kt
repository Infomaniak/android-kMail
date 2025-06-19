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

import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.dataset.DummyThreads.threadDraft
import com.infomaniak.mail.dataset.DummyThreads.threadInboxSnoozed

object DummyFolders {

    const val FOLDER_INBOX_ID = "FOLDER_INBOX_ID"
    const val FOLDER_DRAFT_ID = "FOLDER_DRAFT_ID"

    val folderInbox = Folder().apply {
        id = FOLDER_INBOX_ID
        _role = FolderRole.INBOX.name

        threadInboxSnoozed.folderId = id
        threads.add(threadInboxSnoozed)
    }

    val folderDraft = Folder().apply {
        id = FOLDER_DRAFT_ID
        _role = FolderRole.DRAFT.name

        threadDraft.folderId = id
        threads.add(threadDraft)
    }
}
