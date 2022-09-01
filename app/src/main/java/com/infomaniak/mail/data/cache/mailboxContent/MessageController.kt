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

import android.util.Log
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController.getLatestDraftSync
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.toSharedFlow
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmSingleQuery
import kotlinx.coroutines.flow.SharedFlow

object MessageController {

    //region Get data
    fun getMessageSync(id: String): Message? {
        return getMessage(id).find()
    }

    fun getMessageAsync(id: String): SharedFlow<SingleQueryChange<Message>> {
        return getMessage(id).asFlow().toSharedFlow()
    }

    fun MutableRealm.getLatestMessageSync(uid: String): Message? {
        return getMessageSync(uid)?.let(::findLatest)
    }
    //endregion

    //region Edit data
    fun MutableRealm.deleteMessages(messages: List<Message>) {
        messages.forEach { deleteLatestMessage(it.uid) }
    }

    fun deleteMessage(uid: String) {
        RealmDatabase.mailboxContent.writeBlocking { deleteLatestMessage(uid) }
    }
    //endregion

    //region Utils
    fun upsertApiData(apiMessages: List<Message>, thread: Thread) {

        // Get current data
        Log.d(RealmDatabase.TAG, "Messages: Get current data")
        val realmMessages = thread.messages

        // Get outdated data
        Log.d(RealmDatabase.TAG, "Messages: Get outdated data")
        // val deletableMessages = MailboxContentController.getDeletableMessages(messagesFromApi)
        val deletableMessages = realmMessages.filter { realmMessage ->
            apiMessages.none { apiMessage -> apiMessage.uid == realmMessage.uid }
        }

        RealmDatabase.mailboxContent.writeBlocking {
            // Save new data
            Log.d(RealmDatabase.TAG, "Messages: Save new data")
            apiMessages.forEach { apiMessage ->
                if (!apiMessage.isManaged()) copyToRealm(apiMessage, UpdatePolicy.ALL)
            }

            // Delete outdated data
            Log.d(RealmDatabase.TAG, "Messages: Delete outdated data")
            deleteMessages(deletableMessages)
        }
    }

    private fun getMessage(uid: String): RealmSingleQuery<Message> {
        return RealmDatabase.mailboxContent.query<Message>("${Message::uid.name} == '$uid'").first()
    }

    private fun MutableRealm.deleteLatestMessage(uid: String) {
        getLatestMessageSync(uid)?.also { message ->
            message.draftUuid?.let { getLatestDraftSync(it) }?.let(::delete)
        }?.let(::delete)
    }
    //endregion

    /**
     * TODO?
     */
    // fun upsertMessages(messages: List<Message>) {
    //     RealmController.mailboxContent.writeBlocking { messages.forEach { copyToRealm(it, UpdatePolicy.ALL) } }
    // }

    // fun deleteMessages(messages: List<Message>) {
    //     MailRealm.mailboxContent.writeBlocking {
    //         messages.forEach { message -> deleteLatestMessage(message.uid) }
    //     }
    // }

    // fun upsertMessage(message: Message) {
    //     MailRealm.mailboxContent.writeBlocking { copyToRealm(message, UpdatePolicy.ALL) }
    // }

    // fun updateMessage(uid: String, onUpdate: (message: Message) -> Unit) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestMessage(uid)?.let(onUpdate) }
    // }

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
}
