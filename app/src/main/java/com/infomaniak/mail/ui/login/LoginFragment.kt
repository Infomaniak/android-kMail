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

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.infomaniak.lib.core.R
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.lib.login.InfomaniakLogin
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.data.LocalSettings.*
import com.infomaniak.mail.databinding.FragmentLoginBinding
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MainDispatcher
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.Utils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private lateinit var binding: FragmentLoginBinding
    private val navigationArgs by lazy { LoginActivityArgs.fromBundle(requireActivity().intent.extras ?: bundleOf()) }
    private val introViewModel: IntroViewModel by activityViewModels()

    private lateinit var infomaniakLogin: InfomaniakLogin

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    @MainDispatcher
    lateinit var mainDispatcher: CoroutineDispatcher

    private val webViewLoginResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
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

    private val createAccountResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        result.handleCreateAccountActivityResult()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentLoginBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        infomaniakLogin = context.getInfomaniakLogin()

        val introPagerAdapter = IntroPagerAdapter(parentFragmentManager, lifecycle, navigationArgs.isFirstAccount)
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

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                removeOverScroll()
            }
        }

        dotsIndicator.apply {
            attachTo(introViewpager)
            isVisible = navigationArgs.isFirstAccount
        }

        nextButton.setOnClickListener { introViewpager.currentItem += 1 }

        connectButton.apply {
            initProgress(viewLifecycleOwner)
            setOnClickListener {
                signInButton.isEnabled = false
                showProgress()
                trackAccountEvent("openLoginWebview")
                infomaniakLogin.startWebViewLogin(webViewLoginResultLauncher)
            }
        }

        signInButton.setOnClickListener {
            signInButton.isEnabled = false
            connectButton.isEnabled = false
            trackAccountEvent("openCreationWebview")
            infomaniakLogin.startCreateAccountWebView(
                resultLauncher = createAccountResultLauncher,
                createAccountUrl = BuildConfig.CREATE_ACCOUNT_URL,
                successHost = BuildConfig.CREATE_ACCOUNT_SUCCESS_HOST,
                cancelHost = BuildConfig.CREATE_ACCOUNT_CANCEL_HOST,
            )
        }

        introViewModel.updatedAccentColor.observe(viewLifecycleOwner) { (newAccentColor, oldAccentColor) ->
            updateUi(newAccentColor, oldAccentColor)
        }
    }

    private fun ActivityResult.handleCreateAccountActivityResult() {
        if (resultCode == AppCompatActivity.RESULT_OK) {
            val translatedError = data?.getStringExtra(InfomaniakLogin.ERROR_TRANSLATED_TAG)
            when {
                translatedError.isNullOrBlank() -> infomaniakLogin.startWebViewLogin(webViewLoginResultLauncher, false)
                else -> showError(translatedError)
            }
        }
        with(binding) {
            connectButton.isEnabled = true
            signInButton.isEnabled = true
        }
    }

    private fun updateUi(newAccentColor: AccentColor, oldAccentColor: AccentColor) {
        animatePrimaryColorElements(newAccentColor, oldAccentColor)
        animateSecondaryColorElements(newAccentColor, oldAccentColor)
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
        when (val returnValue = LoginActivity.authenticateUser(requireContext(), apiToken)) {
            is User -> {
                trackAccountEvent("loggedIn")
                AccountUtils.reloadApp?.invoke()
            }
            is Utils.MailboxErrorCode -> withContext(mainDispatcher) {
                when (returnValue) {
                    Utils.MailboxErrorCode.NO_MAILBOX -> launchNoMailboxActivity()
                    Utils.MailboxErrorCode.NO_VALID_MAILBOX -> requireContext().launchNoValidMailboxesActivity()
                }
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

    private fun showError(error: String) {
        showSnackbar(error)
        enableConnectButtons()
    }

    private fun enableConnectButtons() = with(binding) {
        connectButton.hideProgress(R.string.connect)
        signInButton.isEnabled = true
    }

    private fun animatePrimaryColorElements(newAccentColor: AccentColor, oldAccentColor: AccentColor) {
        val newPrimary = newAccentColor.getPrimary(requireContext())
        val oldPrimary = oldAccentColor.getPrimary(requireContext())
        val ripple = newAccentColor.getRipple(requireContext())

        binding.apply {
            UiUtils.animateColorChange(oldPrimary, newPrimary) { color ->
                val singleColorStateList = ColorStateList.valueOf(color)
                dotsIndicator.selectedDotColor = color
                connectButton.setBackgroundColor(color)
                nextButton.backgroundTintList = singleColorStateList
                signInButton.setTextColor(color)
                signInButton.rippleColor = ColorStateList.valueOf(ripple)
            }
        }
    }

    private fun animateSecondaryColorElements(newAccentColor: AccentColor, oldAccentColor: AccentColor) {
        val newSecondaryBackground = newAccentColor.getOnboardingSecondaryBackground(requireContext())
        val oldSecondaryBackground = oldAccentColor.getOnboardingSecondaryBackground(requireContext())
        UiUtils.animateColorChange(oldSecondaryBackground, newSecondaryBackground) { color ->
            requireActivity().window.statusBarColor = color
        }
    }

    private fun ViewPager2.removeOverScroll() {
        (getChildAt(0) as? RecyclerView)?.overScrollMode = View.OVER_SCROLL_NEVER
    }


    private fun launchNoMailboxActivity() {
        startActivity(Intent(context, NoMailboxActivity::class.java).clearStack())
    }

    fun getViewPagerCurrentItem(): Int = binding.introViewpager.currentItem

    fun goBackAPage() {
        binding.introViewpager.currentItem -= 1
    }

    private fun trackAccountEvent(name: String) {
        requireContext().trackAccountEvent(name)
    }
}
