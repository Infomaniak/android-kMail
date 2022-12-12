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
import android.graphics.Color
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
                    repeatFrame(118, 225)
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
    private fun setUi(accentColor: AccentColor, position: Int) = with(binding) {
        updateEachPageUi(accentColor)
        val isDark = requireContext().isNightModeEnabled()
        if (accentColor == PINK) {
            when (position) {
                0 -> illu1PinkColors.forEach { iconLayout.changePathColor(it, isDark) }
                1 -> illu2PinkColors.forEach { iconLayout.changePathColor(it, isDark) }
                2 -> illu3PinkColors.forEach { iconLayout.changePathColor(it, isDark) }
                3 -> illu4PinkColors.forEach { iconLayout.changePathColor(it, isDark) }
            }
        } else {
            when (position) {
                0 -> illu1BlueColors.forEach { iconLayout.changePathColor(it, isDark) }
                1 -> illu2BlueColors.forEach { iconLayout.changePathColor(it, isDark) }
                2 -> illu3BlueColors.forEach { iconLayout.changePathColor(it, isDark) }
                3 -> illu4BlueColors.forEach { iconLayout.changePathColor(it, isDark) }
            }
        }

        if (position == ACCENT_COLOR_PICKER_PAGE) updateAccentColorPickerPageUi(accentColor)
    }

    private fun LottieAnimationView.changePathColor(illuColors: IlluColors, isDark: Boolean) {
        val color = if (isDark) illuColors.getDarkColor() else illuColors.getLightColor()
        addValueCallback(
            illuColors.keyPath,
            LottieProperty.COLOR_FILTER
        ) { SimpleColorFilter(color) }
    }

    private fun updateEachPageUi(accentColor: AccentColor) = with(binding) {
        val newColor = accentColor.getSecondaryBackground(requireContext())
        val oldColor = requireActivity().window.statusBarColor
        animateColorChange(oldColor, newColor) { color ->
            waveBackground.imageTintList = ColorStateList.valueOf(color)
        }
    }

    private fun updateAccentColorPickerPageUi(accentColor: AccentColor) = with(binding) {
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

    class IlluColors(val keyPath: KeyPath, val lightColor: String, val darkColor: String) {
        fun getLightColor() = Color.parseColor(lightColor)
        fun getDarkColor() = Color.parseColor(darkColor)
    }

    private companion object {
        const val ACCENT_COLOR_PICKER_PAGE = 0

        val illu1PinkColors = arrayOf(
            IlluColors(KeyPath("POINT 3", "Groupe 1", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("POINT 2", "Groupe 1", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("POINT 1", "Groupe 1", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("POINT 4", "Groupe 1", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("POINT 5", "Groupe 1", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("POINT 6", "Groupe 1", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("CHAT 1", "Groupe 1", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("CHAT 2", "Groupe 1", "Fond 1"), lightColor = "#BD95A7", darkColor = "#AE366D"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 1", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 2", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 3", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 4", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 5", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 6", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 9", "Fond 1"), lightColor = "#FF5B97", darkColor = "#EF0057"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 9", "Fond 1"), lightColor = "#FF5B97", darkColor = "#EF0057"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 12", "Fond 1"), lightColor = "#AB2456", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 15", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 18", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 19", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 20", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 22", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 23", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 24", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 25", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 26", "Fond 1"), lightColor = "#FAFAFA", darkColor = "#282828"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 27", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 28", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 29", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 30", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 31", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 32", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 33", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 34", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 35", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 36", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 37", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 38", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 39", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 43", "Fond 1"), lightColor = "#BD95A7", darkColor = "#AE366D"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 44", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 48", "Fond 1"), lightColor = "#BF4C80", darkColor = "#E75F9C"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 49", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 50", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 55", "Fond 1"), lightColor = "#DFBDCC", darkColor = "#955873"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 56", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 61", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 62", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 66", "Fond 1"), lightColor = "#824D65", darkColor = "#AB6685"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 67", "Fond 1"), lightColor = "#340E00", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 68", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 69", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 70", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
        )

        val illu2PinkColors = arrayOf(
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 4", "Fond 1"), lightColor = "#BF4C80", darkColor = "#E75F9C"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 5", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 6", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 9", "Fond 1"), lightColor = "#CCCCCC", darkColor = "#818181"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 10", "Fond 1"), lightColor = "#CCCCCC", darkColor = "#818181"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 11", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 12", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 13", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 14", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 1", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 2", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 3", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 4", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 5", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 6", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 9", "Fond 1"), lightColor = "#FF5B97", darkColor = "#EF0057"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 12", "Fond 1"), lightColor = "#AB2456", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 15", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 18", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 19", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 20", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 22", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 23", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 24", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 25", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 26", "Fond 1"), lightColor = "#FAFAFA", darkColor = "#282828"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 27", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 28", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 29", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 30", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 31", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 32", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 33", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 34", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 35", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 36", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 37", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 38", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 39", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 43", "Fond 1"), lightColor = "#BD95A7", darkColor = "#AE366D"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 44", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 48", "Fond 1"), lightColor = "#BF4C80", darkColor = "#E75F9C"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 49", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 50", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 54", "Fond 1"), lightColor = "#BF4C80", darkColor = "#E75F9C"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 55", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 56", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 61", "Fond 1"), lightColor = "#DFBDCC", darkColor = "#955873"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 62", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 67", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 68", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 70", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 72", "Fond 1"), lightColor = "#824D65", darkColor = "#AB6685"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 73", "Fond 1"), lightColor = "#340E00", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 74", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 75", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 76", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("HAND", "Groupe 1", "Fond 1"), lightColor = "#824D65", darkColor = "#AB6685"),
            IlluColors(KeyPath("HAND", "Groupe 4", "Fond 1"), lightColor = "#693D51", darkColor = "#CA799E"),
            IlluColors(KeyPath("HAND", "Groupe 5", "Fond 1"), lightColor = "#693D51", darkColor = "#CA799E"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 4", "Fond 1"), lightColor = "#C4C4C4", darkColor = "#7C7C7C"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 5", "Fond 1"), lightColor = "#C4C4C4", darkColor = "#7C7C7C"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 6", "Fond 1"), lightColor = "#C4C4C4", darkColor = "#7C7C7C"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 7", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#A1A1A1"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 8", "Fond 1"), lightColor = "#CCCCCC", darkColor = "#818181"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 9", "Fond 1"), lightColor = "#CCCCCC", darkColor = "#818181"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 10", "Fond 1"), lightColor = "#CCCCCC", darkColor = "#818181"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 13", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 14", "Fond 1"), lightColor = "#C4C4C4", darkColor = "#7C7C7C"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 15", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 4", "Fond 1"), lightColor = "#BF4C80", darkColor = "#E75F9C"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 5", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 6", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 7", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 8", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 9", "Fond 1"), lightColor = "#CCCCCC", darkColor = "#818181"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 10", "Fond 1"), lightColor = "#CCCCCC", darkColor = "#818181"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 11", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 12", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 13", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 15", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
        )

        val illu3PinkColors = arrayOf(
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 1", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 2", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 3", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#A1A1A1"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 4", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 4", "Fond 1 "), lightColor = "#F7E8EF", darkColor = "#282828"),
            IlluColors(KeyPath("NOTIFICATION 3", "Groupe 1", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("NOTIFICATION 3", "Groupe 2", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("NOTIFICATION 3", "Groupe 3", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#A1A1A1"),
            IlluColors(KeyPath("NOTIFICATION 3", "Groupe 4", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("NOTIFICATION 3", "Groupe 4", "Fond 1 "), lightColor = "#F7E8EF", darkColor = "#282828"),
            IlluColors(KeyPath("NOTIFICATION 4", "Groupe 1", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("NOTIFICATION 4", "Groupe 2", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("NOTIFICATION 4", "Groupe 3", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#A1A1A1"),
            IlluColors(KeyPath("NOTIFICATION 4", "Groupe 4", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("NOTIFICATION 4", "Groupe 4", "Fond 1 "), lightColor = "#F7E8EF", darkColor = "#282828"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 1", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 2", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 3", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 4", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 5", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 6", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 9", "Fond 1"), lightColor = "#FF5B97", darkColor = "#EF0057"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 12", "Fond 1"), lightColor = "#AB2456", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 15", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 18", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 19", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 20", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 22", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 23", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 24", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 25", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 26", "Fond 1"), lightColor = "#FAFAFA", darkColor = "#282828"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 27", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 28", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 29", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 30", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 31", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 32", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 33", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 34", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 35", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 36", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 37", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 38", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 39", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 43", "Fond 1"), lightColor = "#BD95A7", darkColor = "#AE366D"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 44", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 48", "Fond 1"), lightColor = "#BF4C80", darkColor = "#E75F9C"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 49", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 50", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 54", "Fond 1"), lightColor = "#BF4C80", darkColor = "#E75F9C"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 55", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 56", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 61", "Fond 1"), lightColor = "#DFBDCC", darkColor = "#955873"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 62", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 67", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 68", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 70", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 72", "Fond 1"), lightColor = "#824D65", darkColor = "#AB6685"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 73", "Fond 1"), lightColor = "#340E00", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 74", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 75", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 76", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("HAND", "Groupe 1", "Fond 1"), lightColor = "#824D65", darkColor = "#AB6685"),
            IlluColors(KeyPath("HAND", "Groupe 4", "Fond 1"), lightColor = "#693D51", darkColor = "#CA799E"),
            IlluColors(KeyPath("HAND", "Groupe 4", "Fond 1"), lightColor = "#693D51", darkColor = "#CA799E"),
            IlluColors(KeyPath("STAR", "Groupe 1", "Contour 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("STAR", "Groupe 2", "Fond 1"), lightColor = "#FAFAFA", darkColor = "#282828"),
            IlluColors(KeyPath("BEN", "Groupe 1", "Contour 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("BEN", "Groupe 2", "Contour 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("BEN", "Groupe 3", "Contour 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("BEN", "Groupe 4", "Contour 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("BEN", "Groupe 5", "Contour 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("BEN", "Groupe 6", "Contour 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("BEN", "Groupe 7", "Fond 1"), lightColor = "#FAFAFA", darkColor = "#282828"),
            IlluColors(KeyPath("RING", "Groupe 1", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("RING", "Groupe 2", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("RING", "Groupe 3", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("RING", "Groupe 4", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("RING", "Groupe 5", "Fond 1"), lightColor = "#FAFAFA", darkColor = "#282828"),
        )

        val illu4PinkColors = arrayOf(
            IlluColors(KeyPath("WOMAN", "Groupe 4", "Fond 1"), lightColor = "#BF4C80", darkColor = "#E75F9C"),
            IlluColors(KeyPath("WOMAN", "Groupe 5", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("WOMAN", "Groupe 6", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("POINT 1", "Groupe 1", "Fond 1"), lightColor = "#BF4C80", darkColor = "#E75F9C"),
            IlluColors(KeyPath("POINT 2", "Groupe 1", "Fond 1"), lightColor = "#BF4C80", darkColor = "#E75F9C"),
            IlluColors(KeyPath("MEN", "Groupe 5", "Fond 1"), lightColor = "#BD95A7", darkColor = "#AE366D"),
            IlluColors(KeyPath("MEN", "Groupe 5", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("MEN", "Groupe 6", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("POINT 3", "Groupe 1", "Fond 1"), lightColor = "#BD95A7", darkColor = "#AE366D"),
            IlluColors(KeyPath("POINT 4", "Groupe 1", "Fond 1"), lightColor = "#BD95A7", darkColor = "#AE366D"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 1", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 2", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 3", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 4", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 5", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 6", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 9", "Fond 1"), lightColor = "#FF5B97", darkColor = "#EF0057"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 12", "Fond 1"), lightColor = "#AB2456", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 15", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 18", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 19", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 20", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 22", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 23", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 24", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 25", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 26", "Fond 1"), lightColor = "#FAFAFA", darkColor = "#282828"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 27", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 28", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 29", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 30", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 31", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 32", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 33", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 34", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 35", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 36", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 37", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 38", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 39", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 43", "Fond 1"), lightColor = "#BD95A7", darkColor = "#AE366D"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 44", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 48", "Fond 1"), lightColor = "#BF4C80", darkColor = "#E75F9C"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 49", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 50", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 54", "Fond 1"), lightColor = "#BF4C80", darkColor = "#E75F9C"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 55", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 56", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 61", "Fond 1"), lightColor = "#DFBDCC", darkColor = "#955873"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 62", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 67", "Fond 1"), lightColor = "#BC0055", darkColor = "#D0759F"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 68", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 70", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 72", "Fond 1"), lightColor = "#824D65", darkColor = "#AB6685"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 73", "Fond 1"), lightColor = "#340E00", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 74", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 75", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 76", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("LETTER", "Groupe 1", "Fond 1"), lightColor = "#FF4388", darkColor = "#B80043"),
            IlluColors(KeyPath("LETTER", "Groupe 2", "Fond 1"), lightColor = "#D81B60", darkColor = "#FB2C77"),
            IlluColors(KeyPath("LETTER", "Groupe 3", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#EAEAEA"),
            IlluColors(KeyPath("LETTER", "Groupe 4", "Fond 1"), lightColor = "#F8F8F8", darkColor = "#E4E4E4"),
            IlluColors(KeyPath("LETTER", "Groupe 5", "Fond 1"), lightColor = "#FAF0F0", darkColor = "#F1DDDD"),
            IlluColors(KeyPath("LETTER", "Groupe 6", "Fond 1"), lightColor = "#E10B59", darkColor = "#DC1A60"),
            IlluColors(KeyPath("LETTER", "Groupe 7", "Fond 1"), lightColor = "#E10B59", darkColor = "#DC1A60"),
        )






        val illu1BlueColors = arrayOf(
            IlluColors(KeyPath("POINT 3", "Groupe 1", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("POINT 2", "Groupe 1", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("POINT 1", "Groupe 1", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("POINT 4", "Groupe 1", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("POINT 5", "Groupe 1", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("POINT 6", "Groupe 1", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("CHAT 1", "Groupe 1", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("CHAT 2", "Groupe 1", "Fond 1"), lightColor = "#3981AA", darkColor = "#56AFE1"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 1", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 2", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 3", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 4", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 5", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 6", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 9", "Fond 1"), lightColor = "#69C9FF", darkColor = "#6DCBFF"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 9", "Fond 1"), lightColor = "#69C9FF", darkColor = "#6DCBFF"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 12", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 15", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 18", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 19", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 20", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 22", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 23", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 24", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 25", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 26", "Fond 1"), lightColor = "#FAFAFA", darkColor = "#282828"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 27", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 28", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 29", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 30", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 31", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 32", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 33", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 34", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 35", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 36", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 37", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 38", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 39", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 43", "Fond 1"), lightColor = "#3981AA", darkColor = "#56AFE1"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 44", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 48", "Fond 1"), lightColor = "#289CDD", darkColor = "#0D7DBC"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 49", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 50", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 55", "Fond 1"), lightColor = "#84BAD8", darkColor = "#955873"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 56", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 61", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 62", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 66", "Fond 1"), lightColor = "#10405B", darkColor = "#AB6685"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 67", "Fond 1"), lightColor = "#340E00", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 68", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 69", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 70", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
        )

        val illu2BlueColors = arrayOf(
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 4", "Fond 1"), lightColor = "#289CDD", darkColor = "#0D7DBC"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 5", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 6", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 9", "Fond 1"), lightColor = "#CCCCCC", darkColor = "#818181"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 10", "Fond 1"), lightColor = "#CCCCCC", darkColor = "#818181"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 11", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 12", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 13", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 14", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 1", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 2", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 3", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 4", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 5", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 6", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 9", "Fond 1"), lightColor = "#69C9FF", darkColor = "#6DCBFF"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 12", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 15", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 18", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 19", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 20", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 22", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 23", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 24", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 25", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 26", "Fond 1"), lightColor = "#FAFAFA", darkColor = "#282828"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 27", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 28", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 29", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 30", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 31", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 32", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 33", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 34", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 35", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 36", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 37", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 38", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 39", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 43", "Fond 1"), lightColor = "#3981AA", darkColor = "#56AFE1"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 44", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 48", "Fond 1"), lightColor = "#289CDD", darkColor = "#0D7DBC"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 49", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 50", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 54", "Fond 1"), lightColor = "#289CDD", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 55", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 56", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 61", "Fond 1"), lightColor = "#84BAD8", darkColor = "#588EAC"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 62", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 67", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 68", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 70", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 72", "Fond 1"), lightColor = "#10405B", darkColor = "#10405B"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 73", "Fond 1"), lightColor = "#340E00", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 74", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 75", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 76", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("HAND", "Groupe 1", "Fond 1"), lightColor = "#10405B", darkColor = "#10405B"),
            IlluColors(KeyPath("HAND", "Groupe 4", "Fond 1"), lightColor = "#0B3547", darkColor = "#266E8D"),
            IlluColors(KeyPath("HAND", "Groupe 5", "Fond 1"), lightColor = "#0B3547", darkColor = "#266E8D"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 4", "Fond 1"), lightColor = "#C4C4C4", darkColor = "#7C7C7C"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 5", "Fond 1"), lightColor = "#C4C4C4", darkColor = "#7C7C7C"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 6", "Fond 1"), lightColor = "#C4C4C4", darkColor = "#7C7C7C"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 7", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 8", "Fond 1"), lightColor = "#CCCCCC", darkColor = "#818181"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 9", "Fond 1"), lightColor = "#CCCCCC", darkColor = "#818181"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 10", "Fond 1"), lightColor = "#CCCCCC", darkColor = "#818181"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 13", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 14", "Fond 1"), lightColor = "#C4C4C4", darkColor = "#7C7C7C"),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 15", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 4", "Fond 1"), lightColor = "#289CDD", darkColor = "#0D7DBC"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 5", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 6", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 7", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 8", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 9", "Fond 1"), lightColor = "#CCCCCC", darkColor = "#818181"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 10", "Fond 1"), lightColor = "#CCCCCC", darkColor = "#818181"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 11", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 12", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 13", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 15", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
        )

        val illu3BlueColors = arrayOf(
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 1", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 2", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 3", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 4", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 4", "Fond 1 "), lightColor = "#F7E8EF", darkColor = "#282828"),
            IlluColors(KeyPath("NOTIFICATION 3", "Groupe 1", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("NOTIFICATION 3", "Groupe 2", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("NOTIFICATION 3", "Groupe 3", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("NOTIFICATION 3", "Groupe 4", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("NOTIFICATION 3", "Groupe 4", "Fond 1 "), lightColor = "#F7E8EF", darkColor = "#282828"),
            IlluColors(KeyPath("NOTIFICATION 4", "Groupe 1", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("NOTIFICATION 4", "Groupe 2", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("NOTIFICATION 4", "Groupe 3", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("NOTIFICATION 4", "Groupe 4", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("NOTIFICATION 4", "Groupe 4", "Fond 1 "), lightColor = "#F7E8EF", darkColor = "#282828"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 1", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 2", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 3", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 4", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 5", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 6", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 9", "Fond 1"), lightColor = "#69C9FF", darkColor = "#6DCBFF"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 12", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 15", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 18", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 19", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 20", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 22", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 23", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 24", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 25", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 26", "Fond 1"), lightColor = "#FAFAFA", darkColor = "#282828"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 27", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 28", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 29", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 30", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 31", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 32", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 33", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 34", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 35", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 36", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 37", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 38", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 39", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 43", "Fond 1"), lightColor = "#3981AA", darkColor = "#56AFE1"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 44", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 48", "Fond 1"), lightColor = "#289CDD", darkColor = "#0D7DBC"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 49", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 50", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 54", "Fond 1"), lightColor = "#289CDD", darkColor = "#0D7DBC"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 55", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 56", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 61", "Fond 1"), lightColor = "#84BAD8", darkColor = "#588EAC"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 62", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 67", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 68", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 70", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 72", "Fond 1"), lightColor = "#10405B", darkColor = "#10405B"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 73", "Fond 1"), lightColor = "#340E00", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 74", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 75", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 76", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("HAND", "Groupe 1", "Fond 1"), lightColor = "#10405B", darkColor = "#10405B"),
            IlluColors(KeyPath("HAND", "Groupe 4", "Fond 1"), lightColor = "#0B3547", darkColor = "#266E8D"),
            IlluColors(KeyPath("HAND", "Groupe 4", "Fond 1"), lightColor = "#0B3547", darkColor = "#266E8D"),
            IlluColors(KeyPath("STAR", "Groupe 1", "Contour 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("STAR", "Groupe 2", "Fond 1"), lightColor = "#FAFAFA", darkColor = "#282828"),
            IlluColors(KeyPath("BEN", "Groupe 1", "Contour 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("BEN", "Groupe 2", "Contour 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("BEN", "Groupe 3", "Contour 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("BEN", "Groupe 4", "Contour 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("BEN", "Groupe 5", "Contour 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("BEN", "Groupe 6", "Contour 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("BEN", "Groupe 7", "Fond 1"), lightColor = "#FAFAFA", darkColor = "#282828"),
            IlluColors(KeyPath("RING", "Groupe 1", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("RING", "Groupe 2", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("RING", "Groupe 3", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("RING", "Groupe 4", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("RING", "Groupe 5", "Fond 1"), lightColor = "#FAFAFA", darkColor = "#282828"),
        )

        val illu4BlueColors = arrayOf(
            IlluColors(KeyPath("WOMAN", "Groupe 4", "Fond 1"), lightColor = "#289CDD", darkColor = "#0D7DBC"),
            IlluColors(KeyPath("WOMAN", "Groupe 5", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("WOMAN", "Groupe 6", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("POINT 1", "Groupe 1", "Fond 1"), lightColor = "#289CDD", darkColor = "#0D7DBC"),
            IlluColors(KeyPath("POINT 2", "Groupe 1", "Fond 1"), lightColor = "#289CDD", darkColor = "#0D7DBC"),
            IlluColors(KeyPath("MEN", "Groupe 5", "Fond 1"), lightColor = "#3981AA", darkColor = "#56AFE1"),
            IlluColors(KeyPath("MEN", "Groupe 5", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("MEN", "Groupe 6", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("POINT 3", "Groupe 1", "Fond 1"), lightColor = "#3981AA", darkColor = "#56AFE1"),
            IlluColors(KeyPath("POINT 4", "Groupe 1", "Fond 1"), lightColor = "#3981AA", darkColor = "#56AFE1"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 1", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 2", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 3", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 4", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 5", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 6", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 9", "Fond 1"), lightColor = "#69C9FF", darkColor = "#6DCBFF"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 12", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 15", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 18", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 19", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 20", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 22", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 23", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 24", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 25", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 26", "Fond 1"), lightColor = "#FAFAFA", darkColor = "#282828"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 27", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 28", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 29", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 30", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 31", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 32", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 33", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 34", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 35", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 36", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 37", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 38", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 39", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 43", "Fond 1"), lightColor = "#3981AA", darkColor = "#56AFE1"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 44", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 48", "Fond 1"), lightColor = "#289CDD", darkColor = "#0D7DBC"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 49", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 50", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 54", "Fond 1"), lightColor = "#289CDD", darkColor = "#0D7DBC"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 55", "Fond 1"), lightColor = "#C6AC9F", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 56", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 61", "Fond 1"), lightColor = "#84BAD8", darkColor = "#588EAC"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 62", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 67", "Fond 1"), lightColor = "#0098FF", darkColor = "#0177C7"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 68", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 70", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 72", "Fond 1"), lightColor = "#10405B", darkColor = "#10405B"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 73", "Fond 1"), lightColor = "#340E00", darkColor = "#996452"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 74", "Fond 1"), lightColor = "#F5F5F5", darkColor = "#3E3E3E"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 75", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#1A1A1A"),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 76", "Fond 1"), lightColor = "#E0E0E0", darkColor = "#D4D4D4"),
            IlluColors(KeyPath("LETTER", "Groupe 1", "Fond 1"), lightColor = "#FF4388", darkColor = "#6DCBFF"),
            IlluColors(KeyPath("LETTER", "Groupe 2", "Fond 1"), lightColor = "#D81B60", darkColor = "#0A85C9"),
            IlluColors(KeyPath("LETTER", "Groupe 3", "Fond 1"), lightColor = "#FFFFFF", darkColor = "#EAEAEA"),
            IlluColors(KeyPath("LETTER", "Groupe 4", "Fond 1"), lightColor = "#F8F8F8", darkColor = "#E4E4E4"),
            IlluColors(KeyPath("LETTER", "Groupe 5", "Fond 1"), lightColor = "#FAF0F0", darkColor = "#E8F6FF"),
            IlluColors(KeyPath("LETTER", "Groupe 6", "Fond 1"), lightColor = "#E10B59", darkColor = "#0875A5"),
            IlluColors(KeyPath("LETTER", "Groupe 7", "Fond 1"), lightColor = "#E10B59", darkColor = "#0875A5"),
        )
    }
}
