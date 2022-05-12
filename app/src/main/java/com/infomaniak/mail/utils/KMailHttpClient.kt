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
package com.infomaniak.mail.utils

import androidx.collection.ArrayMap
import com.infomaniak.lib.core.models.user.User
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

object KMailHttpClient {

    private val mutex = Mutex()
    private var httpClientMap: ArrayMap<Pair<Int, Long?>, OkHttpClient> = ArrayMap()

    var onRefreshTokenError: ((user: User) -> Unit)? = null

    suspend fun getHttpClient(userId: Int, timeout: Long? = null): OkHttpClient {
        mutex.withLock {
            var httpClient = httpClientMap[Pair(userId, timeout)]
            if (httpClient == null) {
                httpClient = AccountUtils.getHttpClientUser(userId, timeout) {
                    onRefreshTokenError?.invoke(it)
                }
                httpClientMap[Pair(userId, timeout)] = httpClient
            }
            return httpClient
        }
    }
}
