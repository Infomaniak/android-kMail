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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.data.cache.mailboxInfos.MailboxController
import com.infomaniak.mail.data.cache.userInfos.UserPreferencesController
import com.infomaniak.mail.data.models.UiSettings
import com.infomaniak.mail.data.models.user.UserPreferences
import com.infomaniak.mail.databinding.FragmentSettingsBinding
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.notYetImplemented
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        UserPreferencesController.getUserPreferences().setupPreferencesText()
    }

    private fun setupBack() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun setupAdapter() {
        binding.mailboxesList.adapter = mailboxesAdapter
        lifecycleScope.launch(Dispatchers.IO) {
            val mailboxes = MailboxController.getMailboxesSync(AccountUtils.currentUserId)
            withContext(Dispatchers.Main) { mailboxesAdapter.setMailboxes(mailboxes) }
        }
    }

    private fun UserPreferences.setupPreferencesText() = with(binding) {
        densitySubtitle.setText(getListDensityMode().localisedNameRes)
        themeSubtitle.setText(getThemeMode().localisedNameRes)
        displayModeSubtitle.setText(getThreadMode().localisedNameRes)
        externalContentSubtitle.setText(getExternalContentMode().localisedNameRes)
        accentColorSubtitle.setText(UiSettings(requireContext()).colorTheme.localisedNameRes)
    }

    private fun setupListeners() = with(binding) {

        settingsSend.setOnClickListener {
            safeNavigate(SettingsFragmentDirections.actionSettingsToSendSettings())
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

        settingsAccentColor.setOnClickListener {
            safeNavigate(SettingsFragmentDirections.actionSettingsToAccentColorSetting())
        }

        settingsSwipeActions.setOnClickListener {
            safeNavigate(SettingsFragmentDirections.actionSettingsToSwipeActionsSetting())
        }

        settingsMessageDisplay.setOnClickListener {
            safeNavigate(SettingsFragmentDirections.actionSettingsToDisplayModeSetting())
        }

        settingsExternalContent.setOnClickListener {
            safeNavigate(SettingsFragmentDirections.actionSettingsToExternalContentSetting())
        }
    }
}
