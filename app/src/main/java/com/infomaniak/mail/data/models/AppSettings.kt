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

import com.infomaniak.mail.utils.RealmModules
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

open class AppSettings(
    var _appLaunchesCount: Int = 0,
    var _appSecurityEnabled: Boolean = false,
    var _currentUserId: Int = -1,
    var _migrated: Boolean = false,
    var _onlyWifiSync: Boolean = false,
) : RealmObject() {

    companion object {
        private const val DB_NAME = "AppSettings.realm"
        private val realmConfiguration: RealmConfiguration = RealmConfiguration.Builder().name(DB_NAME)
            .modules(RealmModules.AppSettingsModule())
            .build()

        private fun getRealmInstance() = Realm.getInstance(realmConfiguration)

        private fun getAppSettingsQuery(realm: Realm) = realm.where(AppSettings::class.java).findFirst()

        fun getAppSettings(): AppSettings {
            return getRealmInstance().use { realm ->
                getAppSettingsQuery(realm)?.let {
                    realm.copyFromRealm(it, 0)
                }
            } ?: AppSettings()
        }

        fun updateAppSettings(onUpdate: (appSettings: AppSettings) -> Unit) {
            return getRealmInstance().use { realm ->
                var appSettings = getAppSettingsQuery(realm)

                realm.executeTransaction {
                    if (appSettings == null) {
                        appSettings = it.copyToRealm(AppSettings())
                    }
                    onUpdate(appSettings!!)
                }
            }
        }

        fun removeAppSettings() {
            getRealmInstance().use { realm ->
                realm.executeTransaction {
                    it.where(AppSettings::class.java).findFirst()?.deleteFromRealm()
                }
            }
        }

        var appLaunches: Int = getAppSettings()._appLaunchesCount
            set(value) {
                field = value
                GlobalScope.launch(Dispatchers.IO) {
                    updateAppSettings { appSettings -> appSettings._appLaunchesCount = value }
                }
            }

        var appSecurityLock: Boolean = getAppSettings()._appSecurityEnabled
            set(value) {
                field = value
                GlobalScope.launch(Dispatchers.IO) {
                    updateAppSettings { appSettings -> appSettings._appSecurityEnabled = value }
                }
            }

        var migrated: Boolean = getAppSettings()._migrated
            set(value) {
                field = value
                GlobalScope.launch(Dispatchers.IO) {
                    updateAppSettings { appSettings -> appSettings._migrated = value }
                }
            }

        var onlyWifiSync: Boolean = getAppSettings()._onlyWifiSync
            set(value) {
                field = value
                GlobalScope.launch(Dispatchers.IO) {
                    updateAppSettings { appSettings -> appSettings._onlyWifiSync = value }
                }
            }
    }
}