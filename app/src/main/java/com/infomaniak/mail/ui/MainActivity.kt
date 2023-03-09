/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.FloatRange
import androidx.drawerlayout.widget.DrawerLayout
import androidx.drawerlayout.widget.DrawerLayout.*
import androidx.lifecycle.distinctUntilChanged
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import com.infomaniak.lib.core.networking.LiveDataNetworkStatus
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.MatomoMail.trackScreen
import com.infomaniak.mail.R
import com.infomaniak.mail.checkPlayServices
import com.infomaniak.mail.databinding.ActivityMainBinding
import com.infomaniak.mail.firebase.RegisterFirebaseBroadcastReceiver
import com.infomaniak.mail.ui.main.menu.MenuDrawerFragment
import com.infomaniak.mail.utils.PermissionUtils
import com.infomaniak.mail.utils.UiUtils
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

class MainActivity : ThemedActivity() {

    // This binding is not private because it's used in ThreadListFragment (`(activity as? MainActivity)?.binding`)
    val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val mainViewModel: MainViewModel by viewModels()

    private val permissionUtils by lazy { PermissionUtils(this).also { registerMainPermissions(it) } }

    private val backgroundColor: Int by lazy { getColor(R.color.backgroundColor) }
    private val backgroundHeaderColor: Int by lazy { getColor(R.color.backgroundHeaderColor) }
    private val menuDrawerBackgroundColor: Int by lazy { getColor(R.color.menuDrawerBackgroundColor) }
    private val registerFirebaseBroadcastReceiver by lazy { RegisterFirebaseBroadcastReceiver() }

    private val navController by lazy {
        (supportFragmentManager.findFragmentById(R.id.hostFragment) as NavHostFragment).navController
    }

    private val drawerListener = object : DrawerLayout.DrawerListener {

        var hasDragged = false

        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            colorSystemBarsWithMenuDrawer(slideOffset)
        }

        override fun onDrawerOpened(drawerView: View) {
            if (hasDragged) trackMenuDrawerEvent("openByGesture", TrackerAction.DRAG)
            colorSystemBarsWithMenuDrawer()
            (binding.menuDrawerFragment.getFragment() as? MenuDrawerFragment)?.onDrawerOpened()
        }

        override fun onDrawerClosed(drawerView: View) {
            if (hasDragged) trackMenuDrawerEvent("closeByGesture", TrackerAction.DRAG)
            (binding.menuDrawerFragment.getFragment() as? MenuDrawerFragment)?.closeDropdowns()
        }

        override fun onDrawerStateChanged(newState: Int) {
            when (newState) {
                STATE_DRAGGING -> hasDragged = true
                STATE_IDLE -> hasDragged = false
                else -> Unit
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        handleOnBackPressed()

        // TODO: Does the NewMessageActivity still crash when there is too much recipients?
        observeNetworkStatus()
        binding.drawerLayout.addDrawerListener(drawerListener)
        registerFirebaseBroadcastReceiver.initFirebaseBroadcastReceiver(this, mainViewModel)

        setupSnackBar()
        setupNavController()
        setupMenuDrawerCallbacks()

        mainViewModel.updateUserInfo()
        mainViewModel.loadCurrentMailbox()

        mainViewModel.observeMergedContactsLive()
        permissionUtils.requestMainPermissionsIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        mainViewModel.forceRefreshMailboxesAndFolders()
    }

    override fun onResume() {
        super.onResume()
        checkPlayServices()
        if (binding.drawerLayout.isOpen) colorSystemBarsWithMenuDrawer()
    }

    private fun handleOnBackPressed() = with(binding) {

        fun closeDrawer() {
            (menuDrawerFragment.getFragment() as? MenuDrawerFragment)?.closeDrawer()
        }

        fun popBack() {
            if (navController.currentDestination?.id == R.id.threadListFragment) {
                finish()
            } else {
                navController.popBackStack()
            }
        }

        onBackPressedDispatcher.addCallback(this@MainActivity) {
            if (drawerLayout.isOpen) closeDrawer() else popBack()
        }
    }

    override fun onDestroy() {
        binding.drawerLayout.removeDrawerListener(drawerListener)
        super.onDestroy()
    }

    private fun observeNetworkStatus() {
        LiveDataNetworkStatus(this).distinctUntilChanged().observe(this) { isAvailable ->
            Log.d("Internet availability", if (isAvailable) "Available" else "Unavailable")
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "Network"
                message = "Internet access is available : $isAvailable"
                level = if (isAvailable) SentryLevel.INFO else SentryLevel.WARNING
            })
            mainViewModel.isInternetAvailable.value = isAvailable
        }
    }

    private fun setupSnackBar() {
        fun getAnchor(): View? = when (navController.currentDestination?.id) {
            R.id.threadListFragment -> findViewById(R.id.newMessageFab)
            R.id.threadFragment -> findViewById(R.id.quickActionBar)
            else -> null
        }

        mainViewModel.snackBarManager.setup(this, ::getAnchor) {
            trackEvent("snackbar", "undo")
            mainViewModel.undoAction(it)
        }
    }

    private fun setupNavController() {
        navController.addOnDestinationChangedListener { _, destination, _ -> onDestinationChanged(destination) }
    }

    private fun setupMenuDrawerCallbacks() = with(binding) {
        (menuDrawerFragment.getFragment() as? MenuDrawerFragment)?.exitDrawer = { drawerLayout.close() }
    }

    private fun registerMainPermissions(permissionUtils: PermissionUtils) {
        permissionUtils.registerMainPermissions { permissionsResults ->
            if (permissionsResults[Manifest.permission.READ_CONTACTS] == true) mainViewModel.updateUserInfo()
        }
    }

    private fun onDestinationChanged(destination: NavDestination) {
        destination.addSentryBreadcrumb()

        setDrawerLockMode(destination.id == R.id.threadListFragment)

        when (destination.id) {
            R.id.junkBottomSheetDialog,
            R.id.messageActionBottomSheetDialog,
            R.id.replyBottomSheetDialog,
            R.id.detailedContactBottomSheetDialog,
            R.id.threadFragment,
            R.id.threadActionsBottomSheetDialog -> null
            R.id.searchFragment -> R.color.backgroundColor
            else -> R.color.backgroundHeaderColor
        }?.let {
            window.statusBarColor = getColor(it)
        }

        window.navigationBarColor = getColor(
            when (destination.id) {
                R.id.threadFragment -> R.color.elevatedBackground
                R.id.messageActionBottomSheetDialog,
                R.id.replyBottomSheetDialog,
                R.id.detailedContactBottomSheetDialog,
                R.id.threadActionsBottomSheetDialog -> R.color.backgroundColorSecondary
                else -> R.color.backgroundColor
            }
        )

        destination.trackDestination()
    }

    @SuppressLint("RestrictedApi")
    private fun NavDestination.addSentryBreadcrumb() {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "Navigation"
            message = "Accessed to destination : $displayName"
            level = SentryLevel.INFO
        })
    }

    @SuppressLint("RestrictedApi")
    private fun NavDestination.trackDestination() {
        trackScreen(displayName.substringAfter("${BuildConfig.APPLICATION_ID}:id"), label.toString())
    }

    private fun setDrawerLockMode(isUnlocked: Boolean) {
        binding.drawerLayout.setDrawerLockMode(if (isUnlocked) LOCK_MODE_UNLOCKED else LOCK_MODE_LOCKED_CLOSED)
    }

    private fun colorSystemBarsWithMenuDrawer(@FloatRange(0.0, 1.0) slideOffset: Float = FULLY_SLID) {
        if (slideOffset == FULLY_SLID) {
            window.statusBarColor = menuDrawerBackgroundColor
            window.navigationBarColor = menuDrawerBackgroundColor
        } else {
            window.statusBarColor = UiUtils.pointBetweenColors(backgroundHeaderColor, menuDrawerBackgroundColor, slideOffset)
            window.navigationBarColor = UiUtils.pointBetweenColors(backgroundColor, menuDrawerBackgroundColor, slideOffset)
        }
    }

    private companion object {
        const val FULLY_SLID = 1.0f
    }
}
