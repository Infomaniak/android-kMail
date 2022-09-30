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
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.infomaniak.mail.data.UiSettings
import com.infomaniak.mail.data.UiSettings.ExternalContent
import com.infomaniak.mail.data.UiSettings.ExternalContent.ALWAYS
import com.infomaniak.mail.data.UiSettings.ExternalContent.ASK_ME
import com.infomaniak.mail.databinding.FragmentExternalContentSettingBinding
import com.infomaniak.mail.utils.notYetImplemented

class ExternalContentSettingFragment : Fragment() {

    private lateinit var binding: FragmentExternalContentSettingBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentExternalContentSettingBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBack()
        setupUi()
        setupListeners()
    }

    private fun setupBack() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun setupUi() = with(binding) {
        when (UiSettings.getInstance(requireContext()).externalContent) {
            ALWAYS -> settingsOptionAlwaysCheck.selectOption()
            ASK_ME -> settingsOptionAskMeCheck.selectOption()
        }
    }

    private fun setupListeners() = with(binding) {
        settingsOptionAlways.setOnClickListener {
            notYetImplemented()
            updateExternalContentSetting(ALWAYS, settingsOptionAlwaysCheck)
        }
        settingsOptionAskMe.setOnClickListener {
            notYetImplemented()
            updateExternalContentSetting(ASK_ME, settingsOptionAskMeCheck)
        }
    }

    private fun updateExternalContentSetting(externalContent: ExternalContent, chosenOption: ImageView) {
        UiSettings.getInstance(requireContext()).externalContent = externalContent
        chosenOption.selectOption()
    }

    private fun ImageView.selectOption() = with(binding) {
        settingsOptionAlwaysCheck.let { if (it != this@selectOption) it.isInvisible = true }
        settingsOptionAskMeCheck.let { if (it != this@selectOption) it.isInvisible = true }

        this@selectOption.isVisible = true
    }
}
