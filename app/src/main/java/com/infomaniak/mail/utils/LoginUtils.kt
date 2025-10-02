/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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

import android.content.Context
import androidx.activity.result.ActivityResult
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.infomaniak.core.cancellable
import com.infomaniak.core.legacy.R
import com.infomaniak.core.legacy.models.ApiResponse
import com.infomaniak.core.legacy.models.user.User
import com.infomaniak.core.legacy.networking.HttpClient
import com.infomaniak.core.legacy.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.lib.login.InfomaniakLogin
import com.infomaniak.lib.login.InfomaniakLogin.ErrorStatus
import com.infomaniak.lib.login.InfomaniakLogin.TokenResult
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MainDispatcher
import com.infomaniak.mail.ui.login.LoginActivity
import com.infomaniak.mail.utils.Utils.MailboxErrorCode
import com.infomaniak.mail.utils.extensions.launchNoMailboxActivity
import com.infomaniak.mail.utils.extensions.launchNoValidMailboxesActivity
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ActivityScoped
class LoginUtils @Inject constructor(
    private val mailboxController: MailboxController,
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
        resetLoginButtons: () -> Unit,
    ) = with(fragment) {
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val authCode = result.data?.getStringExtra(InfomaniakLogin.CODE_TAG)
            val translatedError = result.data?.getStringExtra(InfomaniakLogin.ERROR_TRANSLATED_TAG)
            when {
                translatedError?.isNotBlank() == true -> showError(translatedError)
                authCode?.isNotBlank() == true -> authenticateUser(authCode, infomaniakLogin)
                else -> showError(requireContext().getString(R.string.anErrorHasOccurred))
            }
        }

        resetLoginButtons()
    }

    suspend fun Fragment.authenticateUser(
        token: ApiToken,
        infomaniakLogin: InfomaniakLogin,
        withRedirection: Boolean = true,
    ) {
        val context = requireContext()
        runCatching {
            when (val returnValue = LoginActivity.authenticateUser(token, mailboxController)) {
                is User -> {
                    if (withRedirection) context.loginSuccess(returnValue)
                    return
                }
                is MailboxErrorCode -> if (withRedirection) context.mailboxError(returnValue)
                is ApiResponse<*> -> context.apiError(returnValue)
                else -> context.otherError()
            }
            logout(infomaniakLogin, token)
        }.cancellable().onFailure { exception ->
            onAuthenticateUserError(ErrorStatus.UNKNOWN)
            SentryLog.e("authenticateUser", "Failure on getToken", exception)
        }
    }

    private fun Fragment.authenticateUser(authCode: String, infomaniakLogin: InfomaniakLogin) {
        lifecycleScope.launch {
            runCatching {
                val tokenResult = infomaniakLogin.getToken(
                    okHttpClient = HttpClient.okHttpClientNoTokenInterceptor,
                    code = authCode,
                )
                when (tokenResult) {
                    is TokenResult.Success -> onAuthenticateUserSuccess(tokenResult.apiToken, infomaniakLogin)
                    is TokenResult.Error -> onAuthenticateUserError(tokenResult.errorStatus)
                }
            }.cancellable().onFailure { exception ->
                onAuthenticateUserError(ErrorStatus.UNKNOWN)
                SentryLog.e("authenticateUser", "Failure on getToken", exception)
            }
        }
    }

    private fun Fragment.onAuthenticateUserSuccess(
        apiToken: ApiToken,
        infomaniakLogin: InfomaniakLogin,
    ) = lifecycleScope.launch {

        val context = requireContext()

        when (val returnValue = LoginActivity.authenticateUser(apiToken, mailboxController)) {
            is User -> return@launch context.loginSuccess(returnValue)
            is MailboxErrorCode -> context.mailboxError(returnValue)
            is ApiResponse<*> -> context.apiError(returnValue)
            else -> context.otherError()
        }

        logout(infomaniakLogin, apiToken)
    }

    private suspend fun Context.loginSuccess(user: User) {
        trackAccountEvent(MatomoName.LoggedIn)
        ioDispatcher {
            mailboxController.getFirstValidMailbox(user.id)?.mailboxId?.let { AccountUtils.currentMailboxId = it }
        }
        AccountUtils.reloadApp?.invoke()
    }

    private fun Context.mailboxError(errorCode: MailboxErrorCode) {
        when (errorCode) {
            MailboxErrorCode.NO_MAILBOX -> launchNoMailboxActivity()
            MailboxErrorCode.NO_VALID_MAILBOX -> launchNoValidMailboxesActivity()
        }
    }

    private suspend fun Context.apiError(apiResponse: ApiResponse<*>) = withContext(mainDispatcher) {
        showError(getString(apiResponse.translateError()))
    }

    private suspend fun Context.otherError() = withContext(mainDispatcher) {
        showError(getString(R.string.anErrorHasOccurred))
    }

    private suspend fun logout(infomaniakLogin: InfomaniakLogin, apiToken: ApiToken) {
        runCatching {
            val errorStatus = infomaniakLogin.deleteToken(
                okHttpClient = HttpClient.okHttpClientNoTokenInterceptor,
                token = apiToken,
            )

            if (errorStatus != null) {
                SentryLog.e("DeleteTokenError", "API response error $errorStatus")
            }
        }.cancellable().onFailure {
            SentryLog.e("DeleteTokenError", "Failure on deleteToken")
        }
    }

    private fun Fragment.onAuthenticateUserError(errorStatus: ErrorStatus) {
        val errorResId = when (errorStatus) {
            ErrorStatus.SERVER -> R.string.serverError
            ErrorStatus.CONNECTION -> R.string.connectionError
            else -> R.string.anErrorHasOccurred
        }
        showError(requireContext().getString(errorResId))
    }
}
