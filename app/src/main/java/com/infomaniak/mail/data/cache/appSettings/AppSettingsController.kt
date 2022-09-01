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
package com.infomaniak.mail.data.cache.appSettings

import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.AppSettings
import io.realm.kotlin.ext.query

object AppSettingsController {

    //region Get data
    fun getAppSettings(): AppSettings = with(RealmDatabase.appSettings) {
        query<AppSettings>().first().find() ?: writeBlocking { copyToRealm(AppSettings()) }
    }
    //endregion

    //region Edit data
    fun updateAppSettings(onUpdate: (appSettings: AppSettings) -> Unit) {
        RealmDatabase.appSettings.writeBlocking { findLatest(getAppSettings())?.let(onUpdate) }
    }

    fun removeAppSettings() {
        RealmDatabase.appSettings.writeBlocking { findLatest(getAppSettings())?.let(::delete) }
    }
    //endregion
}
