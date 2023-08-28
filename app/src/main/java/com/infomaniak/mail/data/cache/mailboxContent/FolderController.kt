/*
 * Infomaniak Mail - Android
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

import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.utils.copyListToRealm
import com.infomaniak.mail.utils.flatMapFolderChildren
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

class FolderController @Inject constructor(private val mailboxContentRealm: RealmDatabase.MailboxContent) {

    //region Get data
    fun getRootsFoldersAsync(): Flow<ResultsChange<Folder>> {
        return getFoldersQuery(mailboxContentRealm(), onlyRoots = true).asFlow()
    }

    fun getDefaultFoldersAsync(): Flow<ResultsChange<Folder>> {
        return getDefaultFoldersQuery(mailboxContentRealm()).asFlow()
    }

    fun getCustomFoldersAsync(): Flow<ResultsChange<Folder>> {
        return getCustomFoldersQuery(mailboxContentRealm()).asFlow()
    }

    fun getFolder(id: String): Folder? {
        return getFolderQuery(Folder::id.name, id, mailboxContentRealm()).find()
    }

    fun getFolder(role: FolderRole): Folder? {
        return getFolderQuery(Folder.rolePropertyName, role.name, mailboxContentRealm()).find()
    }

    fun getFolderAsync(id: String): Flow<Folder> {
        return getFolderQuery(Folder::id.name, id, mailboxContentRealm()).asFlow().mapNotNull { it.obj }
    }

    fun getRootFolder(name: String) = with(mailboxContentRealm()) {
        query<Folder>("$isRootFolder AND ${Folder::name.name} == $0", name).first().find()
    }
    //endregion

    fun update(remoteFolders: List<Folder>, realm: Realm) {
        val remoteFoldersWithChildren = remoteFolders.flatMapFolderChildren()

        realm.writeBlocking {

            SentryLog.d(RealmDatabase.TAG, "Folders: Delete outdated data")
            deleteOutdatedFolders(remoteFoldersWithChildren)

            SentryLog.d(RealmDatabase.TAG, "Folders: Save new data")
            upsertFolders(remoteFoldersWithChildren)
        }
    }

    fun deleteSearchFolderData(realm: MutableRealm) = with(getOrCreateSearchFolder(realm)) {
        messages = realmListOf()
        threads = realmListOf()
    }

    private fun MutableRealm.deleteOutdatedFolders(remoteFolders: List<Folder>) {
        getFolders(exceptionsFoldersIds = remoteFolders.map { it.id }, realm = this).reversed().forEach { folder ->
            deleteLocalFolder(folder)
        }
    }

    private fun MutableRealm.deleteLocalFolder(folder: Folder) {
        MessageController.deleteMessages(folder.messages, realm = this)
        if (folder.threads.isNotEmpty()) delete(folder.threads)
        delete(folder)
    }

    private fun MutableRealm.upsertFolders(remoteFolders: List<Folder>) {

        remoteFolders.forEach { remoteFolder ->

            getFolder(remoteFolder.id, realm = this)?.let { localFolder ->

                val collapseStateNeedsReset = remoteFolder.isRoot && remoteFolder.children.isEmpty()
                val isCollapsed = if (collapseStateNeedsReset) false else localFolder.isCollapsed

                remoteFolder.initLocalValues(
                    localFolder.lastUpdatedAt,
                    localFolder.cursor,
                    localFolder.unreadCountLocal,
                    localFolder.threads,
                    localFolder.messages,
                    localFolder.remainingOldMessagesToFetch,
                    localFolder.isHistoryComplete,
                    localFolder.isHidden,
                    isCollapsed,
                )
            }
        }

        copyListToRealm(remoteFolders)
    }
    //endregion

    companion object {
        const val SEARCH_FOLDER_ID = "search_folder_id"

        private val isNotSearch = "${Folder::id.name} != '$SEARCH_FOLDER_ID'"
        private val isRootFolder = "${Folder.parentsPropertyName}.@count == 0"

        //region Queries
        private fun getFoldersQuery(realm: TypedRealm, onlyRoots: Boolean = false): RealmQuery<Folder> {
            val rootsQuery = if (onlyRoots) " AND $isRootFolder" else ""
            return realm
                .query<Folder>(isNotSearch + rootsQuery)
                .sort(Folder::name.name, Sort.ASCENDING)
                .sort(Folder::isFavorite.name, Sort.DESCENDING)
        }

        private fun getDefaultFoldersQuery(realm: TypedRealm): RealmQuery<Folder> {
            val hasRole = "${Folder.rolePropertyName} != nil"
            return realm.query("$isNotSearch AND $hasRole")
        }

        private fun getCustomFoldersQuery(realm: TypedRealm): RealmQuery<Folder> {
            val hasNoRole = "${Folder.rolePropertyName} == nil"
            return realm
                .query<Folder>("$isNotSearch AND $isRootFolder AND $hasNoRole")
                .sort(Folder::name.name, Sort.ASCENDING)
                .sort(Folder::isFavorite.name, Sort.DESCENDING)
        }

        private fun getFoldersQuery(exceptionsFoldersIds: List<String>, realm: TypedRealm): RealmQuery<Folder> {
            return realm.query("NOT ${Folder::id.name} IN $0 AND $isNotSearch", exceptionsFoldersIds)
        }

        private fun getFolderQuery(key: String, value: String, realm: TypedRealm): RealmSingleQuery<Folder> {
            return realm.query<Folder>("$key == $0", value).first()
        }
        //endregion

        //region Get data
        private fun getFolders(exceptionsFoldersIds: List<String> = emptyList(), realm: TypedRealm): RealmResults<Folder> {
            val realmQuery = if (exceptionsFoldersIds.isEmpty()) {
                getFoldersQuery(realm)
            } else {
                getFoldersQuery(exceptionsFoldersIds, realm)
            }
            return realmQuery.find()
        }

        fun getFolder(id: String, realm: TypedRealm): Folder? {
            return getFolderQuery(Folder::id.name, id, realm).find()
        }

        fun getFolder(role: FolderRole, realm: TypedRealm): Folder? {
            return getFolderQuery(Folder.rolePropertyName, role.name, realm).find()
        }

        fun getOrCreateSearchFolder(realm: MutableRealm): Folder {
            return getFolderQuery(Folder::id.name, SEARCH_FOLDER_ID, realm).find() ?: let {
                realm.copyToRealm(Folder().apply { id = SEARCH_FOLDER_ID })
            }
        }

        /**
         * An "incomplete Thread" is a Thread in a specific Folder where only Messages from this Folder are displayed.
         * - In the Draft folder, we only want to display draft Messages.
         * - In the Trash folder, we only want to display deleted Messages.
         */
        fun getIdsOfFoldersWithIncompleteThreads(realm: TypedRealm): List<String> {
            return mutableListOf<String>().apply {
                getFolder(FolderRole.DRAFT, realm)?.id?.let(::add)
                getFolder(FolderRole.TRASH, realm)?.id?.let(::add)
            }
        }
        //endregion

        //region Edit data
        fun updateFolder(id: String, realm: Realm, onUpdate: (folder: Folder) -> Unit) {
            realm.writeBlocking { getFolder(id, realm = this)?.let(onUpdate) }
        }

        fun updateFolderAndChildren(id: String, realm: Realm, onUpdate: (folder: Folder) -> Unit) {

            tailrec fun updateChildrenRecursively(inputList: MutableList<Folder>) {
                val folder = inputList.removeFirst()
                onUpdate(folder)
                inputList.addAll(folder.children)

                if (inputList.isNotEmpty()) updateChildrenRecursively(inputList)
            }

            realm.writeBlocking {
                getFolder(id, realm = this)?.let { folder -> updateChildrenRecursively(mutableListOf(folder)) }
            }
        }

        fun refreshUnreadCount(id: String, mailboxObjectId: String, realm: MutableRealm) {

            val folder = getFolder(id, realm) ?: return

            val unreadCount = ThreadController.getUnreadThreadsCount(folder)
            folder.unreadCountLocal = unreadCount

            if (folder.role == FolderRole.INBOX) {
                MailboxController.updateMailbox(mailboxObjectId) { mailbox ->
                    mailbox.unreadCountLocal = unreadCount
                }
            }
        }
        //endregion
    }
}
