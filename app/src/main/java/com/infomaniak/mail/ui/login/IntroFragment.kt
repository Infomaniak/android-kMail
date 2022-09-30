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

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.google.android.material.tabs.TabLayout
import com.infomaniak.mail.R
import com.infomaniak.mail.data.UiSettings
import com.infomaniak.mail.data.UiSettings.AccentColor
import com.infomaniak.mail.data.UiSettings.AccentColor.BLUE
import com.infomaniak.mail.data.UiSettings.AccentColor.PINK
import com.infomaniak.mail.databinding.FragmentIntroBinding
import com.infomaniak.mail.utils.UiUtils.animateColorChange
import com.infomaniak.mail.utils.getAttributeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.android.material.R as RMaterial

class IntroFragment : Fragment() {

    private val navigationArgs: IntroFragmentArgs by navArgs()

    private val introViewModel: IntroViewModel by activityViewModels()

    private lateinit var binding: FragmentIntroBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentIntroBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        when (navigationArgs.position) {
            0 -> {
                pinkBlueSwitch.isVisible = true
                val tabIndex = introViewModel.currentAccentColor.value?.introTabIndex
                    ?: UiSettings.DEFAULT_ACCENT_COLOR.introTabIndex
                val selectedTab = pinkBlueTabLayout.getTabAt(tabIndex)
                pinkBlueTabLayout.selectTab(selectedTab)
                setTabSelectedListener()
            }
            1 -> {
                waveBackground.setImageResource(R.drawable.ic_back_wave_2)
                title.setText(R.string.onBoardingTitle2)
                description.setText(R.string.onBoardingDescription2)
            }
            2 -> {
                waveBackground.setImageResource(R.drawable.ic_back_wave_3)
                title.setText(R.string.onBoardingTitle3)
                description.setText(R.string.onBoardingDescription3)
            }
            3 -> {
                waveBackground.setImageResource(R.drawable.ic_back_wave_4)
                title.setText(R.string.onBoardingTitle4)
                description.setText(R.string.onBoardingDescription4)
            }
        }

        updateUiWhenThemeChanges(navigationArgs.position)
    }

    private fun setTabSelectedListener() = with(binding) {
        pinkBlueTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val newSelectedAccentColor = if (pinkBlueTabLayout.selectedTabPosition == 0) PINK else BLUE
                UiSettings(requireContext()).accentColor = newSelectedAccentColor
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

    private fun updateUiWhenThemeChanges(position: Int?) {
        introViewModel.currentAccentColor.observe(viewLifecycleOwner) { accentColor ->
            setUi(
                accentColor = accentColor ?: UiSettings.DEFAULT_ACCENT_COLOR,
                position = position,
                animate = accentColor != null,
            )
        }
    }

    /**
     * `animate` is necessary because when the activity is started we want to avoid animating the color change the first time.
     */
    private fun setUi(accentColor: AccentColor, position: Int?, animate: Boolean = true) = with(binding) {
        updateEachPageUi(accentColor, animate)
        if (position == 0) {
            updateFirstPageUi(accentColor, animate)
            if (navigationArgs.isFirstAccount) updateActivityUi(accentColor, animate)
        }
    }

    private fun updateEachPageUi(accentColor: AccentColor, animate: Boolean) = with(binding) {
        val newColor = accentColor.getSecondaryBackground(requireContext())
        val oldColor = requireActivity().window.statusBarColor
        animateColorChange(animate, oldColor, newColor) { color ->
            waveBackground.imageTintList = ColorStateList.valueOf(color)
        }

        val drawable: Drawable? = getThemedDrawable(accentColor.theme, R.drawable.ic_boarding_illu_1)
        iconLayout.setImageDrawable(drawable)
    }

    private fun getThemedDrawable(theme: Int, @DrawableRes drawableRes: Int): Drawable? {
        return VectorDrawableCompat.create(resources, drawableRes, ContextThemeWrapper(context, theme).theme)
    }

    private fun updateFirstPageUi(accentColor: AccentColor, animate: Boolean) = with(binding) {
        animateTabIndicatorAndTextColor(accentColor, requireContext(), animate)
        animateTabBackgroundColor(accentColor, requireContext(), animate)
    }

    private fun animateTabIndicatorAndTextColor(accentColor: AccentColor, context: Context, animate: Boolean) = with(binding) {
        val isPink = accentColor == PINK
        val newPrimary = accentColor.getPrimary(context)
        val colorOnPrimary = context.getAttributeColor(RMaterial.attr.colorOnPrimary)
        val oldPrimary = if (isPink) BLUE.getPrimary(context) else PINK.getPrimary(context)

        animateColorChange(animate, oldPrimary, newPrimary) { color ->
            pinkBlueTabLayout.setSelectedTabIndicatorColor(color)
        }
        animateColorChange(animate, newPrimary, oldPrimary) { color ->
            pinkBlueTabLayout.setTabTextColors(color, colorOnPrimary)
        }
    }

    private fun animateTabBackgroundColor(accentColor: AccentColor, context: Context, animate: Boolean) = with(binding) {
        val isPink = accentColor == PINK
        val tabBackgroundRes = if (isPink) R.color.blueBoardingSecondaryBackground else R.color.pinkBoardingSecondaryBackground
        val tabBackground = ContextCompat.getColor(context, tabBackgroundRes)
        val oldBackground = if (isPink) {
            BLUE.getSecondaryBackground(context)
        } else {
            PINK.getSecondaryBackground(context)
        }

        animateColorChange(animate, oldBackground, tabBackground) { color ->
            pinkBlueTabLayout.setBackgroundColor(color)
        }
    }

    private fun updateActivityUi(accentColor: AccentColor, animate: Boolean) {
        (requireActivity() as LoginActivity).updateUi(accentColor, animate)
    }

    class IntroViewModel : ViewModel() {
        var currentAccentColor: MutableLiveData<AccentColor?> = MutableLiveData(null)
    }
}
