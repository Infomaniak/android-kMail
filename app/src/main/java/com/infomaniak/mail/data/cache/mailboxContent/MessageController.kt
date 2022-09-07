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
import com.infomaniak.mail.data.cache.mailboxContent.DraftController.getDraft
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
    fun getMessage(id: String, realm: MutableRealm? = null): Message? {
        return realm.getMessageQuery(id).find()
    }

    private fun getMessageAsync(id: String, realm: MutableRealm? = null): SharedFlow<SingleQueryChange<Message>> {
        return realm.getMessageQuery(id).asFlow().toSharedFlow()
    }

    private fun MutableRealm?.getMessageQuery(uid: String): RealmSingleQuery<Message> {
        return (this ?: RealmDatabase.mailboxContent).query<Message>("${Message::uid.name} == '$uid'").first()
    }
    //endregion

    //region Edit data
    fun update(apiMessages: List<Message>, thread: Thread) {

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

    fun updateMessage(uid: String, onUpdate: (message: Message) -> Unit) {
        RealmDatabase.mailboxContent.writeBlocking { getMessage(uid, this)?.let(onUpdate) }
    }

    fun MutableRealm.deleteMessages(messages: List<Message>) {
        messages.forEach { deleteMessage(it.uid, this) }
    }

    fun deleteMessage(uid: String, realm: MutableRealm? = null) {
        if (realm == null) {
            RealmDatabase.mailboxContent.writeBlocking { deleteMessage(uid, this) }
        } else {
            getMessage(uid, realm)
                ?.also { message -> message.draftUuid?.let { getDraft(it, realm) }?.let(realm::delete) }
                ?.let(realm::delete)
        }
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
