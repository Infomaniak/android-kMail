/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

object FolderController {

    const val SEARCH_FOLDER_ID = "search_folder_id"

    private inline val defaultRealm get() = RealmDatabase.mailboxContent()

    private val isNotSearch = "${Folder::id.name} != '$SEARCH_FOLDER_ID'"

    //region Queries
    /**
     * The `sortByName` for Folders is done twice in the app, but it's not factorisable.
     * So if this sort logic changes, it needs to be changed in both locations.
     * The other location is in `Utils.formatFoldersListWithAllChildren()`.
     */
    private fun getFoldersQuery(realm: TypedRealm, onlyRoots: Boolean = false): RealmQuery<Folder> {
        val rootsQuery = if (onlyRoots) " AND ${Folder.parentsPropertyName}.@count == 0" else ""
        return realm
            .query<Folder>(isNotSearch + rootsQuery)
            .sort(Folder::name.name, Sort.ASCENDING)
            .sort(Folder::isFavorite.name, Sort.DESCENDING)
    }

    private fun getFoldersQuery(exceptionsFoldersIds: List<String>, realm: TypedRealm): RealmQuery<Folder> {
        val checkIsNotInExceptions = "NOT ${Folder::id.name} IN {${exceptionsFoldersIds.joinToString { "'$it'" }}}"
        return realm.query("$checkIsNotInExceptions AND $isNotSearch")
    }

    private fun getFolderQuery(key: String, value: String, realm: TypedRealm): RealmSingleQuery<Folder> {
        return realm.query<Folder>("$key == '$value'").first()
    }
    //endregion

    //region Get data
    private fun getFolders(
        exceptionsFoldersIds: List<String> = emptyList(),
        realm: TypedRealm = defaultRealm,
    ): RealmResults<Folder> {
        val realmQuery = if (exceptionsFoldersIds.isEmpty()) {
            getFoldersQuery(realm)
        } else {
            getFoldersQuery(exceptionsFoldersIds, realm)
        }
        return realmQuery.find()
    }

    fun getRootsFoldersAsync(): Flow<ResultsChange<Folder>> {
        return getFoldersQuery(defaultRealm, onlyRoots = true).asFlow()
    }

    fun getFolder(id: String, realm: TypedRealm = defaultRealm): Folder? {
        return getFolderQuery(Folder::id.name, id, realm).find()
    }

    fun getFolder(role: FolderRole, realm: TypedRealm = defaultRealm): Folder? {
        return getFolderQuery(Folder.rolePropertyName, role.name, realm).find()
    }

    fun getOrCreateSearchFolder(realm: MutableRealm): Folder {
        return getFolderQuery(Folder::id.name, SEARCH_FOLDER_ID, realm).find() ?: let {
            realm.copyToRealm(Folder().apply { id = SEARCH_FOLDER_ID })
        }
    }

    fun getFolderAsync(id: String): Flow<Folder> {
        return getFolderQuery(Folder::id.name, id, defaultRealm).asFlow().mapNotNull { it.obj }
    }

    /**
     * An "incomplete Thread" is a Thread in a specific Folder where only Messages from this Folder are displayed.
     * - In the Drafts, we only want to display draft Messages.
     * - In the Trash, we only want to display deleted Messages.
     */
    fun getIdsOfFoldersWithIncompleteThreads(realm: TypedRealm): List<String> {
        return mutableListOf<String>().apply {
            getFolder(FolderRole.DRAFT, realm)?.id?.let(::add)
            getFolder(FolderRole.TRASH, realm)?.id?.let(::add)
        }
    }
    //endregion

    //region Edit data
    fun updateFolder(id: String, realm: Realm = defaultRealm, onUpdate: (folder: Folder) -> Unit) {
        realm.writeBlocking { getFolder(id, realm = this)?.let(onUpdate) }
    }

    fun update(remoteFolders: List<Folder>, realm: Realm) {
        realm.writeBlocking {

            Log.d(RealmDatabase.TAG, "Folders: Delete outdated data")
            deleteOutdatedFolders(remoteFolders)

            Log.d(RealmDatabase.TAG, "Folders: Save new data")
            insertNewData(remoteFolders)
        }
    }

    fun deleteSearchData(realm: MutableRealm) = with(getOrCreateSearchFolder(realm)) {
        messages = realmListOf()
        threads = realmListOf()
    }

    private fun MutableRealm.deleteOutdatedFolders(remoteFolders: List<Folder>) {
        getFolders(exceptionsFoldersIds = remoteFolders.map { it.id }, realm = this).reversed().forEach { folder ->
            deleteLocalFolder(folder)
        }
    }

    private fun MutableRealm.deleteLocalFolder(folder: Folder) {
        deleteMessages(folder.messages)
        if (folder.threads.isNotEmpty()) delete(folder.threads)
        delete(folder)
    }

    private fun MutableRealm.insertNewData(remoteFolders: List<Folder>) {

        remoteFolders.forEach { remoteFolder ->

            getFolder(remoteFolder.id, realm = this)?.let { localFolder ->
                remoteFolder.initLocalValues(
                    localFolder.lastUpdatedAt,
                    localFolder.cursor,
                    localFolder.unreadCount,
                    localFolder.threads,
                    localFolder.messages,
                )
            }

            copyToRealm(remoteFolder, UpdatePolicy.ALL)
        }
    }

    fun refreshUnreadCount(id: String, mailboxObjectId: String, realm: MutableRealm) {

        val folder = getFolder(id, realm) ?: return

        val unreadCount = ThreadController.getUnreadThreadsCount(folder)
        folder.unreadCount = unreadCount

        if (folder.role == FolderRole.INBOX) {
            MailboxController.updateMailbox(mailboxObjectId) { mailbox ->
                mailbox.inboxUnreadCount = unreadCount
            }
        }
    }
    //endregion
}
