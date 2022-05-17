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

@file:UseSerializers(RealmListSerializer::class)

package com.infomaniak.mail.data.models

import android.content.Context
import android.util.Log
import androidx.annotation.IdRes
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.api.RealmListSerializer
import com.infomaniak.mail.data.cache.MailRealm
import com.infomaniak.mail.data.cache.MailboxContentController
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.realmListOf
import io.realm.toRealmList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
class Folder : RealmObject {
    @PrimaryKey
    var id: String = ""
    var path: String = ""
    var name: String = ""
    @Suppress("PropertyName")
    @SerialName("role")
    var _role: String? = null
    @SerialName("unread_count")
    var unreadCount: Int = 0
    @SerialName("total_count")
    var totalCount: Int = 0
    @SerialName("is_fake")
    var isFake: Boolean = false
    @SerialName("is_collapsed")
    var isCollapsed: Boolean = false
    @SerialName("is_favorite")
    var isFavorite: Boolean = false
    var separator: String = ""
    var children: RealmList<Folder> = realmListOf()

    /**
     * Local
     */
    var threads: RealmList<Thread> = realmListOf()
    var parentLink: Folder? = null // TODO

    fun updateAndSelect(isInternetAvailable: Boolean, mailboxUuid: String) {
        fetchThreadsFromApi(isInternetAvailable, mailboxUuid)
        select()
    }

    fun select() {
        MailRealm.mutableCurrentFolderIdFlow.value = id
    }

    private fun fetchThreadsFromApi(isInternetAvailable: Boolean, mailboxUuid: String) {
        // Get current data
        Log.d("API", "Threads: Get current data")
        val threadsFromRealm = threads
        val threadsFromApi = ApiRepository.getThreads(mailboxUuid, id).data?.threads
            ?.map { threadFromApi ->
                threadFromApi.initLocalValues()
                // TODO: Put this back (and make it work) when we have EmbeddedObjects
                // threadsFromRealm.find { it.uid == threadFromApi.uid }?.let { threadFromRealm ->
                //     threadFromApi.messages.forEach { messageFromApi ->
                //         threadFromRealm.messages.find { it.uid == messageFromApi.uid }?.let { messageFromRealm ->
                //             messageFromApi.apply {
                //                 fullyDownloaded = messageFromRealm.fullyDownloaded
                //                 body = messageFromRealm.body
                //                 attachments = messageFromRealm.attachments
                //             }
                //         }
                //     }
                // }
                // threadFromApi
            }
            ?: emptyList()

        // Get outdated data
        Log.d("API", "Threads: Get outdated data")
        val deletableThreads = if (isInternetAvailable) {
            threadsFromRealm.filter { fromRealm ->
                !threadsFromApi.any { fromApi -> fromApi.uid == fromRealm.uid }
            }
        } else {
            emptyList()
        }
        val deletableMessages = deletableThreads.flatMap { thread -> thread.messages.filter { it.folderId == id } }

        // Save new data
        Log.i("API", "Threads: Save new data")
        threads = threadsFromApi.toRealmList()
        MailboxContentController.upsertFolder(this)

        // Delete outdated data
        Log.e("API", "Threads: Delete outdated data")
        deletableMessages.forEach { MailboxContentController.deleteMessage(it.uid) }
        deletableThreads.forEach { MailboxContentController.deleteThread(it.uid) }
    }

    fun getLocalizedName(context: Context): String = getRole()?.folderNameRes?.let(context::getString) ?: name

    fun getRole(): FolderRole? = when (_role) {
        FolderRole.ARCHIVE.name -> FolderRole.ARCHIVE
        FolderRole.DRAFT.name -> FolderRole.DRAFT
        FolderRole.INBOX.name -> FolderRole.INBOX
        FolderRole.SENT.name -> FolderRole.SENT
        FolderRole.SPAM.name -> FolderRole.SPAM
        FolderRole.TRASH.name -> FolderRole.TRASH
        else -> null
    }

    enum class FolderRole(@IdRes val folderNameRes: Int, val order: Int) {
        INBOX(R.string.InboxFolder, 1),
        DRAFT(R.string.DraftFolder, 2),
        SENT(R.string.SentFolder, 3),
        SPAM(R.string.SpamFolder, 4),
        TRASH(R.string.TrashFolder, 5),
        ARCHIVE(R.string.ArchiveFolder, 6),
    }
}
