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
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.deleteMessages
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController.deleteThreads
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
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

object FolderController {

    //region Get data
    private fun getFolders(realm: MutableRealm? = null): RealmResults<Folder> {
        return realm.getFoldersQuery().find()
    }

    fun getFoldersAsync(realm: MutableRealm? = null): SharedFlow<ResultsChange<Folder>> {
        return realm.getFoldersQuery().asFlow().toSharedFlow()
    }

    private fun MutableRealm?.getFoldersQuery(): RealmQuery<Folder> {
        return (this ?: RealmDatabase.mailboxContent).query()
    }

    fun getFolder(id: String, realm: MutableRealm? = null): Folder? {
        return realm.getFolderQuery(id).find()
    }

    fun getFolderAsync(id: String, realm: MutableRealm? = null): SharedFlow<SingleQueryChange<Folder>> {
        return realm.getFolderQuery(id).asFlow().toSharedFlow()
    }

    private fun MutableRealm?.getFolderQuery(id: String): RealmSingleQuery<Folder> {
        return (this ?: RealmDatabase.mailboxContent).query<Folder>("${Folder::id.name} = '$id'").first()
    }

    fun getFolder(role: FolderRole, realm: MutableRealm? = null): Folder? {
        return realm.getFolderQuery(role).find()
    }

    private fun MutableRealm?.getFolderQuery(role: FolderRole): RealmSingleQuery<Folder> {
        return (this ?: RealmDatabase.mailboxContent).query<Folder>("${Folder::_role.name} = '${role.name}'").first()
    }
    //endregion

    //region Edit data
    fun update(apiFolders: List<Folder>) {
        RealmDatabase.mailboxContent.writeBlocking {

            // Get current data
            Log.d(RealmDatabase.TAG, "Folders: Get current data")
            val realmFolders = getFolders(this)

            // Get outdated data
            Log.d(RealmDatabase.TAG, "Folders: Get outdated data")
            val (outdatedFolders, outdatedThreads, outdatedMessages) = getOutdatedData(realmFolders, apiFolders)

            // Save new data
            Log.d(RealmDatabase.TAG, "Folders: Save new data")
            insertNewData(realmFolders, apiFolders)

            // Delete outdated data
            Log.d(RealmDatabase.TAG, "Folders: Delete outdated data")
            deleteMessages(outdatedMessages)
            deleteThreads(outdatedThreads)
            deleteFolders(outdatedFolders)
        }
    }

    private fun getOutdatedData(
        realmFolders: RealmResults<Folder>,
        apiFolders: List<Folder>,
    ): Triple<List<Folder>, List<Thread>, List<Message>> {

        // Outdated Folders
        val outdatedFolders = realmFolders.filter { realmFolder ->
            apiFolders.none { apiFolder -> apiFolder.id == realmFolder.id }
        }

        // Outdated Messages
        val possiblyOutdatedThreads = outdatedFolders.flatMap { it.threads }
        val outdatedMessages = possiblyOutdatedThreads.flatMap { it.messages }.filter { message ->
            outdatedFolders.any { folder -> folder.id == message.folderId }
        }

        // Outdated Threads
        val outdatedThreads = possiblyOutdatedThreads.filter { thread ->
            thread.messages.all { message -> outdatedMessages.any { it.uid == message.uid } }
        }

        return Triple(outdatedFolders, outdatedThreads, outdatedMessages)
    }

    private fun MutableRealm.insertNewData(realmFolders: RealmResults<Folder>, apiFolders: List<Folder>) {
        apiFolders.forEach { apiFolder ->

            realmFolders.find { it.id == apiFolder.id }?.let { realmFolder ->
                apiFolder.initLocalValues(
                    threads = realmFolder.threads.toRealmList(),
                    parentLink = realmFolder.parentLink,
                    lastUpdatedAt = realmFolder.lastUpdatedAt,
                )
            }

            copyToRealm(apiFolder, UpdatePolicy.ALL)
        }
    }

    private fun upsertFolder(folder: Folder): Folder {
        return RealmDatabase.mailboxContent.writeBlocking { copyToRealm(folder, UpdatePolicy.ALL) }
    }

    fun updateFolder(id: String, realm: MutableRealm? = null, onUpdate: (folder: Folder) -> Unit) {
        val block: (MutableRealm) -> Unit = { getFolder(id, it)?.let(onUpdate) }
        return realm?.let(block) ?: RealmDatabase.mailboxContent.writeBlocking(block)
    }

    fun updateFolderLastUpdatedAt(id: String, realm: MutableRealm? = null) {
        updateFolder(id, realm) {
            it.lastUpdatedAt = Date().toRealmInstant()
        }
    }

    private fun MutableRealm.deleteFolders(folders: List<Folder>) {
        folders.forEach { getFolder(it.id, this)?.let(::delete) }
    }
    //endregion

    // TODO: RealmKotlin doesn't fully support `IN` for now.
    // TODO: Workaround: https://github.com/realm/realm-js/issues/2781#issuecomment-607213640
    // fun getDeletableFolders(foldersToKeep: List<Folder>): RealmResults<Folder> {
    //     val foldersIds = foldersToKeep.map { it.id }
    //     val query = foldersIds.joinToString(
    //         prefix = "NOT (${Folder::id.name} = '",
    //         separator = "' OR ${Folder::id.name} = '",
    //         postfix = "')"
    //     )
    //     return MailRealm.mailboxContent.query<Folder>(query).find()
    // }
}
