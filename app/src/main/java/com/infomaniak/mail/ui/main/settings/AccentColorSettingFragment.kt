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
package com.infomaniak.mail.ui.main.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.AccentColor
import com.infomaniak.mail.data.LocalSettings.AccentColor.BLUE
import com.infomaniak.mail.data.LocalSettings.AccentColor.PINK
import com.infomaniak.mail.databinding.FragmentAccentColorSettingBinding

class AccentColorSettingFragment : Fragment() {

    private lateinit var binding: FragmentAccentColorSettingBinding

    private val localSettings by lazy { LocalSettings.getInstance(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentAccentColorSettingBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding.radioGroup) {
        super.onViewCreated(view, savedInstanceState)

        initBijectionTable(
            R.id.pinkRadioButton to PINK,
            R.id.blueRadioButton to BLUE,
        )

        check(localSettings.accentColor)

        onItemCheckedListener { _, _, enum ->
            (enum as? AccentColor)?.let(::chooseColor)
        }
    }

    private fun chooseColor(accentColor: AccentColor) {
        activity?.setTheme(accentColor.theme)
        localSettings.accentColor = accentColor
        activity?.recreate()
    }
}
