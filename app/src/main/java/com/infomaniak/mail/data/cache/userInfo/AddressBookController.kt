/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
package com.infomaniak.mail.data.cache.userInfo

import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.addressBook.AddressBook
import com.infomaniak.mail.data.models.addressBook.ContactGroup
import com.infomaniak.mail.di.UserInfoRealm
import com.infomaniak.mail.utils.extensions.update
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmSingleQuery
import javax.inject.Inject

class AddressBookController @Inject constructor(@UserInfoRealm private val userInfoRealm: Realm) {

    //region Queries
    private fun getDefaultAddressBookQuery(): RealmSingleQuery<AddressBook> {
        return userInfoRealm.query<AddressBook>("${AddressBook::isDefault.name} == true").first()
    }

    private fun getAllAddressBookQuery(): RealmQuery<AddressBook> {
        return userInfoRealm.query<AddressBook>()
    }

    private fun getAddressBookWithGroupQuery(contactGroup: ContactGroup): RealmSingleQuery<AddressBook> {
        val myContactGroup = userInfoRealm.query<ContactGroup>("id == $0", contactGroup.id).first().find()!!
        return userInfoRealm.query<AddressBook>("ANY ${AddressBook::contactGroups.name} == $0", myContactGroup).first()
    }
    //endregion

    //region Get data
    fun getDefaultAddressBook() = getDefaultAddressBookQuery().find()!!

    fun getAllAddressBook() = getAllAddressBookQuery().find()

    fun getAddressBookWithGroup(contactGroup: ContactGroup) = getAddressBookWithGroupQuery(contactGroup).find()
    //endregion

    //region Edit data
    suspend fun update(apiAddressBooks: List<AddressBook>) {
        SentryLog.d(RealmDatabase.TAG, "AddressBooks: Save new data")
        userInfoRealm.update<AddressBook>(apiAddressBooks)
    }
    //endregion
}
