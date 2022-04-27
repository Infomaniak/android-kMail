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
import com.infomaniak.mail.data.models.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.threads.Thread
import com.infomaniak.mail.utils.Realms
import io.realm.MutableRealm
import io.realm.RealmResults
import io.realm.query

object MailboxContentController {

    /**
     * Folders
     */
    fun getFolders(): RealmResults<Folder> =
        Realms.mailboxContent.query<Folder>().find()

    private fun getFolderById(id: String): Folder? =
        Realms.mailboxContent.query<Folder>("${Folder::id.name} == '$id'").first().find()

    fun getFolderByRole(role: Folder.FolderRole): Folder? =
        Realms.mailboxContent.query<Folder>("${Folder::_role.name} == '${role.name}'").first().find()

    private fun MutableRealm.getLatestFolderById(id: String): Folder? =
        getFolderById(id)?.let(::findLatest)

    fun upsertFolder(folder: Folder) {
        Realms.mailboxContent.writeBlocking {
            removeFolderIfAlreadyExisting(folder) // TODO: remove this when the UPSERT is working
            copyToRealm(folder)
        }
    }

    fun updateFolder(id: String, onUpdate: (folder: Folder) -> Unit) {
        Realms.mailboxContent.writeBlocking { getLatestFolderById(id)?.let(onUpdate) }
    }

    fun removeFolder(id: String) {
        Realms.mailboxContent.writeBlocking { getLatestFolderById(id)?.let(::delete) }
    }

    private fun MutableRealm.removeFolderIfAlreadyExisting(folder: Folder) {
        getFolderById(folder.id)?.let { findLatest(it)?.let(::delete) }
    }

    /**
     * Threads
     */
    private fun getThreadByUid(uid: String): Thread? =
        Realms.mailboxContent.query<Thread>("${Thread::uid.name} == '$uid'").first().find()

    private fun MutableRealm.getLatestThreadByUid(uid: String): Thread? =
        getThreadByUid(uid)?.let(::findLatest)

    fun upsertThread(thread: Thread) {
        Realms.mailboxContent.writeBlocking {
            removeThreadIfAlreadyExisting(thread) // TODO: remove this when the UPSERT is working
            copyToRealm(thread)
        }
    }

    fun updateThread(uid: String, onUpdate: (thread: Thread) -> Unit) {
        Realms.mailboxContent.writeBlocking { getLatestThreadByUid(uid)?.let(onUpdate) }
    }

    fun removeThread(uid: String) {
        Realms.mailboxContent.writeBlocking { getLatestThreadByUid(uid)?.let(::delete) }
    }

    private fun MutableRealm.removeThreadIfAlreadyExisting(thread: Thread) {
        getThreadByUid(thread.uid)?.let { findLatest(it)?.let(::delete) }
    }

    /**
     * Messages
     */
    private fun getMessageByUid(uid: String): Message? =
        Realms.mailboxContent.query<Message>("${Message::uid.name} == '$uid'").first().find()

    private fun MutableRealm.getLatestMessageByUid(uid: String): Message? =
        getMessageByUid(uid)?.let(::findLatest)

    fun upsertMessage(message: Message) {
        Realms.mailboxContent.writeBlocking {
            removeMessageIfAlreadyExisting(message) // TODO: remove this when the UPSERT is working
            copyToRealm(message)
        }
    }

    fun updateMessage(uid: String, onUpdate: (message: Message) -> Unit) {
        Realms.mailboxContent.writeBlocking { getLatestMessageByUid(uid)?.let(onUpdate) }
    }

    fun removeMessage(uid: String) {
        Realms.mailboxContent.writeBlocking { getLatestMessageByUid(uid)?.let(::delete) }
    }

    private fun MutableRealm.removeMessageIfAlreadyExisting(message: Message) {
        getMessageByUid(message.uid)?.let { findLatest(it)?.let(::delete) }
    }

    /**
     * Recipients
     */
    private fun getRecipients(): RealmResults<Recipient> =
        Realms.mailboxContent.query<Recipient>().find()

    private fun getRecipientsByEmail(email: String): RealmResults<Recipient> =
        Realms.mailboxContent.query<Recipient>("${Recipient::email.name} == '$email'").find()

    private fun getRecipientByEmail(email: String): Recipient? =
        getRecipientsByEmail(email).firstOrNull()

    private fun MutableRealm.getLatestRecipientByEmail(email: String): Recipient? =
        getRecipientByEmail(email)?.let(::findLatest)

    fun upsertRecipient(recipient: Recipient) {
        Realms.mailboxContent.writeBlocking {
            removeRecipientIfAlreadyExisting(recipient) // TODO: remove this when the UPSERT is working
            copyToRealm(recipient)
        }
    }

    fun updateRecipient(email: String, onUpdate: (recipient: Recipient) -> Unit) {
        Realms.mailboxContent.writeBlocking { getLatestRecipientByEmail(email)?.let(onUpdate) }
    }

    fun removeRecipient(email: String) {
        Realms.mailboxContent.writeBlocking { getLatestRecipientByEmail(email)?.let(::delete) }
    }

    private fun MutableRealm.removeRecipientIfAlreadyExisting(recipient: Recipient) {
        getRecipientByEmail(recipient.email)?.let { findLatest(it)?.let(::delete) }
    }

    fun cleanRecipients() {
        Realms.mailboxContent.writeBlocking {
            getRecipients().map { it.email }.distinct().forEach { email ->
                getRecipientsByEmail(email).forEachIndexed { index, recipient ->
                    if (index > 0) findLatest(recipient)?.let(::delete)
                }
            }
        }
    }
}
