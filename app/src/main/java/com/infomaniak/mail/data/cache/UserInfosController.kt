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
import com.infomaniak.mail.data.models.addressBook.AddressBook
import com.infomaniak.mail.data.models.user.UserPreferences
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import kotlinx.coroutines.flow.Flow

object UserInfosController {

    /**
     * User Preferences
     */
    fun getUserPreferences(): UserPreferences = with(MailRealm.userInfos) {
        query<UserPreferences>().first().find() ?: writeBlocking { copyToRealm(UserPreferences()) }
    }

    fun updateUserPreferences(onUpdate: (appSettings: UserPreferences) -> Unit) {
        MailRealm.userInfos.writeBlocking { findLatest(getUserPreferences())?.let(onUpdate) }
    }

    fun removeUserPreferences() {
        MailRealm.userInfos.writeBlocking { findLatest(getUserPreferences())?.let(::delete) }
    }

    /**
     * Address Books
     */
    private fun getAddressBook(id: Int): AddressBook? {
        return MailRealm.userInfos.query<AddressBook>("${AddressBook::id.name} == '$id'").first().find()
    }

    private fun MutableRealm.getLatestAddressBook(id: Int): AddressBook? = getAddressBook(id)?.let(::findLatest)

    fun getAddressBooks(): Flow<ResultsChange<AddressBook>> = MailRealm.userInfos.query<AddressBook>().asFlow()

    fun upsertAddressBooks(addressBooks: List<AddressBook>) {
        MailRealm.userInfos.writeBlocking { addressBooks.forEach { copyToRealm(it, UpdatePolicy.ALL) } }
    }

    fun deleteAddressBooks(addressBooks: List<AddressBook>) {
        MailRealm.userInfos.writeBlocking { addressBooks.forEach { getLatestAddressBook(it.id)?.let(::delete) } }
    }

    fun Contact.getAddressBook(): AddressBook? = getAddressBook(addressBookId)

    /**
     * Contacts
     */
    private fun getContact(id: String): Contact? {
        return MailRealm.userInfos.query<Contact>("${Contact::id.name} == '$id'").first().find()
    }

    private fun MutableRealm.getLatestContact(id: String): Contact? = getContact(id)?.let(::findLatest)

    fun getContacts(): Flow<ResultsChange<Contact>> = MailRealm.userInfos.query<Contact>().asFlow()

    fun upsertContacts(contacts: List<Contact>) {
        MailRealm.userInfos.writeBlocking { contacts.forEach { copyToRealm(it, UpdatePolicy.ALL) } }
    }

    fun deleteContacts(contacts: List<Contact>) {
        MailRealm.userInfos.writeBlocking { contacts.forEach { getLatestContact(it.id)?.let(::delete) } }
    }

    fun AddressBook.getContacts(): List<Contact> {
        return MailRealm.userInfos.query<Contact>("${Contact::addressBookId.name} == '$id'").find()
    }
}
