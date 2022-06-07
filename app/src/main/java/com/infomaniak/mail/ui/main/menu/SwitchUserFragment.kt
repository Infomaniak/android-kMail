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
package com.infomaniak.mail.ui.main.menu

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.infomaniak.mail.data.cache.MailboxContentController
import com.infomaniak.mail.data.cache.MailboxInfoController
import com.infomaniak.mail.databinding.FragmentSwitchUserBinding
import com.infomaniak.mail.ui.main.menu.SettingAccountAdapter.UiAccount
import com.infomaniak.mail.ui.main.menu.SettingAccountAdapter.UiMailbox
import com.infomaniak.mail.utils.AccountUtils

class SwitchUserFragment : Fragment() {

    private val binding: FragmentSwitchUserBinding by lazy { FragmentSwitchUserBinding.inflate(layoutInflater) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = binding.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        // TODO: Handle multiple accounts
        // TODO: Order accounts with selected one first
        // TODO: Get the unread count for all mailboxes and not only the current one
        val count = MailboxContentController.getFolders().map { it.unreadCount }.reduce { acc, count -> acc + count }
        val uiAccount = UiAccount(
            AccountUtils.currentUser!!,
            MailboxInfoController.getMailboxes().map { UiMailbox(it.objectId, it.email, count) }
        )
        val accounts = listOf(uiAccount)
        orderUiAccount(accounts)
        recyclerViewAccount.adapter = SettingAccountAdapter(accounts)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun orderUiAccount(uiAccounts: List<UiAccount>) {
        uiAccounts.forEach { account -> account.mailboxes.sortedByDescending { it.unreadCount } }
    }
}
