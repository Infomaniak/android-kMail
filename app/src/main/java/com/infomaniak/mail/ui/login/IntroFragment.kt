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

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.color.DynamicColors
import com.google.android.material.tabs.TabLayout
import com.infomaniak.lib.core.utils.capitalizeFirstChar
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.isNightModeEnabled
import com.infomaniak.mail.MatomoMail.trackOnBoardingEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.AccentColor
import com.infomaniak.mail.data.LocalSettings.AccentColor.BLUE
import com.infomaniak.mail.data.LocalSettings.AccentColor.PINK
import com.infomaniak.mail.databinding.FragmentIntroBinding
import com.infomaniak.mail.ui.login.IlluColors.Category
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.pinkColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuOnBoarding1BlueColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuOnBoarding1Colors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuOnBoarding1PinkColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuOnBoarding234BlueColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuOnBoarding234Colors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuOnBoarding234PinkColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuOnBoarding2BlueColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuOnBoarding2Colors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuOnBoarding2PinkColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuOnBoarding3BlueColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuOnBoarding3Colors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuOnBoarding3PinkColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuOnBoarding4BlueColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuOnBoarding4Colors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuOnBoarding4PinkColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuOnBoardingBlueColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuOnBoardingColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.illuOnBoardingPinkColors
import com.infomaniak.mail.ui.login.IlluColors.Companion.keyPath
import com.infomaniak.mail.ui.login.IlluColors.FinalLayer
import com.infomaniak.mail.utils.UiUtils.animateColorChange
import com.infomaniak.mail.utils.changePathColor
import com.infomaniak.mail.utils.enumValueFrom
import com.infomaniak.mail.utils.repeatFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            0 -> introViewModel.updatedAccentColor.value?.let { (newAccentColor, _) ->
                pinkBlueSwitch.isVisible = true

                if (!DynamicColors.isDynamicColorAvailable()) {
                    pinkBlueTabLayout.removeTab(pinkBlueTabLayout.getTabAt(AccentColor.SYSTEM.introTabIndex)!!)
                }

                val selectedTab = pinkBlueTabLayout.getTabAt(newAccentColor.introTabIndex)
                pinkBlueTabLayout.selectTab(selectedTab)
                setTabSelectedListener()

                iconLayout.apply {
                    setAnimation(R.raw.illu_onboarding_1)
                    repeatFrame(54, 138)
                }
            }
            1 -> {
                waveBackground.setImageResource(R.drawable.ic_back_wave_2)
                title.setText(R.string.onBoardingTitle2)
                description.setText(R.string.onBoardingDescription2)
                iconLayout.apply {
                    setAnimation(R.raw.illu_onboarding_2)
                    repeatFrame(108, 253)
                }
            }
            2 -> {
                waveBackground.setImageResource(R.drawable.ic_back_wave_3)
                title.setText(R.string.onBoardingTitle3)
                description.setText(R.string.onBoardingDescription3)
                iconLayout.apply {
                    setAnimation(R.raw.illu_onboarding_3)
                    repeatFrame(111, 187)
                }
            }
            3 -> {
                waveBackground.setImageResource(R.drawable.ic_back_wave_4)
                title.setText(R.string.onBoardingTitle4)
                description.setText(R.string.onBoardingDescription4)
                iconLayout.apply {
                    setAnimation(R.raw.illu_onboarding_4)
                    repeatFrame(127, 236)
                }
            }
        }

        updateUiWhenThemeChanges(navigationArgs.position)

        setUi(localSettings.accentColor, localSettings.accentColor, navigationArgs.position)
    }

    private fun setTabSelectedListener() = with(binding) {
        pinkBlueTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val newSelectedAccentColor = (AccentColor::introTabIndex enumValueFrom pinkBlueTabLayout.selectedTabPosition)!!
                val oldSelectedAccentColor = localSettings.accentColor
                localSettings.accentColor = newSelectedAccentColor
                triggerUiUpdateWhenAnimationEnd(newSelectedAccentColor, oldSelectedAccentColor)

                trackOnBoardingEvent("switchColor${newSelectedAccentColor.toString().capitalizeFirstChar()}")
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
    }

    private fun triggerUiUpdateWhenAnimationEnd(newAccentColor: AccentColor, oldAccentColor: AccentColor) {
        lifecycleScope.launch(Dispatchers.IO) {
            val duration = resources.getInteger(R.integer.loginLayoutAnimationDuration).toLong()
            delay(duration)
            introViewModel.updatedAccentColor.postValue(newAccentColor to oldAccentColor)
        }
    }

    private fun updateUiWhenThemeChanges(position: Int) {
        introViewModel.updatedAccentColor.observe(viewLifecycleOwner) { (newAccentColor, oldAccentColor) ->
            setUi(newAccentColor, oldAccentColor, position)
        }
    }

    /**
     * `animate` is necessary because when the activity is started, we want to avoid animating the color change the first time.
     */
    private fun setUi(newAccentColor: AccentColor, oldAccentColor: AccentColor, position: Int) {
        updateEachPageUi(newAccentColor, oldAccentColor)

        binding.iconLayout.changeIllustrationColors(position, newAccentColor)

        if (position == ACCENT_COLOR_PICKER_PAGE) updateAccentColorPickerPageUi(newAccentColor, oldAccentColor)
    }

    private fun LottieAnimationView.changeIllustrationColors(position: Int, accentColor: AccentColor) {
        val isDark = requireContext().isNightModeEnabled()

        illuOnBoardingColors.forEach { changePathColor(it, isDark) }
        when (position) {
            1, 2, 3 -> illuOnBoarding234Colors.forEach { changePathColor(it, isDark) }
        }

        when (position) {
            0 -> illuOnBoarding1Colors.forEach { changePathColor(it, isDark) }
            1 -> illuOnBoarding2Colors.forEach { changePathColor(it, isDark) }
            2 -> illuOnBoarding3Colors.forEach { changePathColor(it, isDark) }
            3 -> illuOnBoarding4Colors.forEach { changePathColor(it, isDark) }
        }

        if (accentColor == PINK) {
            illuOnBoardingPinkColors.forEach { changePathColor(it, isDark) }
            when (position) {
                1, 2, 3 -> illuOnBoarding234PinkColors.forEach { changePathColor(it, isDark) }
            }
            when (position) {
                0 -> illuOnBoarding1PinkColors.forEach { changePathColor(it, isDark) }
                1 -> illuOnBoarding2PinkColors.forEach { changePathColor(it, isDark) }
                2 -> illuOnBoarding3PinkColors.forEach { changePathColor(it, isDark) }
                3 -> illuOnBoarding4PinkColors.forEach { changePathColor(it, isDark) }
            }
        } else if (accentColor == BLUE) {
            illuOnBoardingBlueColors.forEach { changePathColor(it, isDark) }
            when (position) {
                1, 2, 3 -> illuOnBoarding234BlueColors.forEach { changePathColor(it, isDark) }
            }
            when (position) {
                0 -> illuOnBoarding1BlueColors.forEach { changePathColor(it, isDark) }
                1 -> illuOnBoarding2BlueColors.forEach { changePathColor(it, isDark) }
                2 -> illuOnBoarding3BlueColors.forEach { changePathColor(it, isDark) }
                3 -> illuOnBoarding4BlueColors.forEach { changePathColor(it, isDark) }
            }
        } else {
            val (
                illuOnBoardingAccentColors,
                illuOnBoarding234AccentColors,
                illuOnBoarding1AccentColors,
                illuOnBoarding2AccentColors,
                illuOnBoarding3AccentColors,
                illuOnBoarding4AccentColors
            ) = illuColorsFromAccentColor(accentColor)

            illuOnBoardingAccentColors.forEach { changePathColor(it, isDark) }
            when (position) {
                1, 2, 3 -> illuOnBoarding234AccentColors.forEach { changePathColor(it, isDark) }
            }
            when (position) {
                0 -> illuOnBoarding1AccentColors.forEach { changePathColor(it, isDark) }
                1 -> illuOnBoarding2AccentColors.forEach { changePathColor(it, isDark) }
                2 -> illuOnBoarding3AccentColors.forEach { changePathColor(it, isDark) }
                3 -> illuOnBoarding4AccentColors.forEach { changePathColor(it, isDark) }
            }

        }
    }

    private fun illuColorsFromAccentColor(accentColor: AccentColor): IlluOnBoardingColors {
        val palette = pinkColors

        val illuOnBoardingAccentColors = listOf(
            IlluColors(keyPath(Category.IPHONESCREEN, 1), palette[0]),
            IlluColors(keyPath(Category.IPHONESCREEN, 2), palette[0]),
            IlluColors(keyPath(Category.IPHONESCREEN, 3), palette[0]),
            IlluColors(keyPath(Category.IPHONESCREEN, 4), palette[0]),
            IlluColors(keyPath(Category.IPHONESCREEN, 5), palette[0]),
            IlluColors(keyPath(Category.IPHONESCREEN, 6), palette[0]),
            IlluColors(keyPath(Category.IPHONESCREEN, 9), palette[1]),
            IlluColors(keyPath(Category.IPHONESCREEN, 12), palette[2]),
            IlluColors(keyPath(Category.IPHONESCREEN, 15), palette[0]),
            IlluColors(keyPath(Category.IPHONESCREEN, 19), palette[0]),
            IlluColors(keyPath(Category.IPHONESCREEN, 20), palette[0]),
            IlluColors(keyPath(Category.IPHONESCREEN, 23), palette[0]),
            IlluColors(keyPath(Category.IPHONESCREEN, 24), palette[0]),
            IlluColors(keyPath(Category.IPHONESCREEN, 43), palette[3]),
            IlluColors(keyPath(Category.IPHONESCREEN, 48), palette[4]),
        )

        val illuOnBoarding234AccentColors = listOf(
            IlluColors(keyPath(Category.IPHONESCREEN, 54), palette[4]),
            IlluColors(keyPath(Category.IPHONESCREEN, 61), palette[5]),
            IlluColors(keyPath(Category.IPHONESCREEN, 67), palette[0]),
            IlluColors(keyPath(Category.IPHONESCREEN, 72), palette[6]),
        )

        val illuOnBoarding1AccentColors = listOf(
            IlluColors(keyPath(Category.CHAT, 1, 1), palette[0]),
            IlluColors(keyPath(Category.CHAT, 1, 2), palette[3]),
            IlluColors(keyPath(Category.IPHONESCREEN, 55), palette[5]),
            IlluColors(keyPath(Category.IPHONESCREEN, 61), palette[0]),
            IlluColors(keyPath(Category.IPHONESCREEN, 66), palette[6]),
        )

        val illuOnBoarding2AccentColors = listOf(
            IlluColors(keyPath(Category.NOTIFICATION, 4, 2), palette[4]),
            IlluColors(keyPath(Category.NOTIFICATION, 11, 2), palette[0]),
            IlluColors(keyPath(Category.HAND, 1), palette[6]),
            IlluColors(keyPath(Category.HAND, 4), palette[7]),
            IlluColors(keyPath(Category.HAND, 5), palette[7]),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 15, 1), palette[0]),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 4, 2), palette[4]),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 11, 2), palette[0]),
        )

        val illuOnBoarding3AccentColors = listOf(
            IlluColors(keyPath(Category.NOTIFICATION, 4, 2), palette[0]),
            IlluColors(keyPath(Category.NOTIFICATION, 5, 2), palette[8]),
            IlluColors(keyPath(Category.NOTIFICATION, 4, 3), palette[0]),
            IlluColors(keyPath(Category.NOTIFICATION, 5, 3), palette[8]),
            IlluColors(keyPath(Category.NOTIFICATION, 4, 4), palette[0]),
            IlluColors(keyPath(Category.NOTIFICATION, 5, 4), palette[8]),
            IlluColors(keyPath(Category.HAND, 1), palette[6]),
            IlluColors(keyPath(Category.HAND, 4), palette[7]),
            IlluColors(keyPath(Category.HAND, 5), palette[7]),
            IlluColors(keyPath(Category.STAR, 1, finalLayer = FinalLayer.BORDER), palette[0]),
            IlluColors(keyPath(Category.BIN, 1, finalLayer = FinalLayer.BORDER), palette[0]),
            IlluColors(keyPath(Category.BIN, 2, finalLayer = FinalLayer.BORDER), palette[0]),
            IlluColors(keyPath(Category.BIN, 3, finalLayer = FinalLayer.BORDER), palette[0]),
            IlluColors(keyPath(Category.BIN, 4, finalLayer = FinalLayer.BORDER), palette[0]),
            IlluColors(keyPath(Category.BIN, 5, finalLayer = FinalLayer.BORDER), palette[0]),
            IlluColors(keyPath(Category.BIN, 6, finalLayer = FinalLayer.BORDER), palette[0]),
            IlluColors(keyPath(Category.CLOCK, 1), palette[0]),
            IlluColors(keyPath(Category.CLOCK, 2), palette[0]),
            IlluColors(keyPath(Category.CLOCK, 3), palette[0]),
            IlluColors(keyPath(Category.CLOCK, 4), palette[0]),
        )


        val illuOnBoarding4AccentColors = listOf(
            IlluColors(keyPath(Category.WOMAN, 4), palette[4]),
            IlluColors(keyPath(Category.MEN, 5), palette[3]),
            IlluColors(keyPath(Category.POINT, 1, 1), palette[4]),
            IlluColors(keyPath(Category.POINT, 1, 2), palette[4]),
            IlluColors(keyPath(Category.POINT, 1, 3), palette[3]),
            IlluColors(keyPath(Category.POINT, 1, 4), palette[3]),
            IlluColors(keyPath(Category.LETTER, 1), palette[9]),
            IlluColors(keyPath(Category.LETTER, 2), palette[10]),
            IlluColors(keyPath(Category.LETTER, 5), palette[11]),
            IlluColors(keyPath(Category.LETTER, 6), palette[12]),
            IlluColors(keyPath(Category.LETTER, 7), palette[12]),
        )


        return IlluOnBoardingColors(
            illuOnBoardingAccentColors,
            illuOnBoarding234AccentColors,
            illuOnBoarding1AccentColors,
            illuOnBoarding2AccentColors,
            illuOnBoarding3AccentColors,
            illuOnBoarding4AccentColors,
        )
    }

    private fun updateEachPageUi(newAccentColor: AccentColor, oldAccentColor: AccentColor) {
        val newColor = newAccentColor.getOnboardingSecondaryBackground(requireContext())
        val oldColor = oldAccentColor.getOnboardingSecondaryBackground(requireContext())
        animateColorChange(oldColor, newColor) { color ->
            binding.waveBackground.imageTintList = ColorStateList.valueOf(color)
        }
    }

    private fun updateAccentColorPickerPageUi(newAccentColor: AccentColor, oldAccentColor: AccentColor) {
        animateTabIndicatorColor(newAccentColor, oldAccentColor)
    }

    private fun animateTabIndicatorColor(newAccentColor: AccentColor, oldAccentColor: AccentColor) = with(binding) {
        val newPrimary = newAccentColor.getPrimary(context)
        val oldPrimary = oldAccentColor.getPrimary(context)
        animateColorChange(oldPrimary, newPrimary, applyColor = pinkBlueTabLayout::setSelectedTabIndicatorColor)
    }

    private companion object {
        const val ACCENT_COLOR_PICKER_PAGE = 0
    }

    data class IlluOnBoardingColors(
        val illuOnBoardingAccentColors: List<IlluColors>,
        val illuOnBoarding234AccentColors: List<IlluColors>,
        val illuOnBoarding1AccentColors: List<IlluColors>,
        val illuOnBoarding2AccentColors: List<IlluColors>,
        val illuOnBoarding3AccentColors: List<IlluColors>,
        val illuOnBoarding4AccentColors: List<IlluColors>,
    )
}
