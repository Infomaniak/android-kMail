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
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import kotlinx.coroutines.flow.Flow

object FolderController {

    //region Queries
    private fun getFoldersQuery(realm: TypedRealm? = null): RealmQuery<Folder> {
        return (realm ?: RealmDatabase.mailboxContent()).query()
    }

    private fun getFoldersQuery(exceptionsFoldersIds: List<String>, realm: TypedRealm? = null): RealmQuery<Folder> {
        val checkIsNotInExceptions = "NOT ${Folder::id.name} IN {${exceptionsFoldersIds.joinToString { "\"$it\"" }}}"
        return (realm ?: RealmDatabase.mailboxContent()).query(checkIsNotInExceptions)
    }

    private fun getFolderQuery(key: String, value: String, realm: TypedRealm? = null): RealmSingleQuery<Folder> {
        return (realm ?: RealmDatabase.mailboxContent()).query<Folder>("$key == '$value'").first()
    }
    //endregion

    //region Get data
    private fun getFolders(exceptionsFoldersIds: List<String>, realm: TypedRealm? = null): RealmResults<Folder> {
        return getFoldersQuery(exceptionsFoldersIds, realm).find()
    }

    fun getFoldersAsync(realm: TypedRealm? = null): Flow<ResultsChange<Folder>> {
        return getFoldersQuery(realm).asFlow()
    }

    fun getFolder(id: String, realm: TypedRealm? = null): Folder? {
        return getFolderQuery(Folder::id.name, id, realm).find()
    }

    fun getFolder(role: FolderRole, realm: TypedRealm? = null): Folder? {
        return getFolderQuery(Folder::_role.name, role.name, realm).find()
    }

    fun getFolderAsync(id: String, realm: TypedRealm? = null): Flow<SingleQueryChange<Folder>> {
        return getFolderQuery(Folder::id.name, id, realm).asFlow()
    }
    //endregion

    //region Edit data
    fun update(remoteFolders: List<Folder>) {
        RealmDatabase.mailboxContent().writeBlocking {

            Log.d(RealmDatabase.TAG, "Folders: Delete outdated data")
            deleteOutdatedFolders(remoteFolders)

            Log.d(RealmDatabase.TAG, "Folders: Save new data")
            insertNewData(remoteFolders)
        }
    }

    private fun MutableRealm.deleteOutdatedFolders(remoteFolders: List<Folder>) {
        getFolders(exceptionsFoldersIds = remoteFolders.map { it.id }, realm = this).reversed().forEach { folder ->
            deleteLocalFolder(folder)
        }
    }

    private fun MutableRealm.deleteLocalFolder(folder: Folder) {

        val messages = MessageController.getMessages(folder.id, realm = this).find()
        deleteMessages(messages)

        val threadsQuery = ThreadController.getThreads(folder.id, realm = this).query("${Thread::foldersIds.name}.@count == 1")
        delete(threadsQuery)

        delete(folder)
    }

    private fun MutableRealm.insertNewData(remoteFolders: List<Folder>) {

        remoteFolders.forEach { remoteFolder ->

            getFolder(remoteFolder.id, realm = this)?.let { localFolder ->
                remoteFolder.initLocalValues(
                    parentLink = localFolder.parentLink,
                    lastUpdatedAt = localFolder.lastUpdatedAt,
                    cursor = localFolder.cursor,
                )
            }

            copyToRealm(remoteFolder, UpdatePolicy.ALL)
        }
    }

    fun updateFolder(id: String, realm: MutableRealm? = null, onUpdate: (folder: Folder) -> Unit) {
        val block: (MutableRealm) -> Unit = { getFolder(id, realm = it)?.let(onUpdate) }
        realm?.let(block) ?: RealmDatabase.mailboxContent().writeBlocking(block)
    }

    fun MutableRealm.incrementFolderUnreadCount(folderId: String, unseenMessagesCount: Int) {
        updateFolder(folderId, realm = this) {
            it.unreadCount += unseenMessagesCount
        }
    }
    //endregion
}
