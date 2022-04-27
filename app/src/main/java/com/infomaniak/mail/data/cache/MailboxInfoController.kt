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

import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.utils.Realms
import io.realm.MutableRealm
import io.realm.RealmResults
import io.realm.query

object MailboxInfoController {
    fun getMailboxInfos(): RealmResults<Mailbox> =
        Realms.mailboxInfo.query<Mailbox>().find()

    fun getMailboxInfoByEmail(email: String): Mailbox? =
        Realms.mailboxInfo.query<Mailbox>("${Mailbox::email.name} == '$email'").first().find()

    private fun getMailboxInfoByObjectId(objectId: String): Mailbox? =
        Realms.mailboxInfo.query<Mailbox>("${Mailbox::objectId.name} == '$objectId'").first().find()

    private fun MutableRealm.getLatestMailboxInfoByObjectId(objectId: String): Mailbox? =
        getMailboxInfoByObjectId(objectId)?.let(::findLatest)

    fun upsertMailboxInfo(mailbox: Mailbox) {
        Realms.mailboxInfo.writeBlocking {
            removeMailboxInfoIfAlreadyExisting(mailbox) // TODO: remove this when the UPSERT is working
            copyToRealm(mailbox)
        }
    }

    fun updateMailboxInfo(objectId: String, onUpdate: (mailbox: Mailbox) -> Unit) {
        Realms.mailboxInfo.writeBlocking { getLatestMailboxInfoByObjectId(objectId)?.let(onUpdate) }
    }

    fun removeMailboxInfo(objectId: String) {
        Realms.mailboxInfo.writeBlocking { getLatestMailboxInfoByObjectId(objectId)?.let(::delete) }
    }

    private fun MutableRealm.removeMailboxInfoIfAlreadyExisting(mailbox: Mailbox) {
        getMailboxInfoByObjectId(mailbox.objectId)?.let { findLatest(it)?.let(::delete) }
    }
}
