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
package com.infomaniak.mail.data.models

import com.google.gson.annotations.SerializedName

data class Folder(
    val id: String,
    val path: String,
    val name: String,
    val role: FolderRole,
    @SerializedName("unread_count")
    val unreadCount: Int,
    @SerializedName("total_count")
    val totalCount: Int,
    @SerializedName("is_fake")
    val isFake: Boolean,
    @SerializedName("is_collapsed")
    val isCollapsed: Boolean,
    @SerializedName("is_favorite")
    val isFavorite: Boolean,
    val separator: String,
    val children: ArrayList<Folder>,

    /**
     * Local
     */
    val threads: ArrayList<Thread>,
    val parentLink: Folder?,
) {
    enum class FolderRole(name: String, localizedName: String, order: Int) {
        ARCHIVE("ARCHIVE", "Archives", 6),
        DRAFT("DRAFT", "Drafts", 2),
        INBOX("INBOX", "Inbox", 1),
        SENT("SENT", "Sent", 3),
        SPAM("SPAM", "Spam", 4),
        TRASH("TRASH", "Trash", 5),
    }
}