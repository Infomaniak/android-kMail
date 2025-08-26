/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
import com.infomaniak.mail.data.models.addressBook.ContactGroup
import com.infomaniak.mail.di.UserInfoRealm
import com.infomaniak.mail.utils.extensions.update
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmQuery
import javax.inject.Inject

class ContactGroupController @Inject constructor(@UserInfoRealm private val userInfoRealm: Realm) {

    //region Queries
    private fun getGroupQuery(): RealmQuery<ContactGroup> {
        return userInfoRealm.query<ContactGroup>()
    }
    //endregion

    //region Get data
    fun getGroup() = getGroupQuery().find()
    //endregion

    //region Edit data
    suspend fun update(apiGroup: List<ContactGroup>) {
        SentryLog.d(RealmDatabase.TAG, "Group: Save new data")
        userInfoRealm.update<ContactGroup>(apiGroup)
    }
    //endregion
}
