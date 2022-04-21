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
package com.infomaniak.mail.data.models

import com.infomaniak.mail.utils.Realms
import io.realm.RealmObject
import io.realm.query

@Suppress("PropertyName")
class AppSettings : RealmObject {
    var _currentUserId: Int = -1
    var _currentMailboxId: Int = -1

    companion object {
        fun getAppSettings(): AppSettings = with(Realms.appSettings) {
            query<AppSettings>().first().find() ?: writeBlocking { copyToRealm(AppSettings()) }
        }

        fun updateAppSettings(onUpdate: (appSettings: AppSettings) -> Unit) {
            Realms.appSettings.writeBlocking { findLatest(getAppSettings())?.let(onUpdate) }
        }

        fun removeAppSettings() {
            Realms.appSettings.writeBlocking { findLatest(getAppSettings())?.let(::delete) }
        }
    }
}