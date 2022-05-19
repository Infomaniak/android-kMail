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
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.MutableRealm
import io.realm.MutableRealm.UpdatePolicy
import io.realm.RealmResults
import io.realm.query

object MailboxContentController {

    /**
     * Folders
     */
    fun getFolder(id: String): Folder? = MailRealm.mailboxContent.query<Folder>("${Folder::id.name} == '$id'").first().find()

    private fun MutableRealm.getLatestFolder(id: String): Folder? = getFolder(id)?.let(::findLatest)

    fun getFolders(): RealmResults<Folder> = MailRealm.mailboxContent.query<Folder>().find()

    fun upsertFolder(folder: Folder): Folder = MailRealm.mailboxContent.writeBlocking { copyToRealm(folder, UpdatePolicy.ALL) }

    // fun updateFolder(id: String, onUpdate: (folder: Folder) -> Unit) {
    //     mailboxContent.writeBlocking { getLatestFolder(id)?.let(onUpdate) }
    // }

    // fun deleteFolder(id: String) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestFolder(id)?.let(::delete) }
    // }

    fun deleteFolders(folders: List<Folder>) {
        MailRealm.mailboxContent.writeBlocking { folders.forEach { getLatestFolder(it.id)?.let(::delete) } }
    }

    /**
     * Threads
     */
    // fun getFolderThreads(folderId: String): List<Thread> {
    //     return mailboxContent.writeBlocking { getLatestFolder(folderId) }?.threads ?: emptyList()
    // }

    fun getThread(uid: String): Thread? = MailRealm.mailboxContent.query<Thread>("${Thread::uid.name} == '$uid'").first().find()

    private fun MutableRealm.getLatestThread(uid: String): Thread? = getThread(uid)?.let(::findLatest)

    fun getLatestThread(uid: String): Thread? = MailRealm.mailboxContent.writeBlocking { getLatestThread(uid) }

    // fun upsertThread(thread: Thread) {
    //     mailboxContent.writeBlocking { copyToRealm(thread, UpdatePolicy.ALL) }
    // }

    // fun upsertLatestThread(uid: String) {
    //     mailboxContent.writeBlocking { getLatestThread(uid)?.let { copyToRealm(it, UpdatePolicy.ALL) } }
    // }

    // fun updateThread(uid: String, onUpdate: (thread: Thread) -> Unit) {
    //     mailboxContent.writeBlocking { getLatestThread(uid)?.let(onUpdate) }
    // }

    // fun deleteThread(uid: String) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestThread(uid)?.let(::delete) }
    // }

    fun deleteThreads(threads: List<Thread>) {
        MailRealm.mailboxContent.writeBlocking { threads.forEach { getLatestFolder(it.uid)?.let(::delete) } }
    }

    /**
     * Messages
     */
    // fun upsertMessage(message: Message) {
    //     mailboxContent.writeBlocking { copyToRealm(message, UpdatePolicy.ALL) }
    // }

    private fun getMessage(uid: String): Message? {
        return MailRealm.mailboxContent.query<Message>("${Message::uid.name} == '$uid'").first().find()
    }

    fun getLatestMessage(uid: String): Message? = MailRealm.mailboxContent.writeBlocking { getLatestMessage(uid) }

    private fun MutableRealm.getLatestMessage(uid: String): Message? = getMessage(uid)?.let(::findLatest)

    fun upsertMessages(messages: List<Message>) {
        MailRealm.mailboxContent.writeBlocking { messages.forEach { copyToRealm(it, UpdatePolicy.ALL) } }
    }

    // fun updateMessage(uid: String, onUpdate: (message: Message) -> Unit) {
    //     mailboxContent.writeBlocking { getLatestMessage(uid)?.let(onUpdate) }
    // }

    // fun deleteMessage(uid: String) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestMessage(uid)?.let(::delete) }
    // }

    fun deleteMessages(messages: List<Message>) {
        MailRealm.mailboxContent.writeBlocking { messages.forEach { getLatestFolder(it.uid)?.let(::delete) } }
    }

    /**
     * Recipients
     */
    // private fun getRecipients(): RealmResults<Recipient> = mailboxContent.query<Recipient>().find()

    // private fun getRecipientsByEmail(email: String): RealmResults<Recipient> = mailboxContent.query<Recipient>("${Recipient::email.name} == '$email'").find()

    // private fun getRecipientByEmail(email: String): Recipient? = getRecipientsByEmail(email).firstOrNull()

    // private fun MutableRealm.getLatestRecipientByEmail(email: String): Recipient? = getRecipientByEmail(email)?.let(::findLatest)

    // fun upsertRecipient(recipient: Recipient) {
    //     mailboxContent.writeBlocking {
    //         removeRecipientIfAlreadyExisting(recipient) // TODO: remove this when the UPSERT is working
    //         copyToRealm(recipient)
    //     }
    // }

    // fun updateRecipient(email: String, onUpdate: (recipient: Recipient) -> Unit) {
    //     mailboxContent.writeBlocking { getLatestRecipientByEmail(email)?.let(onUpdate) }
    // }

    // fun removeRecipient(email: String) {
    //     mailboxContent.writeBlocking { getLatestRecipientByEmail(email)?.let(::delete) }
    // }

    // private fun MutableRealm.removeRecipientIfAlreadyExisting(recipient: Recipient) {
    //     getRecipientByEmail(recipient.email)?.let { findLatest(it)?.let(::delete) }
    // }

    // fun cleanRecipients() {
    //     mailboxContent.writeBlocking {
    //         getRecipients().map { it.email }.distinct().forEach { email ->
    //             getRecipientsByEmail(email).forEachIndexed { index, recipient ->
    //                 if (index > 0) findLatest(recipient)?.let(::delete)
    //             }
    //         }
    //     }
    // }
}
