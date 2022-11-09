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
import com.infomaniak.mail.utils.toRealmInstant
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import kotlinx.coroutines.flow.Flow
import java.util.*

object FolderController {

    //region Queries
    private fun MutableRealm?.getFoldersQuery(): RealmQuery<Folder> {
        return (this ?: RealmDatabase.mailboxContent()).query()
    }

    private fun MutableRealm?.getFoldersQuery(exceptionsFoldersIds: List<String>): RealmQuery<Folder> {
        val checkIsNotInExceptions = "NOT ${Folder::id.name} IN {${exceptionsFoldersIds.joinToString { "\"$it\"" }}}"
        return (this ?: RealmDatabase.mailboxContent()).query(checkIsNotInExceptions)
    }

    private fun MutableRealm?.getFolderQuery(key: String, value: String): RealmSingleQuery<Folder> {
        return (this ?: RealmDatabase.mailboxContent()).query<Folder>("$key = '$value'").first()
    }
    //endregion

    //region Get data
    private fun getFolders(exceptionsFoldersIds: List<String>, realm: MutableRealm? = null): RealmResults<Folder> {
        return realm.getFoldersQuery(exceptionsFoldersIds).find()
    }

    fun getFoldersAsync(realm: MutableRealm? = null): Flow<ResultsChange<Folder>> {
        return realm.getFoldersQuery().asFlow()
    }

    fun getFolder(id: String, realm: MutableRealm? = null): Folder? {
        return realm.getFolderQuery(Folder::id.name, id).find()
    }

    fun getFolder(role: FolderRole, realm: MutableRealm? = null): Folder? {
        return realm.getFolderQuery(Folder::_role.name, role.name).find()
    }

    fun getFolderAsync(id: String, realm: MutableRealm? = null): Flow<SingleQueryChange<Folder>> {
        return realm.getFolderQuery(Folder::id.name, id).asFlow()
    }
    //endregion

    //region Edit data
    fun update(apiFolders: List<Folder>) {
        RealmDatabase.mailboxContent().writeBlocking {

            Log.d(RealmDatabase.TAG, "Folders: Delete outdated data")
            deleteOutdatedFolders(apiFolders)

            Log.d(RealmDatabase.TAG, "Folders: Save new data")
            insertNewData(apiFolders)
        }
    }

    private fun MutableRealm.deleteOutdatedFolders(apiFolders: List<Folder>) {
        getFolders(exceptionsFoldersIds = apiFolders.map { it.id }, this).reversed().forEach { folder ->
            folder.threads.reversed().forEach { thread ->
                deleteMessages(thread.messages)
                delete(thread)
            }
            delete(folder)
        }
    }

    private fun MutableRealm.insertNewData(apiFolders: List<Folder>) {

        apiFolders.forEach { apiFolder ->

            getFolder(apiFolder.id, this)?.let { localFolder ->
                apiFolder.initLocalValues(
                    threads = localFolder.threads.toRealmList(),
                    parentLink = localFolder.parentLink,
                    lastUpdatedAt = localFolder.lastUpdatedAt,
                )
            }

            copyToRealm(apiFolder, UpdatePolicy.ALL)
        }
    }

    fun updateFolder(id: String, realm: MutableRealm? = null, onUpdate: (folder: Folder) -> Unit) {
        val block: (MutableRealm) -> Unit = { getFolder(id, it)?.let(onUpdate) }
        realm?.let(block) ?: RealmDatabase.mailboxContent().writeBlocking(block)
    }

    fun updateFolderLastUpdatedAt(id: String, realm: MutableRealm) {
        updateFolder(id, realm) {
            it.lastUpdatedAt = Date().toRealmInstant()
        }
    }

    fun MutableRealm.incrementFolderUnreadCount(folderId: String, unseenMessagesCount: Int) {
        updateFolder(folderId, this) {
            it.unreadCount += unseenMessagesCount
        }
    }
    //endregion
}
