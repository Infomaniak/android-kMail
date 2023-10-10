/*
 * Infomaniak Mail - Android
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

import android.content.res.ColorStateList
import android.os.Build
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
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.infomaniak.lib.core.R
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.data.LocalSettings.AccentColor
import com.infomaniak.mail.databinding.FragmentLoginBinding
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MainDispatcher
import com.infomaniak.mail.utils.LoginUtils
import com.infomaniak.mail.utils.UiUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var binding: FragmentLoginBinding by safeBinding()
    private val navigationArgs by lazy { LoginActivityArgs.fromBundle(requireActivity().intent.extras ?: bundleOf()) }
    private val introViewModel: IntroViewModel by activityViewModels()

    private val loginActivity by lazy { requireActivity() as LoginActivity }

    private val connectButtonProgressTimer by lazy {
        Utils.createRefreshTimer(onTimerFinish = binding.connectButton::showProgress)
    }

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
                connectButtonProgressTimer.start()
                requireContext().trackAccountEvent("openLoginWebview")
                loginActivity.infomaniakLogin.startWebViewLogin(webViewLoginResultLauncher)
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
        animateSecondaryColorElements(newAccentColor, oldAccentColor)
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

    private fun showError(error: String) {
        showSnackbar(error)
        resetLoginButtons()
    }

    private fun resetLoginButtons() = with(binding) {
        connectButton.hideProgress(R.string.connect)
        signInButton.isEnabled = true
    }

    private fun ViewPager2.removeOverScroll() {
        (getChildAt(0) as? RecyclerView)?.overScrollMode = View.OVER_SCROLL_NEVER
    }

    private fun getViewPagerCurrentItem(): Int = binding.introViewpager.currentItem

    private fun goBackAPage() {
        binding.introViewpager.currentItem -= 1
    }
}
