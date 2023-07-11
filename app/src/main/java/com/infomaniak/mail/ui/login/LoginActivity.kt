/*
 * Infomaniak ikMail - Android
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

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.Utils.lockOrientationForSmallScreens
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.lib.login.InfomaniakLogin
import com.infomaniak.lib.login.InfomaniakLogin.ErrorStatus
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.MatomoMail.trackScreen
import com.infomaniak.mail.MatomoMail.trackUserInfo
import com.infomaniak.mail.data.LocalSettings.AccentColor
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.databinding.ActivityLoginBinding
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MainDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.UiUtils.animateColorChange
import com.infomaniak.mail.utils.Utils.MailboxErrorCode
import com.infomaniak.mail.utils.getInfomaniakLogin
import com.infomaniak.mail.utils.launchNoValidMailboxesActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.infomaniak.lib.core.R as RCore

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private val binding by lazy { ActivityLoginBinding.inflate(layoutInflater) }
    private val navigationArgs by lazy { LoginActivityArgs.fromBundle(intent.extras ?: bundleOf()) }
    private val introViewModel: IntroViewModel by viewModels()

    private lateinit var infomaniakLogin: InfomaniakLogin

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    @MainDispatcher
    lateinit var mainDispatcher: CoroutineDispatcher

    private val webViewLoginResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        with(result) {
            if (resultCode == RESULT_OK) {
                val authCode = data?.getStringExtra(InfomaniakLogin.CODE_TAG)
                val translatedError = data?.getStringExtra(InfomaniakLogin.ERROR_TRANSLATED_TAG)
                when {
                    translatedError?.isNotBlank() == true -> showError(translatedError)
                    authCode?.isNotBlank() == true -> authenticateUser(authCode)
                    else -> showError(getString(RCore.string.anErrorHasOccurred))
                }
            } else {
                enableConnectButtons()
            }
        }
    }

    private val createAccountResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        result.handleCreateAccountActivityResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) = with(binding) {

        lockOrientationForSmallScreens()

        super.onCreate(savedInstanceState)
        setContentView(root)
        handleOnBackPressed()

        infomaniakLogin = context.getInfomaniakLogin()

        val introPagerAdapter = IntroPagerAdapter(supportFragmentManager, lifecycle, navigationArgs.isFirstAccount)
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
            initProgress(this@LoginActivity)
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

        introViewModel.updatedAccentColor.observe(this@LoginActivity) { (newAccentColor, oldAccentColor) ->
            updateUi(newAccentColor, oldAccentColor)
        }

        trackScreen()
    }

    private fun handleOnBackPressed() = with(binding) {
        onBackPressedDispatcher.addCallback(this@LoginActivity) {
            if (introViewpager.currentItem == 0) finish() else introViewpager.currentItem -= 1
        }
    }

    private fun ActivityResult.handleCreateAccountActivityResult() {
        if (resultCode == RESULT_OK) {
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
        when (val returnValue = authenticateUser(this@LoginActivity, apiToken)) {
            is User -> {
                trackAccountEvent("loggedIn")
                AccountUtils.reloadApp?.invoke()
            }
            is MailboxErrorCode -> withContext(mainDispatcher) {
                when (returnValue) {
                    MailboxErrorCode.NO_MAILBOX -> launchNoMailboxActivity()
                    MailboxErrorCode.NO_VALID_MAILBOX -> launchNoValidMailboxesActivity()
                }
            }
            is ApiResponse<*> -> withContext(mainDispatcher) { showError(getString(returnValue.translatedError)) }
            else -> withContext(mainDispatcher) { showError(getString(RCore.string.anErrorHasOccurred)) }
        }
    }

    private fun onAuthenticateUserError(errorStatus: ErrorStatus) {
        val errorResId = when (errorStatus) {
            ErrorStatus.SERVER -> RCore.string.serverError
            ErrorStatus.CONNECTION -> RCore.string.connectionError
            else -> RCore.string.anErrorHasOccurred
        }
        showError(getString(errorResId))
    }

    private fun showError(error: String) {
        showSnackbar(error)
        enableConnectButtons()
    }

    private fun enableConnectButtons() = with(binding) {
        connectButton.hideProgress(RCore.string.connect)
        signInButton.isEnabled = true
    }

    private fun animatePrimaryColorElements(newAccentColor: AccentColor, oldAccentColor: AccentColor) {
        val newPrimary = newAccentColor.getPrimary(this)
        val oldPrimary = oldAccentColor.getPrimary(this)
        val ripple = newAccentColor.getRipple(this)

        binding.apply {
            animateColorChange(oldPrimary, newPrimary) { color ->
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
        val newSecondaryBackground = newAccentColor.getOnboardingSecondaryBackground(this)
        val oldSecondaryBackground = oldAccentColor.getOnboardingSecondaryBackground(this)
        animateColorChange(oldSecondaryBackground, newSecondaryBackground) { color ->
            window.statusBarColor = color
        }
    }

    private fun ViewPager2.removeOverScroll() {
        (getChildAt(0) as? RecyclerView)?.overScrollMode = View.OVER_SCROLL_NEVER
    }

    private fun launchNoMailboxActivity() {
        startActivity(Intent(this@LoginActivity, NoMailboxActivity::class.java).clearStack())
    }

    companion object {

        suspend fun authenticateUser(context: Context, apiToken: ApiToken): Any {
            if (AccountUtils.getUserById(apiToken.userId) != null) return getErrorResponse(RCore.string.errorUserAlreadyPresent)

            InfomaniakCore.bearerToken = apiToken.accessToken
            val userProfileResponse = ApiRepository.getUserProfile(HttpClient.okHttpClientNoTokenInterceptor)

            if (userProfileResponse.result == ApiResponse.Status.ERROR) return userProfileResponse
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
                        MailboxController.updateMailboxes(context, mailboxes)

                        return@let if (mailboxes.none { it.isValid }) MailboxErrorCode.NO_VALID_MAILBOX else user
                    } ?: run {
                        getErrorResponse(RCore.string.serverError)
                    }
                }
            }
        }

        private fun getErrorResponse(@StringRes text: Int): ApiResponse<Any> {
            return ApiResponse(result = ApiResponse.Status.ERROR, translatedError = text)
        }
    }
}
