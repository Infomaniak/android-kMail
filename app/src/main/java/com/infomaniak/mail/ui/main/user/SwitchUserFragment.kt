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
package com.infomaniak.mail.ui.main.user

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.infomaniak.mail.databinding.FragmentSwitchUserBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.login.LoginActivity
import com.infomaniak.mail.utils.AccountUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SwitchUserFragment : Fragment() {

    private val mainViewModel: MainViewModel by activityViewModels()
    private val switchUserViewModel: SwitchUserViewModel by viewModels()

    private lateinit var binding: FragmentSwitchUserBinding

    private val accountsAdapter = SwitchUserAccountsAdapter { selectedMailbox ->
        // TODO: This code is currently removed because if it triggers, the app crashes. This crash is because of the
        // TODO: removal of the ThreadList DiffUtil. The DiffUtil was checking if the Threads are still `.valid()`.
        // TODO: When we change Mailbox, the Threads are not valid anymore, so we don't want to display them.
        // TODO: Now that we don't check that anymore, we display them, so the app crashes.
        // if (selectedMailbox.userId == AccountUtils.currentUserId) {
        //     mainViewModel.openMailbox(selectedMailbox)
        //     findNavController().popBackStack()
        // } else {
        lifecycleScope.launch(Dispatchers.IO) {
            AccountUtils.currentUser = AccountUtils.getUserById(selectedMailbox.userId)
            AccountUtils.currentMailboxId = selectedMailbox.mailboxId

            withContext(Dispatchers.Main) {
                mainViewModel.close()

                AccountUtils.reloadApp?.invoke()
            }
        }
        // }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSwitchUserBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        setAdapter()
        setOnClickListeners()
        observeAccounts()
    }

    private fun setAdapter() {
        binding.recyclerViewAccount.adapter = accountsAdapter
    }

    private fun setOnClickListeners() = with(binding) {
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        addAccount.setOnClickListener { startActivity(Intent(context, LoginActivity::class.java)) }
    }

    private fun observeAccounts() {
        switchUserViewModel.listenToAccounts().observe(viewLifecycleOwner, accountsAdapter::notifyAdapter)
    }
}
