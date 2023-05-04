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
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.query
import io.sentry.Sentry
import io.sentry.SentryLevel

object SignatureController {

    //region Get data
    private fun getDefaultSignature(realm: TypedRealm): Signature? {
        return realm.query<Signature>("${Signature::isDefault.name} == true").first().find()
    }

    fun getSignature(realm: TypedRealm): Signature {
        return getDefaultSignature(realm) ?: realm.query<Signature>().first().find()!!
    }
    //endregion

    //region Edit data
    fun update(apiSignatures: List<Signature>) {
        Log.d(RealmDatabase.TAG, "Signatures: Save new data")

        val defaultSignaturesCount = apiSignatures.count { it.isDefault }
        when {
            apiSignatures.isEmpty() -> Sentry.withScope { scope ->
                scope.level = SentryLevel.ERROR
                scope.setExtra("email", AccountUtils.currentMailboxEmail.toString())
                Sentry.captureMessage("This user doesn't have any Signature")
            }
            defaultSignaturesCount == 0 -> Sentry.withScope { scope ->
                scope.level = SentryLevel.ERROR
                scope.setExtra("signaturesCount", "${apiSignatures.count()}")
                scope.setExtra("email", AccountUtils.currentMailboxEmail.toString())
                Sentry.captureMessage("This user has Signatures, but no default one")
            }
            defaultSignaturesCount > 1 -> Sentry.withScope { scope ->
                scope.level = SentryLevel.ERROR
                scope.setExtra("defaultSignaturesCount", "$defaultSignaturesCount")
                scope.setExtra("totalSignaturesCount", "${apiSignatures.count()}")
                scope.setExtra("email", AccountUtils.currentMailboxEmail.toString())
                Sentry.captureMessage("This user has several default Signatures")
            }
        }

        RealmDatabase.mailboxContent().update<Signature>(apiSignatures)
    }
    //endregion
}
