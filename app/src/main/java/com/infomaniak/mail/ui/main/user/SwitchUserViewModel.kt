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
import com.infomaniak.mail.data.cache.mailboxInfos.MailboxController
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.utils.AccountUtils
import io.realm.kotlin.notifications.ResultsChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class SwitchUserViewModel : ViewModel() {

    fun fetchAccounts(users: List<User>) = viewModelScope.launch(Dispatchers.IO) {
        users.forEach { user ->
            val okHttpClient = createOkHttpClientForSpecificUser(user)
            ApiRepository.getMailboxes(okHttpClient).data
                ?.map {
                    val quotas = if (it.isLimited) ApiRepository.getQuotas(it.hostingId, it.mailbox).data else null
                    it.initLocalValues(user.id, quotas)
                }
                ?.let(MailboxController::upsertMailboxes)
        }
    }

    fun accounts(users: List<User>): LiveData<List<Pair<User, List<Mailbox>>>> = liveData(Dispatchers.IO) {
        val flows = users.map { user -> MailboxController.getMailboxesAsync(user.id) }
        emitSource(
            combine(flows) { mailboxesFlows ->
                transformMailboxesFlowsIntoAccounts(mailboxesFlows, users).toList()
            }.asLiveData()
        )
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

    private fun transformMailboxesFlowsIntoAccounts(
        mailboxesFlows: Array<ResultsChange<Mailbox>>,
        users: List<User>,
    ): MutableList<Pair<User, List<Mailbox>>> {
        return mutableListOf<Pair<User, List<Mailbox>>>().apply {
            mailboxesFlows.forEach { flow ->
                val mailboxes = flow.list
                val userId = mailboxes.firstOrNull()?.userId
                users.find { it.id == userId }?.let { user ->
                    add(user to mailboxes)
                }
            }
        }
    }
}
