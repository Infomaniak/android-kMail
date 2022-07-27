/*
 * Infomaniak kMail - Android
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
package com.infomaniak.mail.ui.main

import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED
import androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.infomaniak.lib.core.utils.LiveDataNetworkStatus
import com.infomaniak.mail.R
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.databinding.ActivityMainBinding
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

class MainActivity : ThemedActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // TODO: This is removed for now because it makes the NewMessageActivity crash when there is too much recipients.
        // listenToNetworkStatus()
        setupNavController()

        MailData.loadUserInfos()
    }

    private fun listenToNetworkStatus() {
        LiveDataNetworkStatus(this).observe(this) { isAvailable ->
            Log.d("Internet availability", if (isAvailable) "Available" else "Unavailable")
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "Network"
                message = "Internet access is available : $isAvailable"
                level = if (isAvailable) SentryLevel.INFO else SentryLevel.WARNING
            })
            mainViewModel.isInternetAvailable.value = isAvailable
            if (isAvailable) {
                // lifecycleScope.launch { AccountUtils.updateCurrentUserAndDrives(this@MainActivity) } // TODO?
            }
        }
    }

    private fun setupNavController() {
        (supportFragmentManager.findFragmentById(R.id.hostFragment) as NavHostFragment)
            .navController
            .addOnDestinationChangedListener { _, destination, _ -> onDestinationChanged(destination) }
    }

    private fun onDestinationChanged(destination: NavDestination) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "Navigation"
            message = "Accessed to destination : ${destination.displayName}"
            level = SentryLevel.INFO
        })

        setDrawerLockMode(destination.id == R.id.threadListFragment)

        // TODO: Matomo
        // with(destination) {
        //     application.trackScreen(displayName.substringAfter("${BuildConfig.APPLICATION_ID}:id"), label.toString())
        // }
    }

    private fun setDrawerLockMode(isUnlocked: Boolean) {
        binding.drawerLayout.setDrawerLockMode(if (isUnlocked) LOCK_MODE_UNLOCKED else LOCK_MODE_LOCKED_CLOSED)
    }
}
