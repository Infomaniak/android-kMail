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
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.infomaniak.core.Xor
import com.infomaniak.core.login.crossapp.CrossAppLogin
import com.infomaniak.core.login.crossapp.DerivedTokenGenerator
import com.infomaniak.core.login.crossapp.DerivedTokenGeneratorImpl
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.Utils
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.hideProgressCatching
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.utils.showProgressCatching
import com.infomaniak.lib.core.utils.updateTextColor
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.awaitOneLongClick
import com.infomaniak.mail.data.LocalSettings.AccentColor
import com.infomaniak.mail.databinding.FragmentLoginBinding
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MainDispatcher
import com.infomaniak.mail.utils.LoginUtils
import com.infomaniak.mail.utils.UiUtils.animateColorChange
import com.infomaniak.mail.utils.extensions.applySideAndBottomSystemInsets
import com.infomaniak.mail.utils.extensions.applyWindowInsetsListener
import com.infomaniak.mail.utils.extensions.removeOverScrollForApiBelow31
import com.infomaniak.mail.utils.extensions.statusBar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import javax.inject.Inject
import com.infomaniak.lib.core.R as RCore

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
        //TODO: For cross app login implementation, look at:
        // Pre-heat (i.e. `DynamicLazyMap`) getting app/device integrity attestation token if we know we might need it,
        // that is, we have no accounts yet, and there are other friend apps.
        //
        //TODO:
        // Pre-heat getting external accounts.
        // Pre-heat checking tokens (by retrieving the profile)
        // If there are external accounts with at least a valid token, pre-heat app/device integrity attestation token
        // Pre-heat getting images
        //
        //TODO:
        // Display all retrieved accounts, and gray out the ones with definitely stale tokens.
        //TODO:
        // On "Continue with this/these account(s)", in parallel:
        // a. Attempt syncing shared app id now.
        // b. With auto retries on network issues, attempt tokens derivation for selected accounts.

        //TODO: See those files:
        // LoginUtils.kt, LoginActivity.kt, this one (LoginFragment.kt), and NewAccountFragment.kt
        // IntroPagerAdapter.kt and IntroFragment.kt

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
                    nextButton.isInvisible = showConnectButton
                    connectButton.isInvisible = !showConnectButton
                    signInButton.isInvisible = !showConnectButton
                }
            })

            removeOverScrollForApiBelow31()
        }

        dotsIndicator.apply {
            attachTo(introViewpager)
            isVisible = navigationArgs.isFirstAccount
        }

        nextButton.setOnClickListener { introViewpager.currentItem += 1 }

        connectButton.apply {
            initProgress(viewLifecycleOwner, getCurrentOnPrimary())
            setOnClickListener {
                updateTextColor(getCurrentOnPrimary())
                signInButton.isEnabled = false
                connectButtonProgressTimer.start()
                trackAccountEvent(MatomoName.OpenLoginWebview)
                loginActivity.infomaniakLogin.startWebViewLogin(webViewLoginResultLauncher)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            @OptIn(ExperimentalSerializationApi::class)
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val derivedTokenGenerator: DerivedTokenGenerator = DerivedTokenGeneratorImpl(
                    coroutineScope = this,
                    hostAppPackageName = BuildConfig.APPLICATION_ID,
                    clientId = BuildConfig.CLIENT_ID,
                    userAgent = HttpUtils.getUserAgent
                )
                val crossAppLogin = CrossAppLogin.forContext(requireContext())
                val externalAccounts = crossAppLogin.retrieveAccountsFromOtherApps()
                connectButton.text = "${externalAccounts.size} accounts"
                connectButton.awaitOneLongClick()
                println("Got ${externalAccounts.size} accounts from other apps:")
                println("Accounts retrieved: $externalAccounts")
                externalAccounts.firstOrNull()?.let { account ->
                    when (val result = derivedTokenGenerator.attemptDerivingOneOfTheseTokens(account.tokens)) {
                        is Xor.First -> with(loginUtils) {
                            authenticateUser(token = result.value, infomaniakLogin = loginActivity.infomaniakLogin)
                        }
                        is Xor.Second -> {
                            println(result.value)
                            connectButton.text = "Ooops"
                        }
                    }
                }
            }
        }

        signInButton.setOnClickListener {
            safeNavigate(LoginFragmentDirections.actionLoginFragmentToNewAccountFragment())
        }

        introViewModel.updatedAccentColor.observe(viewLifecycleOwner) { (newAccentColor, oldAccentColor) ->
            updateUi(newAccentColor, oldAccentColor)
        }

        handleOnBackPressed()
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
            connectButton.setBackgroundColor(color)
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
}
