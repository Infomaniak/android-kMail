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
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.login.InfomaniakLogin
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings.AccentColor
import com.infomaniak.mail.databinding.FragmentNewAccountBinding
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MainDispatcher
import com.infomaniak.mail.utils.LoginUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@AndroidEntryPoint
class NewAccountFragment : Fragment() {

    private lateinit var binding: FragmentNewAccountBinding
    private val introViewModel: IntroViewModel by activityViewModels()

    private val loginActivity by lazy { requireActivity() as LoginActivity }

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    @MainDispatcher
    lateinit var mainDispatcher: CoroutineDispatcher

    @Inject
    lateinit var loginUtils: LoginUtils

    private lateinit var webViewLoginResultLauncher: ActivityResultLauncher<Intent>

    private val createAccountResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        result.handleCreateAccountActivityResult()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        webViewLoginResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
            loginUtils.handleWebViewLoginResult(this, result, loginActivity.infomaniakLogin, ::onFailedLogin)
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
        requireActivity().window.statusBarColor = context.getColor(R.color.backgroundColor)

        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        selectIllustrationAccordingToTheme()

        loginUtils.initShowError(::showError)

        createNewAddressButton.setOnClickListener {
            createNewAddressButton.isEnabled = false
            requireContext().trackAccountEvent("openCreationWebview")
            loginActivity.infomaniakLogin.startCreateAccountWebView(
                resultLauncher = createAccountResultLauncher,
                createAccountUrl = BuildConfig.CREATE_ACCOUNT_URL,
                successHost = BuildConfig.CREATE_ACCOUNT_SUCCESS_HOST,
                cancelHost = BuildConfig.CREATE_ACCOUNT_CANCEL_HOST,
            )
        }
    }

    private fun selectIllustrationAccordingToTheme() = with(binding) {
        val drawableRes = when (introViewModel.updatedAccentColor.value?.first) {
            AccentColor.PINK -> R.drawable.new_account_illustration_pink
            AccentColor.BLUE -> R.drawable.new_account_illustration_blue
            AccentColor.SYSTEM -> R.drawable.new_account_illustration_material
            null -> null
        }

        val drawable = ResourcesCompat.getDrawable(resources, drawableRes!!, null)
        binding.illustration.setImageDrawable(drawable)
    }

    private fun ActivityResult.handleCreateAccountActivityResult() {
        if (resultCode == AppCompatActivity.RESULT_OK) {
            val translatedError = data?.getStringExtra(InfomaniakLogin.ERROR_TRANSLATED_TAG)
            when {
                translatedError.isNullOrBlank() -> loginActivity.infomaniakLogin.startWebViewLogin(
                    webViewLoginResultLauncher,
                    false
                )
                else -> showError(translatedError)
            }
        }
        onFailedLogin()
    }

    private fun showError(error: String) {
        showSnackbar(error)
        onFailedLogin()
    }

    private fun onFailedLogin() {
        binding.createNewAddressButton.isEnabled = true
    }
}
