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
import com.infomaniak.core.auth.TokenAuthenticator.Companion.changeAccessToken
import com.infomaniak.core.auth.models.UserLoginResult
import com.infomaniak.core.auth.models.user.User
import com.infomaniak.core.auth.utils.LoginUtils
import com.infomaniak.core.cancellable
import com.infomaniak.core.legacy.R
import com.infomaniak.core.network.api.ApiController.toApiError
import com.infomaniak.core.network.api.InternalTranslatedErrorCode
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.models.ApiResponseStatus
import com.infomaniak.core.network.networking.HttpClient
import com.infomaniak.core.network.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.core.network.utils.ErrorCodeTranslated
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.lib.login.InfomaniakLogin
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.MatomoMail.trackUserInfo
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MainDispatcher
import com.infomaniak.mail.utils.Utils.MailboxErrorCode
import com.infomaniak.mail.utils.extensions.launchNoMailboxActivity
import com.infomaniak.mail.utils.extensions.launchNoValidMailboxesActivity
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.invoke
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ActivityScoped
class LoginUtils @Inject constructor(
    private val mailboxController: MailboxController,
    @ActivityContext private val activityContext: Context, // Needs to be activity context in order to call startActivity
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) {

    lateinit var showError: (String) -> Unit

    fun initShowError(showError: (String) -> Unit) {
        this.showError = showError
    }

    suspend fun handleWebViewLoginResult(
        result: ActivityResult,
        infomaniakLogin: InfomaniakLogin,
        resetLoginButtons: () -> Unit,
    ) {
        val userResult = LoginUtils.getLoginResultAfterWebView(
            result = result,
            context = activityContext,
            infomaniakLogin = infomaniakLogin,
            userExistenceChecker = AccountUtils,
        )

        handleUserLoginResult(userResult, infomaniakLogin, resetLoginButtons)
    }

    suspend fun handleUserLoginResult(
        userLoginResult: UserLoginResult?,
        infomaniakLogin: InfomaniakLogin,
        resetLoginButtons: () -> Unit,
    ) {
        when (userLoginResult) {
            is UserLoginResult.Success -> {
                val loginOutcome = fetchMailboxes(listOf(userLoginResult.user)).single()
                loginOutcome.handleErrors(infomaniakLogin)
                loginOutcome.handleNavigation()

                if (loginOutcome is LoginOutcome.Failure) resetLoginButtons()
            }
            is UserLoginResult.Failure -> showError(userLoginResult.errorMessage)
            null -> Unit // User closed the webview without going through
        }

        if (userLoginResult !is UserLoginResult.Success) resetLoginButtons()
    }

    suspend fun fetchMailboxes(users: List<User>): List<LoginOutcome> = users.map { user ->
        val mailboxFetchResult = runCatching {
            fetchMailbox(user, mailboxController)
        }.cancellable().getOrDefault(LoginOutcome.Failure.Other(user.apiToken))

        computeLoginOutcome(user.apiToken, mailboxFetchResult)
    }

    suspend fun fetchMailbox(user: User, mailboxController: MailboxController): Any {
        val okhttpClient = HttpClient.okHttpClient.newBuilder().addInterceptor { chain ->
            val newRequest = changeAccessToken(chain.request(), user.apiToken)
            chain.proceed(newRequest)
        }.build()

        val apiResponse = ApiRepository.getMailboxes(okhttpClient)

        return when {
            !apiResponse.isSuccess() -> apiResponse
            apiResponse.data?.isEmpty() == true -> MailboxErrorCode.NO_MAILBOX
            else -> {
                apiResponse.data?.let { mailboxes ->
                    trackUserInfo(MatomoName.NbMailboxes, mailboxes.count())
                    AccountUtils.addUser(user)
                    mailboxController.updateMailboxes(mailboxes)
                    return@let if (mailboxes.none { it.isAvailable }) MailboxErrorCode.NO_VALID_MAILBOX else user
                } ?: run {
                    getErrorResponse(InternalTranslatedErrorCode.UnknownError)
                }
            }
        }
    }

    private fun getErrorResponse(error: ErrorCodeTranslated): ApiResponse<Any> {
        return ApiResponse(result = ApiResponseStatus.ERROR, error = error.toApiError())
    }

    private fun computeLoginOutcome(apiToken: ApiToken, mailboxFetchResult: Any): LoginOutcome {
        return when (mailboxFetchResult) {
            is User -> LoginOutcome.Success(mailboxFetchResult, apiToken)
            is MailboxErrorCode -> LoginOutcome.Failure.Mailbox(mailboxFetchResult, apiToken)
            is ApiResponse<*> -> LoginOutcome.Failure.ApiError(mailboxFetchResult, apiToken)
            else -> LoginOutcome.Failure.Other(apiToken)
        }
    }

    suspend fun LoginOutcome.handleErrors(infomaniakLogin: InfomaniakLogin) {
        when (this) {
            is LoginOutcome.Success, is LoginOutcome.Failure.Mailbox -> Unit
            is LoginOutcome.Failure.ApiError -> apiError(apiResponse)
            is LoginOutcome.Failure.Other -> otherError()
        }

        if (this is LoginOutcome.Failure) logout(infomaniakLogin, apiToken)
    }

    suspend fun LoginOutcome.handleNavigation() {
        when (this) {
            is LoginOutcome.Success -> return loginSuccess(user)
            is LoginOutcome.Failure.Mailbox -> mailboxError(errorCode)
            is LoginOutcome.Failure.ApiError, is LoginOutcome.Failure.Other -> Unit
        }
    }

    private suspend fun loginSuccess(user: User) {
        trackAccountEvent(MatomoName.LoggedIn)
        ioDispatcher {
            mailboxController.getFirstValidMailbox(user.id)?.mailboxId?.let { AccountUtils.currentMailboxId = it }
        }
        AccountUtils.reloadApp?.invoke()
    }

    private fun mailboxError(errorCode: MailboxErrorCode) {
        when (errorCode) {
            MailboxErrorCode.NO_MAILBOX -> activityContext.launchNoMailboxActivity()
            MailboxErrorCode.NO_VALID_MAILBOX -> activityContext.launchNoValidMailboxesActivity()
        }
    }

    suspend fun apiError(apiResponse: ApiResponse<*>) = withContext(mainDispatcher) {
        showError(activityContext.getString(apiResponse.translateError()))
    }

    private suspend fun otherError() = withContext(mainDispatcher) {
        showError(activityContext.getString(R.string.anErrorHasOccurred))
    }

    private suspend fun logout(infomaniakLogin: InfomaniakLogin, apiToken: ApiToken) {
        runCatching {
            val errorStatus = infomaniakLogin.deleteToken(
                okHttpClient = HttpClient.okHttpClient,
                token = apiToken,
            )

            if (errorStatus != null) {
                SentryLog.e("DeleteTokenError", "API response error $errorStatus")
            }
        }.cancellable().onFailure {
            SentryLog.e("DeleteTokenError", "Failure on deleteToken")
        }
    }
}

sealed interface LoginOutcome {
    val apiToken: ApiToken

    data class Success(val user: User, override val apiToken: ApiToken) : LoginOutcome

    sealed interface Failure : LoginOutcome {
        data class Mailbox(val errorCode: MailboxErrorCode, override val apiToken: ApiToken) : Failure
        data class ApiError(val apiResponse: ApiResponse<*>, override val apiToken: ApiToken) : Failure
        data class Other(override val apiToken: ApiToken) : Failure
    }
}
