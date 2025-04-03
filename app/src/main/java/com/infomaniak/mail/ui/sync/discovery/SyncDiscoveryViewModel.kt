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
package com.infomaniak.mail.ui.sync.discovery

import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.sync.discovery.SyncDiscoveryRepository.Companion.APP_LAUNCHES_KEY
import com.infomaniak.mail.ui.sync.discovery.SyncDiscoveryRepository.Companion.DEFAULT_APP_LAUNCHES
import com.infomaniak.mail.ui.sync.discovery.SyncDiscoveryRepository.Companion.NUMBER_OF_RETRY_KEY
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncDiscoveryViewModel @Inject constructor(
    private val syncDiscoveryRepository: SyncDiscoveryRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val appLaunches = syncDiscoveryRepository.flowOf(APP_LAUNCHES_KEY).distinctUntilChanged()
    private val numberOfRetry = syncDiscoveryRepository.flowOf(NUMBER_OF_RETRY_KEY).distinctUntilChanged()

    val canShowSyncDiscovery = appLaunches.combine(numberOfRetry) { appLaunches, numberOfRetry ->
        val showSyncDiscovery = appLaunches == 0 && numberOfRetry > 0

        if (showSyncDiscovery) {
            decrementNumberOfRetry()
            resetAppLaunches()
        }

        showSyncDiscovery
    }.asLiveData(viewModelScope.coroutineContext + ioDispatcher)

    fun <T> set(key: Preferences.Key<T>, value: T) = viewModelScope.launch(ioDispatcher) {
        syncDiscoveryRepository.setValue(key, value)
    }

    fun decrementAppLaunches() = viewModelScope.launch(ioDispatcher) {
        val appLaunches = syncDiscoveryRepository.getValue(APP_LAUNCHES_KEY)
        if (appLaunches > 0) set(APP_LAUNCHES_KEY, appLaunches - 1)
    }

    private fun decrementNumberOfRetry() = viewModelScope.launch(ioDispatcher) {
        val numberOfRetry = syncDiscoveryRepository.getValue(NUMBER_OF_RETRY_KEY)
        set(NUMBER_OF_RETRY_KEY, numberOfRetry - 1)
    }

    private fun resetAppLaunches() {
        set(APP_LAUNCHES_KEY, DEFAULT_APP_LAUNCHES)
    }
}
