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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.infomaniak.core.legacy.utils.SnackbarUtils.showSnackbar
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.lib.login.InfomaniakLogin
import com.infomaniak.mail.CREATE_ACCOUNT_CANCEL_HOST
import com.infomaniak.mail.CREATE_ACCOUNT_SUCCESS_HOST
import com.infomaniak.mail.CREATE_ACCOUNT_URL
import com.infomaniak.mail.MatomoMail.MatomoName
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

    private var binding: FragmentNewAccountBinding by safeBinding()
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

    private val createAccountResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        result.handleCreateAccountActivityResult()
    }

    private val webViewLoginResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        loginUtils.handleWebViewLoginResult(fragment = this, result, loginActivity.infomaniakLogin, ::onFailedLogin)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        introViewModel.updatedAccentColor.value?.first?.theme?.let(requireActivity()::setTheme)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentNewAccountBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        selectIllustrationAccordingToTheme()

        loginUtils.initShowError(::showError)

        createNewAddressButton.setOnClickListener {
            createNewAddressButton.isEnabled = false
            trackAccountEvent(MatomoName.OpenCreationWebview)
            loginActivity.infomaniakLogin.startCreateAccountWebView(
                resultLauncher = createAccountResultLauncher,
                createAccountUrl = CREATE_ACCOUNT_URL,
                successHost = CREATE_ACCOUNT_SUCCESS_HOST,
                cancelHost = CREATE_ACCOUNT_CANCEL_HOST,
            )
        }
    }

    private fun selectIllustrationAccordingToTheme() {
        val accentColor = introViewModel.updatedAccentColor.value?.first ?: return

        val drawableRes = when (accentColor) {
            AccentColor.PINK -> R.drawable.new_account_illustration_pink
            AccentColor.BLUE -> R.drawable.new_account_illustration_blue
            AccentColor.SYSTEM -> R.drawable.new_account_illustration_material
        }

        val drawable = ResourcesCompat.getDrawable(resources, drawableRes, null)
        binding.illustration.setImageDrawable(drawable)
    }

    private fun ActivityResult.handleCreateAccountActivityResult() {
        if (resultCode == AppCompatActivity.RESULT_OK) {
            val translatedError = data?.getStringExtra(InfomaniakLogin.ERROR_TRANSLATED_TAG)
            when {
                translatedError.isNullOrBlank() -> loginActivity.infomaniakLogin.startWebViewLogin(
                    resultLauncher = webViewLoginResultLauncher,
                    removeCookies = false,
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
