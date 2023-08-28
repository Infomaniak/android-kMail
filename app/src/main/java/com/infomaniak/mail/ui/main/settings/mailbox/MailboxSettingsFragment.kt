/*
 * Infomaniak Mail - Android
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
package com.infomaniak.mail.ui.main.settings.mailbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentMailboxSettingsBinding
import com.infomaniak.mail.ui.main.settings.ItemSettingView
import com.infomaniak.mail.utils.animatedNavigation
import com.infomaniak.mail.utils.notYetImplemented

class MailboxSettingsFragment : Fragment() {

    private lateinit var binding: FragmentMailboxSettingsBinding
    private val navigationArgs: MailboxSettingsFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMailboxSettingsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.setTitle(navigationArgs.mailboxEmail)
        setSubtitlesInitialState()
        setupListeners()
    }

    private fun setSubtitlesInitialState() = with(binding) {
        settingsSecurityAdsFilter.updateActivatedSubtitle()
        settingsSecuritySpamFilter.updateActivatedSubtitle()
    }

    private fun ItemSettingView.updateActivatedSubtitle() {
        setSubtitle(if (isChecked) R.string.settingsEnabled else R.string.settingsDisabled)
    }

    private fun setupListeners() = with(binding) {
        settingsMailboxGeneralSignature.setOnClickListener {
            animatedNavigation(
                MailboxSettingsFragmentDirections.actionMailboxSettingsToSignatureSetting(navigationArgs.mailboxObjectId)
            )
        }
        settingsMailboxGeneralAutoreply.setOnClickListener { notYetImplemented() }
        settingsMailboxGeneralFolders.setOnClickListener { notYetImplemented() }
        settingsMailboxGeneralNotifications.setOnClickListener { notYetImplemented() }
        settingsInboxType.setOnClickListener { notYetImplemented() }
        settingsInboxRules.setOnClickListener { notYetImplemented() }
        settingsInboxRedirect.setOnClickListener { notYetImplemented() }
        settingsInboxAlias.setOnClickListener { notYetImplemented() }
        settingsSecurityAdsFilter.apply {
            setOnClickListener {
                notYetImplemented()
                updateActivatedSubtitle()
            }
        }
        settingsSecuritySpamFilter.apply {
            setOnClickListener {
                notYetImplemented()
                updateActivatedSubtitle()
            }
        }
        settingsSecurityBlockedRecipients.setOnClickListener { notYetImplemented() }
        settingsPrivacyDeleteSearchHistory.setOnClickListener { notYetImplemented() }
        settingsPrivacyViewLogs.setOnClickListener { notYetImplemented() }
    }
}
