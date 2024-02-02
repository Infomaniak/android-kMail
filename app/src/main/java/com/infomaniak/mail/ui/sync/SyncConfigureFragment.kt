/*
 * Infomaniak Mail - Android
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
package com.infomaniak.mail.ui.sync

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.goToPlayStore
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.MatomoMail.trackSyncAutoConfigEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentSyncConfigureBinding
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.utils.isUserAlreadySynchronized
import com.infomaniak.mail.utils.setSystemBarsColors

class SyncConfigureFragment : Fragment() {

    private var binding: FragmentSyncConfigureBinding by safeBinding()
    private val syncAutoConfigViewModel: SyncAutoConfigViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSyncConfigureBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setSystemBarsColors(statusBarColor = R.color.backgroundColor)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateStatusIfNeeded()
    }

    private fun setupListeners() = with(binding) {
        installButton.setOnClickListener {
            trackSyncAutoConfigEvent("openPlayStore")
            context.goToPlayStore(SyncAutoConfigViewModel.SYNC_PACKAGE)
        }

        startButton.setOnClickListener {
            if (requireContext().isUserAlreadySynchronized()) {
                trackSyncAutoConfigEvent("alreadySynchronized", TrackerAction.DATA)
                goBackToThreadList(MainActivity.SYNC_AUTO_CONFIG_ALREADY_SYNC)
            } else {
                trackSyncAutoConfigEvent("openSyncApp")
                syncAutoConfigViewModel.configureUserAutoSync { intent ->
                    startActivity(intent)
                    goBackToThreadList(MainActivity.SYNC_AUTO_CONFIG_SUCCESS)
                }
            }
        }
    }

    private fun goBackToThreadList(reason: String) = with(requireActivity()) {
        setResult(AppCompatActivity.RESULT_OK, Intent().putExtra(MainActivity.SYNC_AUTO_CONFIG_KEY, reason))
        finish()
    }

    private fun updateStatusIfNeeded() {
        setVisibilityState(state = if (syncAutoConfigViewModel.isSyncAppUpToDate()) State.READY else State.INSTALL)
    }

    private fun setVisibilityState(state: State) = with(binding) {
        val isReady = state == State.READY

        startButton.isVisible = isReady
        installedTextView.isVisible = isReady
        startDescription.isVisible = isReady

        installDescription.isGone = isReady
        installButton.isGone = isReady
    }

    private enum class State {
        INSTALL,
        READY,
    }
}
