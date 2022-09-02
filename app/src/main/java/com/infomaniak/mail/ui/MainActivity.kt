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

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.core.graphics.toColor
import androidx.core.graphics.toColorInt
import androidx.drawerlayout.widget.DrawerLayout
import androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED
import androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.infomaniak.lib.core.utils.LiveDataNetworkStatus
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ActivityMainBinding
import com.infomaniak.mail.ui.main.menu.MenuDrawerFragment
import com.infomaniak.mail.utils.UiUtils
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

class MainActivity : ThemedActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    lateinit var backgroundColor: Color
    lateinit var backgroundHeaderColor: Color

    private val drawerListener = object : DrawerLayout.DrawerListener {
        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            window.statusBarColor = UiUtils.pointBetweenColors(backgroundHeaderColor, backgroundColor, slideOffset).toColorInt()
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

        backgroundColor = getColor(R.color.backgroundColor).toColor()
        backgroundHeaderColor = getColor(R.color.backgroundHeaderColor).toColor()

        // TODO: Does the NewMessageActivity still crash when there is too much recipients?
        listenToNetworkStatus()
        binding.drawerLayout.addDrawerListener(drawerListener)

        setupNavController()
        setupMenuDrawerCallbacks()
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
                // lifecycleScope.launch(Dispatchers.IO) { AccountUtils.updateCurrentUserAndDrives(this@MainActivity) } // TODO?
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
                R.id.threadFragment -> R.color.backgroundQuickActions
                else -> R.color.backgroundColor
            }
        )

        // TODO: Matomo
        // with(destination) {
        //     application.trackScreen(displayName.substringAfter("${BuildConfig.APPLICATION_ID}:id"), label.toString())
        // }
    }

    override fun onBackPressed(): Unit = with(binding) {
        if (drawerLayout.isOpen) {
            (menuDrawerFragment.getFragment() as? MenuDrawerFragment)?.closeDrawer()
        } else {
            super.onBackPressed()
        }
    }

    private fun setupMenuDrawerCallbacks() = with(binding) {
        (menuDrawerFragment.getFragment() as? MenuDrawerFragment)?.apply {
            exitDrawer = { drawerLayout.close() }
            isDrawerOpen = { drawerLayout.isOpen }
        }
    }

    private fun setDrawerLockMode(isUnlocked: Boolean) {
        binding.drawerLayout.setDrawerLockMode(if (isUnlocked) LOCK_MODE_UNLOCKED else LOCK_MODE_LOCKED_CLOSED)
    }

    override fun onDestroy() {
        binding.drawerLayout.removeDrawerListener(drawerListener)
        super.onDestroy()
    }
}
