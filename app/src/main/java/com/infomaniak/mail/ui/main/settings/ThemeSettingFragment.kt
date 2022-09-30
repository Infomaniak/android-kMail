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
import android.widget.ImageView
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.infomaniak.mail.data.UiSettings
import com.infomaniak.mail.data.UiSettings.Theme
import com.infomaniak.mail.data.UiSettings.Theme.*
import com.infomaniak.mail.databinding.FragmentThemeSettingBinding

class ThemeSettingFragment : Fragment() {

    private lateinit var binding: FragmentThemeSettingBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentThemeSettingBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBack()
        setUpCheckMarks()
        setupListeners()
    }

    private fun setupBack() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun setUpCheckMarks() = with(binding) {
        when (UiSettings.getInstance(requireContext()).theme) {
            LIGHT -> settingsOptionLightThemeCheck
            DARK -> settingsOptionDarkThemeCheck
            else -> settingsOptionDefaultThemeCheck
        }.selectOption()
    }

    private fun setupListeners() = with(binding) {
        settingsOptionDefaultTheme.setOnClickListener { chooseTheme(SYSTEM, settingsOptionDefaultThemeCheck) }
        settingsOptionLightTheme.setOnClickListener { chooseTheme(LIGHT, settingsOptionLightThemeCheck) }
        settingsOptionDarkTheme.setOnClickListener { chooseTheme(DARK, settingsOptionDarkThemeCheck) }
    }

    private fun chooseTheme(theme: Theme, selectedImageView: ImageView) {
        selectedImageView.selectOption()
        setDefaultNightMode(theme.mode)
        UiSettings.getInstance(requireContext()).theme = theme
    }

    private fun ImageView.selectOption() = with(binding) {
        settingsOptionDefaultThemeCheck.setCheckMarkGone(this@selectOption)
        settingsOptionLightThemeCheck.setCheckMarkGone(this@selectOption)
        settingsOptionDarkThemeCheck.setCheckMarkGone(this@selectOption)

        this@selectOption.isVisible = true
    }

    private fun ImageView.setCheckMarkGone(selectedOption: ImageView) {
        if (this != selectedOption) isGone = true
    }
}
