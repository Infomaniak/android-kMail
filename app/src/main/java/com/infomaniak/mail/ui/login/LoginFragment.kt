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
package com.infomaniak.mail.ui.login

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.infomaniak.core.Xor
import com.infomaniak.core.crossloginui.data.CrossLoginAccount
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.utils.awaitOneClick
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.Utils
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.hideProgressCatching
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.utils.showProgressCatching
import com.infomaniak.lib.core.utils.updateTextColor
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings.AccentColor
import com.infomaniak.mail.databinding.FragmentLoginBinding
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MainDispatcher
import com.infomaniak.mail.ui.login.CrossLoginBottomSheetDialog.Companion.ON_ANOTHER_ACCOUNT_CLICKED_KEY
import com.infomaniak.mail.utils.LoginUtils
import com.infomaniak.mail.utils.UiUtils.animateColorChange
import com.infomaniak.mail.utils.colorStateList
import com.infomaniak.mail.utils.extensions.applySideAndBottomSystemInsets
import com.infomaniak.mail.utils.extensions.applyWindowInsetsListener
import com.infomaniak.mail.utils.extensions.removeOverScrollForApiBelow31
import com.infomaniak.mail.utils.extensions.statusBar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import splitties.coroutines.repeatWhileActive
import splitties.experimental.ExperimentalSplittiesApi
import javax.inject.Inject
import com.infomaniak.core.crossloginui.R as RCrossLogin
import com.infomaniak.lib.core.R as RCore

@OptIn(ExperimentalSerializationApi::class)
@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var binding: FragmentLoginBinding by safeBinding()
    private val navigationArgs by lazy { LoginActivityArgs.fromBundle(requireActivity().intent.extras ?: bundleOf()) }
    private val introViewModel: IntroViewModel by activityViewModels()

    private val loginActivity by lazy { requireActivity() as LoginActivity }

    private val connectButtonProgressTimer by lazy { Utils.createRefreshTimer(onTimerFinish = ::startProgress) }

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    @MainDispatcher
    lateinit var mainDispatcher: CoroutineDispatcher

    @Inject
    lateinit var loginUtils: LoginUtils

    private val webViewLoginResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        loginUtils.handleWebViewLoginResult(fragment = this, result, loginActivity.infomaniakLogin, ::resetLoginButtons)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentLoginBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        // TODO: For cross app login implementation, look at:
        //  Pre-heat (i.e. `DynamicLazyMap`) getting app/device integrity attestation token if we know we might need it,
        //  that is, we have no accounts yet, and there are other friend apps.
        //
        // TODO:
        //  Pre-heat getting external accounts.
        //  Pre-heat checking tokens (by retrieving the profile)
        //  If there are external accounts with at least a valid token, pre-heat app/device integrity attestation token
        //  Pre-heat getting images
        //
        // TODO:
        //  Display all retrieved accounts, and gray out the ones with definitely stale tokens.
        // TODO:
        //  On "Continue with this/these account(s)", in parallel:
        //  a. Attempt syncing shared app id now.
        //  b. With auto retries on network issues, attempt tokens derivation for selected accounts.

        // TODO: See those files:
        //  LoginUtils.kt, LoginActivity.kt, this one (LoginFragment.kt), and NewAccountFragment.kt
        //  IntroPagerAdapter.kt and IntroFragment.kt

        applyWindowInsetsListener(shouldConsume = false) { root, insets ->
            root.applySideAndBottomSystemInsets(insets)
            dummyToolbarEdgeToEdge.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                insets.statusBar().top,
            )
        }

        loginUtils.initShowError(::showError)

        val introPagerAdapter = IntroPagerAdapter(
            manager = childFragmentManager,
            lifecycle = viewLifecycleOwner.lifecycle,
            isFirstAccount = navigationArgs.isFirstAccount,
        )

        introViewpager.apply {
            adapter = introPagerAdapter
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    val showConnectButton = position == introPagerAdapter.itemCount - 1
                    nextButton.isGone = showConnectButton
                    connectButton.isVisible = showConnectButton

                    if (showConnectButton) {
                        if (introViewModel.crossLoginAccounts.value!!.isEmpty()) {
                            crossLoginSelection.isGone = true
                            signInButton.isVisible = true
                        } else {
                            signInButton.isGone = true
                            crossLoginSelection.isVisible = true
                        }
                    } else {
                        crossLoginSelection.isGone = true
                        signInButton.isGone = true
                    }
                }
            })

            removeOverScrollForApiBelow31()
        }

        dotsIndicator.apply {
            attachTo(introViewpager)
            isVisible = navigationArgs.isFirstAccount
        }

        nextButton.setOnClickListener { introViewpager.currentItem += 1 }

        signInButton.setOnClickListener {
            safeNavigate(LoginFragmentDirections.actionLoginFragmentToNewAccountFragment())
        }

        introViewModel.updatedAccentColor.observe(viewLifecycleOwner) { (newAccentColor, oldAccentColor) ->
            updateUi(newAccentColor, oldAccentColor)
        }

        handleOnBackPressed()

        observeAccentColor()
        observeCrossLoginAccounts()
        observeCrossLoginSelectedIds()
        setCrossLoginClickListener()
        initCrossLogin()
    }

    override fun onDestroyView() {
        connectButtonProgressTimer.cancel()
        super.onDestroyView()
    }

    private fun handleOnBackPressed() = with(requireActivity()) {
        onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (getViewPagerCurrentItem() == 0) finish() else goBackAPage()
        }
    }

    private fun observeAccentColor() {
        introViewModel.updatedAccentColor.observe(viewLifecycleOwner) { (newAccentColor, _) ->
            binding.crossLoginSelection.setPrimaryColor(newAccentColor.getPrimary(requireContext()))
            binding.crossLoginSelection.setOnPrimaryColor(newAccentColor.getOnPrimary(requireContext()))
        }
    }

    private fun observeCrossLoginAccounts() {
        introViewModel.crossLoginAccounts.observe(viewLifecycleOwner) { accounts ->
            SentryLog.i(TAG, "Got ${accounts.count()} accounts from other apps")
            binding.crossLoginSelection.setAccounts(accounts)
        }
    }

    private fun observeCrossLoginSelectedIds() {
        introViewModel.crossLoginSelectedIds.observe(viewLifecycleOwner) { ids ->
            if (ids.isEmpty()) return@observe

            val count = ids.count()
            SentryLog.i(TAG, "User selected $count accounts")
            binding.crossLoginSelection.setSelectedIds(ids)
            binding.connectButton.text = requireContext().resources.getQuantityString(
                RCrossLogin.plurals.buttonContinueWithAccounts, count, count,
            )
        }
    }

    private fun setCrossLoginClickListener() {

        // Open Login webView when coming back from CrossLogin bottomSheet
        parentFragmentManager.setFragmentResultListener(
            /* requestKey = */ ON_ANOTHER_ACCOUNT_CLICKED_KEY,
            /* lifecycleOwner = */viewLifecycleOwner,
        ) { _, bundle ->
            bundle.getString(ON_ANOTHER_ACCOUNT_CLICKED_KEY)?.let { openLoginWebView() }
        }

        // Open CrossLogin bottomSheet
        binding.crossLoginSelection.setOnClickListener {
            safelyNavigate(LoginFragmentDirections.actionLoginFragmentToCrossLoginBottomSheetDialog())
        }
    }

    @OptIn(ExperimentalSplittiesApi::class)
    private fun initCrossLogin() = viewLifecycleOwner.lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {

            introViewModel.initDerivedTokenGenerator(coroutineScope = this)

            binding.connectButton.initProgress(viewLifecycleOwner, getCurrentOnPrimary())

            val accounts = introViewModel.getCrossLoginAccounts(context = requireContext())

            if (accounts.isNotEmpty()) {
                introViewModel.crossLoginAccounts.value = accounts
                introViewModel.crossLoginSelectedIds.value = accounts.map { it.id }.toSet()
            }

            repeatWhileActive {
                binding.connectButton.awaitOneClick()
                connectButtonProgressTimer.start()
                if (accounts.isEmpty()) {
                    binding.connectButton.updateTextColor(getCurrentOnPrimary())
                    binding.signInButton.isEnabled = false
                    openLoginWebView()
                } else {
                    handleCrossAppLogin()
                }
            }
        }
    }

    private suspend fun handleCrossAppLogin() {

        suspend fun authenticateToken(token: ApiToken, withRedirection: Boolean): Unit = with(loginUtils) {
            authenticateUser(token, loginActivity.infomaniakLogin, withRedirection)
        }

        val accounts = introViewModel.crossLoginAccounts.value
            ?.filter { introViewModel.crossLoginSelectedIds.value?.contains(it.id) == true }
            ?: return
        val tokenGenerator = introViewModel.derivedTokenGenerator ?: return

        var firstAccountToken: ApiToken? = null
        val accountsAndTokens = mutableMapOf<CrossLoginAccount, ApiToken?>()

        accounts.forEach { account ->
            val token = when (val result = tokenGenerator.attemptDerivingOneOfTheseTokens(account.tokens)) {
                is Xor.First -> {
                    SentryLog.i(TAG, "Succeeded to derive token for account: ${account.id}")
                    if (firstAccountToken == null) firstAccountToken = result.value
                    result.value
                }
                is Xor.Second -> {
                    SentryLog.e(TAG, "Failed to derive token for account ${account.id}, with reason: ${result.value}")
                    null
                }
            }
            accountsAndTokens.put(account, token)
        }

        val currentlySelectedInAnAppToken = accountsAndTokens.firstNotNullOfOrNull { (account, token) ->
            if (account.isCurrentlySelectedInAnApp) token else null
        }

        val remainingTokens = accountsAndTokens
            .filterNot { (_, token) -> token == currentlySelectedInAnAppToken }
            .values

        remainingTokens.forEach { token ->
            token?.let { authenticateToken(token = it, withRedirection = false) }
        }

        (currentlySelectedInAnAppToken ?: firstAccountToken)?.let { authenticateToken(token = it, withRedirection = true) }
    }

    private fun openLoginWebView() {
        trackAccountEvent(MatomoName.OpenLoginWebview)
        loginActivity.infomaniakLogin.startWebViewLogin(webViewLoginResultLauncher)
    }

    private fun updateUi(newAccentColor: AccentColor, oldAccentColor: AccentColor) {
        animatePrimaryColorElements(newAccentColor, oldAccentColor)
        animateOnPrimaryColorElements(newAccentColor, oldAccentColor)
        animateSecondaryColorElements(newAccentColor, oldAccentColor)
    }

    private fun animatePrimaryColorElements(newAccentColor: AccentColor, oldAccentColor: AccentColor) = with(binding) {
        val newPrimary = newAccentColor.getPrimary(context)
        val oldPrimary = oldAccentColor.getPrimary(context)
        val ripple = newAccentColor.getRipple(context)

        animateColorChange(oldPrimary, newPrimary) { color ->
            dotsIndicator.selectedDotColor = color
            connectButton.backgroundTintList = colorStateList {
                addForState(state = android.R.attr.state_enabled, color = color)
                addForRemainingStates(color = requireContext().getColor(R.color.backgroundDisabledPrimaryButton))
            }
            nextButton.backgroundTintList = ColorStateList.valueOf(color)
            signInButton.setTextColor(color)
            signInButton.rippleColor = ColorStateList.valueOf(ripple)
        }
    }

    private fun animateOnPrimaryColorElements(newAccentColor: AccentColor, oldAccentColor: AccentColor) = with(binding) {
        val newOnPrimary = newAccentColor.getOnPrimary(context)
        val oldOnPrimary = oldAccentColor.getOnPrimary(context)

        animateColorChange(oldOnPrimary, newOnPrimary) { color ->
            connectButton.setTextColor(color)
            nextButton.imageTintList = ColorStateList.valueOf(color)
        }
    }

    private fun animateSecondaryColorElements(newAccentColor: AccentColor, oldAccentColor: AccentColor) {
        val newSecondaryBackground = newAccentColor.getOnboardingSecondaryBackground(requireContext())
        val oldSecondaryBackground = oldAccentColor.getOnboardingSecondaryBackground(requireContext())

        animateColorChange(oldSecondaryBackground, newSecondaryBackground) { color ->
            binding.dummyToolbarEdgeToEdge.setBackgroundColor(color)
        }
    }

    private fun showError(error: String) {
        showSnackbar(error)
        resetLoginButtons()
    }

    private fun resetLoginButtons() = with(binding) {
        connectButtonProgressTimer.cancel()
        connectButton.hideProgressCatching(RCore.string.connect)
        signInButton.isEnabled = true
    }

    private fun getViewPagerCurrentItem(): Int = binding.introViewpager.currentItem

    private fun goBackAPage() {
        binding.introViewpager.currentItem -= 1
    }

    private fun startProgress() {
        binding.connectButton.showProgressCatching(getCurrentOnPrimary())
    }

    private fun getCurrentOnPrimary(): Int? = introViewModel.updatedAccentColor.value?.first?.getOnPrimary(requireContext())

    companion object {
        private val TAG = LoginFragment::class.java.simpleName
    }
}
