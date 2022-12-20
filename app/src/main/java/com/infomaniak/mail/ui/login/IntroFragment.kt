/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.google.android.material.tabs.TabLayout
import com.infomaniak.lib.core.utils.isNightModeEnabled
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.AccentColor
import com.infomaniak.mail.data.LocalSettings.AccentColor.BLUE
import com.infomaniak.mail.data.LocalSettings.AccentColor.PINK
import com.infomaniak.mail.databinding.FragmentIntroBinding
import com.infomaniak.mail.ui.login.IlluColors.Companion.illu1BlueColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illu1Colors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illu1PinkColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illu234BlueColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illu234Colors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illu234PinkColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illu2BlueColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illu2Colors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illu2PinkColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illu3BlueColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illu3Colors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illu3PinkColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illu4BlueColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illu4Colors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illu4PinkColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuBlueColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuPinkColors
import com.infomaniak.mail.utils.UiUtils.animateColorChange
import com.infomaniak.mail.utils.getAttributeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.android.material.R as RMaterial

class IntroFragment : Fragment() {

    private lateinit var binding: FragmentIntroBinding
    private val navigationArgs: IntroFragmentArgs by navArgs()
    private val introViewModel: IntroViewModel by activityViewModels()

    private val localSettings by lazy { LocalSettings.getInstance(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentIntroBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        when (navigationArgs.position) {
            0 -> introViewModel.currentAccentColor.value?.let { accentColor ->
                pinkBlueSwitch.isVisible = true
                val selectedTab = pinkBlueTabLayout.getTabAt(accentColor.introTabIndex)
                pinkBlueTabLayout.selectTab(selectedTab)
                setTabSelectedListener()
                iconLayout.apply {
                    setAnimation(R.raw.illu_1)
                    repeatFrame(54, 138)
                }
            }
            1 -> {
                waveBackground.setImageResource(R.drawable.ic_back_wave_2)
                title.setText(R.string.onBoardingTitle2)
                description.setText(R.string.onBoardingDescription2)
                iconLayout.apply {
                    setAnimation(R.raw.illu_2)
                    repeatFrame(108, 253)
                }
            }
            2 -> {
                waveBackground.setImageResource(R.drawable.ic_back_wave_3)
                title.setText(R.string.onBoardingTitle3)
                description.setText(R.string.onBoardingDescription3)
                iconLayout.apply {
                    setAnimation(R.raw.illu_3)
                    repeatFrame(111, 187)
                }
            }
            3 -> {
                waveBackground.setImageResource(R.drawable.ic_back_wave_4)
                title.setText(R.string.onBoardingTitle4)
                description.setText(R.string.onBoardingDescription4)
                iconLayout.apply {
                    setAnimation(R.raw.illu_4)
                    repeatFrame(127, 236)
                }
            }
        }

        iconLayout.addLottieOnCompositionLoadedListener {
            iconLayout.resolveKeyPath(KeyPath("**")).forEach {
                Log.d("keypath", it.keysToString())
            }
        }

        updateUiWhenThemeChanges(navigationArgs.position)

        setUi(localSettings.accentColor, navigationArgs.position)
    }

    private fun LottieAnimationView.repeatFrame(firstFrame: Int, lastFrame: Int) {
        addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) = Unit

            override fun onAnimationEnd(animation: Animator) {
                removeAllAnimatorListeners()
                repeatCount = ValueAnimator.INFINITE
                setMinAndMaxFrame(firstFrame, lastFrame)
                playAnimation()
            }

            override fun onAnimationCancel(animation: Animator) = Unit

            override fun onAnimationRepeat(animation: Animator) = Unit
        })
    }

    private fun setTabSelectedListener() = with(binding) {
        pinkBlueTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val newSelectedAccentColor = if (pinkBlueTabLayout.selectedTabPosition == PINK.introTabIndex) PINK else BLUE
                localSettings.accentColor = newSelectedAccentColor
                triggerUiUpdateWhenAnimationEnd(newSelectedAccentColor)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
    }

    private fun triggerUiUpdateWhenAnimationEnd(accentColor: AccentColor) {
        lifecycleScope.launch(Dispatchers.IO) {
            val duration = resources.getInteger(R.integer.loginLayoutAnimationDuration).toLong()
            delay(duration)
            introViewModel.currentAccentColor.postValue(accentColor)
        }
    }

    private fun updateUiWhenThemeChanges(position: Int) {
        introViewModel.currentAccentColor.observe(viewLifecycleOwner) { accentColor ->
            setUi(accentColor, position)
        }
    }

    /**
     * `animate` is necessary because when the activity is started, we want to avoid animating the color change the first time.
     */
    private fun setUi(accentColor: AccentColor, position: Int) {
        updateEachPageUi(accentColor)

        binding.iconLayout.changeIllustrationColors(position, accentColor)

        if (position == ACCENT_COLOR_PICKER_PAGE) updateAccentColorPickerPageUi(accentColor)
    }

    private fun LottieAnimationView.changeIllustrationColors(position: Int, accentColor: AccentColor) {
        val isDark = requireContext().isNightModeEnabled()

        illuColors.forEach { changePathColor(it, isDark) }
        when (position) {
            1, 2, 3 -> illu234Colors.forEach { changePathColor(it, isDark) }
        }

        when (position) {
            0 -> illu1Colors.forEach { changePathColor(it, isDark) }
            1 -> illu2Colors.forEach { changePathColor(it, isDark) }
            2 -> illu3Colors.forEach { changePathColor(it, isDark) }
            3 -> illu4Colors.forEach { changePathColor(it, isDark) }
        }

        if (accentColor == PINK) {
            illuPinkColors.forEach { changePathColor(it, isDark) }
            when (position) {
                1, 2, 3 -> illu234PinkColors.forEach { changePathColor(it, isDark) }
            }
            when (position) {
                0 -> illu1PinkColors.forEach { changePathColor(it, isDark) }
                1 -> illu2PinkColors.forEach { changePathColor(it, isDark) }
                2 -> illu3PinkColors.forEach { changePathColor(it, isDark) }
                3 -> illu4PinkColors.forEach { changePathColor(it, isDark) }
            }
        } else {
            illuBlueColors.forEach { changePathColor(it, isDark) }
            when (position) {
                1, 2, 3 -> illu234BlueColors.forEach { changePathColor(it, isDark) }
            }
            when (position) {
                0 -> illu1BlueColors.forEach { changePathColor(it, isDark) }
                1 -> illu2BlueColors.forEach { changePathColor(it, isDark) }
                2 -> illu3BlueColors.forEach { changePathColor(it, isDark) }
                3 -> illu4BlueColors.forEach { changePathColor(it, isDark) }
            }
        }
    }

    private fun LottieAnimationView.changePathColor(illuColors: IlluColors, isDark: Boolean) {
        val color = if (isDark) illuColors.getDarkColor() else illuColors.getLightColor()
        addValueCallback(
            illuColors.keyPath,
            LottieProperty.COLOR_FILTER
        ) { SimpleColorFilter(color) }
    }

    private fun updateEachPageUi(accentColor: AccentColor) {
        val newColor = accentColor.getSecondaryBackground(requireContext())
        val oldColor = requireActivity().window.statusBarColor
        animateColorChange(oldColor, newColor) { color ->
            binding.waveBackground.imageTintList = ColorStateList.valueOf(color)
        }
    }

    private fun updateAccentColorPickerPageUi(accentColor: AccentColor) {
        animateTabIndicatorAndTextColor(accentColor, requireContext())
        animateTabBackgroundColor(accentColor, requireContext())
    }

    private fun animateTabIndicatorAndTextColor(accentColor: AccentColor, context: Context) = with(binding) {
        val isPink = accentColor == PINK
        val newPrimary = accentColor.getPrimary(context)
        val colorOnPrimary = context.getAttributeColor(RMaterial.attr.colorOnPrimary)
        val oldPrimary = if (isPink) BLUE.getPrimary(context) else PINK.getPrimary(context)

        animateColorChange(oldPrimary, newPrimary) { color ->
            pinkBlueTabLayout.setSelectedTabIndicatorColor(color)
        }
        animateColorChange(newPrimary, oldPrimary) { color ->
            pinkBlueTabLayout.setTabTextColors(color, colorOnPrimary)
        }
    }

    private fun animateTabBackgroundColor(accentColor: AccentColor, context: Context) = with(binding) {
        val isPink = accentColor == PINK
        val tabBackgroundRes = if (isPink) R.color.blueBoardingSecondaryBackground else R.color.pinkBoardingSecondaryBackground
        val tabBackground = ContextCompat.getColor(context, tabBackgroundRes)
        val oldBackground = if (isPink) {
            BLUE.getSecondaryBackground(context)
        } else {
            PINK.getSecondaryBackground(context)
        }

        animateColorChange(oldBackground, tabBackground) { color ->
            pinkBlueTabLayout.setBackgroundColor(color)
        }
    }

    private companion object {
        const val ACCENT_COLOR_PICKER_PAGE = 0
    }
}
