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
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.mail.data.cache.mailboxInfos.MailboxController
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.databinding.FragmentSwitchUserBinding
import com.infomaniak.mail.ui.LoginActivity
import com.infomaniak.mail.ui.main.MainViewModel
import com.infomaniak.mail.ui.main.menu.user.SwitchUserAccountsAdapter.UiAccount
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.sortMailboxes
import io.realm.kotlin.notifications.ResultsChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SwitchUserFragment : Fragment() {

    private val mainViewModel: MainViewModel by activityViewModels()
    private val switchUserViewModel: SwitchUserViewModel by viewModels()

    private lateinit var binding: FragmentSwitchUserBinding

    private val accountsAdapter = SwitchUserAccountsAdapter { selectedMailbox ->
        if (selectedMailbox.userId == AccountUtils.currentUserId) {
            mainViewModel.openMailbox(selectedMailbox)
            findNavController().popBackStack()
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                AccountUtils.currentUser = AccountUtils.getUserById(selectedMailbox.userId)
                AccountUtils.currentMailboxId = selectedMailbox.mailboxId

                withContext(Dispatchers.Main) {
                    mainViewModel.close()

                    AccountUtils.reloadApp?.invoke(bundleOf())
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSwitchUserBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        recyclerViewAccount.adapter = accountsAdapter
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        addAccount.setOnClickListener { startActivity(Intent(context, LoginActivity::class.java)) }
        listenToAccounts()
    }

    private fun listenToAccounts() {
        lifecycleScope.launch(Dispatchers.IO) {

            val users = AccountUtils.getAllUsersSync()
            switchUserViewModel.fetchAccounts(users)

            val flows = users.map { user -> MailboxController.getMailboxesAsync(user.id) }

            combine(flows) { mailboxesFlows ->
                transformMailboxesFlowsIntoAccounts(mailboxesFlows, users)
            }.collect {
                withContext(Dispatchers.Main) { onAccountsChange(it) }
            }
        }
    }

    private fun transformMailboxesFlowsIntoAccounts(mailboxesFlows: Array<ResultsChange<Mailbox>>, users: List<User>) =
        mutableListOf<Pair<User, List<Mailbox>>>().apply {
            mailboxesFlows.forEach { flow ->
                val mailboxes = flow.list
                val userId = mailboxes.firstOrNull()?.userId
                users.find { it.id == userId }?.let { user ->
                    add(user to mailboxes)
                }
            }
        }

    private fun onAccountsChange(accounts: List<Pair<User, List<Mailbox>>>) {
        val uiAccounts = accounts
            .map { (user, mailboxes) -> UiAccount(user, mailboxes.sortMailboxes()) }
            .sortAccounts()

        accountsAdapter.notifyAdapter(uiAccounts, MainViewModel.currentMailboxObjectId.value)
    }

    private fun List<UiAccount>.sortAccounts(): List<UiAccount> {
        return filter { it.user.id != AccountUtils.currentUserId }
            .toMutableList()
            .apply { this@sortAccounts.find { it.user.id == AccountUtils.currentUserId }?.let { add(0, it) } }
    }
}
