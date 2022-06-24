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
package com.infomaniak.mail.ui.main.menu.user

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.infomaniak.mail.R
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.databinding.FragmentSwitchUserBinding
import com.infomaniak.mail.ui.LoginActivity
import com.infomaniak.mail.ui.main.menu.user.SwitchUserAccountsAdapter.UiAccount
import com.infomaniak.mail.ui.main.menu.user.SwitchUserMailboxesAdapter.Companion.sortMailboxes
import com.infomaniak.mail.utils.AccountUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class SwitchUserFragment : Fragment() {

    private val switchUserViewModel: SwitchUserViewModel by viewModels()

    private val binding: FragmentSwitchUserBinding by lazy { FragmentSwitchUserBinding.inflate(layoutInflater) }

    private var mailboxesJob: Job? = null

    private val accountsAdapter = SwitchUserAccountsAdapter { selectedMailbox ->
        if (selectedMailbox.userId == AccountUtils.currentUserId) {
            MailData.selectMailbox(selectedMailbox)
            findNavController().popBackStack()
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                AccountUtils.currentUser = AccountUtils.getUserById(selectedMailbox.userId)
                AccountUtils.currentMailboxId = selectedMailbox.mailboxId
                AccountUtils.reloadApp?.invoke(bundleOf())
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = binding.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        recyclerViewAccount.adapter = accountsAdapter
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    override fun onResume() {
        super.onResume()

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            (menuItem.itemId == R.id.addAccount).also { if (it) startActivity(Intent(this.context, LoginActivity::class.java)) }
        }

        listenToMailboxes()
        switchUserViewModel.loadMailboxes(viewLifecycleOwner)
    }

    override fun onPause() {
        mailboxesJob?.cancel()
        mailboxesJob = null

        super.onPause()
    }

    private fun listenToMailboxes() {
        with(switchUserViewModel) {

            if (mailboxesJob != null) mailboxesJob?.cancel()

            mailboxesJob = viewModelScope.launch(Dispatchers.Main) {
                uiAccountsFlow.filterNotNull().collect { accounts ->

                    val uiAccounts = accounts
                        .map { (user, mailboxes) -> UiAccount(user, mailboxes.sortMailboxes()) }
                        .sortAccounts()

                    accountsAdapter.setAccounts(uiAccounts)
                    accountsAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun List<UiAccount>.sortAccounts(): List<UiAccount> {
        return filter { it.user.id != AccountUtils.currentUserId }
            .toMutableList()
            .apply { this@sortAccounts.find { it.user.id == AccountUtils.currentUserId }?.let { add(0, it) } }
            .toList()
    }
}
