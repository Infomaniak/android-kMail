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
import com.google.android.material.color.DynamicColors
import com.google.android.material.tabs.TabLayout
import com.infomaniak.lib.core.utils.capitalizeFirstChar
import com.infomaniak.lib.core.utils.context
import com.infomaniak.mail.MatomoMail.trackOnBoardingEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.AccentColor
import com.infomaniak.mail.databinding.FragmentIntroBinding
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.login.IlluColors.changeIllustrationColors
import com.infomaniak.mail.utils.UiUtils.animateColorChange
import com.infomaniak.mail.utils.enumValueFrom
import com.infomaniak.mail.utils.repeatFrame
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class IntroFragment : Fragment() {

    private lateinit var binding: FragmentIntroBinding
    private val navigationArgs: IntroFragmentArgs by navArgs()
    private val introViewModel: IntroViewModel by activityViewModels()

    @Inject
    lateinit var localSettings: LocalSettings

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentIntroBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        when (navigationArgs.position) {
            0 -> introViewModel.updatedAccentColor.value?.let { (newAccentColor) ->
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
        lifecycleScope.launch(ioDispatcher) {
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

    private fun setUi(newAccentColor: AccentColor, oldAccentColor: AccentColor, position: Int) {
        updateEachPageUi(newAccentColor, oldAccentColor)

        binding.iconLayout.changeIllustrationColors(position, newAccentColor)

        if (position == ACCENT_COLOR_PICKER_PAGE) updateAccentColorPickerPageUi(newAccentColor, oldAccentColor)
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
}
