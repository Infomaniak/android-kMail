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
import com.infomaniak.mail.data.models.MergedContact
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.BaseRealmObject

object ContactController {

    //region Get data
    fun getMergedContacts(exceptionContactIds: List<String>? = null, realm: MutableRealm? = null): RealmResults<MergedContact> {
        return realm.getMergedContactsQuery(exceptionContactIds).find()
    }

    private fun MutableRealm?.getMergedContactsQuery(exceptionContactIds: List<String>?): RealmQuery<MergedContact> {
        val checkIsNotInExceptions = exceptionContactIds?.let { ids ->
            "NOT ${MergedContact::id.name} IN {${ids.joinToString { "\"$it\"" }}}"
        }
        return (this ?: RealmDatabase.userInfos).nullQuery(checkIsNotInExceptions)
    }

    private inline fun <reified T : BaseRealmObject> TypedRealm.nullQuery(queryInput: String?): RealmQuery<T> {
        return if (queryInput == null) query() else query(queryInput)
    }
    //endregion

    //region Edit data
    fun update(apiContacts: List<MergedContact>) {
        Log.d(RealmDatabase.TAG, "MergedContacts: Save new data")
        RealmDatabase.userInfos.update<MergedContact>(apiContacts)
    }

    fun update2(apiContacts: List<MergedContact>) {

        RealmDatabase.userInfos.writeBlocking {
            // Get outdated data
            Log.d(RealmDatabase.TAG, "Contacts: Get outdated data")
            val deletableContacts = getMergedContacts(apiContacts.map { it.id }, this)

            // Save new data
            Log.d(RealmDatabase.TAG, "Contacts: Save new data")
            upsertMergedContacts(apiContacts, this)

            // Delete outdated data
            Log.d(RealmDatabase.TAG, "Contacts: Delete outdated data")
            deleteMergedContacts(deletableContacts, this)
        }
    }

    private fun upsertMergedContacts(contacts: List<MergedContact>, realm: MutableRealm? = null) {
        val block: (MutableRealm) -> Unit = { contacts.forEach { contact -> it.copyToRealm(contact, UpdatePolicy.ALL) } }
        realm?.let(block) ?: RealmDatabase.userInfos.writeBlocking(block)
    }

    private fun deleteMergedContacts(contacts: RealmResults<MergedContact>, realm: MutableRealm? = null) {
        val block: (MutableRealm) -> Unit = { it.delete(contacts) }
        realm?.let(block) ?: RealmDatabase.userInfos.writeBlocking(block)
    }
    //endregion
}
