/*
 * Infomaniak kMail - Android
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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.infomaniak.lib.applock.Utils.isKeyguardSecure
import com.infomaniak.lib.core.utils.openAppNotificationSettings
import com.infomaniak.mail.MatomoMail.toFloat
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.databinding.FragmentSettingsBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.animatedNavigation
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private lateinit var binding: FragmentSettingsBinding
    private val mainViewModel: MainViewModel by activityViewModels()

    private val localSettings by lazy { LocalSettings.getInstance(requireContext()) }

    private val mailboxesAdapter = SettingsMailboxesAdapter { selectedMailbox ->
        with(selectedMailbox) {
            animatedNavigation(SettingsFragmentDirections.actionSettingsToMailboxSettings(objectId, email))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSettingsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMailboxesAdapter()
        setupListeners()
        setSubtitlesInitialState()
    }

    override fun onResume() {
        super.onResume()
        setSwitchesInitialState()
    }

    private fun setupMailboxesAdapter() {
        binding.mailboxesList.adapter = mailboxesAdapter
        mainViewModel.mailboxesLive.observe(viewLifecycleOwner, mailboxesAdapter::setMailboxes)
    }

    private fun setSubtitlesInitialState() = with(binding) {
        with(localSettings) {
            settingsThreadListDensity.setSubtitle(threadDensity.localisedNameRes)
            settingsTheme.setSubtitle(theme.localisedNameRes)
            settingsAccentColor.setSubtitle(accentColor.localisedNameRes)
            settingsExternalContent.setSubtitle(externalContent.localisedNameRes)
        }
    }

    private fun setSwitchesInitialState() = with(binding) {
        settingsAppLock.isChecked = localSettings.isAppLocked
    }

    private fun setupListeners() = with(binding) {

        settingsNotifications.setOnClickListener {
            trackEvent("settingsNotifications", "openNotificationSettings")
            requireContext().openAppNotificationSettings()
        }

        settingsSend.setOnClickListener {
            animatedNavigation(SettingsFragmentDirections.actionSettingsToSendSettings())
        }

        settingsAppLock.apply {
            isVisible = context.isKeyguardSecure()
            isChecked = localSettings.isAppLocked
            setOnClickListener {
                trackEvent("settingsGeneral", "lock", value = isChecked.toFloat())
                // Reverse switch (before official parameter changed) by silent click
                requireActivity().reverseSwitch(localSettings)
            }
        }

        settingsThreadListDensity.setOnClickListener {
            animatedNavigation(SettingsFragmentDirections.actionSettingsToThreadListDensitySetting())
        }

        settingsTheme.setOnClickListener {
            animatedNavigation(SettingsFragmentDirections.actionSettingsToThemeSetting())
        }

        settingsAccentColor.setOnClickListener {
            animatedNavigation(SettingsFragmentDirections.actionSettingsToAccentColorSetting())
        }

        settingsSwipeActions.setOnClickListener {
            animatedNavigation(SettingsFragmentDirections.actionSettingsToSwipeActionsSetting())
        }

        settingsExternalContent.setOnClickListener {
            animatedNavigation(SettingsFragmentDirections.actionSettingsToExternalContentSetting())
        }
    }
}
