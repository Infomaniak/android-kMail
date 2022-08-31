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
import com.infomaniak.mail.data.cache.RealmDatabase.update
import com.infomaniak.mail.data.models.addressBook.AddressBook
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery

object AddressBookController {

    //region Get data
    private fun getAddressBooks(realm: MutableRealm? = null): RealmResults<AddressBook> {
        return realm.getAddressBooksQuery().find()
    }

    private fun MutableRealm?.getAddressBooksQuery(): RealmQuery<AddressBook> {
        return (this ?: RealmDatabase.userInfos).query()
    }

    private fun getAddressBook(id: Int, realm: MutableRealm? = null): AddressBook? {
        return realm.getAddressBookQuery(id).find()
    }

    private fun MutableRealm?.getAddressBookQuery(id: Int): RealmSingleQuery<AddressBook> {
        return (this ?: RealmDatabase.userInfos).query<AddressBook>("${AddressBook::id.name} = '$id'").first()
    }
    //endregion

    //region Edit data
    fun update(apiAddressBooks: List<AddressBook>) {
        Log.d(RealmDatabase.TAG, "AddressBooks: Save new data")
        RealmDatabase.userInfos.update<AddressBook>(apiAddressBooks)
    }

    private fun upsertAddressBooks(addressBooks: List<AddressBook>) {
        RealmDatabase.userInfos.writeBlocking { addressBooks.forEach { copyToRealm(it, UpdatePolicy.ALL) } }
    }

    private fun deleteAddressBooks(addressBooks: List<AddressBook>) {
        RealmDatabase.userInfos.writeBlocking { addressBooks.forEach { getAddressBook(it.id, this)?.let(::delete) } }
    }
    //endregion
}
