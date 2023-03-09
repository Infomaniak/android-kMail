/*
 * Infomaniak kMail - Android
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
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import com.infomaniak.lib.core.models.ApiError
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.Utils.lockOrientationForSmallScreens
import com.infomaniak.lib.core.utils.hideProgress
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.showProgress
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.lib.login.InfomaniakLogin
import com.infomaniak.lib.login.InfomaniakLogin.ErrorStatus
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.MatomoMail.trackScreen
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings.AccentColor
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.databinding.ActivityLoginBinding
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.UiUtils.animateColorChange
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.getInfomaniakLogin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.infomaniak.lib.core.R as RCore

class LoginActivity : AppCompatActivity() {

    private val binding by lazy { ActivityLoginBinding.inflate(layoutInflater) }
    private val navigationArgs by lazy { LoginActivityArgs.fromBundle(intent.extras ?: bundleOf()) }
    private val introViewModel: IntroViewModel by viewModels()

    private lateinit var infomaniakLogin: InfomaniakLogin

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

        introViewModel.currentAccentColor.observe(this@LoginActivity) { accentColor ->
            updateUi(accentColor)
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

    private fun updateUi(accentColor: AccentColor) = with(binding) {
        animatePrimaryColorElements(accentColor)
        animateSecondaryColorElements(accentColor)
    }

    private fun authenticateUser(authCode: String) = lifecycleScope.launch(Dispatchers.IO) {
        infomaniakLogin.getToken(
            okHttpClient = HttpClient.okHttpClientNoInterceptor,
            code = authCode,
            onSuccess = {
                lifecycleScope.launch(Dispatchers.IO) {
                    when (val user = authenticateUser(this@LoginActivity, it)) {
                        is User -> {
                            trackAccountEvent("loggedIn")
                            AccountUtils.reloadApp?.invoke()
                        }
                        is ApiResponse<*> -> withContext(Dispatchers.Main) {
                            if (user.error?.code == NO_MAILBOX_ERROR_CODE) {
                                launchNoMailboxActivity()
                            } else {
                                showError(getString(user.translatedError))
                            }
                        }
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

    private fun showError(error: String) {
        showSnackbar(error)
        enableConnectButtons()
    }

    private fun enableConnectButtons() = with(binding) {
        connectButton.hideProgress(RCore.string.connect)
        signInButton.isEnabled = true
    }

    private fun animatePrimaryColorElements(accentColor: AccentColor) = with(binding) {
        val newPrimary = accentColor.getPrimary(this@LoginActivity)
        val oldPrimary = dotsIndicator.selectedDotColor
        val ripple = accentColor.getRipple(this@LoginActivity)

        animateColorChange(oldPrimary, newPrimary) { color ->
            val singleColorStateList = ColorStateList.valueOf(color)
            dotsIndicator.selectedDotColor = color
            connectButton.setBackgroundColor(color)
            nextButton.backgroundTintList = singleColorStateList
            signInButton.setTextColor(color)
            signInButton.rippleColor = ColorStateList.valueOf(ripple)
        }
    }

    private fun animateSecondaryColorElements(accentColor: AccentColor) {
        val newSecondaryBackground = accentColor.getSecondaryBackground(this@LoginActivity)
        val oldSecondaryBackground = window.statusBarColor
        animateColorChange(oldSecondaryBackground, newSecondaryBackground) { color ->
            window.statusBarColor = color
        }
    }

    private fun ViewPager2.removeOverScroll() {
        (getChildAt(0) as? RecyclerView)?.overScrollMode = View.OVER_SCROLL_NEVER
    }

    private fun launchNoMailboxActivity() = with(binding) {
        startActivity(Intent(this@LoginActivity, NoMailboxActivity::class.java))
        connectButton.hideProgress(R.string.buttonLogin)
        signInButton.isEnabled = true
    }

    companion object {

        private const val NO_MAILBOX_ERROR_CODE = "no_mailbox"

        suspend fun authenticateUser(context: Context, apiToken: ApiToken): Any {

            return if (AccountUtils.getUserById(apiToken.userId) == null) {
                InfomaniakCore.bearerToken = apiToken.accessToken
                val userProfileResponse = ApiRepository.getUserProfile(HttpClient.okHttpClientNoInterceptor)

                if (userProfileResponse.result == ApiResponse.Status.ERROR) {
                    userProfileResponse
                } else {
                    val user = userProfileResponse.data?.apply {
                        this.apiToken = apiToken
                        this.organizations = arrayListOf()
                    }

                    user?.let {
                        val apiResponse = ApiRepository.getMailboxes(HttpClient.okHttpClientNoInterceptor)
                        when {
                            !apiResponse.isSuccess() -> apiResponse
                            apiResponse.data?.isEmpty() == true -> {
                                ApiResponse<List<Mailbox>>(
                                    result = ApiResponse.Status.ERROR,
                                    error = ApiError(code = NO_MAILBOX_ERROR_CODE),
                                )
                            }
                            else -> {
                                apiResponse.data?.let { mailboxes ->
                                    context.trackEvent("userInfo", "nbMailboxes", TrackerAction.DATA, mailboxes.count().toFloat())
                                    AccountUtils.addUser(it)
                                    MailboxController.updateMailboxes(context, mailboxes)

                                    it
                                } ?: run {
                                    getErrorResponse(RCore.string.serverError)
                                }
                            }
                        }
                    } ?: getErrorResponse(RCore.string.anErrorHasOccurred)
                }
            } else {
                getErrorResponse(RCore.string.errorUserAlreadyPresent)
            }
        }

        private fun getErrorResponse(@StringRes text: Int): ApiResponse<Any> {
            return ApiResponse(result = ApiResponse.Status.ERROR, translatedError = text)
        }
    }
}
