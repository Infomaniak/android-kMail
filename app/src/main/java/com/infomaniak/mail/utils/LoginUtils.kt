/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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

import androidx.activity.result.ActivityResult
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.infomaniak.lib.core.R
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.lib.login.InfomaniakLogin
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

    private fun Fragment.authenticateUser(authCode: String, infomaniakLogin: InfomaniakLogin) {
        lifecycleScope.launch(ioDispatcher) {
            infomaniakLogin.getToken(
                okHttpClient = HttpClient.okHttpClientNoTokenInterceptor,
                code = authCode,
                onSuccess = { onAuthenticateUserSuccess(it, infomaniakLogin) },
                onError = { onAuthenticateUserError(it) },
            )
        }
    }

    private fun Fragment.onAuthenticateUserSuccess(
        apiToken: ApiToken,
        infomaniakLogin: InfomaniakLogin,
    ) = lifecycleScope.launch(ioDispatcher) {

        val context = requireContext()

        suspend fun loginSuccess(user: User) {
            context.trackAccountEvent("loggedIn")
            mailboxController.getFirstValidMailbox(user.id)?.mailboxId?.let { AccountUtils.currentMailboxId = it }
            AccountUtils.reloadApp?.invoke()
        }

        suspend fun mailboxError(errorCode: MailboxErrorCode) = withContext(mainDispatcher) {
            when (errorCode) {
                MailboxErrorCode.NO_MAILBOX -> context.launchNoMailboxActivity()
                MailboxErrorCode.NO_VALID_MAILBOX -> context.launchNoValidMailboxesActivity()
            }
        }

        suspend fun apiError(apiResponse: ApiResponse<*>) = withContext(mainDispatcher) {
            showError(context.getString(apiResponse.translatedError))
        }

        suspend fun otherError() = withContext(mainDispatcher) {
            showError(context.getString(R.string.anErrorHasOccurred))
        }

        when (val returnValue = LoginActivity.authenticateUser(context, apiToken, mailboxController)) {
            is User -> return@launch loginSuccess(returnValue)
            is MailboxErrorCode -> mailboxError(returnValue)
            is ApiResponse<*> -> apiError(returnValue)
            else -> otherError()
        }

        infomaniakLogin.deleteToken(
            okHttpClient = HttpClient.okHttpClientNoTokenInterceptor,
            token = apiToken,
            onError = { SentryLog.i("DeleteTokenError", "API response error: $it") },
        )
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
