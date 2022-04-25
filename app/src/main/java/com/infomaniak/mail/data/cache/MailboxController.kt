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
package com.infomaniak.mail.data.cache

import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.threads.Thread
import com.infomaniak.mail.utils.Realms
import io.realm.MutableRealm
import io.realm.MutableRealm.UpdatePolicy
import io.realm.RealmResults
import io.realm.query

object MailboxController {

    /**
     * Folders
     */
    fun getFolders(): RealmResults<Folder> =
        Realms.mailbox.query<Folder>().find()

    private fun getFolderById(id: String): Folder? =
        Realms.mailbox.query<Folder>("${Folder::id.name} == '$id'").first().find()

    fun getFolderByRole(role: Folder.FolderRole): Folder? =
        Realms.mailbox.query<Folder>("${Folder::_role.name} == '${role.name}'").first().find()

    private fun MutableRealm.getLatestFolderById(id: String): Folder? {
        val folder = getFolderById(id)
        return if (folder != null) findLatest(folder) else folder
    }

    fun upsertFolder(folder: Folder) {
        Realms.mailbox.writeBlocking {
            removeFolderIfAlreadyExisting(folder) // TODO: remove this when the Upsert is working
            copyToRealm(folder, UpdatePolicy.ALL)
        }
    }

    fun updateFolder(id: String, onUpdate: (folder: Folder) -> Unit) {
        Realms.mailbox.writeBlocking { getLatestFolderById(id)?.let(onUpdate) }
    }

    fun removeFolder(id: String) {
        Realms.mailbox.writeBlocking { getLatestFolderById(id)?.let(::delete) }
    }

    private fun MutableRealm.removeFolderIfAlreadyExisting(folder: Folder) {
        getFolderById(folder.id)?.let { findLatest(it)?.let(::delete) }
    }

    /**
     * Threads
     */
    private fun getThreadByUid(uid: String): Thread? =
        Realms.mailbox.query<Thread>("${Thread::uid.name} == '$uid'").first().find()

    private fun MutableRealm.getLatestThreadByUid(uid: String): Thread? {
        val thread = getThreadByUid(uid)
        return if (thread != null) findLatest(thread) else thread
    }

    fun upsertThread(thread: Thread) {
        Realms.mailbox.writeBlocking {
            removeThreadIfAlreadyExisting(thread) // TODO: remove this when the Upsert is working
            copyToRealm(thread, UpdatePolicy.ALL)
        }
    }

    fun updateThread(uid: String, onUpdate: (thread: Thread) -> Unit) {
        Realms.mailbox.writeBlocking { getLatestThreadByUid(uid)?.let(onUpdate) }
    }

    fun removeThread(uid: String) {
        Realms.mailbox.writeBlocking { getLatestThreadByUid(uid)?.let(::delete) }
    }

    private fun MutableRealm.removeThreadIfAlreadyExisting(thread: Thread) {
        getThreadByUid(thread.uid)?.let { findLatest(it)?.let(::delete) }
    }

    /**
     * Messages
     */
    private fun getMessageByUid(uid: String): Message? =
        Realms.mailbox.query<Message>("${Message::uid.name} == '$uid'").first().find()

    private fun MutableRealm.getLatestMessageByUid(uid: String): Message? {
        val message = getMessageByUid(uid)
        return if (message != null) findLatest(message) else message
    }

    fun upsertMessage(message: Message) {
        Realms.mailbox.writeBlocking {
            removeMessageIfAlreadyExisting(message) // TODO: remove this when the Upsert is working
            copyToRealm(message, UpdatePolicy.ALL)
        }
    }

    fun updateMessage(uid: String, onUpdate: (message: Message) -> Unit) {
        Realms.mailbox.writeBlocking { getLatestMessageByUid(uid)?.let(onUpdate) }
    }

    fun removeMessage(uid: String) {
        Realms.mailbox.writeBlocking { getLatestMessageByUid(uid)?.let(::delete) }
    }

    private fun MutableRealm.removeMessageIfAlreadyExisting(message: Message) {
        getMessageByUid(message.uid)?.let { findLatest(it)?.let(::delete) }
    }
}
