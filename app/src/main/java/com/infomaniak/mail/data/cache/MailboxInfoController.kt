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
import io.realm.MutableRealm
import io.realm.MutableRealm.UpdatePolicy
import io.realm.RealmResults
import io.realm.query

object MailboxInfoController {

    fun getMailboxes(): RealmResults<Mailbox> =
        MailRealm.mailboxInfo.query<Mailbox>().find()

    fun upsertMailbox(mailbox: Mailbox) {
        MailRealm.mailboxInfo.writeBlocking { copyToRealm(mailbox, UpdatePolicy.ALL) }
    }

    fun deleteMailbox(objectId: String) {
        MailRealm.mailboxInfo.writeBlocking { getLatestMailbox(objectId)?.let(::delete) }
    }

    private fun MutableRealm.getLatestMailbox(objectId: String): Mailbox? =
        getMailbox(objectId)?.let(::findLatest)

    private fun getMailbox(objectId: String): Mailbox? =
        MailRealm.mailboxInfo.query<Mailbox>("${Mailbox::objectId.name} == '$objectId'").first().find()

//    fun selectMailboxByEmail(email: String) {
//        currentMailbox = MailRealm.mailboxInfo.query<Mailbox>("${Mailbox::email.name} == '$email'").first().find()
//        currentMailbox?.let { AccountUtils.currentMailboxId = it.mailboxId } ?: throw MailboxNotFoundException(email)
//    }

//    fun getMailboxInfoByEmail(email: String): Mailbox? =
//        MailRealm.mailboxInfo.query<Mailbox>("${Mailbox::email.name} == '$email'").first().find()


//    private fun updateMailboxInfo(objectId: String, onUpdate: (mailbox: Mailbox) -> Unit) {
//        MailRealm.mailboxInfo.writeBlocking { getLatestMailboxInfoByObjectId(objectId)?.let(onUpdate) }
//    }

//    private fun MutableRealm.removeMailboxInfoIfAlreadyExisting(mailbox: Mailbox) {
//        getMailboxInfoByObjectId(mailbox.objectId)?.let { findLatest(it)?.let(::delete) }
//    }

//    class MailboxNotFoundException(email: String) : Exception("Mailbox [$email] not found")
}
