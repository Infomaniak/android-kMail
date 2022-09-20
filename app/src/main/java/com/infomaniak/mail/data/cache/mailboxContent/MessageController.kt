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
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmSingleQuery

object MessageController {

    //region Get data
    fun getMessage(id: String, realm: MutableRealm? = null): Message? {
        return realm.getMessageQuery(id).find()
    }

    private fun MutableRealm?.getMessageQuery(uid: String): RealmSingleQuery<Message> {
        return (this ?: RealmDatabase.mailboxContent).query<Message>("${Message::uid.name} = '$uid'").first()
    }
    //endregion

    //region Edit data
    fun update(realmMessages: List<Message>, apiMessages: List<Message>) {
        RealmDatabase.mailboxContent.writeBlocking {

            // Get outdated data
            Log.d(RealmDatabase.TAG, "Messages: Get outdated data")
            val outdatedMessages = getOutdatedMessages(realmMessages, apiMessages)

            // Save new data
            Log.d(RealmDatabase.TAG, "Messages: Save new data")
            insertNewData(apiMessages)

            // Delete outdated data
            Log.d(RealmDatabase.TAG, "Messages: Delete outdated data")
            deleteMessages(outdatedMessages)
        }
    }

    private fun getOutdatedMessages(realmMessages: List<Message>, apiMessages: List<Message>): List<Message> {
        return realmMessages.filter { realmMessage ->
            apiMessages.none { apiMessage -> apiMessage.uid == realmMessage.uid }
        }
    }

    private fun MutableRealm.insertNewData(apiMessages: List<Message>) {
        apiMessages.forEach { apiMessage ->
            if (!apiMessage.isManaged()) copyToRealm(apiMessage, UpdatePolicy.ALL)
        }
    }

    fun updateMessage(uid: String, onUpdate: (message: Message) -> Unit) {
        RealmDatabase.mailboxContent.writeBlocking { getMessage(uid, this)?.let(onUpdate) }
    }

    fun MutableRealm.deleteMessages(messages: List<Message>) {
        messages.forEach { deleteMessage(it.uid, this) }
    }

    fun deleteMessage(uid: String, realm: MutableRealm? = null) {
        val block: (MutableRealm) -> Unit = {
            getMessage(uid, it)
                ?.also { message -> message.draftUuid?.let { draftUuid -> getDraft(draftUuid, it) }?.let(it::delete) }
                ?.let(it::delete)
        }
        realm?.let(block) ?: RealmDatabase.mailboxContent.writeBlocking(block)
    }
    //endregion
}
