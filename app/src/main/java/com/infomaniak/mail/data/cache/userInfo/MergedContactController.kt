/*
 * Infomaniak ikMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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

import android.util.Log
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.di.UserInfoRealm
import com.infomaniak.mail.utils.update
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MergedContactController @Inject constructor(@UserInfoRealm private val userInfoRealm: Realm) {

    //region Queries
    private fun getMergedContactsQuery(sorted: Boolean = false): RealmQuery<MergedContact> {
        return userInfoRealm.query<MergedContact>().apply {
            if (sorted) sort(MergedContact::name.name)
        }
    }
    //endregion

    //region Get data
    fun getMergedContacts(sorted: Boolean): RealmResults<MergedContact> {
        return getMergedContactsQuery(sorted).find()
    }

    fun getMergedContactsAsync(): Flow<ResultsChange<MergedContact>> {
        return getMergedContactsQuery().asFlow()
    }
    //endregion

    //region Edit data
    fun update(mergedContacts: List<MergedContact>) {
        SentryLog.d(RealmDatabase.TAG, "MergedContacts: Save new data")
        userInfoRealm.update<MergedContact>(mergedContacts)
    }
    //endregion
}
