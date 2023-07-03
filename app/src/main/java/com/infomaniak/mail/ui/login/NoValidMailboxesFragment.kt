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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.MatomoMail.ADD_MAILBOX_NAME
import com.infomaniak.mail.MatomoMail.trackNoValidMailboxesEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentNoValidMailboxesBinding
import com.infomaniak.mail.ui.main.menu.SwitchMailboxesAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NoValidMailboxesFragment : Fragment() {

    private lateinit var binding: FragmentNoValidMailboxesBinding
    private val noValidMailboxesViewModel: NoValidMailboxesViewModel by activityViewModels()

    private val lockedMailboxesAdapter = SwitchMailboxesAdapter(
        isInMenuDrawer = false,
        lifecycleScope = lifecycleScope,
        onLockedMailboxClicked = {},
    )

    private val invalidPasswordMailboxesAdapter = SwitchMailboxesAdapter(
        isInMenuDrawer = false,
        lifecycleScope = lifecycleScope,
        onLockedMailboxClicked = {},
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentNoValidMailboxesBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {

        observeMailboxesCount()
        observeMailboxesLive()

        changeAccountButton.setOnClickListener {
            trackNoValidMailboxesEvent("switchAccount")
            safeNavigate(NoValidMailboxesFragmentDirections.actionNoValidMailboxesFragmentToSwitchUserFragment())
        }

        attachNewMailboxButton.setOnClickListener {
            trackNoValidMailboxesEvent(ADD_MAILBOX_NAME)
            safeNavigate(NoValidMailboxesFragmentDirections.actionNoValidMailboxesFragmentToAttachMailboxFragment())
        }

    }

    private fun setQuantityTextTitle(mailboxCount: Long) = with(binding) {
        val count = mailboxCount.toInt()
        val lockedMailboxTitleString = resources.getQuantityString(R.plurals.lockedMailboxTitle, count)

        invalidMailboxTitle.text = lockedMailboxTitleString
        lockedMailboxTitle.text = lockedMailboxTitleString
        invalidPasswordTitle.text = resources.getQuantityString(R.plurals.blockedPasswordTitle, count)
        invalidMailboxDescription.text = resources.getQuantityText(R.plurals.lockedMailboxDescription, count)
    }

    private fun observeMailboxesLive() = with(binding) {
        noValidMailboxesViewModel.invalidPasswordMailboxesLive.observe(viewLifecycleOwner) { invalidPasswordMailboxes ->
            invalidPasswordMailboxesGroup.isVisible = invalidPasswordMailboxes.isNotEmpty()
            invalidPasswordMailboxesAdapter.setMailboxes(invalidPasswordMailboxes)
            invalidPasswordMailboxesRecyclerView.adapter = invalidPasswordMailboxesAdapter
        }

        noValidMailboxesViewModel.lockedMailboxesLive.observe(viewLifecycleOwner) { lockedMailboxes ->
            lockedMailboxesGroup.isVisible = lockedMailboxes.isNotEmpty()
            lockedMailboxesAdapter.setMailboxes(lockedMailboxes)
            lockedMailboxesRecyclerView.adapter = lockedMailboxesAdapter

        }
    }

    private fun observeMailboxesCount() {
        noValidMailboxesViewModel.mailboxesCount.observe(viewLifecycleOwner, ::setQuantityTextTitle)
    }
}
