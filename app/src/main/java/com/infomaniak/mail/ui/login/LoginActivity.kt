/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
package com.infomaniak.mail.ui.login

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.ApiResponseStatus
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.Utils.lockOrientationForSmallScreens
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.lib.login.InfomaniakLogin
import com.infomaniak.mail.MatomoMail.trackDestination
import com.infomaniak.mail.MatomoMail.trackScreen
import com.infomaniak.mail.MatomoMail.trackUserInfo
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.databinding.ActivityLoginBinding
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.Utils.MailboxErrorCode
import com.infomaniak.mail.utils.getInfomaniakLogin
import dagger.hilt.android.AndroidEntryPoint
import com.infomaniak.lib.core.R as RCore

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private val binding by lazy { ActivityLoginBinding.inflate(layoutInflater) }

    private val navController by lazy {
        (supportFragmentManager.findFragmentById(R.id.loginHostFragment) as NavHostFragment).navController
    }

    lateinit var infomaniakLogin: InfomaniakLogin

    override fun onCreate(savedInstanceState: Bundle?) {
        lockOrientationForSmallScreens()

        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        infomaniakLogin = getInfomaniakLogin()

        setupNavController()

        trackScreen()
    }

    private fun setupNavController() {
        navController.addOnDestinationChangedListener { _, destination, arguments ->
            onDestinationChanged(destination, arguments)
        }
    }

    // This `SuppressLint` seems useless, but it's for the CI. Don't remove it.
    @SuppressLint("RestrictedApi")
    private fun onDestinationChanged(destination: NavDestination, arguments: Bundle?) {
        SentryDebug.addNavigationBreadcrumb(destination.displayName, arguments)
        trackDestination(destination)
    }

    companion object {

        fun authenticateUser(context: Context, apiToken: ApiToken, mailboxController: MailboxController): Any {
            if (AccountUtils.getUserById(apiToken.userId) != null) return getErrorResponse(RCore.string.errorUserAlreadyPresent)

            InfomaniakCore.bearerToken = apiToken.accessToken
            val userProfileResponse = ApiRepository.getUserProfile(HttpClient.okHttpClientNoTokenInterceptor)

            if (userProfileResponse.result == ApiResponseStatus.ERROR) return userProfileResponse
            if (userProfileResponse.data == null) return getErrorResponse(RCore.string.anErrorHasOccurred)

            val user = userProfileResponse.data!!.apply {
                this.apiToken = apiToken
                this.organizations = arrayListOf()
            }

            val apiResponse = ApiRepository.getMailboxes(HttpClient.okHttpClientNoTokenInterceptor)

            return when {
                !apiResponse.isSuccess() -> apiResponse
                apiResponse.data?.isEmpty() == true -> MailboxErrorCode.NO_MAILBOX
                else -> {
                    apiResponse.data?.let { mailboxes ->
                        context.trackUserInfo("nbMailboxes", mailboxes.count())
                        AccountUtils.addUser(user)
                        mailboxController.updateMailboxes(mailboxes)
                        return@let if (mailboxes.none { it.isValid }) MailboxErrorCode.NO_VALID_MAILBOX else user
                    } ?: run {
                        getErrorResponse(RCore.string.serverError)
                    }
                }
            }
        }

        private fun getErrorResponse(@StringRes text: Int): ApiResponse<Any> {
            return ApiResponse(result = ApiResponseStatus.ERROR, translatedError = text)
        }
    }
}
