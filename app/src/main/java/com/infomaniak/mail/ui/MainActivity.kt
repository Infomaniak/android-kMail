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
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.annotation.FloatRange
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import com.infomaniak.lib.core.networking.LiveDataNetworkStatus
import com.infomaniak.lib.core.utils.showToast
import com.infomaniak.lib.stores.checkUpdateIsAvailable
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.GplayUtils.checkPlayServices
import com.infomaniak.mail.MatomoMail.trackDestination
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ActivityMainBinding
import com.infomaniak.mail.firebase.RegisterFirebaseBroadcastReceiver
import com.infomaniak.mail.ui.main.menu.MenuDrawerFragment
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivity
import com.infomaniak.mail.utils.PermissionUtils
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.UiUtils
import com.infomaniak.mail.utils.updateNavigationBarColor
import com.infomaniak.mail.workers.DraftsActionsWorker
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ThemedActivity() {

    // This binding is not private because it's used in ThreadListFragment (`(activity as? MainActivity)?.binding`)
    val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val mainViewModel: MainViewModel by viewModels()

    private val permissionUtils by lazy { PermissionUtils(this).also(::registerMainPermissions) }

    private val backgroundColor: Int by lazy { getColor(R.color.backgroundColor) }
    private val backgroundHeaderColor: Int by lazy { getColor(R.color.backgroundHeaderColor) }
    private val menuDrawerBackgroundColor: Int by lazy { getColor(R.color.menuDrawerBackgroundColor) }
    private val registerFirebaseBroadcastReceiver by lazy { RegisterFirebaseBroadcastReceiver() }
    private val newMessageActivityResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        // TODO : Const for "draftAction" and find best way to send "save" as value
        if (result.data?.getStringExtra("draftAction") == "save") {
            mainViewModel.snackBarManager.postValue(getString(R.string.snackbarDraftSaving))
        }
    }

    private val navController by lazy {
        (supportFragmentManager.findFragmentById(R.id.hostFragment) as NavHostFragment).navController
    }

    @Inject
    lateinit var draftsActionsWorkerScheduler: DraftsActionsWorker.Scheduler

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
                DrawerLayout.STATE_DRAGGING -> hasDragged = true
                DrawerLayout.STATE_IDLE -> hasDragged = false
                else -> Unit
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        setContentView(binding.root)
        handleOnBackPressed()

        observeNetworkStatus()
        observeDraftWorker()
        binding.drawerLayout.addDrawerListener(drawerListener)
        registerFirebaseBroadcastReceiver.initFirebaseBroadcastReceiver(this, mainViewModel)

        setupSnackBar()
        setupNavController()
        setupMenuDrawerCallbacks()

        handleUpdates()

        mainViewModel.updateUserInfo()
        loadCurrentMailbox()

        mainViewModel.observeMergedContactsLive()

        permissionUtils.requestMainPermissionsIfNeeded()
    }

    private fun observeDraftWorker() {
        val treatedWorkInfoUuids = mutableSetOf<UUID>()

        draftsActionsWorkerScheduler.getCompletedWorkInfoLiveData().observe(this) {
            for (workInfo in it) {
                if (!treatedWorkInfoUuids.add(workInfo.id)) continue

                workInfo.outputData
                    .getIntArray(DraftsActionsWorker.ERROR_MESSAGE_RESID_KEY)
                    ?.forEach(this::showToast)

                val remoteDraftUuid = workInfo.outputData.getString(DraftsActionsWorker.SAVED_DRAFT_UUID_KEY)
                remoteDraftUuid?.let(::showSavedDraftSnackBar)
            }
        }
    }

    private fun showSavedDraftSnackBar(remoteDraftUuid: String) {
        mainViewModel.snackBarManager.postValue(getString(R.string.snackbarDraftSaved), null, R.string.actionDelete) {
            mainViewModel.deleteDraft(remoteDraftUuid)
        }
    }

    private fun loadCurrentMailbox() {
        mainViewModel.loadCurrentMailbox().observe(this) {
            lifecycleScope.launch {
                repeatOnLifecycle(State.STARTED) {
                    mainViewModel.forceRefreshMailboxesAndFolders()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        localSettings.appLaunches++
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

        fun closeMultiSelect() {
            mainViewModel.isMultiSelectOn = false
        }

        fun popBack() {
            if (navController.currentDestination?.id == R.id.threadListFragment) {
                finish()
            } else {
                navController.popBackStack()
            }
        }

        onBackPressedDispatcher.addCallback(this@MainActivity) {
            when {
                drawerLayout.isOpen -> closeDrawer()
                mainViewModel.isMultiSelectOn -> closeMultiSelect()
                else -> popBack()
            }
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
        navController.addOnDestinationChangedListener { _, destination, arguments ->
            onDestinationChanged(destination, arguments)
        }
    }

    private fun setupMenuDrawerCallbacks() = with(binding) {
        (menuDrawerFragment.getFragment() as? MenuDrawerFragment)?.exitDrawer = { drawerLayout.close() }
    }

    private fun registerMainPermissions(permissionUtils: PermissionUtils) {
        permissionUtils.registerMainPermissions { permissionsResults ->
            if (permissionsResults[Manifest.permission.READ_CONTACTS] == true) mainViewModel.updateUserInfo()
        }
    }

    // This `SuppressLint` seems useless, but it's for the CI. Don't remove it.
    @SuppressLint("RestrictedApi")
    private fun onDestinationChanged(destination: NavDestination, arguments: Bundle?) {

        SentryDebug.addNavigationBreadcrumb(destination.displayName, arguments)

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

        val colorRes = when (destination.id) {
            R.id.threadFragment -> R.color.elevatedBackground
            R.id.messageActionBottomSheetDialog,
            R.id.replyBottomSheetDialog,
            R.id.detailedContactBottomSheetDialog,
            R.id.threadActionsBottomSheetDialog -> R.color.backgroundColorSecondary
            R.id.threadListFragment -> {
                if (mainViewModel.isMultiSelectOn) R.color.elevatedBackground else R.color.backgroundColor
            }
            else -> R.color.backgroundColor
        }

        window.updateNavigationBarColor(getColor(colorRes))

        trackDestination(destination)
    }

    fun setDrawerLockMode(isUnlocked: Boolean) {
        val drawerLockMode = if (isUnlocked) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED
        binding.drawerLayout.setDrawerLockMode(drawerLockMode)
    }

    private fun colorSystemBarsWithMenuDrawer(@FloatRange(0.0, 1.0) slideOffset: Float = FULLY_SLID) = with(window) {
        if (slideOffset == FULLY_SLID) {
            statusBarColor = menuDrawerBackgroundColor
            updateNavigationBarColor(menuDrawerBackgroundColor)
        } else {
            statusBarColor = UiUtils.pointBetweenColors(backgroundHeaderColor, menuDrawerBackgroundColor, slideOffset)
            updateNavigationBarColor(UiUtils.pointBetweenColors(backgroundColor, menuDrawerBackgroundColor, slideOffset))
        }
    }

    private fun handleUpdates() {
        if (!localSettings.updateLater || localSettings.appLaunches % 10 == 0) {
            checkUpdateIsAvailable(BuildConfig.APPLICATION_ID, BuildConfig.VERSION_CODE) { updateIsAvailable ->
                if (updateIsAvailable) navController.navigate(R.id.updateAvailableBottomSheetDialog)
            }
        }
    }

    fun navigateToNewMessageActivity(args: Bundle? = null) {
        val intent = Intent(this, NewMessageActivity::class.java)
        args?.let(intent::putExtras)
        newMessageActivityResultLauncher.launch(intent)
    }

    private companion object {
        const val FULLY_SLID = 1.0f
    }
}
