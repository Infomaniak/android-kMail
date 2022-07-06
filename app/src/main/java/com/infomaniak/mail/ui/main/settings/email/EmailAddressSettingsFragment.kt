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
package com.infomaniak.mail.ui.main.settings.email

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.databinding.FragmentEmailAddressSettingsBinding

class EmailAddressSettingsFragment : Fragment() {

    private val navigationArgs: EmailAddressSettingsFragmentArgs by navArgs()
    private lateinit var binding: FragmentEmailAddressSettingsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentEmailAddressSettingsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mailbox = MailData.mailboxesFlow.value?.find { it.objectId == navigationArgs.mailboxObjectId } ?: return
        setupBack()
        setupUi(mailbox)
        setupListeners()
    }

    private fun setupBack() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun setupUi(mailbox: Mailbox) {
        binding.toolbarText.text = mailbox.email
    }

    private fun setupListeners() = with(binding) {
        settingsMailboxGeneralSignature.setOnClickListener { } // TODO
        settingsMailboxGeneralAutoreply.setOnClickListener { } // TODO
        settingsMailboxGeneralFolders.setOnClickListener { } // TODO
        settingsMailboxGeneralNotifications.setOnClickListener { settingsMailboxGeneralNotificationsSwitch.toggle() } // TODO
        settingsInboxType.setOnClickListener { } // TODO
        settingsInboxRules.setOnClickListener { } // TODO
        settingsInboxRedirect.setOnClickListener { } // TODO
        settingsInboxAlias.setOnClickListener { } // TODO
        settingsSecurityAdsFilter.setOnClickListener { settingsSecurityAdsFilterSwitch.toggle() } // TODO
        settingsSecuritySpamFilter.setOnClickListener { settingsSecuritySpamFilterSwitch.toggle() } // TODO
        settingsSecurityBlockedRecipients.setOnClickListener { } // TODO
        settingsPrivacyDeleteSearchHistory.setOnClickListener { } // TODO
        settingsPrivacyViewLogs.setOnClickListener { } // TODO
    }
}
