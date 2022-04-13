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
package com.infomaniak.mail.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.R
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.clearStack
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.lib.login.InfomaniakLogin
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.databinding.ActivityLoginBinding
import com.infomaniak.mail.ui.main.MainActivity
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.showSnackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private lateinit var infomaniakLogin: InfomaniakLogin

    private val webViewLoginResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        with(result) {
            if (resultCode == RESULT_OK) {
                val authCode = data?.extras?.getString(InfomaniakLogin.CODE_TAG)
                val translatedError = data?.extras?.getString(InfomaniakLogin.ERROR_TRANSLATED_TAG)
                when {
                    translatedError?.isNotBlank() == true -> showError(translatedError)
                    authCode?.isNotBlank() == true -> authenticateUser(authCode)
                    else -> showError("anErrorHasOccurred 1")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        infomaniakLogin = InfomaniakLogin(context = this, appUID = BuildConfig.APPLICATION_ID, clientID = BuildConfig.CLIENT_ID)
        infomaniakLogin.startWebViewLogin(webViewLoginResultLauncher)
    }

    private fun authenticateUser(authCode: String) {
        lifecycleScope.launch {
            infomaniakLogin.getToken(
                okHttpClient = HttpClient.okHttpClientNoInterceptor,
                code = authCode,
                onSuccess = {
                    Log.e("TOTO", "apiToken: $it")
                    lifecycleScope.launch(Dispatchers.IO) {
                        when (val user = authenticateUser(this@LoginActivity, it)) {
                            is User -> {
//                                application.trackCurrentUserId()
//                                trackAccountEvent("loggedIn")
                                launchMainActivity(user)
                            }
                            is ApiResponse<*> -> withContext(Dispatchers.Main) {
                                if (user.error?.code?.equals("no_drive") == true) {
                                    launchNoDriveActivity()
                                } else {
                                    showError(getString(user.translatedError))
                                }
                            }
                            else -> withContext(Dispatchers.Main) { showError("anErrorHasOccurred 2") }
                        }
                    }
                },
                onError = {
                    val error = when (it) {
                        InfomaniakLogin.ErrorStatus.SERVER -> "serverError"
                        InfomaniakLogin.ErrorStatus.CONNECTION -> "connectionError"
                        else -> "anErrorHasOccurred 3"
                    }
                    showError(error)
                },
            )
        }
    }

    private fun showError(error: String) {
        showSnackbar(error)
    }

    private fun launchMainActivity(user: User) {
        Log.e("TOTO", "launchMainActivity: $user")
        startActivity(Intent(this, MainActivity::class.java).clearStack())
    }

    private fun launchNoDriveActivity() {
        Log.e("TOTO", "launchNoDriveActivity: ")
        startActivity(Intent(this, LaunchActivity::class.java))
    }

    companion object {
        const val MIN_HEIGHT_FOR_LANDSCAPE = 4

        suspend fun authenticateUser(context: Context, apiToken: ApiToken): Any {
            Log.e("TOTO", "authenticateUser: ")

            AccountUtils.getUserById(apiToken.userId)?.let {
                return getErrorResponse(R.string.errorUserAlreadyPresent)
            } ?: run {
                InfomaniakCore.bearerToken = apiToken.accessToken
                val userProfileResponse = ApiRepository.getUserProfile(HttpClient.okHttpClientNoInterceptor)
                if (userProfileResponse.result == ApiResponse.Status.ERROR) {
                    return userProfileResponse
                } else {
                    val user: User? = userProfileResponse.data?.apply {
                        this.apiToken = apiToken
                        this.organizations = ArrayList()
                    }

                    user?.let {
                        AccountUtils.addUser(user)
                        return user
                    } ?: run {
                        return getErrorResponse(R.string.anErrorHasOccurred)
                    }
                }
            }
        }

        private fun getErrorResponse(@StringRes text: Int): ApiResponse<Any> {
            return ApiResponse(result = ApiResponse.Status.ERROR, translatedError = text)
        }
    }
}