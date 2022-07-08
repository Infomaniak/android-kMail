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
package com.infomaniak.mail.ui.main.menu.user

import androidx.lifecycle.*
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.infomaniak.lib.core.BuildConfig
import com.infomaniak.lib.core.auth.TokenAuthenticator
import com.infomaniak.lib.core.auth.TokenInterceptor
import com.infomaniak.lib.core.auth.TokenInterceptorListener
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.MailboxInfosController
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.utils.AccountUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class SwitchUserViewModel : ViewModel() {

    val accounts = MutableLiveData<List<Pair<User, List<Mailbox>>>?>(null)

    fun loadAccounts(lifecycleOwner: LifecycleOwner) {

        AccountUtils.getAllUsers().observeOnce(lifecycleOwner) { users ->
            viewModelScope.launch(Dispatchers.IO) {

                users
                    .map { user -> user to MailboxInfoController.getMailboxesSync(user.id) }
                    .also(accounts::postValue)

                users
                    .mapNotNull { user ->
                        val okHttpClient = createOkHttpClientForSpecificUser(user)
                        ApiRepository.getMailboxes(okHttpClient).data
                            ?.map {
                                val quotas = if (it.isLimited) ApiRepository.getQuotas(it.hostingId, it.mailbox).data else null
                                it.initLocalValues(user.id, quotas)
                            }
                            ?.let { user to it }
                    }
                    .also { accounts::postValue }
            }
        }
    }

    private fun <T> LiveData<T>.observeOnce(lifecycleOwner: LifecycleOwner, observer: Observer<T>) {
        observe(lifecycleOwner, object : Observer<T> {
            override fun onChanged(t: T?) {
                observer.onChanged(t)
                removeObserver(this)
            }
        })
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
}
