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
import com.infomaniak.mail.utils.toSharedFlow
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmSingleQuery
import kotlinx.coroutines.flow.SharedFlow

object DraftController {

    /**
     * Get data
     */
    fun getDraftSync(uuid: String): Draft? {
        return getDraft(uuid).find()
    }

    fun getDraftAsync(uuid: String): SharedFlow<SingleQueryChange<Draft>> {
        return getDraft(uuid).asFlow().toSharedFlow()
    }

    fun MutableRealm.getLatestDraftSync(uuid: String): Draft? {
        return getDraftSync(uuid)?.let(::findLatest)
    }

    /**
     * Edit data
     */
    fun upsertDraft(draft: Draft) {
        RealmDatabase.mailboxContent.writeBlocking { copyToRealm(draft, UpdatePolicy.ALL) }
    }

    /**
     * Utils
     */
    private fun getDraft(uuid: String): RealmSingleQuery<Draft> {
        return RealmDatabase.mailboxContent.query<Draft>("${Draft::uuid.name} == '$uuid'").first()
    }
}
