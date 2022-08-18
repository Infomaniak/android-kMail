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
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmController
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

    /**
     * Get data
     */
    fun getMessageSync(id: String): Message? {
        return getMessage(id).find()
    }

    fun getMessageAsync(id: String): SharedFlow<SingleQueryChange<Message>> {
        return getMessage(id).asFlow().toSharedFlow()
    }

    fun MutableRealm.getLatestMessageSync(uid: String): Message? {
        return getMessageSync(uid)?.let(::findLatest)
    }

    /**
     * Edit data
     */
    fun upsertApiData(apiMessages: List<Message>, thread: Thread) {

        // Get current data
        Log.d(RealmController.TAG, "Messages: Get current data")
        val realmMessages = thread.messages

        // Get outdated data
        Log.d(RealmController.TAG, "Messages: Get outdated data")
        // val deletableMessages = MailboxContentController.getDeletableMessages(messagesFromApi)
        val deletableMessages = realmMessages.filter { realmMessage ->
            apiMessages.none { apiMessage -> apiMessage.uid == realmMessage.uid }
        }

        RealmController.mailboxContent.writeBlocking {
            // Save new data
            Log.d(RealmController.TAG, "Messages: Save new data")
            apiMessages.forEach { apiMessage ->
                if (!apiMessage.isManaged()) copyToRealm(apiMessage, UpdatePolicy.ALL)
            }

            // Delete outdated data
            Log.d(RealmController.TAG, "Messages: Delete outdated data")
            deleteMessages(deletableMessages)
        }
    }

    fun MutableRealm.deleteMessages(messages: List<Message>, mailboxUuid: String? = null) {
        messages.forEach { deleteLatestMessage(it.uid, mailboxUuid) }
    }

    fun deleteMessage(uid: String) {
        RealmController.mailboxContent.writeBlocking { deleteLatestMessage(uid) }
    }

    fun MutableRealm.deleteLatestMessage(uid: String, mailboxUuid: String? = null) {
        getLatestMessageSync(uid)?.also { message ->
            message.draftUuid?.let { draftUuid ->
                if (mailboxUuid?.let { ApiRepository.getDraft(it, draftUuid).isSuccess() } == true) {
                    null
                } else {
                    getLatestDraftSync(draftUuid)
                }
            }?.let(::delete)
        }?.let(::delete)
    }

    /**
     * Utils
     */
    private fun getMessage(uid: String): RealmSingleQuery<Message> {
        return RealmController.mailboxContent.query<Message>("${Message::uid.name} == '$uid'").first()
    }

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
