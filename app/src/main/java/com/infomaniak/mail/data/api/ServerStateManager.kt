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
package com.infomaniak.mail.data.api

import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.models.exceptions.ServerErrorException
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.mailbox.Mailbox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerStateManager @Inject constructor() {
    private val _isServerAvailable = MutableStateFlow(true)
    val isServerAvailable: StateFlow<Boolean> = _isServerAvailable.asStateFlow()

    private var mailboxAvailable = true
    private var folderAvailable = true

    suspend fun getMailboxes(okHttpClient: OkHttpClient? = null): ApiResponse<List<Mailbox>> {
        return handleResponse(ApiRepository.getMailboxes(okHttpClient)) { mailboxAvailable = it }
    }

    suspend fun getFolders(mailboxUuid: String): ApiResponse<List<Folder>> {
        return handleResponse(ApiRepository.getFolders(mailboxUuid)) { folderAvailable = it }
    }

    private fun <T> handleResponse(
        response: ApiResponse<T>,
        onAvailabilityChanged: (Boolean) -> Unit,
    ): ApiResponse<T> = response.also {
        onAvailabilityChanged(it.error?.exception !is ServerErrorException)
        updateServerAvailability()
    }

    private fun updateServerAvailability() {
        _isServerAvailable.value = mailboxAvailable && folderAvailable
    }
}

