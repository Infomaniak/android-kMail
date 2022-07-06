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
package com.infomaniak.mail.ui.main.settings.select

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.infomaniak.mail.databinding.FragmentThemeSettingBinding

class ThemeSettingFragment : Fragment() {

    private lateinit var binding: FragmentThemeSettingBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentThemeSettingBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBack()
        setupListeners()
    }

    private fun setupBack() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun setupListeners() = with(binding) {
        settingsOptionDefaultTheme.setOnClickListener { settingsOptionDefaultThemeCheck.selectOption() }
        settingsOptionLightTheme.setOnClickListener { settingsOptionLightThemeCheck.selectOption() }
        settingsOptionDarkTheme.setOnClickListener { settingsOptionDarkThemeCheck.selectOption() }
    }

    private fun ImageView.selectOption() = with(binding) {

        settingsOptionDefaultThemeCheck.let { if (it != this@selectOption) it.isInvisible = true }
        settingsOptionLightThemeCheck.let { if (it != this@selectOption) it.isInvisible = true }
        settingsOptionDarkThemeCheck.let { if (it != this@selectOption) it.isInvisible = true }

        this@selectOption.isVisible = true
    }
}
