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

import com.infomaniak.mail.data.cache.RealmController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.utils.toSharedFlow
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import kotlinx.coroutines.flow.SharedFlow

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
    fun upsertFolder(folder: Folder): Folder {
        return RealmController.mailboxContent.writeBlocking { copyToRealm(folder, UpdatePolicy.ALL) }
    }

    fun MutableRealm.deleteLatestFolder(id: String) {
        getLatestFolderSync(id)?.let(::delete)
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

    /**
     * TODO?
     */
    // fun updateFolder(id: String, onUpdate: (folder: Folder) -> Unit) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestFolder(id)?.let(onUpdate) }
    // }

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
