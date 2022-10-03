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
import com.infomaniak.mail.utils.toSharedFlow
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import kotlinx.coroutines.flow.SharedFlow

object MergedContactController {
    //region Queries
    private fun MutableRealm?.getMergedContactsQuery(): RealmQuery<MergedContact> {
        return (this ?: RealmDatabase.userInfos).query()
    }
    //endregion

    //region Get data
    fun getMergedContacts(realm: MutableRealm? = null): RealmResults<MergedContact> {
        return realm.getMergedContactsQuery().find()
    }

    fun getMergedContactsAsync(realm: MutableRealm? = null): SharedFlow<ResultsChange<MergedContact>> {
        return realm.getMergedContactsQuery().asFlow().toSharedFlow()
    }
    //endregion

    //region Edit data
    fun update(mergedContacts: List<MergedContact>) {
        Log.d(RealmDatabase.TAG, "MergedContacts: Save new data")
        RealmDatabase.userInfos.update<MergedContact>(mergedContacts)
    }
    //endregion
}
