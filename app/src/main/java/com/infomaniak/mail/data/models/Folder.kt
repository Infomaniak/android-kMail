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
import io.realm.RealmList
import io.realm.RealmObject

open class Folder(
    var id: String = "",
    var path: String = "",
    var name: String = "",
    @SerializedName("role")
    private var _role: String? = null,
    @SerializedName("unread_count")
    var unreadCount: Int = 0,
    @SerializedName("total_count")
    var totalCount: Int = 0,
    @SerializedName("is_fake")
    var isFake: Boolean = false,
    @SerializedName("is_collapsed")
    var isCollapsed: Boolean = false,
    @SerializedName("is_favorite")
    var isFavorite: Boolean = false,
    var separator: String = "",
    var children: RealmList<Folder> = RealmList(),

    /**
     * Local
     */
    var threads: RealmList<Thread> = RealmList(),
    var parentLink: Folder? = null,
) : RealmObject() {

    fun getRole(): FolderRole? = when (_role) {
        FolderRole.ARCHIVE.value -> FolderRole.ARCHIVE
        FolderRole.DRAFT.value -> FolderRole.DRAFT
        FolderRole.INBOX.value -> FolderRole.INBOX
        FolderRole.SENT.value -> FolderRole.SENT
        FolderRole.SPAM.value -> FolderRole.SPAM
        FolderRole.TRASH.value -> FolderRole.TRASH
        else -> null
    }

    enum class FolderRole(val value: String, localizedName: String, order: Int) {
        ARCHIVE("ARCHIVE", "Archives", 6),
        DRAFT("DRAFT", "Drafts", 2),
        INBOX("INBOX", "Inbox", 1),
        SENT("SENT", "Sent", 3),
        SPAM("SPAM", "Spam", 4),
        TRASH("TRASH", "Trash", 5),
    }
}