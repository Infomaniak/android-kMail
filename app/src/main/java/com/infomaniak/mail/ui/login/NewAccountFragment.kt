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
package com.infomaniak.mail.ui.login

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.R
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.lib.login.InfomaniakLogin
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.MatomoMail.trackUserInfo
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.databinding.FragmentNewAccountBinding
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MainDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.getInfomaniakLogin
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class NewAccountFragment : Fragment() {

    private lateinit var binding: FragmentNewAccountBinding
    private val introViewModel: IntroViewModel by activityViewModels()

    private lateinit var infomaniakLogin: InfomaniakLogin

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    @MainDispatcher
    lateinit var mainDispatcher: CoroutineDispatcher

    private val webViewLoginResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            with(result) {
                if (resultCode == AppCompatActivity.RESULT_OK) {
                    val authCode = data?.getStringExtra(InfomaniakLogin.CODE_TAG)
                    val translatedError = data?.getStringExtra(InfomaniakLogin.ERROR_TRANSLATED_TAG)
                    when {
                        translatedError?.isNotBlank() == true -> showError(translatedError)
                        authCode?.isNotBlank() == true -> authenticateUser(authCode)
                        else -> showError(getString(R.string.anErrorHasOccurred))
                    }
                } else {
                    enableConnectButtons()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        introViewModel.updatedAccentColor.value?.first?.theme?.let { requireActivity().setTheme(it) }
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentNewAccountBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        infomaniakLogin = context.getInfomaniakLogin()

        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        createNewAddressButton.setOnClickListener {
            createNewAddressButton.isEnabled = false
            requireContext().trackAccountEvent("openCreationWebview")
            infomaniakLogin.startCreateAccountWebView(
                resultLauncher = createAccountResultLauncher,
                createAccountUrl = BuildConfig.CREATE_ACCOUNT_URL,
                successHost = BuildConfig.CREATE_ACCOUNT_SUCCESS_HOST,
                cancelHost = BuildConfig.CREATE_ACCOUNT_CANCEL_HOST,
            )
        }
    }

    private val createAccountResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.handleCreateAccountActivityResult()
        }

    private fun ActivityResult.handleCreateAccountActivityResult() {
        if (resultCode == AppCompatActivity.RESULT_OK) {
            val translatedError = data?.getStringExtra(InfomaniakLogin.ERROR_TRANSLATED_TAG)
            when {
                translatedError.isNullOrBlank() -> infomaniakLogin.startWebViewLogin(webViewLoginResultLauncher, false)
                else -> showError(translatedError)
            }
        }
        enableConnectButtons()
    }

    private fun showError(error: String) {
        showSnackbar(error)
        enableConnectButtons()
    }

    private fun enableConnectButtons() {
        binding.createNewAddressButton.isEnabled = true
    }

    private fun authenticateUser(authCode: String) = lifecycleScope.launch(ioDispatcher) {
        infomaniakLogin.getToken(
            okHttpClient = HttpClient.okHttpClientNoTokenInterceptor,
            code = authCode,
            onSuccess = ::onAuthenticateUserSuccess,
            onError = ::onAuthenticateUserError,
        )
    }

    private fun onAuthenticateUserSuccess(apiToken: ApiToken) = lifecycleScope.launch(ioDispatcher) {
        when (val returnValue = authenticateUser(requireContext(), apiToken)) {
            is User -> {
                requireContext().trackAccountEvent("loggedIn")
                AccountUtils.reloadApp?.invoke()
            }
            is Utils.MailboxErrorCode -> withContext(mainDispatcher) {
                // TODO :
                // when (returnValue) {
                //     Utils.MailboxErrorCode.NO_MAILBOX -> launchNoMailboxActivity()
                //     Utils.MailboxErrorCode.NO_VALID_MAILBOX -> launchNoValidMailboxesActivity()
                // }
            }
            is ApiResponse<*> -> withContext(mainDispatcher) { showError(getString(returnValue.translatedError)) }
            else -> withContext(mainDispatcher) { showError(getString(R.string.anErrorHasOccurred)) }
        }
    }

    private fun onAuthenticateUserError(errorStatus: InfomaniakLogin.ErrorStatus) {
        val errorResId = when (errorStatus) {
            InfomaniakLogin.ErrorStatus.SERVER -> R.string.serverError
            InfomaniakLogin.ErrorStatus.CONNECTION -> R.string.connectionError
            else -> R.string.anErrorHasOccurred
        }
        showError(getString(errorResId))
    }

    companion object {
        suspend fun authenticateUser(context: Context, apiToken: ApiToken): Any {
            if (AccountUtils.getUserById(apiToken.userId) != null) return getErrorResponse(R.string.errorUserAlreadyPresent)

            InfomaniakCore.bearerToken = apiToken.accessToken
            val userProfileResponse = ApiRepository.getUserProfile(HttpClient.okHttpClientNoTokenInterceptor)

            if (userProfileResponse.result == ApiResponse.Status.ERROR) return userProfileResponse
            if (userProfileResponse.data == null) return getErrorResponse(R.string.anErrorHasOccurred)

            val user = userProfileResponse.data!!.apply {
                this.apiToken = apiToken
                this.organizations = arrayListOf()
            }

            val apiResponse = ApiRepository.getMailboxes(HttpClient.okHttpClientNoTokenInterceptor)

            return when {
                !apiResponse.isSuccess() -> apiResponse
                apiResponse.data?.isEmpty() == true -> Utils.MailboxErrorCode.NO_MAILBOX
                else -> {
                    apiResponse.data?.let { mailboxes ->
                        context.trackUserInfo("nbMailboxes", mailboxes.count())
                        AccountUtils.addUser(user)
                        MailboxController.updateMailboxes(context, mailboxes)

                        return@let if (mailboxes.none { it.isValid }) Utils.MailboxErrorCode.NO_VALID_MAILBOX else user
                    } ?: run {
                        getErrorResponse(R.string.serverError)
                    }
                }
            }
        }

        private fun getErrorResponse(@StringRes text: Int): ApiResponse<Any> {
            return ApiResponse(result = ApiResponse.Status.ERROR, translatedError = text)
        }
    }
}
