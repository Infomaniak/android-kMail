/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.mail.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadThreadsStatusManager @Inject constructor() {
    /**
     * A StateFlow that emits if the app is downloading threads.
     * It starts collecting immediately and keeps the latest value in memory.
     */
    private val _isDownloading = MutableStateFlow(false)
    private val scope = CoroutineScope(Dispatchers.Default)
    @OptIn(FlowPreview::class)
    val isDownloading: StateFlow<Boolean> = _isDownloading
        .debounce(50)
        .stateIn(
            scope = scope,
            started = SharingStarted.Lazily,
            initialValue = false
        )

    fun updateState(isDownloading: Boolean) {
        _isDownloading.value = isDownloading
    }
}
