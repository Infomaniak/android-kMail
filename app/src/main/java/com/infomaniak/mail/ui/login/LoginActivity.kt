/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.infomaniak.core.auth.TokenAuthenticator.Companion.changeAccessToken
import com.infomaniak.core.auth.models.user.User
import com.infomaniak.core.legacy.utils.Utils.lockOrientationForSmallScreens
import com.infomaniak.core.network.api.ApiController.toApiError
import com.infomaniak.core.network.api.InternalTranslatedErrorCode
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.models.ApiResponseStatus
import com.infomaniak.core.network.networking.HttpClient
import com.infomaniak.core.network.utils.ErrorCodeTranslated
import com.infomaniak.core.twofactorauth.front.TwoFactorAuthApprovalAutoManagedBottomSheet
import com.infomaniak.core.twofactorauth.front.addComposeOverlay
import com.infomaniak.lib.login.InfomaniakLogin
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackDestination
import com.infomaniak.mail.MatomoMail.trackScreen
import com.infomaniak.mail.MatomoMail.trackUserInfo
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.databinding.ActivityLoginBinding
import com.infomaniak.mail.twoFactorAuthManager
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.Utils.MailboxErrorCode
import com.infomaniak.mail.utils.Utils.openShortcutHelp
import com.infomaniak.mail.utils.extensions.getInfomaniakLogin
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.invoke

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private val binding by lazy { ActivityLoginBinding.inflate(layoutInflater) }

    private val navController by lazy {
        (supportFragmentManager.findFragmentById(R.id.loginHostFragment) as NavHostFragment).navController
    }

    lateinit var infomaniakLogin: InfomaniakLogin

    private val navigationArgs: LoginActivityArgs? by lazy { intent?.extras?.let { LoginActivityArgs.fromBundle(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        lockOrientationForSmallScreens()

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false

        setContentView(binding.root)
        addComposeOverlay { TwoFactorAuthApprovalAutoManagedBottomSheet(twoFactorAuthManager) }

        infomaniakLogin = getInfomaniakLogin()

        setupNavController()

        handleHelpShortcut()

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

    private fun handleHelpShortcut() {
        navigationArgs?.isHelpShortcutPressed?.let { isHelpShortcutPressed ->
            if (isHelpShortcutPressed) {
                openShortcutHelp(context = this)
            }
        }
    }

    companion object {
        suspend fun fetchMailbox(user: User, mailboxController: MailboxController): Any {
            val okhttpClient = HttpClient.okHttpClient.newBuilder().addInterceptor { chain ->
                val newRequest = changeAccessToken(chain.request(), user.apiToken)
                chain.proceed(newRequest)
            }.build()

            val apiResponse = Dispatchers.IO { ApiRepository.getMailboxes(okhttpClient) }

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
    }
}
