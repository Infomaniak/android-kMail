/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.mail.data.cache.mailboxContent

import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.models.getMessages.GetMessagesByUidsResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class DelayApiCallManager @Inject constructor() {

    private var previousTime = 0L

    /**
     * Realm really doesn't like to be written on too frequently.
     * So we want to be sure that we don't write twice in less than 500 ms.
     * Appreciable side effect: it will also reduce the stress on the API.
     */
    suspend fun getMessagesByUids(
        scope: CoroutineScope,
        mailboxUuid: String,
        folderId: String,
        uids: List<Int>,
        okHttpClient: OkHttpClient?,
    ): ApiResponse<GetMessagesByUidsResult> {

        val duration = abs(System.currentTimeMillis() - previousTime)
        val delay = MAX_DELAY_BETWEEN_API_CALLS - duration

        if (delay > 0L) {
            delay(delay)
            scope.ensureActive()
        }

        previousTime = System.currentTimeMillis()

        return ApiRepository.getMessagesByUids(mailboxUuid, folderId, uids, okHttpClient)
    }

    companion object {
        private const val MAX_DELAY_BETWEEN_API_CALLS = 500L
    }
}
