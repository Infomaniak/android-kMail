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

import android.util.Log
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.addressBook.AddressBook
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

object AddressBookController {

    //region Get data
    private fun getAddressBooks(realm: MutableRealm? = null): RealmResults<AddressBook> {
        return getAddressBooksQuery(realm).find()
    }

    private fun getAddressBooksAsync(realm: MutableRealm? = null): SharedFlow<ResultsChange<AddressBook>> {
        return getAddressBooksQuery(realm).asFlow().toSharedFlow()
    }

    private fun getAddressBooksQuery(realm: MutableRealm? = null): RealmQuery<AddressBook> {
        return (realm ?: RealmDatabase.userInfos).query()
    }

    private fun getAddressBook(id: Int, realm: MutableRealm? = null): AddressBook? {
        return getAddressBookQuery(id, realm).find()
    }

    private fun getAddressBookAsync(id: Int, realm: MutableRealm? = null): SharedFlow<SingleQueryChange<AddressBook>> {
        return getAddressBookQuery(id, realm).asFlow().toSharedFlow()
    }

    private fun getAddressBookQuery(id: Int, realm: MutableRealm? = null): RealmSingleQuery<AddressBook> {
        return (realm ?: RealmDatabase.userInfos).query<AddressBook>("${AddressBook::id.name} == '$id'").first()
    }
    //endregion

    //region Edit data
    fun update(apiAddressBooks: List<AddressBook>) {

        // Get current data
        Log.d(RealmDatabase.TAG, "AddressBooks: Get current data")
        val realmAddressBooks = getAddressBooks()

        // Get outdated data
        Log.d(RealmDatabase.TAG, "AddressBooks: Get outdated data")
        // val deletableAddressBooks = ContactsController.getDeletableAddressBooks(apiAddressBooks)
        val deletableAddressBooks = realmAddressBooks.filter { realmContact ->
            apiAddressBooks.none { it.id == realmContact.id }
        }

        // Save new data
        Log.d(RealmDatabase.TAG, "AddressBooks: Save new data")
        upsertAddressBooks(apiAddressBooks)

        // Delete outdated data
        Log.d(RealmDatabase.TAG, "AddressBooks: Delete outdated data")
        deleteAddressBooks(deletableAddressBooks)
    }

    private fun upsertAddressBooks(addressBooks: List<AddressBook>) {
        RealmDatabase.userInfos.writeBlocking { addressBooks.forEach { copyToRealm(it, UpdatePolicy.ALL) } }
    }

    private fun deleteAddressBooks(addressBooks: List<AddressBook>) {
        RealmDatabase.userInfos.writeBlocking { addressBooks.forEach { getAddressBook(it.id, this)?.let(::delete) } }
    }
    //endregion
}
