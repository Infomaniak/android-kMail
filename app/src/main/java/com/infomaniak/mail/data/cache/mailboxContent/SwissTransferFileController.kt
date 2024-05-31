/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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

import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.SwissTransferFile
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmSingleQuery
import javax.inject.Inject

class SwissTransferFileController @Inject constructor(private val mailboxContentRealm: RealmDatabase.MailboxContent) {

    //region Get data
    fun getSwissTransferFile(localUuid: String) = getSwissTransferFileQuery(localUuid, mailboxContentRealm()).find()!!
    //endregion

    companion object {

        //region Queries
        private fun getSwissTransferFileQuery(localUuid: String, realm: TypedRealm): RealmSingleQuery<SwissTransferFile> {
            return realm.query<SwissTransferFile>("${SwissTransferFile::localUuid.name} == $0", localUuid).first()
        }
        //endregion
    }
}
//endregion
