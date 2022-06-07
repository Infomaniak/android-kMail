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
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.clearStack
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.lib.login.InfomaniakLogin
import com.infomaniak.lib.login.InfomaniakLogin.ErrorStatus
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.databinding.ActivityLoginBinding
import com.infomaniak.mail.ui.main.MainActivity
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.showSnackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.infomaniak.lib.core.R as RCore

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
                    else -> showError(getString(RCore.string.anErrorHasOccurred))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        infomaniakLogin = InfomaniakLogin(
            context = this,
            appUID = BuildConfig.APPLICATION_ID,
            clientID = BuildConfig.CLIENT_ID,
        ).also {
            it.startWebViewLogin(webViewLoginResultLauncher)
        }
    }

    private fun authenticateUser(authCode: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            infomaniakLogin.getToken(
                okHttpClient = HttpClient.okHttpClientNoInterceptor,
                code = authCode,
                onSuccess = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        when (val user = authenticateUser(this@LoginActivity, it)) {
                            is User -> {
                                // application.trackCurrentUserId() // TODO: Matomo
                                // trackAccountEvent("loggedIn") // TODO: Matomo
                                launchMainActivity()
                            }
                            is ApiResponse<*> -> withContext(Dispatchers.Main) { showError(getString(user.translatedError)) }
                            else -> withContext(Dispatchers.Main) { showError(getString(RCore.string.anErrorHasOccurred)) }
                        }
                    }
                },
                onError = {
                    val error = when (it) {
                        ErrorStatus.SERVER -> RCore.string.serverError
                        ErrorStatus.CONNECTION -> RCore.string.connectionError
                        else -> RCore.string.anErrorHasOccurred
                    }
                    showError(getString(error))
                },
            )
        }
    }

    private fun launchMainActivity() {
        startActivity(Intent(this, MainActivity::class.java).clearStack())
    }

    private fun showError(error: String) {
        showSnackbar(error)
    }

    companion object {
        suspend fun authenticateUser(context: Context, apiToken: ApiToken): Any {

            return if (AccountUtils.getUserById(apiToken.userId) == null) {
                InfomaniakCore.bearerToken = apiToken.accessToken
                val userProfileResponse = ApiRepository.getUserProfile(HttpClient.okHttpClientNoInterceptor)

                if (userProfileResponse.result == ApiResponse.Status.ERROR) {
                    userProfileResponse
                } else {
                    val user = userProfileResponse.data?.apply {
                        this.apiToken = apiToken
                        this.organizations = ArrayList()
                    }

                    if (user == null) {
                        getErrorResponse(RCore.string.anErrorHasOccurred)
                    } else {
                        // DriveInfosController.storeDriveInfos(user.id, driveInfo) // TODO?
                        // CloudStorageProvider.notifyRootsChanged(context) // TODO?
                        AccountUtils.addUser(user)
                        user
                    }
                }
            } else {
                getErrorResponse(RCore.string.errorUserAlreadyPresent)
            }
        }

        private fun getErrorResponse(@StringRes text: Int): ApiResponse<Any> =
            ApiResponse(result = ApiResponse.Status.ERROR, translatedError = text)
    }
}
