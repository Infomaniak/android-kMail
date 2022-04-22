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
import com.infomaniak.mail.data.models.threads.Thread
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.realmListOf

class Folder : RealmObject {
    @PrimaryKey
    var id: String = ""
    var path: String = ""
    var name: String = ""

    @Suppress("PropertyName")
    @SerializedName("role")
    var _role: String? = null

    @SerializedName("unread_count")
    var unreadCount: Int = 0

    @SerializedName("total_count")
    var totalCount: Int = 0

    @SerializedName("is_fake")
    var isFake: Boolean = false

    @SerializedName("is_collapsed")
    var isCollapsed: Boolean = false

    @SerializedName("is_favorite")
    var isFavorite: Boolean = false
    var separator: String = ""
    var children: RealmList<Folder> = realmListOf()

    /**
     * Local
     */
    var threads: RealmList<Thread> = realmListOf()
    var parentLink: Folder? = null

    fun getRole(): FolderRole? = when (_role) {
        FolderRole.ARCHIVE.name -> FolderRole.ARCHIVE
        FolderRole.DRAFT.name -> FolderRole.DRAFT
        FolderRole.INBOX.name -> FolderRole.INBOX
        FolderRole.SENT.name -> FolderRole.SENT
        FolderRole.SPAM.name -> FolderRole.SPAM
        FolderRole.TRASH.name -> FolderRole.TRASH
        else -> null
    }

    enum class FolderRole(localizedName: String, order: Int) {
        INBOX("Inbox", 1),
        DRAFT("Drafts", 2),
        SENT("Sent", 3),
        SPAM("Spam", 4),
        TRASH("Trash", 5),
        ARCHIVE("Archives", 6),
    }
}