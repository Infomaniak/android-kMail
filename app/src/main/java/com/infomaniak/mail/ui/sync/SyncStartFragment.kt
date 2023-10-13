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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.databinding.FragmentSyncStartBinding
import com.infomaniak.mail.ui.main.settings.SettingsFragment

class SyncStartFragment : Fragment() {

    private var binding: FragmentSyncStartBinding by safeBinding()
    private val syncAutoConfigViewModel: SyncAutoConfigViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSyncStartBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListener()
    }

    private fun setupClickListener() {
        binding.startButton.setOnClickListener {
            syncAutoConfigViewModel.fetchCredentials { intent ->
                startActivity(intent)
                with(requireActivity()) {
                    setResult(AppCompatActivity.RESULT_OK, Intent().putExtra(SettingsFragment.SYNC_AUTO_CONFIG_SUCCESS_KEY, true))
                    finish()
                }
            }
        }
    }
}
