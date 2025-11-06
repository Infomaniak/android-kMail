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
import com.infomaniak.core.crossapplogin.back.BaseCrossAppLoginViewModel.Companion.filterSelectedAccounts
import com.infomaniak.core.crossapplogin.back.ExternalAccount
import com.infomaniak.core.extensions.capitalizeFirstChar
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.legacy.utils.SnackbarUtils.showSnackbar
import com.infomaniak.core.legacy.utils.Utils
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.observe
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.MatomoMail.trackOnBoardingEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.AccentColor
import com.infomaniak.mail.databinding.FragmentLoginBinding
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MainDispatcher
import com.infomaniak.mail.ui.login.components.OnboardingScreen
import com.infomaniak.mail.ui.theme.MailTheme
import com.infomaniak.mail.utils.LoginUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import splitties.experimental.ExperimentalSplittiesApi
import javax.inject.Inject
import com.infomaniak.core.legacy.R as RCore

@OptIn(ExperimentalSerializationApi::class)
@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var binding: FragmentLoginBinding by safeBinding()
    private val navigationArgs by lazy { LoginActivityArgs.fromBundle(requireActivity().intent.extras ?: bundleOf()) }
    private val introViewModel: IntroViewModel by activityViewModels()

    private val crossAppLoginViewModel: CrossAppLoginViewModel by activityViewModels()

    private val loginActivity by lazy { requireActivity() as LoginActivity }

    private val connectButtonProgressTimer by lazy {
        Utils.createRefreshTimer(milliseconds = 100, onTimerFinish = ::startProgress)
    }

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.setContent {
            val scope = rememberCoroutineScope()

            val accounts by crossAppLoginViewModel.accountsCheckingState.collectAsStateWithLifecycle()
            val skippedIds by crossAppLoginViewModel.skippedAccountIds.collectAsStateWithLifecycle()

            var accentColor by rememberSaveable { mutableStateOf(AccentColor.PINK) }

            LaunchedEffect(accentColor) {
                localSettings.accentColor = accentColor // TODO: Check if logic stores accent color correctly
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
                    )
                }
            }
        }

        // applyWindowInsetsListener(shouldConsume = false) { root, insets ->
        //     root.applySideAndBottomSystemInsets(insets)
        //     dummyToolbarEdgeToEdge.layoutParams = ViewGroup.LayoutParams(
        //         ViewGroup.LayoutParams.MATCH_PARENT,
        //         insets.statusBar().top,
        //     )
        // }

        loginUtils.initShowError(::showError)

        // val introPagerAdapter = IntroPagerAdapter(
        //     manager = childFragmentManager,
        //     lifecycle = viewLifecycleOwner.lifecycle,
        //     isFirstAccount = navigationArgs.isFirstAccount,
        // )
        //
        // introViewpager.apply {
        //     adapter = introPagerAdapter
        //     selectedPagePosition().mapLatest { position ->
        //         val isLoginPage = position == introPagerAdapter.itemCount - 1
        //
        //         nextButton.isGone = isLoginPage
        //         connectButton.isVisible = isLoginPage
        //         crossAppLoginViewModel.availableAccounts.collectLatest { accounts ->
        //             val hasAccounts = accounts.isNotEmpty()
        //             signUpButton.isVisible = isLoginPage
        //             crossLoginSelection.isVisible = isLoginPage && hasAccounts
        //         }
        //     }.launchInOnLifecycle(viewLifecycleOwner)
        //
        //     removeOverScrollForApiBelow31()
        // }
        //
        // dotsIndicator.apply {
        //     attachTo(introViewpager)
        //     isVisible = navigationArgs.isFirstAccount
        // }
        //
        // nextButton.setOnClickListener { introViewpager.currentItem += 1 }
        //
        // signUpButton.setOnClickListener {
        //     safeNavigate(LoginFragmentDirections.actionLoginFragmentToNewAccountFragment())
        // }

        introViewModel.updatedAccentColor.observe(viewLifecycleOwner) { (newAccentColor, oldAccentColor) ->
            // updateUi(newAccentColor, oldAccentColor)
        }

        observeAccentColor()
        observeCrossLoginAccounts()
        setCrossLoginClickListener()
        initCrossLogin()
    }

    private suspend fun connectSelectedAccounts(accounts: List<ExternalAccount>, skippedIds: Set<Long>) {
        val selectedAccounts = accounts.filterSelectedAccounts(skippedIds)
        val loginResult = crossAppLoginViewModel.attemptLogin(selectedAccounts)

        with(loginResult) {
            tokens.forEachIndexed { index, token ->
                // TODO: Check why it's not like drive
                // authenticateUser(token, loginActivity.infomaniakLogin, withRedirection = index == tokens.lastIndex)
            }

            errorMessageIds.forEach { errorId -> showError(getString(errorId)) }
        }
    }

    override fun onDestroyView() {
        connectButtonProgressTimer.cancel()
        super.onDestroyView()
    }

    // private fun handleOnBackPressed() = with(requireActivity()) {
    //     onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
    //         if (getViewPagerCurrentItem() == 0) finish() else goBackAPage()
    //     }
    // }

    private fun observeAccentColor() {
        introViewModel.updatedAccentColor.observe(viewLifecycleOwner) { (newAccentColor, _) ->
            // TODO
            // binding.crossLoginSelection.setPrimaryColor(newAccentColor.getPrimary(requireContext()))
            // binding.crossLoginSelection.setOnPrimaryColor(newAccentColor.getOnPrimary(requireContext()))
        }
    }

    private fun observeCrossLoginAccounts() {
        crossAppLoginViewModel.availableAccounts.observe(viewLifecycleOwner) { accounts ->
            SentryLog.i(TAG, "Got ${accounts.count()} accounts from other apps")
            // binding.crossLoginSelection.setAccounts(accounts)
        }
    }

    private fun setCrossLoginClickListener() {

        // // Open CrossLogin bottomSheet
        // binding.crossLoginSelection.setOnClickListener {
        //     safelyNavigate(LoginFragmentDirections.actionLoginFragmentToCrossLoginBottomSheetDialog())
        // }
        //
        // // Open Login webView when coming back from CrossLogin bottomSheet
        // parentFragmentManager.setFragmentResultListener(
        //     /* requestKey = */ ON_ANOTHER_ACCOUNT_CLICKED_KEY,
        //     /* lifecycleOwner = */viewLifecycleOwner,
        // ) { _, bundle ->
        //     bundle.getString(ON_ANOTHER_ACCOUNT_CLICKED_KEY)?.let { openLoginWebView() }
        // }
    }

    @OptIn(ExperimentalSplittiesApi::class, ExperimentalCoroutinesApi::class)
    private fun initCrossLogin() = viewLifecycleOwner.lifecycleScope.launch {
        launch { crossAppLoginViewModel.activateUpdates(requireActivity()) }
        // launch { crossAppLoginViewModel.skippedAccountIds.collect(binding.crossLoginSelection::setSkippedIds) }
        //
        // binding.connectButton.initProgress(viewLifecycleOwner, getCurrentOnPrimary())
        // repeatWhileActive {
        //     val accountsToLogin = crossAppLoginViewModel.selectedAccounts.mapLatest { accounts ->
        //         val selectedCount = accounts.count()
        //         SentryLog.i(TAG, "User selected $selectedCount accounts")
        //         connectButtonText = when {
        //             accounts.isEmpty() -> resources.getString(RCore.string.connect)
        //             else -> resources.getQuantityString(
        //                 RCrossLogin.plurals.buttonContinueWithAccounts,
        //                 selectedCount,
        //                 selectedCount,
        //             )
        //         }
        //         binding.connectButton.text = connectButtonText
        //         binding.connectButton.awaitOneClick()
        //         accounts
        //     }.first()
        //     connectButtonProgressTimer.start()
        //     if (accountsToLogin.isEmpty()) {
        //         binding.connectButton.updateTextColor(getCurrentOnPrimary())
        //         binding.signUpButton.isEnabled = false
        //         openLoginWebView()
        //     } else {
        //         val loginResult = crossAppLoginViewModel.attemptLogin(selectedAccounts = accountsToLogin)
        //         loginUsers(loginResult)
        //         loginResult.errorMessageIds.forEach { messageResId -> showError(getString(messageResId)) }
        //
        //         delay(1_000L) // Add some delay so the button won't blink back into its original color before leaving the Activity
        //     }
    }

    // private suspend fun loginUsers(loginResult: BaseCrossAppLoginViewModel.LoginResult) {
    //     with(loginUtils) {
    //         val results = CoreLoginUtils.getLoginResultsAfterCrossApp(loginResult.tokens, requireContext(), AccountUtils)
    //         val users = buildList {
    //             results.forEach { result ->
    //                 when (result) {
    //                     is UserLoginResult.Success -> add(result.user)
    //                     is UserLoginResult.Failure -> showError(result.errorMessage)
    //                 }
    //             }
    //         }
    // 
    //         fetchMailboxes(users).forEachIndexed { index, outcome ->
    //             outcome.handleErrors(loginActivity.infomaniakLogin)
    //             if (index == fetchMailboxes(users).lastIndex) outcome.handleNavigation()
    //         }
    //     }
    // }

    @VisibleForTesting
    fun openLoginWebView() {
        trackAccountEvent(MatomoName.OpenLoginWebview)
        loginActivity.infomaniakLogin.startWebViewLogin(webViewLoginResultLauncher)
    }

    private fun openAccountCreation() {
        safelyNavigate(LoginFragmentDirections.actionLoginFragmentToNewAccountFragment())
    }

    // private fun updateUi(newAccentColor: AccentColor, oldAccentColor: AccentColor) {
    //     animatePrimaryColorElements(newAccentColor, oldAccentColor)
    //     animateOnPrimaryColorElements(newAccentColor, oldAccentColor)
    //     animateSecondaryColorElements(newAccentColor, oldAccentColor)
    // }
    //
    // private fun animatePrimaryColorElements(newAccentColor: AccentColor, oldAccentColor: AccentColor) = with(binding) {
    //     val newPrimary = newAccentColor.getPrimary(context)
    //     val oldPrimary = oldAccentColor.getPrimary(context)
    //     val ripple = newAccentColor.getRipple(context)
    //
    //     animateColorChange(oldPrimary, newPrimary) { color ->
    //         dotsIndicator.selectedDotColor = color
    //         connectButton.backgroundTintList = colorStateList {
    //             addForState(state = android.R.attr.state_enabled, color = color)
    //             addForRemainingStates(color = requireContext().getColor(R.color.backgroundDisabledPrimaryButton))
    //         }
    //         nextButton.backgroundTintList = ColorStateList.valueOf(color)
    //         signUpButton.setTextColor(color)
    //         signUpButton.rippleColor = ColorStateList.valueOf(ripple)
    //     }
    // }
    //
    // private fun animateOnPrimaryColorElements(newAccentColor: AccentColor, oldAccentColor: AccentColor) = with(binding) {
    //     val newOnPrimary = newAccentColor.getOnPrimary(context)
    //     val oldOnPrimary = oldAccentColor.getOnPrimary(context)
    //
    //     animateColorChange(oldOnPrimary, newOnPrimary) { color ->
    //         connectButton.setTextColor(color)
    //         nextButton.imageTintList = ColorStateList.valueOf(color)
    //     }
    // }
    //
    // private fun animateSecondaryColorElements(newAccentColor: AccentColor, oldAccentColor: AccentColor) {
    //     val newSecondaryBackground = newAccentColor.getOnboardingSecondaryBackground(requireContext())
    //     val oldSecondaryBackground = oldAccentColor.getOnboardingSecondaryBackground(requireContext())
    //
    //     animateColorChange(oldSecondaryBackground, newSecondaryBackground) { color ->
    //         binding.dummyToolbarEdgeToEdge.setBackgroundColor(color)
    //     }
    // }
    //
    private fun showError(error: String) {
        showSnackbar(error)
        // resetLoginButtons()
    }

    private fun resetLoginButtons() = with(binding) {
        connectButtonProgressTimer.cancel()
        // connectButton.hideProgressCatching(connectButtonText)
        // signUpButton.isEnabled = true
    }
    //
    // private fun getViewPagerCurrentItem(): Int = binding.introViewpager.currentItem
    //
    // private fun goBackAPage() {
    //     binding.introViewpager.currentItem -= 1
    // }
    //
    private fun startProgress() {
        // binding.connectButton.showProgressCatching(getCurrentOnPrimary())
    }

    private fun getCurrentOnPrimary(): Int? = introViewModel.updatedAccentColor.value?.first?.getOnPrimary(requireContext())

    companion object {
        private val TAG = LoginFragment::class.java.simpleName
    }
}
