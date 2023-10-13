/*
 * Infomaniak Mail - Android
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
import android.os.CountDownTimer
import android.view.View
import android.webkit.WebView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.activity.viewModels
import androidx.annotation.FloatRange
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.work.Data
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import com.infomaniak.lib.core.networking.LiveDataNetworkStatus
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.Utils
import com.infomaniak.lib.core.utils.Utils.toEnumOrThrow
import com.infomaniak.lib.stores.checkUpdateIsAvailable
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail.trackDestination
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.draft.Draft.*
import com.infomaniak.mail.databinding.ActivityMainBinding
import com.infomaniak.mail.firebase.RegisterFirebaseBroadcastReceiver
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.main.menu.MenuDrawerFragment
import com.infomaniak.mail.ui.newMessage.NewMessageActivity
import com.infomaniak.mail.ui.sync.SyncAutoConfigActivity
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.workers.DraftsActionsWorker
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    // This binding is not private because it's used in ThreadListFragment (`(activity as? MainActivity)?.binding`)
    val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val mainViewModel: MainViewModel by viewModels()

    private val permissionUtils by lazy { PermissionUtils(this).also(::registerMainPermissions) }

    private val backgroundColor: Int by lazy { getColor(R.color.backgroundColor) }
    private val backgroundHeaderColor: Int by lazy { getColor(R.color.backgroundHeaderColor) }
    private val menuDrawerBackgroundColor: Int by lazy { getColor(R.color.menuDrawerBackgroundColor) }
    private val registerFirebaseBroadcastReceiver by lazy { RegisterFirebaseBroadcastReceiver() }

    private val navController by lazy {
        (supportFragmentManager.findFragmentById(R.id.mainHostFragment) as NavHostFragment).navController
    }

    private val showSendingSnackBarTimer: CountDownTimer by lazy {
        Utils.createRefreshTimer(1_000L) { mainViewModel.snackBarManager.setValue(getString(R.string.snackbarEmailSending)) }
    }

    private val newMessageActivityResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        val draftAction = result.data?.getStringExtra(DRAFT_ACTION_KEY)?.let(DraftAction::valueOf)
        if (draftAction == DraftAction.SEND) showSendingSnackBarTimer.start()
    }

    private val syncAutoConfigActivityResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        result.data?.getStringExtra(SYNC_AUTO_CONFIG_KEY)?.let { reason ->
            if (reason == SYNC_AUTO_CONFIG_ALREADY_SYNC) {
                mainViewModel.snackBarManager.setValue(getString(R.string.errorUserAlreadySynchronized))
            }
            if (navController.currentDestination?.id != R.id.threadListFragment) {
                navController.popBackStack()
            }
        }
    }

    @Inject
    lateinit var draftsActionsWorkerScheduler: DraftsActionsWorker.Scheduler

    @Inject
    lateinit var playServicesUtils: PlayServicesUtils

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    private val drawerListener = object : DrawerLayout.DrawerListener {

        var hasDragged = false

        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            colorSystemBarsWithMenuDrawer(slideOffset)
        }

        override fun onDrawerOpened(drawerView: View) {
            if (hasDragged) trackMenuDrawerEvent("openByGesture", TrackerAction.DRAG)
            colorSystemBarsWithMenuDrawer()
            (binding.menuDrawerFragmentContainer.getFragment() as? MenuDrawerFragment)?.onDrawerOpened()
        }

        override fun onDrawerClosed(drawerView: View) {
            if (hasDragged) trackMenuDrawerEvent("closeByGesture", TrackerAction.DRAG)
            (binding.menuDrawerFragmentContainer.getFragment() as? MenuDrawerFragment)?.closeDropdowns()
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
        observeDeletedMessages()
        observeDeleteThreadTrigger()
        observeDraftWorkerResults()
        binding.drawerLayout.addDrawerListener(drawerListener)
        registerFirebaseBroadcastReceiver.initFirebaseBroadcastReceiver(this, mainViewModel)

        setupSnackBar()
        setupNavController()
        setupMenuDrawerCallbacks()

        handleUpdates()

        mainViewModel.updateUserInfo()
        mainViewModel.updateFeatureFlag()

        loadCurrentMailbox()

        permissionUtils.requestMainPermissionsIfNeeded()
    }

    private fun observeNetworkStatus() {
        LiveDataNetworkStatus(context = this).distinctUntilChanged().observe(this) { isAvailable ->
            SentryLog.d("Internet availability", if (isAvailable) "Available" else "Unavailable")
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "Network"
                message = "Internet access is available : $isAvailable"
                level = if (isAvailable) SentryLevel.INFO else SentryLevel.WARNING
            })
            mainViewModel.isInternetAvailable.value = isAvailable
        }
    }

    private fun observeDeletedMessages() = with(mainViewModel) {
        deletedMessages.observe(owner = this@MainActivity) {
            if (it.isNotEmpty()) {
                handleDeletedMessages(it)
                deletedMessages.value = emptySet()
            }
        }
    }

    private fun observeDeleteThreadTrigger() {
        mainViewModel.deleteThreadOrMessageTrigger.observe(this) { descriptionDialog.resetLoadingAndDismiss() }
    }

    private fun observeDraftWorkerResults() {
        WorkerUtils.flushWorkersBefore(context = this, lifecycleOwner = this) {
            val treatedWorkInfoUuids = mutableSetOf<UUID>()

            draftsActionsWorkerScheduler.getCompletedWorkInfoLiveData().observe(this) {
                it.forEach { workInfo ->
                    if (!treatedWorkInfoUuids.add(workInfo.id)) return@forEach

                    with(workInfo.outputData) {
                        refreshDraftFolderIfNeeded()
                        displayCompletedDraftWorkerResults()
                    }
                }
            }

            val treatedFailedWorkInfoUuids = mutableSetOf<UUID>()

            draftsActionsWorkerScheduler.getFailedWorkInfoLiveData().observe(this) {
                it.forEach { workInfo ->
                    if (!treatedFailedWorkInfoUuids.add(workInfo.id)) return@forEach

                    with(workInfo.outputData) {
                        refreshDraftFolderIfNeeded()
                        val errorRes = getInt(DraftsActionsWorker.ERROR_MESSAGE_RESID_KEY, 0)
                        if (errorRes > 0) mainViewModel.snackBarManager.setValue(getString(errorRes))
                    }
                }
            }
        }
    }

    private fun Data.displayCompletedDraftWorkerResults() {
        getString(DraftsActionsWorker.RESULT_DRAFT_ACTION_KEY)?.let { draftAction ->
            when (draftAction.toEnumOrThrow<DraftAction>()) {
                DraftAction.SAVE -> {
                    val associatedMailboxUuid = getString(DraftsActionsWorker.ASSOCIATED_MAILBOX_UUID_KEY)
                    val remoteDraftUuid = getString(DraftsActionsWorker.REMOTE_DRAFT_UUID_KEY)
                    if (associatedMailboxUuid != null && remoteDraftUuid != null) {
                        showSavedDraftSnackBar(associatedMailboxUuid, remoteDraftUuid)
                    }
                }
                DraftAction.SEND -> showSentDraftSnackBar()
            }
        }
    }

    private fun Data.refreshDraftFolderIfNeeded() {
        val userId = getInt(DraftsActionsWorker.RESULT_USER_ID_KEY, 0)
        if (userId != AccountUtils.currentUserId) return

        getLong(DraftsActionsWorker.BIGGEST_SCHEDULED_DATE_KEY, 0).takeIf { it > 0 }?.let { scheduledDate ->
            mainViewModel.refreshDraftFolderWhenDraftArrives(scheduledDate)
        }
    }

    private fun showSavedDraftSnackBar(associatedMailboxUuid: String, remoteDraftUuid: String) {
        mainViewModel.snackBarManager.setValue(
            title = getString(R.string.snackbarDraftSaved),
            buttonTitle = R.string.actionDelete,
            customBehavior = {
                trackEvent("snackbar", "deleteDraft")
                mainViewModel.deleteDraft(associatedMailboxUuid, remoteDraftUuid)
            },
        )
    }

    // Still display the snackbar even if it took three times 10 seconds of timeout to succeed
    private fun showSentDraftSnackBar() {
        showSendingSnackBarTimer.cancel()
        mainViewModel.snackBarManager.setValue(getString(R.string.snackbarEmailSent))
    }

    private fun loadCurrentMailbox() {
        mainViewModel.loadCurrentMailboxFromLocal().observe(this) {

            lifecycleScope.launch {
                repeatOnLifecycle(State.STARTED) {
                    mainViewModel.refreshEverything()
                }
            }

            scheduleDraftActionsWorkWithDelay()
        }
    }

    /**
     * We want to scheduleWork after a delay in the off chance where we came back from NewMessageActivity while an Activity
     * recreation got triggered.
     *
     * We need to give time to the NewMessageActivity to save the last state of the draft in realm and then scheduleWork on its
     * own. Not waiting would scheduleWork before NewMessageActivity has time to write to realm and schedule its own worker. This
     * would result in an attempt to save any temporary draft saved to realm because of saveDraftDebouncing() effectively sending
     * a second unwanted draft.
     */
    private fun scheduleDraftActionsWorkWithDelay() = lifecycleScope.launch(Dispatchers.IO) {
        delay(1_000L)
        draftsActionsWorkerScheduler.scheduleWork()
    }

    override fun onStart() {
        super.onStart()
        localSettings.appLaunches++
    }

    override fun onResume() {
        super.onResume()
        playServicesUtils.checkPlayServices(this)

        if (binding.drawerLayout.isOpen) colorSystemBarsWithMenuDrawer()
    }

    private fun handleOnBackPressed() = with(binding) {

        fun closeDrawer() {
            (menuDrawerFragmentContainer.getFragment() as? MenuDrawerFragment)?.closeDrawer()
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

    override fun onStop() {
        descriptionDialog.resetLoadingAndDismiss()
        super.onStop()
    }

    override fun onDestroy() {
        binding.drawerLayout.removeDrawerListener(drawerListener)
        super.onDestroy()
    }

    private fun setupSnackBar() {
        fun getAnchor(): View? = when (navController.currentDestination?.id) {
            R.id.threadListFragment -> findViewById(R.id.newMessageFab)
            R.id.threadFragment -> findViewById(R.id.quickActionBar)
            else -> null
        }

        mainViewModel.snackBarManager.setup(
            view = binding.root,
            activity = this,
            getAnchor = ::getAnchor,
            onUndoData = {
                trackEvent("snackbar", "undo")
                mainViewModel.undoAction(it)
            },
        )
    }

    private fun setupNavController() {
        navController.addOnDestinationChangedListener { _, destination, arguments ->
            onDestinationChanged(destination, arguments)
        }
    }

    private fun setupMenuDrawerCallbacks() = with(binding) {
        (menuDrawerFragmentContainer.getFragment() as? MenuDrawerFragment)?.exitDrawer = { drawerLayout.close() }
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
            R.id.messageActionsBottomSheetDialog,
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
            R.id.messageActionsBottomSheetDialog,
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

    fun navigateToSyncAutoConfigActivity() {
        syncAutoConfigActivityResultLauncher.launch(Intent(this, SyncAutoConfigActivity::class.java))
    }

    companion object {
        private const val FULLY_SLID = 1.0f
        const val DRAFT_ACTION_KEY = "draftAction"
        const val SYNC_AUTO_CONFIG_KEY = "syncAutoConfigKey"
        const val SYNC_AUTO_CONFIG_SUCCESS = "syncAutoConfigSuccess"
        const val SYNC_AUTO_CONFIG_ALREADY_SYNC = "syncAutoConfigAlreadySync"
    }
}
