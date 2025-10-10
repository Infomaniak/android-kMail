/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.sync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.safeNavigate
import com.infomaniak.core.legacy.utils.setMargins
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackSyncAutoConfigEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.databinding.FragmentSyncOnboardingBinding
import com.infomaniak.mail.utils.extensions.applyWindowInsetsListener
import com.infomaniak.mail.utils.extensions.safeArea
import com.infomaniak.mail.utils.extensions.statusBar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SyncOnboardingFragment : Fragment() {

    private var binding: FragmentSyncOnboardingBinding by safeBinding()
    private val syncAutoConfigViewModel: SyncAutoConfigViewModel by activityViewModels()

    @Inject
    lateinit var localSettings: LocalSettings

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSyncOnboardingBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        handleEdgeToEdge()

        binding.toolbar.setNavigationOnClickListener { requireActivity().finish() }
        setupClickListener()
    }

    private fun handleEdgeToEdge() = with(binding) {
        applyWindowInsetsListener { _, insets ->
            dummyToolbarEdgeToEdge.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    insets.statusBar().top,
                )
                context?.getColor(R.color.onboarding_secondary_background)?.let(::setBackgroundColor)
            }
            with(insets.safeArea()) {
                toolbar.setMargins(left = left)
            }
        }
    }

    private fun setupClickListener() {
        val matomoName = if (syncAutoConfigViewModel.isSyncAppUpToDate()) {
            MatomoName.ConfigureReady
        } else {
            MatomoName.ConfigureInstall
        }
        binding.continueButton.setOnClickListener {
            trackSyncAutoConfigEvent(matomoName)
            safeNavigate(SyncOnboardingFragmentDirections.actionSyncOnboardingFragmentToSyncConfigureFragment())
        }
    }
}
