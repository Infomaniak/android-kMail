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
import com.infomaniak.core.auth.TokenAuthenticator.Companion.changeAccessToken
import com.infomaniak.core.auth.models.user.User
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
import com.infomaniak.lib.login.InfomaniakLogin.ErrorStatus
import com.infomaniak.lib.login.InfomaniakLogin.TokenResult
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MainDispatcher
import com.infomaniak.mail.ui.login.LoginActivity
import com.infomaniak.mail.utils.Utils.MailboxErrorCode
import com.infomaniak.mail.utils.extensions.launchNoMailboxActivity
import com.infomaniak.mail.utils.extensions.launchNoValidMailboxesActivity
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.invoke
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

    suspend fun handleWebViewLoginResult(
        context: Context,
        result: ActivityResult,
        infomaniakLogin: InfomaniakLogin,
        resetLoginButtons: () -> Unit,
    ) {
        run {
            val authCodeResult = result.toAuthCodeResult(context)
            when (authCodeResult) {
                is AuthCodeResult.Error -> {
                    showError(authCodeResult.message)
                    return@run
                }
                is AuthCodeResult.Canceled -> return@run
                is AuthCodeResult.Success -> Unit
            }

            val tokenResult = infomaniakLogin.getToken(okHttpClient = HttpClient.okHttpClient, code = authCodeResult.code)
            when (tokenResult) {
                is TokenResult.Error -> {
                    context.showUserAuthenticationError(tokenResult.errorStatus)
                    return@run
                }
                is TokenResult.Success -> Unit
            }

            val userResult = authenticateUsers(listOf(tokenResult.apiToken)).single()
            when (userResult) {
                is UserResult.Failure -> {
                    context.apiError(userResult.apiResponse)
                    return@run
                }
                is UserResult.Success -> Unit
            }

            val loginOutcome = fetchMailboxes(listOf(userResult.user)).single()
            loginOutcome.handle(context, infomaniakLogin)
        }

        resetLoginButtons()
    }

    suspend fun authenticateUsers(apiTokens: List<ApiToken>): List<UserResult> = apiTokens.map { apiToken ->
        runCatching { authenticateUserShared(apiToken) }.getOrDefault(UserResult.Failure.Unknown)
    }

    private suspend fun authenticateUserShared(apiToken: ApiToken): UserResult {
        if (AccountUtils.getUserById(apiToken.userId) != null) return UserResult.Failure(
            getErrorResponse(InternalTranslatedErrorCode.UserAlreadyPresent)
        )

        val okhttpClient = HttpClient.okHttpClient.newBuilder().addInterceptor { chain ->
            val newRequest = changeAccessToken(chain.request(), apiToken)
            chain.proceed(newRequest)
        }.build()

        val userProfileResponse = Dispatchers.IO { ApiRepository.getUserProfile(okhttpClient) }

        if (userProfileResponse.result == ApiResponseStatus.ERROR) return UserResult.Failure(userProfileResponse)
        if (userProfileResponse.data == null) return UserResult.Failure.Unknown

        val user = userProfileResponse.data!!.apply {
            this.apiToken = apiToken
            this.organizations = arrayListOf()
        }

        return UserResult.Success(user)
    }

    private suspend fun fetchMailboxes(users: List<User>): List<LoginOutcome> = users.map { user ->
        val mailboxFetchResult = runCatching {
            LoginActivity.fetchMailbox(user, mailboxController)
        }.getOrDefault(LoginOutcome.Failure.Other(user.apiToken))

        computeLoginOutcome(user.apiToken, mailboxFetchResult)
    }

    private fun getErrorResponse(error: ErrorCodeTranslated): ApiResponse<Any> {
        return ApiResponse(result = ApiResponseStatus.ERROR, error = error.toApiError())
    }

    suspend fun handleApiTokens(tokens: List<ApiToken>): List<LoginOutcome> = tokens.map { token ->
        authenticateUser(token)
    }

    private fun ActivityResult.toAuthCodeResult(context: Context): AuthCodeResult {
        if (resultCode != AppCompatActivity.RESULT_OK) return AuthCodeResult.Canceled

        val authCode = data?.getStringExtra(InfomaniakLogin.CODE_TAG)
        val translatedError = data?.getStringExtra(InfomaniakLogin.ERROR_TRANSLATED_TAG)

        return when {
            translatedError?.isNotBlank() == true -> AuthCodeResult.Error(translatedError)
            authCode?.isNotBlank() == true -> AuthCodeResult.Success(authCode)
            else -> AuthCodeResult.Error(context.getString(R.string.anErrorHasOccurred))
        }
    }

    private suspend fun authenticateUser(token: ApiToken): LoginOutcome {
        return runCatching {
            computeLoginOutcome(token, LoginActivity.authenticateUser(token, this.mailboxController))
        }.cancellable().onFailure { exception ->
            SentryLog.e("authenticateUser", "Failure on getToken", exception)
        }.getOrElse { LoginOutcome.Failure.Other(token) }
    }

    private fun computeLoginOutcome(apiToken: ApiToken, mailboxFetchResult: Any): LoginOutcome {
        return when (mailboxFetchResult) {
            is User -> LoginOutcome.Success(mailboxFetchResult, apiToken)
            is MailboxErrorCode -> LoginOutcome.Failure.NoMailbox(mailboxFetchResult, apiToken)
            is ApiResponse<*> -> LoginOutcome.Failure.ApiError(mailboxFetchResult, apiToken)
            else -> LoginOutcome.Failure.Other(apiToken)
        }
    }

    suspend fun LoginOutcome.handle(context: Context, infomaniakLogin: InfomaniakLogin) {
        when (this) {
            is LoginOutcome.Success -> return loginSuccess(user)
            is LoginOutcome.Failure.NoMailbox -> context.mailboxError(errorCode)
            is LoginOutcome.Failure.ApiError -> context.apiError(apiResponse)
            is LoginOutcome.Failure.Other -> context.otherError()
        }

        logout(infomaniakLogin, apiToken)
    }

    private suspend fun loginSuccess(user: User) {
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

    private fun Context.showUserAuthenticationError(errorStatus: ErrorStatus) {
        val errorResId = when (errorStatus) {
            ErrorStatus.SERVER -> R.string.serverError
            ErrorStatus.CONNECTION -> R.string.connectionError
            else -> R.string.anErrorHasOccurred
        }
        showError(getString(errorResId))
    }
}

private sealed interface AuthCodeResult {
    data class Success(val code: String) : AuthCodeResult
    data class Error(val message: String) : AuthCodeResult
    data object Canceled : AuthCodeResult
}

sealed class LoginOutcome(val initiatesNavigation: Boolean) {
    abstract val apiToken: ApiToken

    data class Success(val user: User, override val apiToken: ApiToken) : LoginOutcome(initiatesNavigation = true)

    sealed class Failure(initiatesNavigation: Boolean) : LoginOutcome(initiatesNavigation) {
        data class NoMailbox(
            val errorCode: MailboxErrorCode,
            override val apiToken: ApiToken,
        ) : Failure(initiatesNavigation = true)

        data class ApiError(
            val apiResponse: ApiResponse<*>,
            override val apiToken: ApiToken,
        ) : Failure(initiatesNavigation = false)

        data class Other(override val apiToken: ApiToken) : Failure(initiatesNavigation = false)
    }
}

sealed interface UserResult {
    data class Success(val user: User) : UserResult
    open class Failure(val apiResponse: ApiResponse<*>) : UserResult {
        data object Unknown : Failure(
            ApiResponse<Unit>(
                result = ApiResponseStatus.ERROR,
                error = InternalTranslatedErrorCode.UnknownError.toApiError()
            )
        )
    }
}
