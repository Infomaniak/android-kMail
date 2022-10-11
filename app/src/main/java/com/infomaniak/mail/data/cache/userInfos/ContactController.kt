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
import com.infomaniak.mail.data.models.correspondent.Contact
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery

object ContactController {

    //region Queries
    private fun MutableRealm?.getContactsQuery(): RealmQuery<Contact> {
        return (this ?: RealmDatabase.userInfos()).query()
    }

    private fun MutableRealm?.getContactQuery(id: String): RealmSingleQuery<Contact> {
        return (this ?: RealmDatabase.userInfos()).query<Contact>("${Contact::id.name} = '$id'").first()
    }
    //endregion

    //region Get data
    fun getContacts(realm: MutableRealm? = null): RealmResults<Contact> {
        return realm.getContactsQuery().find()
    }

    private fun getContact(id: String, realm: MutableRealm? = null): Contact? {
        return realm.getContactQuery(id).find()
    }
    //endregion

    //region Edit data
    fun update(apiContacts: List<Contact>) {
        Log.d(RealmDatabase.TAG, "Contacts: Save new data")
        RealmDatabase.userInfos().update<Contact>(apiContacts)
    }

    private fun upsertContacts(contacts: List<Contact>) {
        RealmDatabase.userInfos().writeBlocking { contacts.forEach { copyToRealm(it, UpdatePolicy.ALL) } }
    }

    private fun deleteContacts(contacts: List<Contact>) {
        RealmDatabase.userInfos().writeBlocking { contacts.forEach { getContact(it.id)?.let(::delete) } }
    }
    //endregion
}
