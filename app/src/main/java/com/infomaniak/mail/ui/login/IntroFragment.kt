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
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.google.android.material.tabs.TabLayout
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentIntroBinding
import com.infomaniak.mail.utils.UiUtils.animateColorChange
import com.infomaniak.mail.utils.getAttributeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class IntroFragment : Fragment() {
    private lateinit var binding: FragmentIntroBinding
    private val viewModel: IntroViewModel by activityViewModels()

    class IntroViewModel : ViewModel() {
        @ColorInt
        var theme = MutableLiveData(ThemeColor.BLUE to false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentIntroBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        val position = arguments?.getInt(POSITION_KEY)
        when (position) {
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

        pinkBlueSwitch.isVisible = position == 0

        val selectedTab = pinkBlueTabLayout.getTabAt(if (viewModel.theme.value?.first == ThemeColor.PINK) 0 else 1)
        pinkBlueTabLayout.selectTab(selectedTab)

        setTabListener()
        updateUiWhenThemeChanges(position)
    }

    private fun setUi(themeColor: ThemeColor, position: Int?, animate: Boolean = true) = with(binding) {
        updateEachPageUi(themeColor, animate)
        if (position == 0) updateFirstPageUi(themeColor, animate)
    }

    private fun updateEachPageUi(themeColor: ThemeColor, animate: Boolean) = with(binding) {
        val newColor = themeColor.getWaveColor(requireContext())
        val oldColor =
            requireActivity().window.statusBarColor // waveBackground.imageTintList!!.defaultColor//requireActivity().window.statusBarColor
        animateColorChange(animate, oldColor, newColor) { color ->
            waveBackground.imageTintList = ColorStateList.valueOf(color)
        }

        val drawable: Drawable? = getThemedDrawable(themeColor.theme, R.drawable.ic_boarding_illu_1)
        iconLayout.setImageDrawable(drawable)
    }

    private fun getThemedDrawable(theme: Int, @DrawableRes drawableRes: Int): Drawable? {
        val wrapper = ContextThemeWrapper(context, theme)
        return VectorDrawableCompat.create(resources, drawableRes, wrapper.theme)
    }

    private fun updateFirstPageUi(themeColor: ThemeColor, animate: Boolean) = with(binding) {
        val isPink = themeColor == ThemeColor.PINK
        val context = requireContext()
        val primary = themeColor.getPrimary(context)
        val ripple = themeColor.getRipple(context)
        val tabBackgroundRes = if (isPink) R.color.blueBoardingSecondaryBackground else R.color.pinkBoardingSecondaryBackground
        val tabBackground = ContextCompat.getColor(context, tabBackgroundRes)
        val colorOnPrimary = context.getAttributeColor(com.google.android.material.R.attr.colorOnPrimary)

        val newSecondaryColor = themeColor.getWaveColor(context)
        val oldSecondaryColor = requireActivity().window.statusBarColor
        animateColorChange(animate, oldSecondaryColor, newSecondaryColor) { color ->
            requireActivity().window.statusBarColor = color
        }

        val bluePrimary = ThemeColor.BLUE.getPrimary(context)
        val pinkPrimary = ThemeColor.PINK.getPrimary(context)
        val oldPrimaryColor = if (themeColor == ThemeColor.PINK) bluePrimary else pinkPrimary
        animateColorChange(animate, oldPrimaryColor, primary) { color ->
            pinkBlueTabLayout.setSelectedTabIndicatorColor(color)
        }
        animateColorChange(animate, primary, oldPrimaryColor) { color ->
            pinkBlueTabLayout.setTabTextColors(color, colorOnPrimary)
        }

        val oldBackgroundColor = pinkBlueSwitch.cardBackgroundColor.defaultColor
        animateColorChange(animate, oldBackgroundColor, tabBackground) { color ->
            pinkBlueSwitch.setCardBackgroundColor(color)
        }

        (requireActivity() as LoginActivity).updateUi(primary, ripple, animate)
    }

    private fun setTabListener() = with(binding) {
        pinkBlueTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val newSelectedTheme = if (pinkBlueTabLayout.selectedTabPosition == 0) ThemeColor.PINK else ThemeColor.BLUE
                triggerUiUpdateWhenAnimationEnd(newSelectedTheme)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
    }

    private fun triggerUiUpdateWhenAnimationEnd(themeColor: ThemeColor) {
        lifecycleScope.launch(Dispatchers.IO) {
            val duration = resources.getInteger(R.integer.loginLayoutAnimationDuration).toLong()
            delay(duration)
            withContext(Dispatchers.Main) {
                viewModel.theme.value = themeColor to true
            }
        }
    }

    private fun updateUiWhenThemeChanges(position: Int?) {
        viewModel.theme.observe(viewLifecycleOwner) { theme ->
            setUi(theme.first, position, theme.second)
        }
    }

    enum class ThemeColor(
        @StyleRes val theme: Int,
        @ColorRes private val primary: Int,
        @ColorRes private val waveColor: Int,
        @ColorRes private val ripple: Int
    ) {
        PINK(R.style.AppTheme_Pink, R.color.pinkMail, R.color.pinkBoardingSecondaryBackground, R.color.pinkMailRipple),
        BLUE(R.style.AppTheme_Blue, R.color.blueMail, R.color.blueBoardingSecondaryBackground, R.color.blueMailRipple);

        fun getPrimary(context: Context): Int = with(context) {
            return getColor(primary)
        }

        fun getWaveColor(context: Context): Int = with(context) {
            return getColor(waveColor)
        }

        fun getRipple(context: Context): Int = with(context) {
            return getColor(ripple)
        }
    }

    companion object {
        const val POSITION_KEY = "position"
    }
}
