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
package com.infomaniak.mail

import com.infomaniak.core.auth.TokenInterceptorListener
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.mail.data.cache.appSettings.AppSettingsController
import com.infomaniak.mail.utils.AccountUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import com.infomaniak.lib.core.auth.TokenInterceptorListener as LegacyTokenInterceptorListener

/**
 * Used for the MainApplication. This object contains all the the logic to define a [TokenInterceptorListener] for authenticated
 * okHttp clients.
 *
 * For now we have two interfaces TokenInterceptorListener. One is the [LegacyTokenInterceptorListener] that comes from the legacy
 * Core code and has not been replaced yet, while the other is the [TokenInterceptorListener] that comes from the new Core:Auth.
 * The new one has only been used to replace the auth of coil3 http clients for now.
 */
object TokenInterceptorListenerProvider {
    // Common implementation of both new and legacy TokenInterceptorListener
    private fun getCurrentUserIdFlow() = AppSettingsController.getCurrentUserIdFlow()

    private suspend fun onRefreshTokenSuccessCommon(apiToken: ApiToken) {
        if (AccountUtils.currentUser == null) AccountUtils.requestCurrentUser()
        AccountUtils.setUserToken(AccountUtils.currentUser!!, apiToken)
    }

    private suspend fun onRefreshTokenErrorCommon(refreshTokenError: (User) -> Unit) {
        if (AccountUtils.currentUser == null) AccountUtils.requestCurrentUser()
        refreshTokenError(AccountUtils.currentUser!!)
    }

    private suspend fun getApiTokenCommon(userTokenFlow: SharedFlow<ApiToken?>): ApiToken? = userTokenFlow.first()
    private fun getCurrentUserIdCommon(): Int = AccountUtils.currentUserId

    // Actual implementation of the legacy TokenInterceptorListener
    fun legacyTokenInterceptorListener(
        refreshTokenError: (User) -> Unit,
        globalCoroutineScope: CoroutineScope,
    ): LegacyTokenInterceptorListener = object : LegacyTokenInterceptorListener {
        val userTokenFlow by lazy { getCurrentUserIdFlow().mapToApiToken(globalCoroutineScope) }
        override suspend fun onRefreshTokenSuccess(apiToken: ApiToken) = onRefreshTokenSuccessCommon(apiToken)
        override suspend fun onRefreshTokenError() = onRefreshTokenErrorCommon(refreshTokenError)
        override suspend fun getUserApiToken(): ApiToken? = getApiTokenCommon(userTokenFlow)
        override fun getCurrentUserId(): Int = getCurrentUserIdCommon()
    }

    // Actual implementation of the new TokenInterceptorListener
    fun tokenInterceptorListener(
        refreshTokenError: (User) -> Unit,
        globalCoroutineScope: CoroutineScope,
    ) = object : TokenInterceptorListener {
        val userTokenFlow by lazy { getCurrentUserIdFlow().mapToApiToken(globalCoroutineScope) }
        override suspend fun onRefreshTokenSuccess(apiToken: ApiToken) = onRefreshTokenSuccessCommon(apiToken)
        override suspend fun onRefreshTokenError() = onRefreshTokenErrorCommon(refreshTokenError)
        override suspend fun getApiToken(): ApiToken? = getApiTokenCommon(userTokenFlow)
        override fun getCurrentUserId(): Int = getCurrentUserIdCommon()
    }
}
