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
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.copyListToRealm
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmSingleQuery

object MessageController {

    //region Queries
    private fun MutableRealm?.getMessagesQuery(uids: List<String>): RealmQuery<Message> {
        val messages = "${Message::uid.name} IN {${uids.joinToString { "\"$it\"" }}}"
        return (this ?: RealmDatabase.mailboxContent()).query(messages)
    }

    private fun getMessageQuery(uid: String, realm: TypedRealm? = null): RealmSingleQuery<Message> {
        return (realm ?: RealmDatabase.mailboxContent()).query<Message>("${Message::uid.name} = '$uid'").first()
    }
    //endregion

    //region Get data
    fun getMessages(uids: List<String>, realm: MutableRealm? = null): RealmQuery<Message> {
        return realm.getMessagesQuery(uids)
    }

    fun getMessage(uid: String, realm: TypedRealm? = null): Message? {
        return getMessageQuery(uid, realm).find()
    }
    //endregion

    //region Edit data
    fun updateMessage(uid: String, realm: MutableRealm? = null, onUpdate: (message: Message) -> Unit) {
        val block: (MutableRealm) -> Unit = { getMessage(uid, it)?.let(onUpdate) }
        realm?.let(block) ?: RealmDatabase.mailboxContent().writeBlocking(block)
    }

    fun MutableRealm.update(localMessages: List<Message>, apiMessages: List<Message>) {

        Log.d(RealmDatabase.TAG, "Messages: Delete outdated data")
        deleteMessages(getOutdatedMessages(localMessages, apiMessages))

        Log.d(RealmDatabase.TAG, "Messages: Save new data")
        copyListToRealm(apiMessages, alsoCopyManagedItems = false)
    }

    // TODO: Replace this with a Realm query (blocked by https://github.com/realm/realm-kotlin/issues/591)
    private fun getOutdatedMessages(localMessages: List<Message>, apiMessages: List<Message>): List<Message> {
        return localMessages.filter { localMessage ->
            apiMessages.none { apiMessage -> apiMessage.uid == localMessage.uid }
        }
    }

    fun MutableRealm.deleteMessages(messages: List<Message>) {
        messages.reversed().forEach { deleteMessage(it.uid, this) }
    }

    fun deleteMessage(uid: String, realm: MutableRealm? = null) {
        val block: (MutableRealm) -> Unit = {
            getMessage(uid, it)
                ?.let { message ->
                    DraftController.getDraftByMessageUid(message.uid, it)?.let(it::delete)
                    it.delete(message)
                }
        }
        realm?.let(block) ?: RealmDatabase.mailboxContent().writeBlocking(block)
    }
    //endregion
}
