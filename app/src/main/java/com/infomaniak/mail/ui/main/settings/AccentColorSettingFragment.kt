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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.UiSettings
import com.infomaniak.mail.databinding.FragmentAccentColorSettingBinding

class AccentColorSettingFragment : Fragment() {

    private lateinit var binding: FragmentAccentColorSettingBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentAccentColorSettingBinding.inflate(inflater, container, false).also { binding = it }.root
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
        UiSettings(requireContext()).colorTheme.let { colorTheme ->
            if (colorTheme == UiSettings.ColorTheme.PINK) {
                settingsOptionPinkAccentColorCheck
            } else {
                settingsOptionBlueAccentColorCheck
            }.selectColor(colorTheme)
        }
    }

    private fun setupListeners() = with(binding) {
        settingsOptionPinkAccentColor.setOnClickListener {
            chooseColor(UiSettings.ColorTheme.PINK, settingsOptionPinkAccentColorCheck)
        }
        settingsOptionBlueAccentColor.setOnClickListener {
            chooseColor(UiSettings.ColorTheme.BLUE, settingsOptionBlueAccentColorCheck)
        }
    }

    private fun chooseColor(color: UiSettings.ColorTheme, selectedImageView: ImageView) {
        activity?.setTheme(if (color == UiSettings.ColorTheme.PINK) R.style.AppTheme_Pink else R.style.AppTheme_Blue)
        UiSettings(requireContext()).colorTheme = color
        selectedImageView.selectColor(color)
    }

    private fun ImageView.selectColor(color: UiSettings.ColorTheme) {
        val colorRes = if (color == UiSettings.ColorTheme.PINK) {
            binding.settingsOptionBlueAccentColorCheck.setCheckMarkGone(this)
            R.color.pinkMail
        } else {
            binding.settingsOptionPinkAccentColorCheck.setCheckMarkGone(this)
            R.color.blueMail
        }
        setColorFilter(context.getColor(colorRes))
        isVisible = true
    }

    private fun ImageView.setCheckMarkGone(selectedOption: ImageView) {
        if (this != selectedOption) isGone = true
    }
}
