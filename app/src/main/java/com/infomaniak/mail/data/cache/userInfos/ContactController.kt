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
package com.infomaniak.mail.data.cache.userInfos

import com.infomaniak.mail.data.cache.RealmController
import com.infomaniak.mail.data.models.Contact
import com.infomaniak.mail.utils.toSharedFlow
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import kotlinx.coroutines.flow.SharedFlow

object ContactController {

    /**
     * Get data
     */
    fun getContactsSync(): RealmResults<Contact> {
        return getContacts().find()
    }

    fun getContactsAsync(): SharedFlow<ResultsChange<Contact>> {
        return getContacts().asFlow().toSharedFlow()
    }

    fun getAddressBookContactsSync(addressBookId: Int): RealmResults<Contact> {
        return getContacts(addressBookId).find()
    }

    fun getAddressBookContactsAsync(addressBookId: Int): SharedFlow<ResultsChange<Contact>> {
        return getContacts(addressBookId).asFlow().toSharedFlow()
    }

    fun getContactSync(id: String): Contact? {
        return getContact(id).find()
    }

    fun getContactAsync(id: String): SharedFlow<SingleQueryChange<Contact>> {
        return getContact(id).asFlow().toSharedFlow()
    }

    /**
     * Edit data
     */
    fun upsertContacts(contacts: List<Contact>) {
        RealmController.userInfos.writeBlocking { contacts.forEach { copyToRealm(it, UpdatePolicy.ALL) } }
    }

    fun deleteContacts(contacts: List<Contact>) {
        RealmController.userInfos.writeBlocking { contacts.forEach { getLatestContact(it.id)?.let(::delete) } }
    }

    /**
     * Utils
     */
    private fun getContacts(): RealmQuery<Contact> {
        return RealmController.userInfos.query()
    }

    private fun getContacts(addressBookId: Int): RealmQuery<Contact> {
        return RealmController.userInfos.query("${Contact::addressBookId.name} == '$addressBookId'")
    }

    private fun getContact(id: String): RealmSingleQuery<Contact> {
        return RealmController.userInfos.query<Contact>("${Contact::id.name} == '$id'").first()
    }

    private fun MutableRealm.getLatestContact(id: String): Contact? {
        return getContactSync(id)?.let(::findLatest)
    }
}
