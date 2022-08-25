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

object RecipientController {

    /**
     * TODO?
     */
    // private fun getRecipients(): RealmResults<Recipient> = MailRealm.mailboxContent.query<Recipient>().find()

    // private fun getRecipientsByEmail(email: String): RealmResults<Recipient> = MailRealm.mailboxContent.query<Recipient>("${Recipient::email.name} == '$email'").find()

    // private fun getRecipientByEmail(email: String): Recipient? = getRecipientsByEmail(email).firstOrNull()

    // private fun MutableRealm.getLatestRecipientByEmail(email: String): Recipient? = getRecipientByEmail(email)?.let(::findLatest)

    // fun upsertRecipient(recipient: Recipient) {
    //     MailRealm.mailboxContent.writeBlocking { copyToRealm(recipient, UpdatePolicy.ALL) }
    // }

    // fun updateRecipient(email: String, onUpdate: (recipient: Recipient) -> Unit) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestRecipientByEmail(email)?.let(onUpdate) }
    // }

    // fun removeRecipient(email: String) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestRecipientByEmail(email)?.let(::delete) }
    // }

    // private fun MutableRealm.removeRecipientIfAlreadyExisting(recipient: Recipient) {
    //     getRecipientByEmail(recipient.email)?.let { findLatest(it)?.let(::delete) }
    // }

    // fun cleanRecipients() {
    //     MailRealm.mailboxContent.writeBlocking {
    //         getRecipients().map { it.email }.distinct().forEach { email ->
    //             getRecipientsByEmail(email).forEachIndexed { index, recipient ->
    //                 if (index > 0) findLatest(recipient)?.let(::delete)
    //             }
    //         }
    //     }
    // }
}
