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
import com.infomaniak.core.auth.models.user.User
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.mail.data.cache.appSettings.AppSettingsController
import com.infomaniak.mail.utils.AccountUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

object TokenInterceptorListenerProvider {
    fun tokenInterceptorListener(
        refreshTokenError: (User) -> Unit,
        globalCoroutineScope: CoroutineScope,
    ) = object : TokenInterceptorListener {
        val userTokenFlow by lazy { AppSettingsController.getCurrentUserIdFlow().mapToApiToken(globalCoroutineScope) }

        override suspend fun onRefreshTokenSuccess(apiToken: ApiToken) {
            if (AccountUtils.currentUser == null) AccountUtils.requestCurrentUser()
            AccountUtils.setUserToken(AccountUtils.currentUser!!, apiToken)
        }

        override suspend fun onRefreshTokenError() {
            if (AccountUtils.currentUser == null) AccountUtils.requestCurrentUser()
            refreshTokenError(AccountUtils.currentUser!!)
        }

        override suspend fun getApiToken(): ApiToken? = userTokenFlow.first()

        override fun getCurrentUserId(): Int = AccountUtils.currentUserId
    }
}
