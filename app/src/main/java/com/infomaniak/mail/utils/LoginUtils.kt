/*
 * Infomaniak ikMail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.infomaniak.lib.core.R
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.clearStack
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.lib.login.InfomaniakLogin
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MainDispatcher
import com.infomaniak.mail.ui.login.LoginActivity
import com.infomaniak.mail.ui.login.NoMailboxActivity
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ActivityScoped
class LoginUtils @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) {

    lateinit var showError: (String) -> Unit

    fun initShowError(showError: (String) -> Unit) {
        this.showError = showError
    }

    fun handleWebViewLoginResult(
        fragment: Fragment,
        result: ActivityResult,
        infomaniakLogin: InfomaniakLogin,
        onFailedLogin: () -> Unit
    ) = with(fragment) {
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val authCode = result.data?.getStringExtra(InfomaniakLogin.CODE_TAG)
            val translatedError = result.data?.getStringExtra(InfomaniakLogin.ERROR_TRANSLATED_TAG)
            when {
                translatedError?.isNotBlank() == true -> showError(translatedError)
                authCode?.isNotBlank() == true -> authenticateUser(authCode, infomaniakLogin)
                else -> showError(requireContext().getString(R.string.anErrorHasOccurred))
            }
        } else {
            onFailedLogin()
        }
    }

    private fun Fragment.authenticateUser(authCode: String, infomaniakLogin: InfomaniakLogin) =
        lifecycleScope.launch(ioDispatcher) {
            infomaniakLogin.getToken(
                okHttpClient = HttpClient.okHttpClientNoTokenInterceptor,
                code = authCode,
                onSuccess = { onAuthenticateUserSuccess(it) },
                onError = { onAuthenticateUserError(it) }
            )
        }

    private fun Fragment.onAuthenticateUserSuccess(apiToken: ApiToken) =
        lifecycleScope.launch(ioDispatcher) {
            when (val returnValue = LoginActivity.authenticateUser(requireContext(), apiToken)) {
                is User -> {
                    requireContext().trackAccountEvent("loggedIn")
                    AccountUtils.reloadApp?.invoke()
                }
                is Utils.MailboxErrorCode -> withContext(mainDispatcher) {
                    when (returnValue) {
                        Utils.MailboxErrorCode.NO_MAILBOX -> launchNoMailboxActivity()
                        Utils.MailboxErrorCode.NO_VALID_MAILBOX -> requireContext().launchNoValidMailboxesActivity()
                    }
                }
                is ApiResponse<*> -> withContext(mainDispatcher) {
                    showError(requireContext().getString(returnValue.translatedError))
                }
                else -> withContext(mainDispatcher) {
                    showError(requireContext().getString(R.string.anErrorHasOccurred))
                }
            }
        }

    private fun Fragment.launchNoMailboxActivity() {
        startActivity(Intent(requireContext(), NoMailboxActivity::class.java).clearStack())
    }

    private fun Fragment.onAuthenticateUserError(errorStatus: InfomaniakLogin.ErrorStatus) {
        val errorResId = when (errorStatus) {
            InfomaniakLogin.ErrorStatus.SERVER -> R.string.serverError
            InfomaniakLogin.ErrorStatus.CONNECTION -> R.string.connectionError
            else -> R.string.anErrorHasOccurred
        }
        showError(requireContext().getString(errorResId))
    }
}
