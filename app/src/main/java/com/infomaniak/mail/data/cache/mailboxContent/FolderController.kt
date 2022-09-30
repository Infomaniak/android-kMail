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

    private fun getFolders(exceptionsFoldersIds: List<String>, realm: MutableRealm? = null): RealmResults<Folder> {
        return realm.getFoldersQuery(exceptionsFoldersIds).find()
    }

    private fun MutableRealm?.getFoldersQuery(exceptionsFoldersIds: List<String>): RealmQuery<Folder> {
        val checkIsNotInExceptions = "NOT ${Folder::id.name} IN {${exceptionsFoldersIds.joinToString { "\"$it\"" }}}"
        return (this ?: RealmDatabase.mailboxContent).query(checkIsNotInExceptions)
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

            Log.d(RealmDatabase.TAG, "Folders: Delete outdated data")
            deleteOutdatedFolders(apiFolders)

            Log.d(RealmDatabase.TAG, "Folders: Save new data")
            insertNewData(apiFolders)
        }
    }

    private fun MutableRealm.deleteOutdatedFolders(apiFolders: List<Folder>) {
        getFolders(apiFolders.map { it.id }, this).reversed().forEach { folder ->
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
        return realm?.let(block) ?: RealmDatabase.mailboxContent.writeBlocking(block)
    }

    fun updateFolderLastUpdatedAt(id: String, realm: MutableRealm? = null) {
        updateFolder(id, realm) {
            it.lastUpdatedAt = Date().toRealmInstant()
        }
    }
    //endregion
}
