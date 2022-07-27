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
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private lateinit var binding: FragmentSettingsBinding

    private val mailboxesAdapter = SettingsMailboxesAdapter { selectedMailbox ->
        safeNavigate(SettingsFragmentDirections.actionSettingsToMailboxSettings(selectedMailbox.objectId))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSettingsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBack()
        setupAdapter()
        setupListeners()
    }

    private fun setupBack() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun setupAdapter() {
        binding.mailboxesList.adapter = mailboxesAdapter
        MailData.mailboxesFlow.value?.let(mailboxesAdapter::setMailboxes)
    }

    private fun setupListeners() = with(binding) {

        settingsSend.setOnClickListener {
            // TODO
        }

        settingsCodeLock.setOnClickListener {
            // TODO
        }

        settingsThreadListDensity.setOnClickListener {
            safeNavigate(SettingsFragmentDirections.actionSettingsToListDensitySetting())
        }

        settingsTheme.setOnClickListener {
            safeNavigate(SettingsFragmentDirections.actionSettingsToThemeSetting())
        }

        settingsSwipeActions.setOnClickListener {
            // TODO
        }

        settingsMessageDisplay.setOnClickListener {
            safeNavigate(SettingsFragmentDirections.actionSettingsToDisplayModeSetting())
        }

        settingsExternalContent.setOnClickListener {
            safeNavigate(SettingsFragmentDirections.actionSettingsToExternalContentSetting())
        }
    }
}
