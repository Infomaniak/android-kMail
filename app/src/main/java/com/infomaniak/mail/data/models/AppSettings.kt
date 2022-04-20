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
import io.realm.query.RealmSingleQuery

@Suppress("PropertyName")
class AppSettings : RealmObject {

    var _appLaunchesCount: Int = 0
    var _appSecurityEnabled: Boolean = false
    var _currentUserId: Int = -1
    var _migrated: Boolean = false

    companion object {

        private fun getAppSettingsQuery(): RealmSingleQuery<AppSettings> = Realms.appSettings.query<AppSettings>().first()

        fun getAppSettings(): AppSettings = getAppSettingsQuery().find() ?: AppSettings()

        fun updateAppSettings(onUpdate: (appSettings: AppSettings) -> Unit) {
            Realms.appSettings.writeBlocking { findLatest(getAppSettings())?.let(onUpdate) }
        }

        fun removeAppSettings() {
            Realms.appSettings.writeBlocking { findLatest(getAppSettings())?.let(::delete) }
        }

        // TODO: The app crash at start if we uncomment this. Why?
        // var appLaunches: Int = getAppSettings()._appLaunchesCount
        //     set(value) {
        //         field = value
        //         GlobalScope.launch(Dispatchers.IO) {
        //             updateAppSettings { appSettings -> appSettings._appLaunchesCount = value }
        //         }
        //     }
        //
        // var appSecurityLock: Boolean = getAppSettings()._appSecurityEnabled
        //     set(value) {
        //         field = value
        //         GlobalScope.launch(Dispatchers.IO) {
        //             updateAppSettings { appSettings -> appSettings._appSecurityEnabled = value }
        //         }
        //     }
        //
        // var migrated: Boolean = getAppSettings()._migrated
        //     set(value) {
        //         field = value
        //         GlobalScope.launch(Dispatchers.IO) {
        //             updateAppSettings { appSettings -> appSettings._migrated = value }
        //         }
        //     }
    }
}