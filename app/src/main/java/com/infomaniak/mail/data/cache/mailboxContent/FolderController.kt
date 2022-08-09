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
package com.infomaniak.mail.data.cache.mailboxContent

import android.util.Log
import com.infomaniak.mail.data.cache.RealmController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.deleteMessages
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController.deleteThreads
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.utils.toRealmInstant
import com.infomaniak.mail.utils.toSharedFlow
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import kotlinx.coroutines.flow.SharedFlow
import java.util.*
import kotlin.math.max

object FolderController {

    /**
     * Get data
     */
    fun getFoldersSync(): RealmResults<Folder> {
        return getFolders().find()
    }

    fun getFoldersAsync(): SharedFlow<ResultsChange<Folder>> {
        return getFolders().asFlow().toSharedFlow()
    }

    fun getFolderSync(id: String): Folder? {
        return getFolder(id).find()
    }

    fun getFolderAsync(id: String): SharedFlow<SingleQueryChange<Folder>> {
        return getFolder(id).asFlow().toSharedFlow()
    }

    fun MutableRealm.getLatestFolderSync(id: String): Folder? = getFolderSync(id)?.let(::findLatest)

    /**
     * Edit data
     */
    fun upsertApiData(apiFolders: List<Folder>): List<Folder> {

        // Get current data
        Log.d(RealmController.TAG, "Folders: Get current data")
        val realmFolders = getFoldersSync()

        // Get outdated data
        Log.d(RealmController.TAG, "Folders: Get outdated data")
        // val deletableFolders = MailboxContentController.getDeletableFolders(foldersFromApi)
        val deletableFolders = realmFolders.filter { realmFolder ->
            apiFolders.none { apiFolder -> apiFolder.id == realmFolder.id }
        }
        val possiblyDeletableThreads = deletableFolders.flatMap { it.threads }
        val deletableMessages = possiblyDeletableThreads.flatMap { it.messages }.filter { message ->
            deletableFolders.any { folder -> folder.id == message.folderId }
        }
        val deletableThreads = possiblyDeletableThreads.filter { thread ->
            thread.messages.all { message -> deletableMessages.any { it.uid == message.uid } }
        }

        RealmController.mailboxContent.writeBlocking {
            // Save new data
            Log.d(RealmController.TAG, "Folders: Save new data")
            apiFolders.forEach { apiFolder ->
                realmFolders.find { it.id == apiFolder.id }?.threads
                    ?.mapNotNull(::findLatest)
                    ?.let { apiFolder.threads = it.toRealmList() }
                copyToRealm(apiFolder, UpdatePolicy.ALL)
            }

            // Delete outdated data
            Log.d(RealmController.TAG, "Folders: Delete outdated data")
            deleteMessages(deletableMessages)
            deleteThreads(deletableThreads)
            deleteFolders(deletableFolders)
        }

        return apiFolders
    }

    fun upsertFolder(folder: Folder): Folder {
        return RealmController.mailboxContent.writeBlocking { copyToRealm(folder, UpdatePolicy.ALL) }
    }

    fun updateFolder(id: String, onUpdate: (folder: Folder) -> Unit) {
        RealmController.mailboxContent.writeBlocking {
            getLatestFolderSync(id)
                ?.let(onUpdate)
        }
    }

    fun updateFolderUnreadCount(id: String, unreadCount: Int) {
        updateFolder(id) {
            it.unreadCount = unreadCount
            // it.totalCount = threadsResult.totalMessagesCount // TODO: We don't use this for now.
            it.lastUpdatedAt = Date().toRealmInstant()
        }
    }

    fun MutableRealm.decrementFolderUnreadCount(folderId: String) {
        getLatestFolderSync(folderId)?.let { latestFolder ->
            if (latestFolder.unreadCount > 0) latestFolder.unreadCount = max(latestFolder.unreadCount - 1, 0)
        }
    }

    fun MutableRealm.deleteFolders(folders: List<Folder>) {
        folders.forEach { deleteLatestFolder(it.id) }
    }

    /**
     * Utils
     */
    private fun getFolders(): RealmQuery<Folder> {
        return RealmController.mailboxContent.query()
    }

    private fun getFolder(id: String): RealmSingleQuery<Folder> {
        return RealmController.mailboxContent.query<Folder>("${Folder::id.name} == '$id'").first()
    }

    private fun MutableRealm.deleteLatestFolder(id: String) {
        getLatestFolderSync(id)?.let(::delete)
    }

    /**
     * TODO?
     */
    // fun deleteFolders(folders: List<Folder>) {
    //     MailRealm.mailboxContent.writeBlocking { folders.forEach { getLatestFolder(it.id)?.let(::delete) } }
    // }

    // fun deleteFolder(id: String) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestFolder(id)?.let(::delete) }
    // }

    // TODO: RealmKotlin doesn't fully support `IN` for now.
    // TODO: Workaround: https://github.com/realm/realm-js/issues/2781#issuecomment-607213640
    // fun getDeletableFolders(foldersToKeep: List<Folder>): RealmResults<Folder> {
    //     val foldersIds = foldersToKeep.map { it.id }
    //     val query = foldersIds.joinToString(
    //         prefix = "NOT (${Folder::id.name} == '",
    //         separator = "' OR ${Folder::id.name} == '",
    //         postfix = "')"
    //     )
    //     return MailRealm.mailboxContent.query<Folder>(query).find()
    // }
}
