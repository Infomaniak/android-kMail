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
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.di.UserInfoRealm
import com.infomaniak.mail.utils.extensions.update
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MergedContactController @Inject constructor(@UserInfoRealm private val userInfoRealm: Realm) {

    //region Queries
    private fun getMergedContactsQuery(): RealmQuery<MergedContact> {
        return userInfoRealm.query<MergedContact>()
            .sort(MergedContact::name.name)
            .sort(MergedContact::comesFromApi.name, Sort.DESCENDING)
    }
    //endregion

    //region Get data
    fun getSortedMergedContacts(): RealmResults<MergedContact> {
        return getMergedContactsQuery().find()
    }

    fun getMergedContactsAsync(): Flow<ResultsChange<MergedContact>> {
        return getMergedContactsQuery().asFlow()
    }
    //endregion

    //region Edit data
    suspend fun update(mergedContacts: List<MergedContact>) {
        SentryLog.d(RealmDatabase.TAG, "MergedContacts: Save new data")
        userInfoRealm.update<MergedContact>(mergedContacts)
    }
    //endregion
}
