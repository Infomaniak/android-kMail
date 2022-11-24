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
package com.infomaniak.mail.ui

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import androidx.drawerlayout.widget.DrawerLayout
import androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED
import androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.infomaniak.lib.core.utils.LiveDataNetworkStatus
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail.trackScreen
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ActivityMainBinding
import com.infomaniak.mail.ui.main.menu.MenuDrawerFragment
import com.infomaniak.mail.utils.UiUtils
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

class MainActivity : ThemedActivity() {

    // This binding is not private because it's used in ThreadListFragment (`(activity as? MainActivity)?.binding`)
    val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val mainViewModel: MainViewModel by viewModels()

    private var contactPermissionResultLauncher = registerForActivityResult(RequestPermission()) { isGranted ->
        if (isGranted) mainViewModel.updateUserInfo()
    }

    private val backgroundColor: Int by lazy { getColor(R.color.backgroundColor) }
    private val backgroundHeaderColor: Int by lazy { getColor(R.color.backgroundHeaderColor) }

    private val drawerListener = object : DrawerLayout.DrawerListener {
        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            window.statusBarColor = UiUtils.pointBetweenColors(backgroundHeaderColor, backgroundColor, slideOffset)
        }

        override fun onDrawerOpened(drawerView: View) {
            window.statusBarColor = getColor(R.color.backgroundColor)
            (binding.menuDrawerFragment.getFragment() as? MenuDrawerFragment)?.onDrawerOpened()
        }

        override fun onDrawerClosed(drawerView: View) {
            (binding.menuDrawerFragment.getFragment() as? MenuDrawerFragment)?.closeDropdowns()
        }

        override fun onDrawerStateChanged(newState: Int) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // TODO: Does the NewMessageActivity still crash when there is too much recipients?
        observeNetworkStatus()
        binding.drawerLayout.addDrawerListener(drawerListener)

        setupNavController()
        setupMenuDrawerCallbacks()

        mainViewModel.updateUserInfo()
        mainViewModel.loadCurrentMailbox()

        mainViewModel.observeRealmMergedContacts()
        requestContactsPermission()
    }

    override fun onBackPressed(): Unit = with(binding) {
        if (drawerLayout.isOpen) {
            (menuDrawerFragment.getFragment() as? MenuDrawerFragment)?.closeDrawer()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        binding.drawerLayout.removeDrawerListener(drawerListener)
        super.onDestroy()
    }

    private fun observeNetworkStatus() {
        LiveDataNetworkStatus(this).observe(this) { isAvailable ->
            Log.d("Internet availability", if (isAvailable) "Available" else "Unavailable")
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "Network"
                message = "Internet access is available : $isAvailable"
                level = if (isAvailable) SentryLevel.INFO else SentryLevel.WARNING
            })
            mainViewModel.isInternetAvailable.value = isAvailable
            if (isAvailable) {
                // lifecycleScope.launch(Dispatchers.IO) { AccountUtils.updateCurrentUserAndDrives(this@MainActivity) } // TODO?
            }
        }
    }

    private fun setupNavController() {
        (supportFragmentManager.findFragmentById(R.id.hostFragment) as NavHostFragment)
            .navController
            .addOnDestinationChangedListener { _, destination, _ -> onDestinationChanged(destination) }
    }

    private fun setupMenuDrawerCallbacks() = with(binding) {
        (menuDrawerFragment.getFragment() as? MenuDrawerFragment)?.apply {
            exitDrawer = { drawerLayout.close() }
            isDrawerOpen = { drawerLayout.isOpen }
        }
    }

    private fun requestContactsPermission() {
        contactPermissionResultLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    private fun onDestinationChanged(destination: NavDestination) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "Navigation"
            message = "Accessed to destination : ${destination.displayName}"
            level = SentryLevel.INFO
        })

        setDrawerLockMode(destination.id == R.id.threadListFragment)

        when (destination.id) {
            R.id.messageActionBottomSheetDialog,
            R.id.replyBottomSheetDialog,
            R.id.detailedContactBottomSheetDialog,
            R.id.threadActionsBottomSheetDialog -> null
            R.id.searchFragment, R.id.threadFragment -> R.color.backgroundColor
            else -> R.color.backgroundHeaderColor
        }?.let {
            window.statusBarColor = getColor(it)
        }

        window.navigationBarColor = getColor(
            when (destination.id) {
                R.id.threadFragment -> R.color.backgroundSecondaryColor
                else -> R.color.backgroundColor
            }
        )

        with(destination) {
            trackScreen(displayName.substringAfter("${BuildConfig.APPLICATION_ID}:id"), label.toString())
        }
    }

    private fun setDrawerLockMode(isUnlocked: Boolean) {
        binding.drawerLayout.setDrawerLockMode(if (isUnlocked) LOCK_MODE_UNLOCKED else LOCK_MODE_LOCKED_CLOSED)
    }

}
