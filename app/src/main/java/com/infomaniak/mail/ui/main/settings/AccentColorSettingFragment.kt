/*
 * Infomaniak Mail - Android
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
package com.infomaniak.mail.ui.main.settings

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import com.google.android.material.color.DynamicColors
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.AccentColor
import com.infomaniak.mail.data.LocalSettings.AccentColor.*
import com.infomaniak.mail.databinding.FragmentAccentColorSettingBinding
import com.infomaniak.mail.utils.extensions.setSystemBarsColors
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AccentColorSettingFragment : Fragment() {

    private var binding: FragmentAccentColorSettingBinding by safeBinding()

    @Inject
    lateinit var localSettings: LocalSettings

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentAccentColorSettingBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding.radioGroup) {
        super.onViewCreated(view, savedInstanceState)
        setSystemBarsColors()

        setSystemAccentUi()

        initBijectionTable(
            R.id.pinkRadioButton to PINK,
            R.id.blueRadioButton to BLUE,
            R.id.systemRadioButton to SYSTEM,
        )

        check(localSettings.accentColor)

        onItemCheckedListener { _, _, accentColor ->
            chooseColor(accentColor as AccentColor)
            trackEvent("settingsAccentColor", accentColor.toString())
        }
    }

    private fun setSystemAccentUi() = with(binding) {
        if (!DynamicColors.isDynamicColorAvailable()) {
            systemRadioButton.isGone = true
            return@with
        }

        val dynamicPrimaryColor = SYSTEM.getPrimary(context)
        systemRadioButton.apply {
            setCheckMarkColor(dynamicPrimaryColor)
            setIcon(GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(dynamicPrimaryColor)
            })
        }
    }

    private fun chooseColor(accentColor: AccentColor) {
        localSettings.accentColor = accentColor
        activity?.apply {
            setTheme(accentColor.theme)
            recreate()
        }
    }
}
