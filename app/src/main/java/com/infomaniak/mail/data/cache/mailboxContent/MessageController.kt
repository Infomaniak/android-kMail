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
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmSingleQuery

object MessageController {

    //region Queries
    private fun MutableRealm?.getMessageQuery(uid: String): RealmSingleQuery<Message> {
        return (this ?: RealmDatabase.mailboxContent()).query<Message>("${Message::uid.name} = '$uid'").first()
    }
    //endregion

    //region Get data
    private fun getMessage(uid: String, realm: MutableRealm? = null): Message? {
        return realm.getMessageQuery(uid).find()
    }
    //endregion

    //region Edit data
    fun update(localMessages: List<Message>, apiMessages: List<Message>) {
        RealmDatabase.mailboxContent().writeBlocking {

            Log.d(RealmDatabase.TAG, "Messages: Delete outdated data")
            deleteMessages(getOutdatedMessages(localMessages, apiMessages))

            Log.d(RealmDatabase.TAG, "Messages: Save new data")
            copyListToRealm(apiMessages, alsoCopyManagedItems = false)
        }
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
