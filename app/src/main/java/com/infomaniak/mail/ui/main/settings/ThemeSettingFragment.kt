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
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.fragment.app.Fragment
import com.infomaniak.mail.R
import com.infomaniak.mail.data.UiSettings
import com.infomaniak.mail.data.UiSettings.Theme
import com.infomaniak.mail.data.UiSettings.Theme.*
import com.infomaniak.mail.databinding.FragmentThemeSettingBinding

class ThemeSettingFragment : Fragment() {

    private lateinit var binding: FragmentThemeSettingBinding

    private val uiSettings by lazy { UiSettings.getInstance(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentThemeSettingBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding.radioGroup) {
        super.onViewCreated(view, savedInstanceState)

        initBijectionTable(
            R.id.systemTheme to SYSTEM,
            R.id.lightTheme to LIGHT,
            R.id.darkTheme to DARK
        )

        check(uiSettings.theme)

        onItemCheckedListener { _, _, enum ->
            chooseTheme(enum as? Theme ?: return@onItemCheckedListener)
        }
    }

    private fun chooseTheme(theme: Theme) {
        setDefaultNightMode(theme.mode)
        uiSettings.theme = theme
    }
}
