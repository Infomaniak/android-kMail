/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.noValidMailboxes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.infomaniak.core.legacy.ui.WebViewActivity
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.safeNavigate
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackNoValidMailboxesEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentNoValidMailboxesBinding
import com.infomaniak.mail.ui.main.menuDrawer.InvalidMailboxesAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NoValidMailboxesFragment : Fragment() {

    private var binding: FragmentNoValidMailboxesBinding by safeBinding()
    private val noValidMailboxesViewModel: NoValidMailboxesViewModel by activityViewModels()

    private val mailboxesAdapter get() = binding.lockedMailboxesRecyclerView.adapter as InvalidMailboxesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentNoValidMailboxesBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapters()
        setupListeners()

        observeMailboxesLive()
    }

    private fun setupAdapters() = with(binding) {
        lockedMailboxesRecyclerView.adapter = InvalidMailboxesAdapter()
    }

    private fun setupListeners() = with(binding) {
        noValidMailboxesBlock.setOnActionClicked {
            trackNoValidMailboxesEvent(MatomoName.ReadFAQ)
            WebViewActivity.startActivity(requireContext(), getString(R.string.faqUrl))
        }

        changeAccountButton.setOnClickListener {
            trackNoValidMailboxesEvent(MatomoName.SwitchAccount)
            safeNavigate(resId = R.id.accountBottomSheetDialog)
        }
    }

    private fun observeMailboxesLive() = with(binding) {
        noValidMailboxesViewModel.lockedMailboxesLive.observe(viewLifecycleOwner) { lockedMailboxes ->
            mailboxesAdapter.setMailboxes(lockedMailboxes)
            lockedMailboxesGroup.isVisible = lockedMailboxes.isNotEmpty()
            lockedMailboxTitle.text = resources.getQuantityString(R.plurals.lockedMailboxTitle, lockedMailboxes.count())
        }
    }
}
