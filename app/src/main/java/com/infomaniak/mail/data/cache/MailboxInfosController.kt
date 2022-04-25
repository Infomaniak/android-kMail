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
import io.realm.MutableRealm.UpdatePolicy
import io.realm.RealmResults
import io.realm.query

object MailboxInfosController {
    fun getMailboxInfos(): RealmResults<Mailbox> =
        Realms.mailboxInfos.query<Mailbox>().find()

    fun getMailboxInfos(objectId: String): Mailbox? =
        Realms.mailboxInfos.query<Mailbox>("${Mailbox::objectId.name} == '$objectId'").first().find()

    fun upsertMailboxInfos(mailbox: Mailbox) {
        Realms.mailboxInfos.writeBlocking { copyToRealm(mailbox, UpdatePolicy.ALL) }
    }

    fun updateMailboxInfos(objectId: String, onUpdate: (mailbox: Mailbox) -> Unit) {
        Realms.mailboxInfos.writeBlocking {
            getMailboxInfos(objectId)?.let { findLatest(it)?.let(onUpdate) }
        }
    }

    fun removeMailboxInfos(objectId: String) {
        Realms.mailboxInfos.writeBlocking {
            getMailboxInfos(objectId)?.let { findLatest(it)?.let(::delete) }
        }
    }
}
