/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025-2026 Infomaniak Network SA
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
package com.infomaniak.mail.ui.sync.discovery

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.preferencesDataStore
import com.infomaniak.core.sentry.SentryLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("UNCHECKED_CAST")
@Singleton
class SyncDiscoveryRepository @Inject constructor(private val context: Context) {

    private val Context.dataStore by preferencesDataStore(
        name = DATA_STORE_NAME,
        corruptionHandler = ReplaceFileCorruptionHandler {
            // If a corruption occurs, the user probably had time to see the sync discovery, no need to start showing it again
            preferencesOf(NUMBER_OF_RETRY_KEY to 0)
        },
    )

    fun <T> flowOf(key: Preferences.Key<T>) = context.dataStore.data.map { it[key] ?: (getDefaultValue(key) as T) }

    suspend fun <T> getValue(key: Preferences.Key<T>) = runCatching {
        flowOf(key).first()
    }.getOrElse { exception ->
        SentryLog.e(TAG, "Error while trying to get value from DataStore for key : $key", exception)
        getDefaultValue(key) as T
    }

    suspend fun <T> setValue(key: Preferences.Key<T>, value: T) {
        runCatching {
            context.dataStore.edit { it[key] = value }
        }.onFailure { exception ->
            SentryLog.e(TAG, "Error while trying to set value into DataStore for key : $key", exception)
        }
    }

    private fun <T> getDefaultValue(key: Preferences.Key<T>) = when (key) {
        APP_LAUNCHES_KEY -> DEFAULT_APP_LAUNCHES
        NUMBER_OF_RETRY_KEY -> DEFAULT_NUMBER_OF_RETRY
        else -> throw IllegalArgumentException("Unknown Preferences.Key")
    }

    companion object {

        const val DEFAULT_APP_LAUNCHES = 50
        const val DEFAULT_NUMBER_OF_RETRY = 3

        val APP_LAUNCHES_KEY = intPreferencesKey("appLaunchesKey")
        val NUMBER_OF_RETRY_KEY = intPreferencesKey("retryKey")

        private const val DATA_STORE_NAME = "SyncDiscoveryDataStore"

        private const val TAG = "SyncDiscoveryRepository"
    }
}
