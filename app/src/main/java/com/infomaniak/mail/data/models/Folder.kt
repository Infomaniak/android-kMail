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

import android.util.Log
import com.google.gson.annotations.SerializedName
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.MailRealm
import com.infomaniak.mail.data.cache.MailboxContentController
import com.infomaniak.mail.data.cache.MailboxContentController.getLatestFolder
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.realmListOf
import io.realm.toRealmList

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
    var parentLink: Folder? = null // TODO

    fun select() {
        MailRealm.mutableCurrentFolderIdFlow.value = id
    }

    fun fetchThreadsFromAPI(mailboxUuid: String) {
        // Get current data
        Log.d("API", "getUpdatedThreads: Get current data")
        val threadsFromRealm = MailRealm.mailboxContent.writeBlocking { getLatestFolder(id) }?.threads ?: emptyList()
        // TODO: Handle connectivity issues. If there is no Internet, this list will be empty, so all Realm Threads will be deleted. We don't want that.
        val threadsFromApi = ApiRepository.getThreads(mailboxUuid, id).data?.threads?.map { it.initLocalValues() }
        // ?.filterIndexed { index, _ -> index < 7 }
        // ?.filterIndexed { index, _ -> index < 5 }
        // ?.filterIndexed { index, _ -> index < 3 }
            ?: emptyList()

        // Get outdated data
        Log.d("API", "getUpdatedThreads: Get outdated data")
        val deletableThreads = threadsFromRealm.filter { fromRealm ->
            !threadsFromApi.any { fromApi -> fromApi.uid == fromRealm.uid }
        }
        val deletableMessages = deletableThreads.flatMap { thread -> thread.messages.filter { it.folderId == id } }

        // Delete outdated data
        Log.e("API", "getUpdatedThreads: Delete outdated data")
        deletableMessages.forEach { MailboxContentController.deleteMessage(it.uid) }
        deletableThreads.forEach { MailboxContentController.deleteThread(it.uid) }

        // Save new data
        Log.i("API", "getUpdatedThreads: Save new data")
        threads = threadsFromApi.toRealmList()
        MailboxContentController.upsertFolder(this)
    }

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
