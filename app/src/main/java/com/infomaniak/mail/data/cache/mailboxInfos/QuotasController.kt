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
package com.infomaniak.mail.data.cache.mailboxInfos

import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.Quotas
import com.infomaniak.mail.utils.toSharedFlow
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmSingleQuery
import kotlinx.coroutines.flow.SharedFlow

object QuotasController {

    //region Get data
    private fun getQuotas(mailboxObjectId: String, realm: MutableRealm? = null): RealmSingleQuery<Quotas> {
        return (realm ?: RealmDatabase.mailboxInfos)
            .query<Quotas>("${Quotas::mailboxObjectId.name} == '$mailboxObjectId'")
            .first()
    }

    fun getQuotasAsync(mailboxObjectId: String, realm: MutableRealm? = null): SharedFlow<SingleQueryChange<Quotas>> {
        return getQuotas(mailboxObjectId, realm).asFlow().toSharedFlow()
    }
    //endregion
}
