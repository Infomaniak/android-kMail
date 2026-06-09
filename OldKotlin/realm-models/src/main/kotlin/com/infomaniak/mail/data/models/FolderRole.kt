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
package com.infomaniak.mail.data.models

enum class FolderRole(
    val order: Int,
    val folderSort: Folder.FolderSort = Folder.FolderSort.Default,
    val groupMessagesBySection: Boolean = true,
) {
    INBOX(10),
    COMMERCIAL(9),
    SOCIALNETWORKS(8),
    SENT(7),
    SNOOZED(
        order = 6,
        folderSort = Folder.FolderSort.Snooze,
        groupMessagesBySection = false,
    ),
    SCHEDULED_DRAFTS(
        order = 5,
        folderSort = Folder.FolderSort.Scheduled,
        groupMessagesBySection = false,
    ),
    DRAFT(4),
    SPAM(3),
    TRASH(2),
    ARCHIVE(1),
}
