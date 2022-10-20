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
package com.infomaniak.mail.ui.main.settings.send

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.R
import com.infomaniak.mail.data.UiSettings
import com.infomaniak.mail.databinding.FragmentSendSettingsBinding
import com.infomaniak.mail.utils.notYetImplemented

class SendSettingsFragment : Fragment() {

    private lateinit var binding: FragmentSendSettingsBinding

    private val uiSettings by lazy { UiSettings.getInstance(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSendSettingsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setSubtitlesInitialState()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        setSwitchesInitialState()
    }

    private fun setSubtitlesInitialState() = with(binding) {
        val cancelDelay = uiSettings.cancelDelay
        val subtitle = if (cancelDelay == 0) {
            getString(R.string.settingsDisabled)
        } else {
            getString(R.string.settingsDelaySeconds, cancelDelay)
        }

        with(uiSettings) {
            settingsCancellationPeriod.setSubtitle(subtitle)
            settingsTransferEmails.setSubtitle(emailForwarding.localisedNameRes)
        }
    }

    private fun setSwitchesInitialState() = with(binding) {
        with(uiSettings) {
            settingsSendIncludeOriginalMessage.isChecked = includeMessageInReply
            settingsSendAcknowledgement.isChecked = askEmailAcknowledgement
        }
    }

    private fun setupListeners() = with(binding) {
        settingsCancellationPeriod.setOnClickListener {
            safeNavigate(SendSettingsFragmentDirections.actionSendSettingsToCancelDelaySetting())
        }

        settingsTransferEmails.setOnClickListener {
            safeNavigate(SendSettingsFragmentDirections.actionSendSettingsToFordwardMailsSetting())
        }

        settingsSendIncludeOriginalMessage.apply {
            setOnClickListener {
                uiSettings.includeMessageInReply = isChecked
                notYetImplemented()
            }
        }

        settingsSendAcknowledgement.apply {
            setOnClickListener {
                uiSettings.askEmailAcknowledgement = isChecked
                notYetImplemented()
            }
        }
    }
}
