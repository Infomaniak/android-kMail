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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.lib.core.views.DividerItemDecorator
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.DialogConfirmLogoutBinding
import com.infomaniak.mail.databinding.FragmentManageMailAddressBinding
import com.infomaniak.mail.ui.main.user.ManageMailAddressViewModel
import com.infomaniak.mail.ui.main.user.SimpleMailboxAdapter
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.animatedNavigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ManageMailAddressFragment : Fragment() {

    private lateinit var binding: FragmentManageMailAddressBinding
    private val dialogBinding by lazy { DialogConfirmLogoutBinding.inflate(layoutInflater) }
    private val manageMailAddressViewModel: ManageMailAddressViewModel by viewModels()

    private var simpleMailboxAdapter = SimpleMailboxAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentManageMailAddressBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        var currentUserEmail = ""

        AccountUtils.currentUser?.let { user ->
            avatar.loadAvatar(user)
            mail.text = user.email
            currentUserEmail = getString(R.string.confirmLogoutTitle, user.email)
        }

        changeAccountButton.setOnClickListener { animatedNavigation(ManageMailAddressFragmentDirections.actionManageMailAddressFragmentToSwitchUserFragment()) }

        disconnectAccountButton.setOnClickListener {
            dialogBinding.confirmLogoutDialogTitle.text = currentUserEmail
            MaterialAlertDialogBuilder(requireContext())
                .setView(dialogBinding.root)
                .setPositiveButton(R.string.buttonLogout) { _, _ -> removeCurrentUser() }
                .setNegativeButton(R.string.buttonCancel, null)
                .create()
                .show()
        }

        mailboxesRecyclerView.apply {
            adapter = simpleMailboxAdapter
            ResourcesCompat.getDrawable(resources, R.drawable.setting_divider, null)?.let {
                addItemDecoration(DividerItemDecorator(it))
            }
        }

        manageMailAddressViewModel.observeAccounts().observe(viewLifecycleOwner) { mailboxes ->
            simpleMailboxAdapter.updateMailboxes(mailboxes.map { it.email })
        }
    }

    private fun removeCurrentUser() = lifecycleScope.launch(Dispatchers.IO) {
        AccountUtils.removeUser(requireContext(), AccountUtils.currentUser!!)
    }
}
