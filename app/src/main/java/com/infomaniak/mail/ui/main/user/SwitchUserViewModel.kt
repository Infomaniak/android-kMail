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
package com.infomaniak.mail.ui.main.user

import androidx.lifecycle.*
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.infomaniak.lib.core.BuildConfig
import com.infomaniak.lib.core.auth.TokenAuthenticator
import com.infomaniak.lib.core.auth.TokenInterceptor
import com.infomaniak.lib.core.auth.TokenInterceptorListener
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.ui.main.user.SwitchUserAccountsAdapter.UiAccount
import com.infomaniak.mail.utils.AccountUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class SwitchUserViewModel : ViewModel() {

    fun observeAccounts(): LiveData<List<UiAccount>> = liveData(Dispatchers.IO) {

        val users = AccountUtils.getAllUsersSync()

        updateMailboxes(users)

        emitSource(MailboxController.getMailboxesAsync().map { mailboxes ->
            users.map { user ->
                UiAccount(user, mailboxes.list.filter { it.userId == user.id })
            }.sortAccounts()
        }.asLiveData())
    }

    private fun updateMailboxes(users: List<User>) = viewModelScope.launch(Dispatchers.IO) {
        users.forEach { user ->
            val okHttpClient = createOkHttpClientForSpecificUser(user)

            val apiMailboxes = ApiRepository.getMailboxes(okHttpClient).data
                ?.map { it.initLocalValues(user.id) }
                ?: emptyList()

            MailboxController.update(apiMailboxes, user.id)
        }
    }

    private fun createOkHttpClientForSpecificUser(user: User): OkHttpClient {

        val tokenInterceptorListener = object : TokenInterceptorListener {
            override suspend fun onRefreshTokenSuccess(apiToken: ApiToken) {
                AccountUtils.setUserToken(user, apiToken)
            }

            override suspend fun onRefreshTokenError() {
                // TODO?
            }

            override suspend fun getApiToken(): ApiToken = user.apiToken
        }

        return OkHttpClient.Builder()
            .apply {
                if (BuildConfig.DEBUG) addNetworkInterceptor(StethoInterceptor())
                addInterceptor(TokenInterceptor(tokenInterceptorListener))
                authenticator(TokenAuthenticator(tokenInterceptorListener))
            }.build()
    }

    private fun List<UiAccount>.sortAccounts(): MutableList<UiAccount> {
        return filter { it.user.id != AccountUtils.currentUserId }
            .toMutableList()
            .apply { this@sortAccounts.find { it.user.id == AccountUtils.currentUserId }?.let { add(0, it) } }
    }
}
