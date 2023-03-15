/*
 * Infomaniak kMail - Android
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
package com.infomaniak.mail.ui.main.menu

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.views.DividerItemDecorator
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentManageMailAddressBinding
import com.infomaniak.mail.ui.main.user.ManageMailAddressViewModel
import com.infomaniak.mail.ui.main.user.SimpleMailboxAdapter
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.animatedNavigation
import com.infomaniak.mail.utils.createDescriptionDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ManageMailAddressFragment : Fragment() {

    private lateinit var binding: FragmentManageMailAddressBinding
    private val manageMailAddressViewModel: ManageMailAddressViewModel by viewModels()

    private val logoutAlert by lazy { initLogoutAlert() }

    private var simpleMailboxAdapter = SimpleMailboxAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentManageMailAddressBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        AccountUtils.currentUser?.let { user ->
            avatar.loadAvatar(user)
            mail.text = user.email
        }

        changeAccountButton.setOnClickListener { animatedNavigation(ManageMailAddressFragmentDirections.actionManageMailAddressFragmentToSwitchUserFragment()) }

        disconnectAccountButton.setOnClickListener {
            context.trackAccountEvent("logOut")
            logoutAlert.show()
        }

        mailboxesRecyclerView.apply {
            adapter = simpleMailboxAdapter
            ResourcesCompat.getDrawable(resources, R.drawable.divider, null)?.let {
                addItemDecoration(DividerItemDecorator(it))
            }
            isFocusable = false
        }

        observeAccountsLive()
    }

    private fun removeCurrentUser() = lifecycleScope.launch(Dispatchers.IO) {
        requireContext().trackAccountEvent("logOutConfirm")
        AccountUtils.removeUser(requireContext(), AccountUtils.currentUser!!)
    }

    private fun observeAccountsLive() = with(manageMailAddressViewModel) {

        updateMailboxes()

        observeAccountsLive.observe(viewLifecycleOwner) { mailboxes ->
            simpleMailboxAdapter.updateMailboxes(mailboxes.map { it.email })
        }
    }

    private fun initLogoutAlert() = createDescriptionDialog(
        title = getString(R.string.confirmLogoutTitle),
        description = AccountUtils.currentUser?.let { getString(R.string.confirmLogoutDescription, it.email) } ?: "",
        onPositiveButtonClicked = ::removeCurrentUser,
    )
}
