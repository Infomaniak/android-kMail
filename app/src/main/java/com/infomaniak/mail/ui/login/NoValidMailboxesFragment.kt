/*
 * Infomaniak kMail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
package com.infomaniak.mail.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.infomaniak.mail.databinding.FragmentNoValidMailboxesBinding
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.MailboxListFragment
import com.infomaniak.mail.ui.main.menu.SwitchMailboxesAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@AndroidEntryPoint
class NoValidMailboxesFragment : Fragment(), MailboxListFragment {

    private lateinit var binding: FragmentNoValidMailboxesBinding
    private val mainViewModel: MainViewModel by activityViewModels()


    override val currentClassName: String = NoValidMailboxesFragment::class.java.name
    override val mailboxesAdapter = SwitchMailboxesAdapter(
        isInMenuDrawer = false,
        lifecycleScope = lifecycleScope,
        onLockedMailboxClicked = { mailboxEmail -> onLockedMailboxClicked(mailboxEmail) },
    )

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentNoValidMailboxesBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {

        // changeAccountButton.setOnClickListener {
        //     animatedNavigation(AccountFragmentDirections.actionAccountFragmentToSwitchUserFragment())
        // }
        //
        // attachNewMailboxButton.setOnClickListener {
        //     context.trackAccountEvent("addMailbox")
        //     safeNavigate(AccountFragmentDirections.actionAccountFragmentToAttachMailboxFragment())
        // }
        //
        // mailboxesRecyclerView.apply {
        //     adapter = mailboxesAdapter
        //     isFocusable = false
        // }

        // observeAccountsLive()
    }

    // private fun observeAccountsLive() {
    //     mainViewModel.mailboxesLive.observe(viewLifecycleOwner, mailboxesAdapter::setMailboxes)
    //     lifecycleScope.launch(ioDispatcher) { accountViewModel.updateMailboxes() }
    // }
}
