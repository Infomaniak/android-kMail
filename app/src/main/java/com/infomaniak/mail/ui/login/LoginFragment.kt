/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2026 Infomaniak Network SA
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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.VisibleForTesting
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.colorResource
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.infomaniak.core.auth.models.UserLoginResult
import com.infomaniak.core.common.extensions.capitalizeFirstChar
import com.infomaniak.core.common.observe
import com.infomaniak.core.crossapplogin.back.BaseCrossAppLoginViewModel
import com.infomaniak.core.crossapplogin.back.BaseCrossAppLoginViewModel.Companion.filterSelectedAccounts
import com.infomaniak.core.crossapplogin.back.ExternalAccount
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.legacy.utils.SnackbarUtils.showSnackbar
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.MatomoMail.trackOnBoardingEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.databinding.FragmentLoginBinding
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MainDispatcher
import com.infomaniak.mail.ui.login.components.OnboardingScreen
import com.infomaniak.mail.ui.theme.MailTheme
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.LoginOutcome
import com.infomaniak.mail.utils.LoginUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import splitties.experimental.ExperimentalSplittiesApi
import javax.inject.Inject
import com.infomaniak.core.auth.utils.LoginUtils as CoreLoginUtils
import com.infomaniak.core.legacy.R as RCore

@OptIn(ExperimentalSerializationApi::class)
@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var binding: FragmentLoginBinding by safeBinding()
    private val navigationArgs by lazy { LoginActivityArgs.fromBundle(requireActivity().intent.extras ?: bundleOf()) }

    private val crossAppLoginViewModel: CrossAppLoginViewModel by activityViewModels()

    private val loginActivity by lazy { requireActivity() as LoginActivity }

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    @MainDispatcher
    lateinit var mainDispatcher: CoroutineDispatcher

    @Inject
    lateinit var localSettings: LocalSettings

    @Inject
    lateinit var loginUtils: LoginUtils

    private val webViewLoginResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        lifecycleScope.launch {
            loginUtils.handleWebViewLoginResult(result, loginActivity.infomaniakLogin, ::resetLoginButtons)
        }
    }

    private lateinit var connectButtonText: String

    private var isLoginButtonLoading by mutableStateOf(false)
    private var isSignUpButtonLoading by mutableStateOf(false)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        connectButtonText = getString(RCore.string.connect)
        return FragmentLoginBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.setContent {
            val scope = rememberCoroutineScope()

            val accounts by crossAppLoginViewModel.accountsCheckingState.collectAsStateWithLifecycle()
            val skippedIds by crossAppLoginViewModel.skippedAccountIds.collectAsStateWithLifecycle()

            var accentColor by rememberSaveable { mutableStateOf(localSettings.accentColor) }

            LaunchedEffect(accentColor) {
                localSettings.accentColor = accentColor
            }

            MailTheme {
                // The color can't be overridden at the theme level because it's used elsewhere inside the app like the threadlist
                Surface(color = colorResource(R.color.backgroundColor)) {
                    OnboardingScreen(
                        accounts = { accounts },
                        skippedIds = { skippedIds },
                        isLoginButtonLoading = { isLoginButtonLoading },
                        isSignUpButtonLoading = { isSignUpButtonLoading },
                        onLogin = { openLoginWebView() },
                        onContinueWithSelectedAccounts = { scope.launch { connectSelectedAccounts(it, skippedIds) } },
                        onCreateAccount = { openAccountCreation() },
                        onUseAnotherAccountClicked = { openLoginWebView() },
                        onSaveSkippedAccounts = { crossAppLoginViewModel.skippedAccountIds.value = it },
                        accentColor = { accentColor },
                        onSelectAccentColor = {
                            accentColor = it
                            trackOnBoardingEvent("${MatomoName.SwitchColor.value}${it.toString().capitalizeFirstChar()}")
                        },
                        displayOnlyLastPage = navigationArgs.isFirstAccount.not(),
                    )
                }
            }
        }

        loginUtils.initShowError(::showError)

        observeCrossLoginAccounts()
        initCrossLogin()
    }

    private suspend fun connectSelectedAccounts(accounts: List<ExternalAccount>, skippedIds: Set<Long>) {
        startLoadingLoginButtons()
        val accountsToLogin = accounts.filterSelectedAccounts(skippedIds)
        val loginResult = crossAppLoginViewModel.attemptLogin(selectedAccounts = accountsToLogin)
        loginUsers(loginResult)
        loginResult.errorMessageIds.forEach { messageResId -> showError(getString(messageResId)) }
    }

    private fun observeCrossLoginAccounts() {
        crossAppLoginViewModel.availableAccounts.observe(viewLifecycleOwner) { accounts ->
            SentryLog.i(TAG, "Got ${accounts.count()} accounts from other apps")
        }
    }

    @OptIn(ExperimentalSplittiesApi::class, ExperimentalCoroutinesApi::class)
    private fun initCrossLogin() = viewLifecycleOwner.lifecycleScope.launch {
        launch { crossAppLoginViewModel.activateUpdates(requireActivity()) }
    }

    private suspend fun loginUsers(loginResult: BaseCrossAppLoginViewModel.LoginResult) {
        with(loginUtils) {
            val results = CoreLoginUtils.getLoginResultsAfterCrossApp(loginResult.tokens, requireContext(), AccountUtils)
            val users = buildList {
                results.forEach { result ->
                    when (result) {
                        is UserLoginResult.Success -> add(result.user)
                        is UserLoginResult.Failure -> showError(result.errorMessage)
                    }
                }
            }

            fetchMailboxes(users).forEachIndexed { index, outcome ->
                outcome.handleErrors(loginActivity.infomaniakLogin)
                if (index == fetchMailboxes(users).lastIndex) {
                    outcome.handleNavigation()
                    if (outcome !is LoginOutcome.Success) resetLoginButtons()
                }
            }
        }
    }

    @VisibleForTesting
    fun openLoginWebView() {
        startLoadingLoginButtons()
        trackAccountEvent(MatomoName.OpenLoginWebview)
        loginActivity.infomaniakLogin.startWebViewLogin(webViewLoginResultLauncher)
    }

    private fun openAccountCreation() {
        safelyNavigate(LoginFragmentDirections.actionLoginFragmentToNewAccountFragment())
    }

    private fun showError(error: String) {
        showSnackbar(error)
        resetLoginButtons()
    }

    private fun startLoadingLoginButtons() {
        isLoginButtonLoading = true
        isSignUpButtonLoading = true
    }

    private fun resetLoginButtons() {
        isLoginButtonLoading = false
        isSignUpButtonLoading = false
    }

    companion object {
        private val TAG = LoginFragment::class.java.simpleName
    }
}
