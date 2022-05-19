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

    fun getMailbox(id: Int): Mailbox? {
        return MailRealm.mailboxInfo.query<Mailbox>("${Mailbox::mailboxId.name} == '$id'").first().find()
    }

    private fun MutableRealm.getLatestMailbox(id: Int): Mailbox? = getMailbox(id)?.let(::findLatest)

    fun getMailboxes(): RealmResults<Mailbox> = MailRealm.mailboxInfo.query<Mailbox>().find()

    // TODO: RealmKotlin doesn't fully support `IN` for now.
    // TODO: Workaround: https://github.com/realm/realm-js/issues/2781#issuecomment-607213640
    fun getDeletableMailboxes(mailboxesToKeep: List<Mailbox>): RealmResults<Mailbox> {
        val mailboxesIds = mailboxesToKeep.map { it.mailboxId }
        val query = mailboxesIds.joinToString(
            prefix = "NOT (${Mailbox::mailboxId.name} == '",
            separator = "' OR ${Mailbox::mailboxId.name} == '",
            postfix = "')"
        )
        return MailRealm.mailboxInfo.query<Mailbox>(query).find()
    }

    // fun upsertMailbox(mailbox: Mailbox) {
    //     mailboxInfo.writeBlocking { copyToRealm(mailbox, UpdatePolicy.ALL) }
    // }

    fun upsertMailboxes(mailboxes: List<Mailbox>) {
        MailRealm.mailboxInfo.writeBlocking { mailboxes.forEach { copyToRealm(it, UpdatePolicy.ALL) } }
    }

    // fun deleteMailbox(id: String) {
    //     mailboxInfo.writeBlocking { getLatestMailbox(id)?.let(::delete) }
    // }

    fun deleteMailboxes(mailboxes: List<Mailbox>) {
        MailRealm.mailboxInfo.writeBlocking { mailboxes.forEach { getLatestMailbox(it.mailboxId)?.let(::delete) } }
    }

    // fun selectMailboxByEmail(email: String) {
    //     currentMailbox = mailboxInfo.query<Mailbox>("${Mailbox::email.name} == '$email'").first().find()
    //     currentMailbox?.let { AccountUtils.currentMailboxId = it.mailboxId } ?: throw MailboxNotFoundException(email)
    // }

    // fun getMailboxInfoByEmail(email: String): Mailbox? = mailboxInfo.query<Mailbox>("${Mailbox::email.name} == '$email'").first().find()

    // private fun updateMailboxInfo(id: String, onUpdate: (mailbox: Mailbox) -> Unit) {
    //     mailboxInfo.writeBlocking { getLatestMailboxInfoById(id)?.let(onUpdate) }
    // }

    // private fun MutableRealm.removeMailboxInfoIfAlreadyExisting(mailbox: Mailbox) {
    //     getMailboxInfoByObjectId(mailbox.mailboxId)?.let { findLatest(it)?.let(::delete) }
    // }

    // class MailboxNotFoundException(email: String) : Exception("Mailbox [$email] not found")
}
