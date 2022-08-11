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
import com.infomaniak.mail.data.cache.RealmController
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

    /**
     * Get data
     */
    fun getAddressBooksSync(): RealmResults<AddressBook> {
        return getAddressBooks().find()
    }

    fun getAddressBooksAsync(): SharedFlow<ResultsChange<AddressBook>> {
        return getAddressBooks().asFlow().toSharedFlow()
    }

    fun getAddressBookSync(id: Int): AddressBook? {
        return getAddressBook(id).find()
    }

    fun getAddressBookAsync(id: Int): SharedFlow<SingleQueryChange<AddressBook>> {
        return getAddressBook(id).asFlow().toSharedFlow()
    }

    /**
     * Edit data
     */
    fun upsertApiData(apiAddressBooks: List<AddressBook>) {

        // Get current data
        Log.d(RealmController.TAG, "AddressBooks: Get current data")
        val realmAddressBooks = getAddressBooksSync()

        // Get outdated data
        Log.d(RealmController.TAG, "AddressBooks: Get outdated data")
        // val deletableAddressBooks = ContactsController.getDeletableAddressBooks(apiAddressBooks)
        val deletableAddressBooks = realmAddressBooks.filter { realmContact ->
            apiAddressBooks.none { it.id == realmContact.id }
        }

        // Save new data
        Log.d(RealmController.TAG, "AddressBooks: Save new data")
        upsertAddressBooks(apiAddressBooks)

        // Delete outdated data
        Log.d(RealmController.TAG, "AddressBooks: Delete outdated data")
        deleteAddressBooks(deletableAddressBooks)
    }

    fun upsertAddressBooks(addressBooks: List<AddressBook>) {
        RealmController.userInfos.writeBlocking { addressBooks.forEach { copyToRealm(it, UpdatePolicy.ALL) } }
    }

    fun deleteAddressBooks(addressBooks: List<AddressBook>) {
        RealmController.userInfos.writeBlocking { addressBooks.forEach { getLatestAddressBook(it.id)?.let(::delete) } }
    }

    /**
     * Utils
     */
    private fun getAddressBooks(): RealmQuery<AddressBook> {
        return RealmController.userInfos.query()
    }

    private fun getAddressBook(id: Int): RealmSingleQuery<AddressBook> {
        return RealmController.userInfos.query<AddressBook>("${AddressBook::id.name} == '$id'").first()
    }

    private fun MutableRealm.getLatestAddressBook(id: Int): AddressBook? {
        return getAddressBookSync(id)?.let(::findLatest)
    }
}
