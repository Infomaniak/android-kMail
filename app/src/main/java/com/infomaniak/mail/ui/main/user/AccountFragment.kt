/*
 * Infomaniak ikMail - Android
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
package com.infomaniak.mail.ui.main.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.MatomoMail.ADD_MAILBOX_NAME
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentAccountBinding
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.MailboxListFragment
import com.infomaniak.mail.ui.main.menu.MailboxesAdapter
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.animatedNavigation
import com.infomaniak.mail.utils.createDescriptionDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AccountFragment : Fragment(), MailboxListFragment {

    private lateinit var binding: FragmentAccountBinding
    private val mainViewModel: MainViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by viewModels()

    private val logoutAlert by lazy { initLogoutAlert() }

    override val hasValidMailboxes = true
    override val currentClassName: String = AccountFragment::class.java.name
    override val mailboxesAdapter = MailboxesAdapter(
        isInMenuDrawer = false,
        hasValidMailboxes = hasValidMailboxes,
        lifecycleScope = lifecycleScope,
        onLockedMailboxClicked = { mailboxEmail -> onLockedMailboxClicked(mailboxEmail) },
        onInvalidPasswordMailboxClicked = { mailbox -> onInvalidPasswordMailboxClicked(mailbox) },
    )

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentAccountBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        AccountUtils.currentUser?.let { user ->
            avatar.loadAvatar(user)
            name.apply {
                isGone = user.displayName.isNullOrBlank()
                text = user.displayName
            }
            mail.text = user.email
        }

        changeAccountButton.setOnClickListener {
            animatedNavigation(AccountFragmentDirections.actionAccountFragmentToSwitchUserFragment())
        }

        attachNewMailboxButton.setOnClickListener {
            context.trackAccountEvent(ADD_MAILBOX_NAME)
            safeNavigate(AccountFragmentDirections.actionAccountFragmentToAttachMailboxFragment())
        }

        disconnectAccountButton.setOnClickListener {
            context.trackAccountEvent("logOut")
            logoutAlert.show()
        }

        mailboxesRecyclerView.apply {
            adapter = mailboxesAdapter
            isFocusable = false
        }

        observeAccountsLive()
    }

    private fun removeCurrentUser() = lifecycleScope.launch(ioDispatcher) {
        requireContext().trackAccountEvent("logOutConfirm")
        AccountUtils.removeUser(requireContext(), AccountUtils.currentUser!!)
    }

    private fun observeAccountsLive() {
        mainViewModel.mailboxesLive.observe(viewLifecycleOwner, mailboxesAdapter::setMailboxes)
        lifecycleScope.launch(ioDispatcher) { accountViewModel.updateMailboxes() }
    }

    private fun initLogoutAlert() = createDescriptionDialog(
        title = getString(R.string.confirmLogoutTitle),
        description = AccountUtils.currentUser?.let { getString(R.string.confirmLogoutDescription, it.email) } ?: "",
        onPositiveButtonClicked = ::removeCurrentUser,
    )
}
