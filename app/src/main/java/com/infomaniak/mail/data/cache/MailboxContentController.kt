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
    fun getFolders(): RealmResults<Folder> = MailRealm.mailboxContent.query<Folder>().find()

    fun getFolder(id: String): Folder? = MailRealm.mailboxContent.query<Folder>("${Folder::id.name} == '$id'").first().find()

    fun upsertFolder(folder: Folder): Folder = MailRealm.mailboxContent.writeBlocking { copyToRealm(folder, UpdatePolicy.ALL) }

    // fun updateFolder(id: String, onUpdate: (folder: Folder) -> Unit) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestFolder(id)?.let(onUpdate) }
    // }

    fun deleteFolder(id: String) {
        MailRealm.mailboxContent.writeBlocking { getLatestFolder(id)?.let(::delete) }
    }

    private fun MutableRealm.getLatestFolder(id: String): Folder? = getFolder(id)?.let(::findLatest)

    /**
     * Threads
     */
    fun getFolderThreads(folderId: String): List<Thread> {
        return MailRealm.mailboxContent.writeBlocking { getLatestFolder(folderId) }?.threads ?: emptyList()
    }

    fun getThread(uid: String): Thread? = MailRealm.mailboxContent.query<Thread>("${Thread::uid.name} == '$uid'").first().find()

    fun getLatestThread(uid: String): Thread? = MailRealm.mailboxContent.writeBlocking { getLatestThread(uid) }

    fun upsertThread(thread: Thread) {
        MailRealm.mailboxContent.writeBlocking { copyToRealm(thread, UpdatePolicy.ALL) }
    }

    // fun upsertLatestThread(uid: String) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestThread(uid)?.let { copyToRealm(it, UpdatePolicy.ALL) } }
    // }

    // fun updateThread(uid: String, onUpdate: (thread: Thread) -> Unit) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestThread(uid)?.let(onUpdate) }
    // }

    fun deleteThread(uid: String) {
        MailRealm.mailboxContent.writeBlocking { getLatestThread(uid)?.let(::delete) }
    }

    private fun MutableRealm.getLatestThread(uid: String): Thread? = getThread(uid)?.let(::findLatest)

    /**
     * Messages
     */
    fun upsertMessage(message: Message): Message {
        return MailRealm.mailboxContent.writeBlocking { copyToRealm(message, UpdatePolicy.ALL) }
    }

    // fun updateMessage(uid: String, onUpdate: (message: Message) -> Unit) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestMessage(uid)?.let(onUpdate) }
    // }

    fun deleteMessage(uid: String) {
        MailRealm.mailboxContent.writeBlocking { getLatestMessage(uid)?.let(::delete) }
    }

    fun getLatestMessage(uid: String): Message? = MailRealm.mailboxContent.writeBlocking { getLatestMessage(uid) }

    private fun MutableRealm.getLatestMessage(uid: String): Message? = getMessage(uid)?.let(::findLatest)

    private fun getMessage(uid: String): Message? {
        return MailRealm.mailboxContent.query<Message>("${Message::uid.name} == '$uid'").first().find()
    }

    /**
     * Recipients
     */
//    private fun getRecipients(): RealmResults<Recipient> = MailRealm.mailboxContent.query<Recipient>().find()

//    private fun getRecipientsByEmail(email: String): RealmResults<Recipient> = MailRealm.mailboxContent.query<Recipient>("${Recipient::email.name} == '$email'").find()

//    private fun getRecipientByEmail(email: String): Recipient? = getRecipientsByEmail(email).firstOrNull()

//    private fun MutableRealm.getLatestRecipientByEmail(email: String): Recipient? = getRecipientByEmail(email)?.let(::findLatest)

//    fun upsertRecipient(recipient: Recipient) {
//        MailRealm.mailboxContent.writeBlocking {
//            removeRecipientIfAlreadyExisting(recipient) // TODO: remove this when the UPSERT is working
//            copyToRealm(recipient)
//        }
//    }

//    fun updateRecipient(email: String, onUpdate: (recipient: Recipient) -> Unit) {
//        MailRealm.mailboxContent.writeBlocking { getLatestRecipientByEmail(email)?.let(onUpdate) }
//    }

//    fun removeRecipient(email: String) {
//        MailRealm.mailboxContent.writeBlocking { getLatestRecipientByEmail(email)?.let(::delete) }
//    }

//    private fun MutableRealm.removeRecipientIfAlreadyExisting(recipient: Recipient) {
//        getRecipientByEmail(recipient.email)?.let { findLatest(it)?.let(::delete) }
//    }

//    fun cleanRecipients() {
//        MailRealm.mailboxContent.writeBlocking {
//            getRecipients().map { it.email }.distinct().forEach { email ->
//                getRecipientsByEmail(email).forEachIndexed { index, recipient ->
//                    if (index > 0) findLatest(recipient)?.let(::delete)
//                }
//            }
//        }
//    }
}
