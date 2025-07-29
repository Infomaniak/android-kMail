/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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

import android.content.Context
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.extensions.copyListToRealm
import com.infomaniak.mail.utils.extensions.flattenFolderChildrenAndRemoveMessages
import com.infomaniak.mail.utils.extensions.sortFolders
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

class FolderController @Inject constructor(
    private val appContext: Context,
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
) {

    //region Get data
    fun getMenuDrawerDefaultFoldersAsync(): Flow<ResultsChange<Folder>> {
        return getFoldersQuery(
            realm = mailboxContentRealm(),
            withoutTypes = listOf(FoldersType.CUSTOM),
            withoutChildren = true,
        ).asFlow()
    }

    fun getMenuDrawerCustomFoldersAsync(): Flow<ResultsChange<Folder>> {
        return getFoldersQuery(
            realm = mailboxContentRealm(),
            withoutTypes = listOf(FoldersType.DEFAULT),
            withoutChildren = true,
        ).asFlow()
    }

    fun getSearchFoldersAsync(): Flow<ResultsChange<Folder>> {
        return getFoldersQuery(mailboxContentRealm(), withoutChildren = true).asFlow()
    }

    fun getMoveFolders(): RealmResults<Folder> {
        return getFoldersQuery(
            realm = mailboxContentRealm(),
            withoutTypes = listOf(FoldersType.SNOOZED, FoldersType.SCHEDULED_DRAFTS, FoldersType.DRAFT),
            withoutChildren = true,
        ).find()
    }

    fun getFolder(id: String): Folder? {
        return getFolderQuery(Folder::id.name, id, mailboxContentRealm()).find()
    }

    fun getFolder(role: FolderRole): Folder? {
        return getFolderQuery(Folder.rolePropertyName, role.name, mailboxContentRealm()).find()
    }

    fun getRootFolder(name: String) = with(mailboxContentRealm()) {
        query<Folder>("$isRootFolder AND ${Folder::name.name} == $0", name).first().find()
    }

    fun getFolderAsync(id: String): Flow<Folder> {
        return getFolderQuery(Folder::id.name, id, mailboxContentRealm()).asFlow().mapNotNull { it.obj }
    }
    //endregion

    //region Edit data
    suspend fun update(mailbox: Mailbox, remoteFolders: List<Folder>, realm: Realm) {
        val remoteFoldersWithChildren = remoteFolders.flattenFolderChildrenAndRemoveMessages()

        realm.write {

            SentryLog.d(RealmDatabase.TAG, "Folders: Delete outdated data")
            deleteOutdatedFolders(mailbox, remoteFoldersWithChildren)

            SentryLog.d(RealmDatabase.TAG, "Folders: Save new data")
            upsertFolders(remoteFoldersWithChildren)
        }
    }

    private fun MutableRealm.deleteOutdatedFolders(mailbox: Mailbox, remoteFolders: List<Folder>) {
        /**
         * This list is reversed because we'll delete items while looping over it.
         * Doing so for managed Realm objects will lively update the list we're iterating through, making us skip the next item.
         * Looping in reverse enables us to not skip any item.
         */
        getFolders(exceptionsFoldersIds = remoteFolders.map { it.id }, realm = this).asReversed().forEach { folder ->
            deleteLocalFolder(mailbox, folder)
        }
    }

    private fun MutableRealm.deleteLocalFolder(mailbox: Mailbox, folder: Folder) {
        MessageController.deleteMessages(appContext, mailbox, folder.messages(realm = this), realm = this)
        if (folder.threads.isNotEmpty()) delete(folder.threads)
        delete(folder)
    }

    private fun MutableRealm.upsertFolders(remoteFolders: List<Folder>) {

        remoteFolders.forEach { remoteFolder ->

            val localFolder = getFolder(remoteFolder.id, realm = this)

            if (remoteFolder.role == FolderRole.SCHEDULED_DRAFTS && localFolder == null) remoteFolder.isDisplayed = false

            localFolder?.let {

                val collapseStateNeedsReset = remoteFolder.isRoot && remoteFolder.children.isEmpty()
                val isCollapsed = if (collapseStateNeedsReset) false else it.isCollapsed

                remoteFolder.initLocalValues(
                    it.lastUpdatedAt,
                    it.cursor,
                    it.unreadCountLocal,
                    it.threads,
                    it.oldMessagesUidsToFetch,
                    it.newMessagesUidsToFetch,
                    it.remainingOldMessagesToFetch,
                    it.isDisplayed,
                    it.isHidden,
                    isCollapsed,
                )
            }
        }

        copyListToRealm(remoteFolders)
    }
    //endregion

    enum class FoldersType {
        DEFAULT,
        CUSTOM,
        SNOOZED,
        SCHEDULED_DRAFTS,
        DRAFT,
    }

    companion object {
        const val SEARCH_FOLDER_ID = "search_folder_id"
        private val isNotSearch = "${Folder::id.name} != '$SEARCH_FOLDER_ID'"
        private val isRootFolder = "${Folder.parentsPropertyName}.@count == 0"

        //region Queries
        private fun getFoldersQuery(
            realm: TypedRealm,
            withoutTypes: List<FoldersType> = emptyList(),
            withoutChildren: Boolean = false,
            visibleFoldersOnly: Boolean = true,
        ): RealmQuery<Folder> {
            val rootsQuery = if (FoldersType.DEFAULT in withoutTypes && withoutChildren) " AND $isRootFolder" else ""
            val typeQuery = withoutTypes.joinToString(separator = "") {
                when (it) {
                    FoldersType.DEFAULT -> " AND ${Folder.rolePropertyName} == nil"
                    FoldersType.CUSTOM -> " AND ${Folder.rolePropertyName} != nil"
                    FoldersType.SNOOZED -> " AND ${Folder.rolePropertyName} != '${FolderRole.SNOOZED.name}'"
                    FoldersType.SCHEDULED_DRAFTS -> " AND ${Folder.rolePropertyName} != '${FolderRole.SCHEDULED_DRAFTS.name}'"
                    FoldersType.DRAFT -> " AND ${Folder.rolePropertyName} != '${FolderRole.DRAFT.name}'"
                }
            }
            val visibilityQuery = if (visibleFoldersOnly) " AND ${Folder::isDisplayed.name} == true" else ""
            return realm.query<Folder>("${isNotSearch}${rootsQuery}${typeQuery}${visibilityQuery}").sortFolders()
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
                getFoldersQuery(realm, visibleFoldersOnly = false)
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
            return getFolderQuery(Folder::id.name, SEARCH_FOLDER_ID, realm).find() ?: run {
                realm.copyToRealm(Folder().apply { id = SEARCH_FOLDER_ID })
            }
        }
        //endregion

        //region Edit data
        suspend fun updateFolder(id: String, realm: Realm, onUpdate: (MutableRealm, Folder) -> Unit) {
            realm.write { getFolder(id, realm = this)?.let { onUpdate(this, it) } }
        }

        suspend fun updateFolderAndChildren(id: String, realm: Realm, onUpdate: (Folder) -> Unit) {

            tailrec fun updateChildrenRecursively(inputList: MutableList<Folder>) {
                val folder = inputList.removeAt(0)
                onUpdate(folder)
                inputList.addAll(folder.children)

                if (inputList.isNotEmpty()) updateChildrenRecursively(inputList)
            }

            realm.write {
                getFolder(id, realm = this)?.let { folder -> updateChildrenRecursively(mutableListOf(folder)) }
            }
        }

        fun deleteSearchFolderData(realm: MutableRealm) = with(getOrCreateSearchFolder(realm)) {
            threads.clear()
        }

        // TODO: Remove this function when the Threads parental issues are fixed
        suspend fun removeThreadFromFolder(folderId: String, thread: Thread, realm: Realm) {
            updateFolder(folderId, realm) { _, folder ->
                folder.threads.remove(thread)
            }
        }
        //endregion
    }
}
