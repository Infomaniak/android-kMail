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
import androidx.activity.enableEdgeToEdge
import androidx.navigation.fragment.NavHostFragment
import com.infomaniak.mail.MatomoMail.trackDestination
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ActivitySyncAutoConfigBinding
import com.infomaniak.mail.ui.BaseActivity
import com.infomaniak.mail.ui.main.SnackbarManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SyncAutoConfigActivity : BaseActivity() {

    private val binding by lazy { ActivitySyncAutoConfigBinding.inflate(layoutInflater) }

    private val navController by lazy {
        (supportFragmentManager.findFragmentById(R.id.syncAutoConfigHostFragment) as NavHostFragment).navController
    }

    @Inject
    lateinit var snackbarManager: SnackbarManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        setupSnackbar()
        setupNavController()
    }

    private fun setupSnackbar() {
        snackbarManager.setup(view = binding.root, activity = this)
    }

    private fun setupNavController() {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            trackDestination(destination)
        }
    }
}
