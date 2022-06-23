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

import com.infomaniak.mail.data.models.Contact
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import kotlinx.coroutines.flow.Flow

object ContactsController {

    private fun getContact(id: String): Contact? {
        return MailRealm.contacts.query<Contact>("${Contact::id.name} == '$id'").first().find()
    }

    private fun MutableRealm.getLatestContact(id: String): Contact? = getContact(id)?.let(::findLatest)

    fun getContacts(): Flow<ResultsChange<Contact>> = MailRealm.contacts.query<Contact>().asFlow()

    fun upsertContacts(contacts: List<Contact>) {
        MailRealm.contacts.writeBlocking { contacts.forEach { copyToRealm(it, UpdatePolicy.ALL) } }
    }

    fun deleteContacts(contacts: List<Contact>) {
        MailRealm.contacts.writeBlocking { contacts.forEach { getLatestContact(it.id)?.let(::delete) } }
    }
}
