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

import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.user.UserPreferences
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.query

// TODO: Will we need this file in the future? It seems to be unused for now.
object UserPreferencesController {

    //region Get data
    fun getUserPreferences(realm: MutableRealm? = null): UserPreferences {
        val block: (MutableRealm) -> UserPreferences = {
            it.query<UserPreferences>().first().find() ?: it.copyToRealm(UserPreferences())
        }
        return realm?.let(block) ?: RealmDatabase.userInfos.writeBlocking(block)
    }
    //endregion

    //region Edit data
    fun updateUserPreferences(onUpdate: (userPreferences: UserPreferences) -> Unit) {
        RealmDatabase.userInfos.writeBlocking { onUpdate(getUserPreferences(this)) }
    }

    fun removeUserPreferences() {
        RealmDatabase.userInfos.writeBlocking { getUserPreferences(this).let(::delete) }
    }
    //endregion
}
