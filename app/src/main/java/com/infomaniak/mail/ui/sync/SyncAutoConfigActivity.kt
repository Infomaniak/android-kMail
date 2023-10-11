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
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.viewModels
import com.infomaniak.lib.core.utils.goToPlayStore
import com.infomaniak.mail.databinding.ActivitySyncAutoConfigBinding
import com.infomaniak.mail.ui.BaseActivity
import com.infomaniak.mail.ui.main.settings.SettingsFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SyncAutoConfigActivity : BaseActivity() {

    private val binding by lazy { ActivitySyncAutoConfigBinding.inflate(layoutInflater) }
    private val syncAutoConfigViewModel: SyncAutoConfigViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupSnackBar()
    }

    override fun onResume() {
        super.onResume()
        updateButtonClickListeners()
    }

    private fun setupSnackBar() {
        syncAutoConfigViewModel.snackBarManager.setup(view = binding.root, activity = this)
    }

    // TODO: Add all Matomo in SyncAutoConfig feature
    private fun updateButtonClickListeners() = with(binding) {
        if (isSyncAppInstalled()) {
            installButton.isEnabled = false
            startButton.isEnabled = true
            startButton.setOnClickListener { onSyncAutoConfigClicked() }
        } else {
            startButton.isEnabled = false
            installButton.isEnabled = true
            installButton.setOnClickListener { goToPlayStore(SyncAutoConfigViewModel.SYNC_PACKAGE) }
        }
    }

    private fun isSyncAppInstalled(): Boolean = runCatching {
        packageManager.getPackageInfo(SyncAutoConfigViewModel.SYNC_PACKAGE, PackageManager.GET_ACTIVITIES)
        true
    }.getOrElse {
        false
    }

    private fun onSyncAutoConfigClicked() = with(syncAutoConfigViewModel) {
        fetchCredentials { intent ->
            startActivity(intent)
            setResult(RESULT_OK, Intent().putExtra(SettingsFragment.SYNC_AUTO_CONFIG_SUCCESS_KEY, true))
            finish()
        }
    }
}
