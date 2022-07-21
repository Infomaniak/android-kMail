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
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import kotlinx.coroutines.flow.Flow

object MailboxContentController {

    /**
     * Folders
     */
    private fun getFolder(id: String): Folder? {
        return MailRealm.mailboxContent.query<Folder>("${Folder::id.name} == '$id'").first().find()
    }

    fun MutableRealm.getLatestFolder(id: String): Folder? = getFolder(id)?.let(::findLatest)

    private fun getFolders(): RealmQuery<Folder> = MailRealm.mailboxContent.query()
    fun getFoldersSync(): RealmResults<Folder> = getFolders().find()
    fun getFoldersAsync(): Flow<ResultsChange<Folder>> = getFolders().asFlow()

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

    fun upsertFolder(folder: Folder): Folder = MailRealm.mailboxContent.writeBlocking { copyToRealm(folder, UpdatePolicy.ALL) }

    fun updateFolder(id: String, onUpdate: (folder: Folder) -> Unit) {
        MailRealm.mailboxContent.writeBlocking { getLatestFolder(id)?.let(onUpdate) }
    }

    // fun deleteFolder(id: String) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestFolder(id)?.let(::delete) }
    // }

    // fun deleteFolders(folders: List<Folder>) {
    //     MailRealm.mailboxContent.writeBlocking { folders.forEach { getLatestFolder(it.id)?.let(::delete) } }
    // }

    fun MutableRealm.deleteLatestFolder(id: String) {
        getLatestFolder(id)?.let(::delete)
    }

    /**
     * Threads
     */
    fun getFolderThreads(folderId: String, filter: Thread.ThreadFilter? = null): List<Thread> {
        val threads = MailRealm.mailboxContent.writeBlocking { getLatestFolder(folderId) }?.threads ?: emptyList()

        return when (filter) {
            Thread.ThreadFilter.SEEN -> threads.filter { it.unseenMessagesCount == 0 }
            Thread.ThreadFilter.UNSEEN -> threads.filter { it.unseenMessagesCount > 0 }
            Thread.ThreadFilter.STARRED -> threads.filter { it.flagged }
            Thread.ThreadFilter.ATTACHMENTS -> threads.filter { it.hasAttachments }
            else -> threads
        }
    }

    fun getThread(uid: String): Thread? = MailRealm.mailboxContent.query<Thread>("${Thread::uid.name} == '$uid'").first().find()

    fun MutableRealm.getLatestThread(uid: String): Thread? = getThread(uid)?.let(::findLatest)

    fun getLatestThread(uid: String): Thread? = MailRealm.mailboxContent.writeBlocking { getLatestThread(uid) }

    // TODO: RealmKotlin doesn't fully support `IN` for now.
    // TODO: Workaround: https://github.com/realm/realm-js/issues/2781#issuecomment-607213640
    // fun getDeletableThreads(folder: Folder, threadsToKeep: List<Thread>): RealmResults<Thread> {
    //     val threadsIds = threadsToKeep.map { it.uid }
    //     val query = threadsIds.joinToString(
    //         prefix = "NOT (${Thread::uid.name} == '",
    //         separator = "' OR ${Thread::uid.name} == '",
    //         postfix = "')"
    //     )
    //     return MailRealm.mailboxContent.query<Thread>(query).find()
    // }

    // fun upsertThread(thread: Thread) {
    //     MailRealm.mailboxContent.writeBlocking { copyToRealm(thread, UpdatePolicy.ALL) }
    // }

    // fun upsertLatestThread(uid: String) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestThread(uid)?.let { copyToRealm(it, UpdatePolicy.ALL) } }
    // }

    // fun updateThread(uid: String, onUpdate: (thread: Thread) -> Unit) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestThread(uid)?.let(onUpdate) }
    // }

    fun MutableRealm.deleteLatestThread(uid: String) {
        getLatestThread(uid)?.let(::delete)
    }

    // fun deleteThreads(threads: List<Thread>) {
    //     MailRealm.mailboxContent.writeBlocking { threads.forEach { getLatestThread(it.uid)?.let(::delete) } }
    // }

    /**
     * Messages
     */
    // fun upsertMessage(message: Message) {
    //     MailRealm.mailboxContent.writeBlocking { copyToRealm(message, UpdatePolicy.ALL) }
    // }

    private fun getMessage(uid: String): Message? {
        return MailRealm.mailboxContent.query<Message>("${Message::uid.name} == '$uid'").first().find()
    }

    fun MutableRealm.getLatestMessage(uid: String): Message? = getMessage(uid)?.let(::findLatest)

    // TODO: RealmKotlin doesn't fully support `IN` for now.
    // TODO: Workaround: https://github.com/realm/realm-js/issues/2781#issuecomment-607213640
    // fun getDeletableMessages(thread: Thread, messagesToKeep: List<Message>): RealmResults<Message> {
    //     val messagesIds = messagesToKeep.map { it.uid }
    //     val query = messagesIds.joinToString(
    //         prefix = "NOT (${Message::uid.name} == '",
    //         separator = "' OR ${Message::uid.name} == '",
    //         postfix = "')"
    //     )
    //     return MailRealm.mailboxContent.query<Message>(query).find()
    // }

    fun upsertMessages(messages: List<Message>) {
        MailRealm.mailboxContent.writeBlocking { messages.forEach { copyToRealm(it, UpdatePolicy.ALL) } }
    }

    // fun updateMessage(uid: String, onUpdate: (message: Message) -> Unit) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestMessage(uid)?.let(onUpdate) }
    // }

    fun MutableRealm.deleteLatestMessage(uid: String) {
        getLatestMessage(uid)?.apply {
            draftUuid?.let { getLatestDraft(it) }?.let(::delete)
        }?.let(::delete)
    }

    fun deleteMessage(uid: String) {
        MailRealm.mailboxContent.writeBlocking { deleteLatestMessage(uid) }
    }

    // fun deleteMessages(messages: List<Message>) {
    //     MailRealm.mailboxContent.writeBlocking {
    //         messages.forEach { message -> deleteLatestMessage(message.uid) }
    //     }
    // }

    /**
     * Drafts
     */
    private fun getDraft(uuid: String): Draft? {
        return MailRealm.mailboxContent.query<Draft>("${Draft::uuid.name} == '$uuid'").first().find()
    }

    private fun MutableRealm.getLatestDraft(uuid: String): Draft? = getDraft(uuid)?.let(::findLatest)

    fun upsertDraft(draft: Draft) {
        MailRealm.mailboxContent.writeBlocking { copyToRealm(draft, UpdatePolicy.ALL) }
    }

    /**
     * Recipients
     */
    // private fun getRecipients(): RealmResults<Recipient> = MailRealm.mailboxContent.query<Recipient>().find()

    // private fun getRecipientsByEmail(email: String): RealmResults<Recipient> = MailRealm.mailboxContent.query<Recipient>("${Recipient::email.name} == '$email'").find()

    // private fun getRecipientByEmail(email: String): Recipient? = getRecipientsByEmail(email).firstOrNull()

    // private fun MutableRealm.getLatestRecipientByEmail(email: String): Recipient? = getRecipientByEmail(email)?.let(::findLatest)

    // fun upsertRecipient(recipient: Recipient) {
    //     MailRealm.mailboxContent.writeBlocking { copyToRealm(recipient, UpdatePolicy.ALL) }
    // }

    // fun updateRecipient(email: String, onUpdate: (recipient: Recipient) -> Unit) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestRecipientByEmail(email)?.let(onUpdate) }
    // }

    // fun removeRecipient(email: String) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestRecipientByEmail(email)?.let(::delete) }
    // }

    // private fun MutableRealm.removeRecipientIfAlreadyExisting(recipient: Recipient) {
    //     getRecipientByEmail(recipient.email)?.let { findLatest(it)?.let(::delete) }
    // }

    // fun cleanRecipients() {
    //     MailRealm.mailboxContent.writeBlocking {
    //         getRecipients().map { it.email }.distinct().forEach { email ->
    //             getRecipientsByEmail(email).forEachIndexed { index, recipient ->
    //                 if (index > 0) findLatest(recipient)?.let(::delete)
    //             }
    //         }
    //     }
    // }
}
