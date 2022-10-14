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

import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.Draft
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmSingleQuery

object DraftController {

    //region Queries
    private fun MutableRealm?.getDraftQuery(uuid: String): RealmSingleQuery<Draft> {
        return (this ?: RealmDatabase.mailboxContent()).query<Draft>("${Draft::uuid.name} = '$uuid'").first()
    }

    private fun MutableRealm?.getDraftByParentMessageUidQuery(messageUid: String): RealmSingleQuery<Draft> {
        return (this ?: RealmDatabase.mailboxContent()).query<Draft>("${Draft::parentMessageUid.name} = '$messageUid'").first()
    }
    //endregion

    //region Get data
    fun getDraft(uuid: String, realm: MutableRealm? = null): Draft? {
        return realm.getDraftQuery(uuid).find()
    }

    fun getDraftByParentMessageUid(parentMessageUid: String, realm: MutableRealm? = null): Draft? {
        return realm.getDraftByParentMessageUidQuery(parentMessageUid).find()
    }
    //endregion

    //region Edit data
    fun upsertDraft(draft: Draft, realm: MutableRealm? = null) {
        val block: (MutableRealm) -> Unit = { it.copyToRealm(draft, UpdatePolicy.ALL) }
        realm?.let(block) ?: RealmDatabase.mailboxContent().writeBlocking(block)
    }

    fun updateDraft(uuid: String, realm: MutableRealm? = null, onUpdate: (draft: Draft) -> Unit) {
        val block: (MutableRealm) -> Unit = { getDraft(uuid, it)?.let(onUpdate) }
        realm?.let(block) ?: RealmDatabase.mailboxContent().writeBlocking(block)
    }
    //endregion
}
