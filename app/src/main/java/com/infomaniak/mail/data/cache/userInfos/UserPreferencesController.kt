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

import com.infomaniak.mail.data.cache.RealmController
import com.infomaniak.mail.data.models.user.UserPreferences
import io.realm.kotlin.ext.query

// TODO: Will we need this file in the future? It seems to be unused for now.
// TODO: Refactor this Controller so it matches other Controllers format. This is still the old format.
object UserPreferencesController {

    fun getUserPreferences(): UserPreferences = with(RealmController.userInfos) {
        query<UserPreferences>().first().find() ?: writeBlocking { copyToRealm(UserPreferences()) }
    }

    fun updateUserPreferences(onUpdate: (userPreferences: UserPreferences) -> Unit): UserPreferences? {
        return RealmController.userInfos.writeBlocking { findLatest(getUserPreferences())?.also(onUpdate) }
    }

    fun removeUserPreferences() {
        RealmController.userInfos.writeBlocking { findLatest(getUserPreferences())?.let(::delete) }
    }
}
