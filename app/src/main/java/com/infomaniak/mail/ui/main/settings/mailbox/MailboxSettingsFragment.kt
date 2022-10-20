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
package com.infomaniak.mail.ui.main.settings.mailbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.databinding.FragmentMailboxSettingsBinding
import com.infomaniak.mail.utils.notYetImplemented
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MailboxSettingsFragment : Fragment() {

    private lateinit var binding: FragmentMailboxSettingsBinding
    private val navigationArgs: MailboxSettingsFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMailboxSettingsBinding.inflate(inflater, container, false).also { binding = it }.root
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

    private fun setupUi() = lifecycleScope.launch(Dispatchers.IO) {
        val email = MailboxController.getMailbox(navigationArgs.mailboxObjectId)?.email ?: return@launch
        withContext(Dispatchers.Main) { binding.toolbarText.text = email }
    }

    private fun setupListeners() = with(binding) {
        // val mailbox = MainViewModel.currentMailboxObjectId.value?.let(MailboxController::getMailboxSync) ?: return
        settingsMailboxGeneralSignature.setOnClickListener { notYetImplemented() } // TODO
        settingsMailboxGeneralAutoreply.setOnClickListener { notYetImplemented() } // TODO
        settingsMailboxGeneralFolders.setOnClickListener { notYetImplemented() } // TODO
        settingsMailboxGeneralNotifications.setOnClickListener { settingsMailboxGeneralNotificationsSwitch.performClick() }
        settingsMailboxGeneralNotificationsSwitch.setOnClickListener { notYetImplemented() } // TODO
        settingsInboxType.setOnClickListener { notYetImplemented() } // TODO
        settingsInboxRules.setOnClickListener { notYetImplemented() } // TODO
        settingsInboxRedirect.setOnClickListener { notYetImplemented() } // TODO
        settingsInboxAlias.setOnClickListener { notYetImplemented() } // TODO
        settingsSecurityAdsFilter.setOnClickListener { settingsSecurityAdsFilterSwitch.performClick() }
        settingsSecurityAdsFilterSwitch.setOnClickListener { switch ->
            notYetImplemented() // TODO
            // MailboxInfoController.updateMailboxInfo(mailbox.objectId) {
            //     it.hasSocialAndCommercialFiltering = (switch as SwitchMaterial).isChecked
            // }
            // ApiRepository.updateMailboxSettings(mailbox.hostingId, mailbox.mailbox)
        }
        settingsSecuritySpamFilter.setOnClickListener { settingsSecuritySpamFilterSwitch.performClick() }
        settingsSecuritySpamFilterSwitch.setOnClickListener {
            notYetImplemented() // TODO
            // MailboxInfoController.updateMailboxInfo(mailbox.objectId) {
            //     it. = (switch as SwitchMaterial).isChecked
            // }
            // ApiRepository.updateMailboxSettings(mailbox.hostingId, mailbox.mailbox, )
        }
        settingsSecurityBlockedRecipients.setOnClickListener { notYetImplemented() } // TODO
        settingsPrivacyDeleteSearchHistory.setOnClickListener { notYetImplemented() } // TODO
        settingsPrivacyViewLogs.setOnClickListener { notYetImplemented() } // TODO
    }
}
