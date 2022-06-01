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

import com.infomaniak.mail.data.models.Draft
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmResults

object MailboxContentController {

    /**
     * Folders
     */
    fun getFolder(id: String): Folder? = MailRealm.mailboxContent.query<Folder>("${Folder::id.name} == '$id'").first().find()

    private fun MutableRealm.getLatestFolder(id: String): Folder? = getFolder(id)?.let(::findLatest)

    fun getFolders(): RealmResults<Folder> = MailRealm.mailboxContent.query<Folder>().find()

    // TODO: RealmKotlin doesn't fully support `IN` for now.
    // TODO: Workaround: https://github.com/realm/realm-js/issues/2781#issuecomment-607213640
    fun getDeletableFolders(foldersToKeep: List<Folder>): RealmResults<Folder> {
        val foldersIds = foldersToKeep.map { it.id }
        val query = foldersIds.joinToString(
            prefix = "NOT (${Folder::id.name} == '",
            separator = "' OR ${Folder::id.name} == '",
            postfix = "')"
        )
        return MailRealm.mailboxContent.query<Folder>(query).find()
    }

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

    // TODO: RealmKotlin doesn't fully support `IN` for now.
    // TODO: Workaround: https://github.com/realm/realm-js/issues/2781#issuecomment-607213640
    fun getDeletableThreads(folder: Folder, threadsToKeep: List<Thread>): RealmResults<Thread> {
        val threadsIds = threadsToKeep.map { it.uid }
        val query = threadsIds.joinToString(
            prefix = "NOT (${Thread::uid.name} == '",
            separator = "' OR ${Thread::uid.name} == '",
            postfix = "')"
        )
        return MailRealm.mailboxContent.query<Thread>(query).find()
    }

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
        MailRealm.mailboxContent.writeBlocking { threads.forEach { getLatestThread(it.uid)?.let(::delete) } }
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

    fun MutableRealm.getLatestMessage(uid: String): Message? = getMessage(uid)?.let(::findLatest)

    // TODO: RealmKotlin doesn't fully support `IN` for now.
    // TODO: Workaround: https://github.com/realm/realm-js/issues/2781#issuecomment-607213640
    fun getDeletableMessages(thread: Thread, messagesToKeep: List<Message>): RealmResults<Message> {
        val messagesIds = messagesToKeep.map { it.uid }
        val query = messagesIds.joinToString(
            prefix = "NOT (${Message::uid.name} == '",
            separator = "' OR ${Message::uid.name} == '",
            postfix = "')"
        )
        return MailRealm.mailboxContent.query<Message>(query).find()
    }

    fun upsertMessages(messages: List<Message>) {
        MailRealm.mailboxContent.writeBlocking { messages.forEach { copyToRealm(it, UpdatePolicy.ALL) } }
    }

    // fun updateMessage(uid: String, onUpdate: (message: Message) -> Unit) {
    //     mailboxContent.writeBlocking { getLatestMessage(uid)?.let(onUpdate) }
    // }

    fun deleteMessage(uid: String) {
        MailRealm.mailboxContent.writeBlocking { getLatestMessage(uid)?.let(::delete) }
    }

    fun deleteMessages(messages: List<Message>) {
        MailRealm.mailboxContent.writeBlocking { messages.forEach { getLatestMessage(it.uid)?.let(::delete) } }
    }

    /**
     * Drafts
     */

    private fun getDraft(uuid: String): Draft? {
        return MailRealm.mailboxContent.query<Draft>("${Draft::uuid.name} == '$uuid'").first().find()
    }

    private fun MutableRealm.getLatestDraft(uuid: String): Draft? = getDraft(uuid)?.let(::findLatest)

    fun deleteDraft(uuid: String) {
        MailRealm.mailboxContent.writeBlocking { getLatestDraft(uuid)?.let(::delete) }
    }

    fun deleteDrafts(drafts: List<Draft>) {
        MailRealm.mailboxContent.writeBlocking { drafts.forEach { getLatestDraft(it.uuid)?.let(::delete) } }
    }

    fun upsertDraft(draft: Draft) {
        MailRealm.mailboxContent.writeBlocking { copyToRealm(draft, UpdatePolicy.ALL) }
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
