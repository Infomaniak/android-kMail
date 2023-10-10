/*
 * Infomaniak Mail - Android
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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.databinding.FragmentSwitchUserBinding
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.launchLoginActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SwitchUserFragment : Fragment() {

    private var binding: FragmentSwitchUserBinding by safeBinding()
    private val switchUserViewModel: SwitchUserViewModel by viewModels()

    private val accountsAdapter = SwitchUserAdapter(AccountUtils.currentUserId) { user ->
        switchUserViewModel.switchAccount(user)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSwitchUserBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        setAdapter()
        setupOnClickListener()
        observeAccounts()
    }

    private fun setAdapter() {
        binding.recyclerViewAccount.adapter = accountsAdapter
    }

    private fun setupOnClickListener() = with(binding) {
        addAccount.setOnClickListener {
            context.trackAccountEvent("add")
            context.launchLoginActivity()
        }
    }

    private fun observeAccounts() {
        switchUserViewModel.allUsers.observe(viewLifecycleOwner, accountsAdapter::initializeAccounts)
    }
}
