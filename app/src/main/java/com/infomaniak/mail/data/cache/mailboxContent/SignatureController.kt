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

import android.util.Log
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.update
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmResults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object SignatureController {

    private inline val defaultRealm get() = RealmDatabase.mailboxContent()

    //region Get data
    private fun getDefaultSignature(realm: TypedRealm): Signature? {
        return realm.query<Signature>("${Signature::isDefault.name} == true").first().find()
    }

    fun getSignature(realm: TypedRealm): Signature {
        return getDefaultSignature(realm) ?: realm.query<Signature>().first().find()!!
    }

    fun getSignaturesLive(mailboxId: Int): Flow<RealmResults<Signature>> {
        val realm = if (mailboxId == AccountUtils.currentMailboxId) {
            defaultRealm
        } else {
            RealmDatabase.newMailboxContentInstance(AccountUtils.currentUserId, mailboxId)
        }

        return realm.query<Signature>().find().asFlow().map { it.list }
    }
    //endregion

    //region Edit data
    fun update(apiSignatures: List<Signature>, mailboxId: Int) {
        Log.d(RealmDatabase.TAG, "Signatures: Save new data")
        val realm = if (mailboxId == AccountUtils.currentMailboxId) {
            defaultRealm
        } else {
            RealmDatabase.newMailboxContentInstance(AccountUtils.currentUserId, mailboxId)
        }

        realm.update<Signature>(apiSignatures)
    }

    fun update(apiSignatures: List<Signature>, realm: MutableRealm) {
        Log.d(RealmDatabase.TAG, "Signatures: Save new data")
        realm.update<Signature>(apiSignatures)
    }
    //endregion
}
