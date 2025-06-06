/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.annotation.FloatRange
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.work.Data
import com.airbnb.lottie.LottieAnimationView
import com.infomaniak.core.utils.FORMAT_ISO_8601_WITH_TIMEZONE_SEPARATOR
import com.infomaniak.core.utils.year
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.Utils
import com.infomaniak.lib.core.utils.Utils.toEnumOrThrow
import com.infomaniak.lib.core.utils.hasPermissions
import com.infomaniak.lib.stores.StoreUtils
import com.infomaniak.lib.stores.StoreUtils.checkUpdateIsRequired
import com.infomaniak.lib.stores.reviewmanagers.InAppReviewManager
import com.infomaniak.lib.stores.updatemanagers.InAppUpdateManager
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail
import com.infomaniak.mail.MatomoMail.trackDestination
import com.infomaniak.mail.MatomoMail.trackEasterEggEvent
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.MatomoMail.trackInAppReviewEvent
import com.infomaniak.mail.MatomoMail.trackInAppUpdateEvent
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.databinding.ActivityMainBinding
import com.infomaniak.mail.firebase.RegisterFirebaseBroadcastReceiver
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.folder.TwoPaneFragment
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerFragment
import com.infomaniak.mail.ui.main.onboarding.PermissionsOnboardingPagerFragment
import com.infomaniak.mail.ui.main.search.SearchFragmentArgs
import com.infomaniak.mail.ui.newMessage.NewMessageActivity
import com.infomaniak.mail.ui.sync.SyncAutoConfigActivity
import com.infomaniak.mail.ui.sync.discovery.SyncDiscoveryManager
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.NotificationUtils.Companion.GENERIC_NEW_MAILS_NOTIFICATION_ID
import com.infomaniak.mail.utils.UiUtils.progressivelyColorSystemBars
import com.infomaniak.mail.utils.Utils.Shortcuts
import com.infomaniak.mail.utils.Utils.openShortcutHelp
import com.infomaniak.mail.utils.date.MailDateFormatUtils.formatDayOfWeekAdaptiveYear
import com.infomaniak.mail.utils.extensions.applyWindowInsetsListener
import com.infomaniak.mail.utils.extensions.isUserAlreadySynchronized
import com.infomaniak.mail.utils.extensions.safeArea
import com.infomaniak.mail.workers.DraftsActionsWorker
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import com.infomaniak.lib.core.R as RCore

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val mainViewModel: MainViewModel by viewModels()

    private val backgroundColor: Int by lazy { getColor(R.color.backgroundColor) }
    private val backgroundHeaderColor: Int by lazy { getColor(R.color.backgroundHeaderColor) }
    private val menuDrawerBackgroundColor: Int by lazy { getColor(R.color.menuDrawerBackgroundColor) }
    private val registerFirebaseBroadcastReceiver by lazy { RegisterFirebaseBroadcastReceiver() }
    private val navigationArgs: MainActivityArgs? by lazy { intent?.extras?.let(MainActivityArgs::fromBundle) }

    private var previousDestinationId: Int? = null

    private val navController by lazy {
        (supportFragmentManager.findFragmentById(R.id.mainHostFragment) as NavHostFragment).navController
    }

    private var draftAction: DraftAction? = null

    private val showSendingSnackbarTimer: CountDownTimer by lazy {
        Utils.createRefreshTimer(milliseconds = 1_000L) {
            val resId = if (draftAction == DraftAction.SCHEDULE) R.string.snackbarScheduling else R.string.snackbarEmailSending
            snackbarManager.setValue(getString(resId))
        }
    }

    private val newMessageActivityResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        draftAction = result.data?.getStringExtra(DRAFT_ACTION_KEY)?.let(DraftAction::valueOf)

        if (draftAction == DraftAction.SEND) showEasterXMas()
        if (draftAction == DraftAction.SEND || draftAction == DraftAction.SCHEDULE) showSendingSnackbarTimer.start()
    }

    private val syncAutoConfigActivityResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        result.data?.getStringExtra(SYNC_AUTO_CONFIG_KEY)?.let { reason ->
            if (reason == SYNC_AUTO_CONFIG_ALREADY_SYNC) {
                snackbarManager.setValue(getString(R.string.errorUserAlreadySynchronized))
            }
            navController.popBackStack(destinationId = R.id.threadListFragment, inclusive = false)
        }
    }

    private val currentFragment get() = getCurrentFragment(R.id.mainHostFragment)

    @Inject
    lateinit var draftsActionsWorkerScheduler: DraftsActionsWorker.Scheduler

    @Inject
    lateinit var playServicesUtils: PlayServicesUtils

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    @Inject
    lateinit var permissionUtils: PermissionUtils

    @Inject
    lateinit var snackbarManager: SnackbarManager

    @Inject
    lateinit var inAppUpdateManager: InAppUpdateManager

    @Inject
    lateinit var inAppReviewManager: InAppReviewManager

    @Inject
    lateinit var notificationManagerCompat: NotificationManagerCompat

    @Inject
    lateinit var syncDiscoveryManager: SyncDiscoveryManager

    private val drawerListener = object : DrawerLayout.DrawerListener {

        var hasDragged = false

        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            colorSystemBarsWithMenuDrawer(slideOffset)
        }

        override fun onDrawerOpened(drawerView: View) {
            if (hasDragged) trackMenuDrawerEvent("openByGesture", TrackerAction.DRAG)
            colorSystemBarsWithMenuDrawer(UiUtils.FULLY_SLID)
            binding.menuDrawerFragmentContainer.getFragment<MenuDrawerFragment?>()?.onDrawerOpened()
        }

        override fun onDrawerClosed(drawerView: View) {
            if (hasDragged) trackMenuDrawerEvent("closeByGesture", TrackerAction.DRAG)
            binding.menuDrawerFragmentContainer.getFragment<MenuDrawerFragment?>()?.closeDropdowns()
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

        enableEdgeToEdge()

        setContentView(binding.root)
        handleOnBackPressed()
        handleMenuDrawerEdgeToEdge()
        registerMainPermissions()

        checkUpdateIsRequired(
            BuildConfig.APPLICATION_ID,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            localSettings.accentColor.theme,
        )

        observeDeletedMessages()
        observeActivityDialogLoaderReset()
        observeDraftWorkerResults()

        binding.drawerLayout.addDrawerListener(drawerListener)
        registerFirebaseBroadcastReceiver.initFirebaseBroadcastReceiver(this, mainViewModel)

        setupSnackbar()
        setupNavController()
        setupMenuDrawer()

        mainViewModel.updateUserInfo()

        loadCurrentMailbox()

        managePermissionsRequesting()

        handleShortcuts()

        initAppUpdateManager()
        initAppReviewManager()
        syncDiscoveryManager.init(::showSyncDiscovery)
    }

    private fun handleMenuDrawerEdgeToEdge() {
        binding.applyWindowInsetsListener(shouldConsume = false) { _, insets ->
            val menuDrawerFragment = binding.menuDrawerFragmentContainer.getFragment<MenuDrawerFragment>()
            with(insets.safeArea()) {
                menuDrawerFragment.drawerHeader?.let {
                    it.setContentPadding(
                        /* left = */ left,
                        /* top = */ top,
                        /* right = */ it.contentPaddingRight,
                        /* bottom = */ it.contentPaddingBottom,
                    )
                }

                menuDrawerFragment.drawerContent?.let { it.updatePadding(left = left, top = it.paddingTop, bottom = bottom) }
            }
        }
    }

    private fun setupMenuDrawer() {
        binding.drawerLayout.isFocusable = false // Set here because not working in XML
        setupMenuDrawerCallbacks()
    }

    private fun initAppReviewManager() {
        inAppReviewManager.init(
            onDialogShown = { trackInAppReviewEvent("presentAlert") },
            onUserWantToReview = { trackInAppReviewEvent("like") },
            onUserWantToGiveFeedback = { trackInAppReviewEvent("dislike") },
        )
    }

    private fun observeDeletedMessages() = with(mainViewModel) {
        deletedMessages.observe(owner = this@MainActivity) {
            if (it.isNotEmpty()) {
                handleDeletedMessages(it)
                deletedMessages.value = emptySet()
            }
        }
    }

    private fun observeActivityDialogLoaderReset() {
        mainViewModel.activityDialogLoaderResetTrigger.observe(this) { descriptionDialog.resetLoadingAndDismiss() }
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
                        displayError(errorRes)
                    }
                }
            }
        }
    }

    private fun displayError(errorRes: Int) {
        if (errorRes > 0) {
            showSendingSnackbarTimer.cancel()

            val hasLimitBeenReached = errorRes == ErrorCode.getTranslateResForDrafts(ErrorCode.SEND_LIMIT_EXCEEDED) ||
                    errorRes == ErrorCode.getTranslateResForDrafts(ErrorCode.SEND_DAILY_LIMIT_REACHED)

            if (mainViewModel.currentMailbox.value?.isFreeMailbox == true && hasLimitBeenReached) {
                trackNewMessageEvent("trySendingWithDailyLimitReached")
                snackbarManager.setValue(getString(errorRes), buttonTitle = R.string.buttonUpgrade) {
                    openMyKSuiteUpgradeBottomSheet(navController, "dailyLimitReachedUpgrade")
                }
            } else {
                snackbarManager.setValue(getString(errorRes))
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
                        showSavedDraftSnackbar(associatedMailboxUuid, remoteDraftUuid)
                    }
                }
                DraftAction.SEND -> {
                    showSentDraftSnackbar()
                }
                DraftAction.SCHEDULE -> {
                    val scheduleDate = getString(DraftsActionsWorker.SCHEDULED_DRAFT_DATE_KEY)
                    val unscheduleDraftUrl = getString(DraftsActionsWorker.UNSCHEDULE_DRAFT_URL_KEY)
                    if (scheduleDate != null && unscheduleDraftUrl != null) {
                        val dateFormat = SimpleDateFormat(FORMAT_ISO_8601_WITH_TIMEZONE_SEPARATOR, Locale.getDefault())
                        showScheduledDraftSnackbar(
                            scheduleDate = dateFormat.parse(scheduleDate)!!,
                            unscheduleDraftUrl = unscheduleDraftUrl,
                        )
                    }
                }
            }
        }
    }

    private fun Data.refreshDraftFolderIfNeeded() {
        val userId = getInt(DraftsActionsWorker.RESULT_USER_ID_KEY, 0)
        if (userId != AccountUtils.currentUserId) return

        getLong(DraftsActionsWorker.BIGGEST_SCHEDULED_MESSAGES_ETOP_KEY, 0).takeIf { it > 0 }?.let { scheduledMessageEtop ->
            mainViewModel.refreshDraftFolderWhenDraftArrives(scheduledMessageEtop)
        }
    }

    private fun showSavedDraftSnackbar(associatedMailboxUuid: String, remoteDraftUuid: String) {
        snackbarManager.setValue(
            title = getString(R.string.snackbarDraftSaved),
            buttonTitle = R.string.actionDelete,
            customBehavior = {
                trackEvent("snackbar", "deleteDraft")
                mainViewModel.deleteDraft(associatedMailboxUuid, remoteDraftUuid)
            },
        )
    }

    // Still display the Snackbar even if it took three times 10 seconds of timeout to succeed
    private fun showSentDraftSnackbar() {
        showSendingSnackbarTimer.cancel()
        snackbarManager.setValue(getString(R.string.snackbarEmailSent))
    }

    // Still display the Snackbar even if it took three times 10 seconds of timeout to succeed
    private fun showScheduledDraftSnackbar(scheduleDate: Date, unscheduleDraftUrl: String) {
        showSendingSnackbarTimer.cancel()

        val dateString = formatDayOfWeekAdaptiveYear(scheduleDate)

        snackbarManager.setValue(
            title = String.format(getString(R.string.snackbarScheduleSaved), dateString),
            buttonTitle = RCore.string.buttonCancel,
            customBehavior = { mainViewModel.unscheduleDraft(unscheduleDraftUrl) },
        )
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
     * We need to give time to the NewMessageActivity to save the last state of the draft in Realm and then scheduleWork on its
     * own. Not waiting would scheduleWork before NewMessageActivity has time to write to Realm and schedule its own worker. This
     * would result in an attempt to save any temporary Draft saved to Realm because of saveDraftDebouncing() effectively sending
     * a second unwanted Draft.
     */
    private fun scheduleDraftActionsWorkWithDelay() = lifecycleScope.launch(Dispatchers.IO) {
        delay(1_000L)
        draftsActionsWorkerScheduler.scheduleWork()
    }

    override fun onResume() {
        super.onResume()
        playServicesUtils.checkPlayServices(this)
        notificationManagerCompat.cancel(GENERIC_NEW_MAILS_NOTIFICATION_ID)
        if (binding.drawerLayout.isOpen) colorSystemBarsWithMenuDrawer(UiUtils.FULLY_SLID)
    }

    private fun handleOnBackPressed() = with(binding) {

        fun closeDrawer() {
            menuDrawerFragmentContainer.getFragment<MenuDrawerFragment?>()?.closeDrawer()
        }

        fun closeMultiSelect() {
            mainViewModel.isMultiSelectOn = false
        }

        fun popBack() {
            when (val fragment = currentFragment) {
                is TwoPaneFragment -> fragment.handleOnBackPressed()
                is PermissionsOnboardingPagerFragment -> fragment.leaveOnboarding()
                else -> navController.popBackStack()
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

    private fun setupSnackbar() {

        fun getAnchor(): View? {
            val fragment = currentFragment
            return if (fragment is TwoPaneFragment) fragment.getAnchor() else null
        }

        snackbarManager.setup(
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
        menuDrawerFragmentContainer.getFragment<MenuDrawerFragment?>()?.exitDrawer = { drawerLayout.close() }
    }

    private fun registerMainPermissions() {
        permissionUtils.registerMainPermissions { permissionsResults ->
            if (permissionsResults[PermissionUtils.READ_CONTACTS_PERMISSION] == true) mainViewModel.updateUserInfo()
        }
    }

    // This `SuppressLint` seems useless, but it's for the CI. Don't remove it.
    @SuppressLint("RestrictedApi")
    private fun onDestinationChanged(destination: NavDestination, arguments: Bundle?) {
        SentryDebug.addNavigationBreadcrumb(destination.displayName, arguments)
        trackDestination(destination)
        setDrawerLockMode(isLocked = destination.id != R.id.threadListFragment)
        previousDestinationId = destination.id
    }

    fun setDrawerLockMode(isLocked: Boolean) {
        val drawerLockMode = if (isLocked) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED
        binding.drawerLayout.setDrawerLockMode(drawerLockMode)
    }

    private fun colorSystemBarsWithMenuDrawer(@FloatRange(0.0, 1.0) slideOffset: Float) {
        window.progressivelyColorSystemBars(
            slideOffset = slideOffset,
            statusBarColorFrom = backgroundHeaderColor,
            statusBarColorTo = menuDrawerBackgroundColor,
            navBarColorFrom = backgroundColor,
            navBarColorTo = menuDrawerBackgroundColor,
        )
    }

    private fun managePermissionsRequesting() {
        if (hasPermissions(PermissionUtils.getMainPermissions(mustRequireNotification = true))) return

        if (localSettings.showPermissionsOnboarding) {
            if (currentFragment !is PermissionsOnboardingPagerFragment) {
                navController.navigate(R.id.permissionsOnboardingPagerFragment)
            }
        } else {
            permissionUtils.requestMainPermissionsIfNeeded()
        }
    }

    private fun initAppUpdateManager() {
        inAppUpdateManager.init(
            onUserChoice = { isWantingUpdate ->
                trackInAppUpdateEvent(if (isWantingUpdate) MatomoMail.DISCOVER_NOW else MatomoMail.DISCOVER_LATER)
            },
            onInstallStart = { trackInAppUpdateEvent("installUpdate") },
            onInstallFailure = { snackbarManager.setValue(getString(RCore.string.errorUpdateInstall)) },
            onInAppUpdateUiChange = { isUpdateDownloaded ->
                SentryLog.d(StoreUtils.APP_UPDATE_TAG, "Must display update button : $isUpdateDownloaded")
                mainViewModel.canInstallUpdate.value = isUpdateDownloaded
            },
            onFDroidResult = { updateIsAvailable ->
                if (updateIsAvailable) navController.navigate(R.id.updateAvailableBottomSheetDialog)
            },
        )
    }

    private fun showSyncDiscovery() = with(localSettings) {
        if (!showPermissionsOnboarding && !isUserAlreadySynchronized()) {
            navController.navigate(R.id.syncDiscoveryBottomSheetDialog)
        }
    }

    private fun showEasterXMas() {

        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        if (month == Calendar.DECEMBER && day <= 25) {
            binding.easterEggXMas.apply {
                isVisible = true
                playAnimation()
            }
            Sentry.captureMessage("Easter egg XMas has been triggered! Woohoo!")
            trackEasterEggEvent("xmas${Date().year()}")
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

    fun openDrawerLayout() {
        binding.drawerLayout.open()
    }

    fun getConfettiContainer(): ViewGroup = binding.easterEggConfettiContainer

    fun getHalloweenLayout(): LottieAnimationView = binding.easterEggHalloween

    private fun handleShortcuts() {
        navigationArgs?.shortcutId?.let { shortcutId ->
            when (shortcutId) {
                Shortcuts.SEARCH.id -> {
                    navController.navigate(
                        R.id.searchFragment,
                        SearchFragmentArgs(dummyFolderId = mainViewModel.currentFolderId ?: Folder.DUMMY_FOLDER_ID).toBundle(),
                    )
                }
                Shortcuts.NEW_MESSAGE.id -> navController.navigate(R.id.newMessageActivity)
                Shortcuts.SUPPORT.id -> {
                    openShortcutHelp(context = this)
                }
            }
        }
    }

    companion object {
        const val DRAFT_ACTION_KEY = "draftAction"
        const val SYNC_AUTO_CONFIG_KEY = "syncAutoConfigKey"
        const val SYNC_AUTO_CONFIG_SUCCESS = "syncAutoConfigSuccess"
        const val SYNC_AUTO_CONFIG_ALREADY_SYNC = "syncAutoConfigAlreadySync"
    }
}
