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
import com.infomaniak.mail.utils.Realms
import io.realm.MutableRealm.UpdatePolicy
import io.realm.query
import com.infomaniak.mail.data.models.threads.Thread

object MailboxController {

    /**
     * Folders
     */
    fun getFolder(id: String): Folder? =
        Realms.mailbox.query<Folder>("${Folder::id.name} == '$id'").first().find()

    fun upsertFolder(folder: Folder) {
        Realms.mailbox.writeBlocking { copyToRealm(folder, UpdatePolicy.ALL) }
    }

    fun updateFolder(objectId: String, onUpdate: (folder: Folder) -> Unit) {
        Realms.mailbox.writeBlocking {
            getFolder(objectId)?.let { findLatest(it)?.let(onUpdate) }
        }
    }

    fun removeFolder(objectId: String) {
        Realms.mailbox.writeBlocking {
            getFolder(objectId)?.let { findLatest(it)?.let(::delete) }
        }
    }

    /**
     * Threads
     */
    fun getThread(uid: String): Thread? =
        Realms.mailbox.query<Thread>("${Thread::uid.name} == '$uid'").first().find()

    fun upsertThread(thread: Thread) {
        Realms.mailbox.writeBlocking { copyToRealm(thread, UpdatePolicy.ALL) }
    }

    fun updateThread(uid: String, onUpdate: (thread: Thread) -> Unit) {
        Realms.mailbox.writeBlocking {
            getThread(uid)?.let { findLatest(it)?.let(onUpdate) }
        }
    }

    fun removeThread(uid: String) {
        Realms.mailbox.writeBlocking {
            getThread(uid)?.let { findLatest(it)?.let(::delete) }
        }
    }

    /**
     * Messages
     */
    fun getMessage(uid: String): Message? =
        Realms.mailbox.query<Message>("${Message::uid.name} == '$uid'").first().find()

    fun upsertMessage(message: Message) {
        Realms.mailbox.writeBlocking { copyToRealm(message, UpdatePolicy.ALL) }
    }

    fun updateMessage(uid: String, onUpdate: (message: Message) -> Unit) {
        Realms.mailbox.writeBlocking {
            getMessage(uid)?.let { findLatest(it)?.let(onUpdate) }
        }
    }

    fun removeMessage(uid: String) {
        Realms.mailbox.writeBlocking {
            getMessage(uid)?.let { findLatest(it)?.let(::delete) }
        }
    }
}