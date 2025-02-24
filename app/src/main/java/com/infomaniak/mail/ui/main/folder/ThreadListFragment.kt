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
package com.infomaniak.mail.ui.main.folder

import android.os.Bundle
import android.os.CountDownTimer
import android.text.format.DateUtils
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView.ListOrientation
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView.ListOrientation.DirectionFlag
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnItemSwipeListener
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnItemSwipeListener.SwipeDirection
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener.ScrollDirection
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener.ScrollState
import com.infomaniak.core.utils.isToday
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.stores.updatemanagers.InAppUpdateManager
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.MatomoMail.trackMultiSelectionEvent
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.MatomoMail.trackThreadListEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings.SwipeAction
import com.infomaniak.mail.data.LocalSettings.ThreadDensity.COMPACT
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.databinding.FragmentThreadListBinding
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.alertDialogs.TitleAlertDialog
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.folder.ThreadListViewModel.ContentDisplayMode
import com.infomaniak.mail.ui.main.settings.appearance.swipe.SwipeActionsSettingsFragment
import com.infomaniak.mail.ui.main.thread.ThreadFragment
import com.infomaniak.mail.ui.newMessage.NewMessageActivityArgs
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.PlayServicesUtils
import com.infomaniak.mail.utils.RealmChangesBinding.Companion.bindResultsChangeToAdapter
import com.infomaniak.mail.utils.SentryDebug.displayForSentry
import com.infomaniak.mail.utils.UiUtils.formatUnreadCount
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.Utils.isPermanentDeleteFolder
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.extensions.*
import com.infomaniak.mail.views.itemViews.MyKSuiteStorageBanner.StorageLevel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import com.infomaniak.lib.core.R as RCore
import com.infomaniak.lib.core.utils.Utils as UtilsCore

@AndroidEntryPoint
class ThreadListFragment : TwoPaneFragment() {

    private var _binding: FragmentThreadListBinding? = null
    val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView

    private val navigationArgs: ThreadListFragmentArgs by navArgs()
    private val threadListViewModel: ThreadListViewModel by viewModels()

    private val threadListMultiSelection by lazy { ThreadListMultiSelection() }

    private var lastUpdatedDate: Date? = null
    private var previousCustomFolderId: String? = null

    private val showLoadingTimer: CountDownTimer by lazy { UtilsCore.createRefreshTimer(onTimerFinish = ::showRefreshLayout) }

    private var isFirstTimeRefreshingThreads = true

    @Inject
    lateinit var notificationManagerCompat: NotificationManagerCompat

    @Inject
    lateinit var playServicesUtils: PlayServicesUtils

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    @Inject
    lateinit var snackbarManager: SnackbarManager

    @Inject
    lateinit var inAppUpdateManager: InAppUpdateManager

    @Inject
    lateinit var titleDialog: TitleAlertDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentThreadListBinding.inflate(inflater, container, false).also { _binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = runCatchingRealm {
        navigateFromNotificationToNewMessage()
        super.onViewCreated(view, savedInstanceState)
        setSystemBarsColors(
            statusBarColor = R.color.backgroundHeaderColor,
            navigationBarColor = if (mainViewModel.isMultiSelectOn) R.color.elevatedBackground else R.color.backgroundColor,
        )

        threadListViewModel.deleteSearchData()
        bindAlertToViewLifecycle(descriptionDialog)

        setupDensityDependentUi()
        setupOnRefresh()
        setupAdapter()
        setupListeners()
        setupUserAvatar()
        setupUnreadCountChip()
        setupMyKSuiteStorageBanner()

        threadListMultiSelection.initMultiSelection(
            mainViewModel = mainViewModel,
            threadListFragment = this,
            unlockSwipeActionsIfSet = ::unlockSwipeActionsIfSet,
            localSettings = localSettings,
        )

        observeNetworkStatus()
        observeCurrentThreads()
        observeDownloadState()
        observeFilter()
        observeCurrentFolder()
        observeCurrentFolderLive()
        observeUpdatedAtTriggers()
        observeFlushFolderTrigger()
        observeUpdateInstall()
        observeWebViewOutdated()
        observeLoadMoreTriggers()
        observeContentDisplayMode()
        observeShareUrlResult()
    }.getOrDefault(Unit)

    @ColorRes
    override fun getStatusBarColor(): Int = R.color.backgroundHeaderColor

    override fun getLeftPane(): View? = _binding?.threadsConstraintLayout

    override fun getRightPane(): FragmentContainerView? = _binding?.threadHostFragment

    override fun getAnchor(): View? {
        return if (isOnlyRightShown()) {
            _binding?.threadHostFragment?.getFragment<ThreadFragment?>()?.getAnchor()
        } else {
            _binding?.newMessageFab
        }
    }

    override fun doAfterFolderChanged() {
        navigateFromNotificationToThread()
    }

    private fun navigateFromNotificationToThread() {
        arguments?.consumeKeyIfProvided(navigationArgs::openThreadUid.name) { threadUid ->

            // Select Thread in ThreadList
            with(threadListAdapter) {
                getItemPosition(threadUid)
                    ?.let { position -> selectNewThread(position, threadUid) }
                    ?: run { preselectNewThread(threadUid) }
            }

            // If we are coming from a Notification, we need to navigate to ThreadFragment.
            openThreadAndResetItsState(threadUid)
        }
    }

    private fun navigateFromNotificationToNewMessage() {
        arguments?.consumeKeyIfProvided(navigationArgs::replyToMessageUid.name) { replyToMessageUid ->

            // If we clicked on the "Reply" action of a Notification, we need to navigate to NewMessageActivity.
            safeNavigateToNewMessageActivity(
                NewMessageActivityArgs(
                    draftMode = navigationArgs.draftMode,
                    previousMessageUid = replyToMessageUid,
                    notificationId = navigationArgs.notificationId,
                ).toBundle(),
            )
        }
    }

    // We remove the key from the `arguments` to prevent it from triggering again. To do this we need to use
    // `arguments` instead of `navigationArgs` so the data can be mutated.
    private fun Bundle.consumeKeyIfProvided(key: String, block: (String) -> Unit) {
        getString(key)?.let {
            remove(key)
            block(it)
        }
    }

    override fun onStart() {
        super.onStart()
        binding.unreadCountChip.apply { isCloseIconVisible = isChecked }
        threadListViewModel.checkWebViewVersion(localSettings.showWebViewOutdated)
    }

    override fun onResume() {
        super.onResume()
        refreshThreadsIfNotificationsAreDisabled()
        updateSwipeActionsAccordingToSettings()
    }

    private fun refreshThreadsIfNotificationsAreDisabled() = with(mainViewModel) {

        if (!isFirstTimeRefreshingThreads) {
            val areGoogleServicesDisabled = !playServicesUtils.areGooglePlayServicesAvailable()
            val areAppNotifsDisabled = !notificationManagerCompat.areNotificationsEnabled()
            val areMailboxNotifsDisabled = currentMailbox.value?.notificationsIsDisabled(notificationManagerCompat) == true
            val shouldRefreshThreads = areGoogleServicesDisabled || areAppNotifsDisabled || areMailboxNotifsDisabled

            if (shouldRefreshThreads) forceRefreshThreads()
        }

        isFirstTimeRefreshingThreads = false
    }

    private fun updateSwipeActionsAccordingToSettings() = with(binding.threadsList) {
        behindSwipedItemBackgroundColor = localSettings.swipeLeft.getBackgroundColor(requireContext())
        behindSwipedItemBackgroundSecondaryColor = localSettings.swipeRight.getBackgroundColor(requireContext())

        behindSwipedItemIconDrawableId = localSettings.swipeLeft.iconRes
        behindSwipedItemIconSecondaryDrawableId = localSettings.swipeRight.iconRes

        unlockSwipeActionsIfSet()
    }

    override fun onDestroyView() {
        showLoadingTimer.cancel()
        TransitionManager.endTransitions(binding.root)
        super.onDestroyView()
        _binding = null
    }

    private fun unlockSwipeActionsIfSet() = with(binding.threadsList) {
        val leftIsSet = localSettings.swipeLeft != SwipeAction.NONE
        if (leftIsSet) enableSwipeDirection(DirectionFlag.LEFT) else disableSwipeDirection(DirectionFlag.LEFT)

        val rightIsSet = localSettings.swipeRight != SwipeAction.NONE
        if (rightIsSet) enableSwipeDirection(DirectionFlag.RIGHT) else disableSwipeDirection(DirectionFlag.RIGHT)
    }

    private fun setupDensityDependentUi() = with(binding) {
        val paddingTop = resources.getDimension(RCore.dimen.marginStandardMedium).toInt()
        threadsList.setPaddingRelative(top = if (localSettings.threadDensity == COMPACT) paddingTop else 0)
    }

    private fun setupOnRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            if (mainViewModel.isDownloadingChanges.value == true) return@setOnRefreshListener
            mainViewModel.forceRefreshThreads()
        }
    }

    private fun setupAdapter() {
        threadListAdapter(
            folderRole = mainViewModel.currentFolder.value?.role,
            callbacks = object : ThreadListAdapterCallbacks {

                override var onSwipeFinished: (() -> Unit)? = { threadListViewModel.isRecoveringFinished.value = true }

                override var onThreadClicked: (Thread) -> Unit = ::navigateToThread

                override var onFlushClicked: ((dialogTitle: String) -> Unit)? = { dialogTitle ->
                    val trackerName = when {
                        isCurrentFolderRole(FolderRole.TRASH) -> "emptyTrash"
                        isCurrentFolderRole(FolderRole.DRAFT) -> "emptyDraft"
                        isCurrentFolderRole(FolderRole.SPAM) -> "emptySpam"
                        else -> null
                    }

                    trackerName?.let { trackThreadListEvent(it) }

                    descriptionDialog.show(
                        title = dialogTitle,
                        description = getString(R.string.threadListEmptyFolderAlertDescription),
                        onPositiveButtonClicked = {
                            trackThreadListEvent("${trackerName}Confirm")
                            mainViewModel.flushFolder()
                        },
                    )
                }

                override var onLoadMoreClicked: () -> Unit = {
                    trackThreadListEvent("loadMore")
                    mainViewModel.getOnePageOfOldMessages()
                }

                override var onPositionClickedChanged: (Int, Int) -> Unit = ::updateAutoAdvanceNaturalThread

                override var deleteThreadInRealm: (String) -> Unit = { threadUid -> mainViewModel.deleteThreadInRealm(threadUid) }
            },
            multiSelection = object : MultiSelectionListener<Thread> {
                override var isEnabled by mainViewModel::isMultiSelectOn
                override val selectedItems by mainViewModel::selectedThreads
                override val publishSelectedItems = mainViewModel::publishSelectedItems
            },
        )

        threadListAdapter.stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY

        binding.threadsList.apply {
            adapter = threadListAdapter
            layoutManager = LinearLayoutManager(context)
            orientation = ListOrientation.VERTICAL_LIST_WITH_VERTICAL_DRAGGING
            disableDragDirection(DirectionFlag.UP)
            disableDragDirection(DirectionFlag.DOWN)
            disableDragDirection(DirectionFlag.RIGHT)
            disableDragDirection(DirectionFlag.LEFT)
            addStickyDateDecoration(threadListAdapter, localSettings.threadDensity)
        }
    }

    private fun setupListeners() = with(binding) {

        toolbar.setNavigationOnClickListener {
            trackMenuDrawerEvent("openByButton")
            (requireActivity() as MainActivity).openDrawerLayout()
        }

        cancel.setOnClickListener {
            context.trackMultiSelectionEvent("cancel")
            mainViewModel.isMultiSelectOn = false
        }
        selectAll.setOnClickListener {
            mainViewModel.selectOrUnselectAll()
            threadListAdapter.updateSelection()
        }

        searchButton.setOnClickListener {
            safeNavigate(
                // We need a valid Folder ID for the API call to not fail, but the value itself won't be used.
                // So if we don't have any, we use a hardcoded one (corresponding to "INBOX" folder).
                ThreadListFragmentDirections.actionThreadListFragmentToSearchFragment(
                    dummyFolderId = mainViewModel.currentFolderId ?: Folder.DUMMY_FOLDER_ID
                ),
            )
        }

        userAvatar.setOnClickListener { safeNavigate(resId = R.id.accountBottomSheetDialog) }

        newMessageFab.setOnClickListener {
            trackNewMessageEvent("openFromFab")
            safeNavigateToNewMessageActivity()
        }

        threadsList.scrollListener = object : OnListScrollListener {
            override fun onListScrollStateChanged(scrollState: ScrollState) = Unit

            override fun onListScrolled(scrollDirection: ScrollDirection, distance: Int) {
                extendCollapseFab(scrollDirection)
            }
        }

        threadsList.swipeListener = object : OnItemSwipeListener<Thread> {
            override fun onItemSwiped(position: Int, direction: SwipeDirection, item: Thread): Boolean {

                val swipeAction = when (direction) {
                    SwipeDirection.LEFT_TO_RIGHT -> localSettings.swipeRight
                    SwipeDirection.RIGHT_TO_LEFT -> localSettings.swipeLeft
                    else -> error("Only SwipeDirection.LEFT_TO_RIGHT and SwipeDirection.RIGHT_TO_LEFT can be triggered")
                }

                val isPermanentDeleteFolder = isPermanentDeleteFolder(item.folder.role)

                val shouldKeepItem = performSwipeActionOnThread(swipeAction, item, position, isPermanentDeleteFolder)

                threadListAdapter.apply {
                    blockOtherSwipes()

                    if (swipeAction == SwipeAction.DELETE && isPermanentDeleteFolder) {
                        Unit // The swiped Thread stay swiped all the way
                    } else {
                        notifyItemChanged(position) // Animate the swiped Thread back to its original position
                    }
                }

                threadListViewModel.isRecoveringFinished.value = false

                // The return value of this callback is used to determine if the
                // swiped item should be kept or deleted from the adapter's list.
                return shouldKeepItem
            }
        }
    }

    /**
     * The boolean return value is used to know if we should keep the Thread in
     * the RecyclerView (true), or remove it when the swipe is done (false).
     */
    private fun performSwipeActionOnThread(
        swipeAction: SwipeAction,
        thread: Thread,
        position: Int,
        isPermanentDeleteFolder: Boolean,
    ): Boolean = with(mainViewModel) {
        trackEvent("swipeActions", swipeAction.matomoValue, TrackerAction.DRAG)

        val folderRole = thread.folder.role

        val shouldKeepItemBecauseOfAction = when (swipeAction) {
            SwipeAction.TUTORIAL -> {
                setDefaultSwipeActions()
                safeNavigate(ThreadListFragmentDirections.actionThreadListFragmentToSettingsFragment())
                findNavController().navigate(R.id.swipeActionsSettingsFragment, args = null, getAnimatedNavOptions())
                true
            }
            SwipeAction.ARCHIVE -> {
                archiveThread(thread.uid)
                folderRole == FolderRole.ARCHIVE
            }
            SwipeAction.DELETE -> {
                descriptionDialog.deleteWithConfirmationPopup(
                    folderRole = folderRole,
                    count = 1,
                    displayLoader = false,
                    onCancel = {
                        // Notify only if the user cancelled the popup (e.g. the thread is not deleted),
                        // otherwise it will notify the next item in the list and make it slightly blink
                        if (threadListAdapter.dataSet.indexOf(thread) == position) threadListAdapter.notifyItemChanged(position)
                    },
                    callback = {
                        if (isPermanentDeleteFolder) threadListAdapter.removeItem(position)
                        deleteThread(thread.uid, isSwipe = true)
                    },
                )
                isPermanentDeleteFolder
            }
            SwipeAction.FAVORITE -> {
                toggleThreadFavoriteStatus(thread.uid)
                true
            }
            SwipeAction.MOVE -> {
                animatedNavigation(ThreadListFragmentDirections.actionThreadListFragmentToMoveFragment(arrayOf(thread.uid)))
                false
            }
            SwipeAction.QUICKACTIONS_MENU -> {
                safeNavigate(
                    ThreadListFragmentDirections.actionThreadListFragmentToThreadActionsBottomSheetDialog(
                        threadUid = thread.uid,
                        shouldLoadDistantResources = false,
                    )
                )
                true
            }
            SwipeAction.READ_UNREAD -> {
                toggleThreadSeenStatus(thread.uid)
                currentFilter.value != ThreadFilter.UNSEEN
            }
            SwipeAction.SPAM -> {
                toggleThreadSpamStatus(thread.uid)
                false
            }
            SwipeAction.POSTPONE -> {
                notYetImplemented()
                true
            }
            SwipeAction.NONE -> error("Cannot swipe on an action which is not set")
        }

        val shouldKeepItemBecauseOfNoConnection = !hasNetwork
        return shouldKeepItemBecauseOfAction || shouldKeepItemBecauseOfNoConnection
    }

    private fun setDefaultSwipeActions() = with(localSettings) {
        if (swipeRight == SwipeAction.TUTORIAL) swipeRight = SwipeActionsSettingsFragment.DEFAULT_SWIPE_ACTION_RIGHT
        if (swipeLeft == SwipeAction.TUTORIAL) swipeLeft = SwipeActionsSettingsFragment.DEFAULT_SWIPE_ACTION_LEFT
    }

    private fun extendCollapseFab(scrollDirection: ScrollDirection) = with(binding) {
        val layoutManager = threadsList.layoutManager as LinearLayoutManager
        if (layoutManager.findFirstCompletelyVisibleItemPosition() == 0 || scrollDirection == ScrollDirection.UP) {
            newMessageFab.extend()
        } else {
            newMessageFab.shrink()
        }
    }

    private fun setupUserAvatar() {
        AccountUtils.currentUser?.let(binding.userAvatar::loadAvatar)
    }

    private fun setupUnreadCountChip() = with(binding) {
        unreadCountChip.apply {
            setOnClickListener {
                trackThreadListEvent("unreadFilter")
                isCloseIconVisible = isChecked
                mainViewModel.currentFilter.value = if (isChecked) ThreadFilter.UNSEEN else ThreadFilter.ALL
            }
        }
    }

    private fun setupMyKSuiteStorageBanner() = with(binding.myKSuiteStorageBanner) {
        mainViewModel.currentQuotasLive.observe(viewLifecycleOwner) { quotas ->
            val mailboxFullness = quotas?.getProgress() ?: return@observe

            storageLevel = when {
                mailboxFullness >= 100 -> StorageLevel.Full
                mailboxFullness > 85 -> StorageLevel.Warning
                else -> StorageLevel.Normal
            }

            setupListener(onCloseButtonClicked = { isGone = true })
        }
    }

    private fun observeNetworkStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.isNetworkAvailable.collect { isNetworkAvailable ->
                if (_binding == null) return@collect

                if (isNetworkAvailable != null) {
                    TransitionManager.beginDelayedTransition(binding.root)
                    binding.noNetwork.isGone = isNetworkAvailable
                    if (!isNetworkAvailable) updateThreadsVisibility()
                }
            }
        }
    }

    private fun observeCurrentThreads() = with(mainViewModel) {
        reassignCurrentThreadsLive()

        currentThreadsLive.bindResultsChangeToAdapter(viewLifecycleOwner, threadListAdapter).apply {
            recyclerView = binding.threadsList

            beforeUpdateAdapter = { threads ->
                threadListViewModel.currentThreadsCount = threads.count()
                SentryLog.i(
                    "UI",
                    "Received threads: ${threadListViewModel.currentThreadsCount} | (${currentFolder.value?.displayForSentry()})",
                )
                updateThreadsVisibility()
            }

            waitingBeforeNotifyAdapter = threadListViewModel.isRecoveringFinished

            deletedItemsIndices = ::removeMultiSelectItems

            afterUpdateAdapter = { threads ->
                if (currentFilter.value == ThreadFilter.UNSEEN && threads.isEmpty()) currentFilter.value = ThreadFilter.ALL
                if (hasSwitchedToAnotherFolder()) scrollToTop()
            }
        }
    }

    private fun observeDownloadState() {
        mainViewModel.isDownloadingChanges
            .distinctUntilChanged()
            .observe(viewLifecycleOwner) { isDownloading ->
                if (isDownloading) {
                    showLoadingTimer.start()
                } else {
                    showLoadingTimer.cancel()
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
    }

    private fun observeFilter() {
        mainViewModel.currentFilter.observe(viewLifecycleOwner) {
            if (it == ThreadFilter.ALL) {
                with(binding.unreadCountChip) {
                    isChecked = false
                    isCloseIconVisible = false
                }
            }
        }
    }

    private fun observeCurrentFolder() {
        mainViewModel.currentFolder.observeNotNull(viewLifecycleOwner) { folder ->
            lastUpdatedDate = null
            displayFolderName(folder)
            threadListAdapter.updateFolderRole(folder.role)
            binding.newMessageFab.extend()
        }
    }

    private fun observeCurrentFolderLive() = with(threadListViewModel) {
        mainViewModel.currentFolderLive.observe(viewLifecycleOwner) { folder ->
            SentryLog.i("UI", "Received cursor: ${folder.cursor} | (${folder.displayForSentry()})")
            currentFolderCursor = folder.cursor
            threadListViewModel.currentThreadsCount = folder.threads.count()
            updateThreadsVisibility()
            updateUnreadCount(folder.unreadCountLocal)
            checkLastUpdateDay()
            updateUpdatedAt(folder.lastUpdatedAt?.toDate())
            startUpdatedAtJob()
        }
    }

    private fun observeUpdatedAtTriggers() {
        threadListViewModel.updatedAtTrigger.observe(viewLifecycleOwner) { updateUpdatedAt() }
    }

    private fun observeFlushFolderTrigger() {
        mainViewModel.flushFolderTrigger.observe(viewLifecycleOwner) { descriptionDialog.resetLoadingAndDismiss() }
    }

    private fun observeUpdateInstall() = with(binding) {
        mainViewModel.canInstallUpdate.observe(viewLifecycleOwner) { isUpdateDownloaded ->
            installUpdate.isVisible = isUpdateDownloaded
            installUpdate.setOnActionClickListener { inAppUpdateManager.installDownloadedUpdate() }
        }
    }

    private fun observeWebViewOutdated() = with(binding) {
        webviewWarning.setOnActionClickListener {
            titleDialog.show(
                title = getString(R.string.displayMailIssueTitle),
                description = getString(R.string.displayMailIssueDescription),
                displayLoader = false,
                positiveButtonText = RCore.string.buttonUpdate,
                negativeButtonText = RCore.string.buttonLater,
                onPositiveButtonClicked = { requireContext().goToPlayStore(WEBVIEW_PACKAGE_NAME) },
                onNegativeButtonClicked = {
                    webviewWarning.isVisible = false
                    localSettings.showWebViewOutdated = false
                }
            )
        }

        threadListViewModel.isWebViewOutdated.observe(viewLifecycleOwner) { isWebViewOutdated ->
            webviewWarning.isVisible = isWebViewOutdated
        }
    }

    private fun observeLoadMoreTriggers() = with(mainViewModel) {
        Utils.waitInitMediator(currentFilter, currentFolderLive).observe(viewLifecycleOwner) { (filter, folder) ->
            runCatchingRealm {
                val shouldDisplayLoadMore = filter == ThreadFilter.ALL
                        && folder.cursor != null
                        && folder.oldMessagesUidsToFetch.isNotEmpty()
                        && folder.threads.isNotEmpty()
                threadListAdapter.updateLoadMore(shouldDisplayLoadMore)
            }
        }
    }

    private fun observeShareUrlResult() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(State.STARTED) {
                mainViewModel.shareThreadUrlResult.collect { url ->
                    if (url.isNullOrEmpty()) showErrorShareUrl() else requireContext().shareString(url)
                }
            }
        }
    }

    private fun checkLastUpdateDay() {
        if (lastUpdatedDate?.isToday() == false) mainViewModel.forceTriggerCurrentFolder()
    }

    private fun updateUpdatedAt(newLastUpdatedDate: Date? = null) {

        newLastUpdatedDate?.let { lastUpdatedDate = it }
        val lastUpdatedAt = lastUpdatedDate

        val ago = when {
            lastUpdatedAt == null -> null
            Date().time - lastUpdatedAt.time < DateUtils.MINUTE_IN_MILLIS -> getString(R.string.threadListHeaderLastUpdateNow)
            else -> DateUtils.getRelativeTimeSpanString(lastUpdatedAt.time).toString().replaceFirstChar { it.lowercaseChar() }
        }

        binding.updatedAt.text = when (ago) {
            null -> getString(R.string.noUpdatedAt)
            else -> getString(R.string.threadListHeaderLastUpdate, ago)
        }
    }

    private fun updateUnreadCount(unreadCount: Int) {
        binding.unreadCountChip.apply {
            text = resources.getQuantityString(R.plurals.threadListHeaderUnreadCount, unreadCount, formatUnreadCount(unreadCount))
            isGone = unreadCount == 0 || mainViewModel.isMultiSelectOn
        }
    }

    private fun displayFolderName(folder: Folder) {
        val folderName = folder.getLocalizedName(binding.context)
        SentryLog.i("UI", "Received folder: ${folder.displayForSentry()}")
        binding.toolbar.title = folderName
    }

    private fun removeMultiSelectItems(deletedIndices: IntArray) = with(mainViewModel) {
        if (isMultiSelectOn) {
            val previousThreads = threadListAdapter.dataSet.filterIsInstance<Thread>()
            var shouldPublish = false
            deletedIndices.forEach {
                val isRemoved = mainViewModel.selectedThreads.remove(previousThreads[it])
                if (isRemoved) shouldPublish = true
            }
            if (shouldPublish) publishSelectedItems()
        }
    }

    private fun updateThreadsVisibility() = with(threadListViewModel) {

        val isNetworkConnected = mainViewModel.hasNetwork

        // The folder's cursor is null, meaning it's the 1st time we are opening this folder.
        val isCursorNull = currentFolderCursor == null

        // We have a cursor, but don't have any info about threads yet, meaning the app is still booting and loading things.
        val isBooting = !isCursorNull && currentThreadsCount == null

        // We know that there is existing threads in this folder, so if we wait long enough, they'll be there.
        val areThereThreadsSoon = runCatchingRealm {
            val folder = mainViewModel.currentFolderLive.value
            folder?.oldMessagesUidsToFetch?.isNotEmpty() == true
        }.getOrDefault(false)

        // If there is network connectivity, but we either don't have a cursor yet
        // or don't have threads yet (but we know that they are coming), it means
        // we are opening this folder for the 1st time and we know we'll have a result at the end.
        val isWaitingFirstThreads = (isCursorNull || areThereThreadsSoon) && isNetworkConnected

        // There is at least 1 thread available to be displayed right now.
        val areThereThreadsNow = (currentThreadsCount ?: 0) > 0

        // If we filtered on something, it means we have threads, so we want to display the Threads display mode.
        val isFilterEnabled = mainViewModel.currentFilter.value != ThreadFilter.ALL

        // If any of these conditions is true, it means Threads are on their way or the
        // app is still loading things, so either way we want to display the Threads mode.
        val shouldDisplayThreadsView = isBooting || isWaitingFirstThreads || areThereThreadsNow || isFilterEnabled

        contentDisplayMode.value = when {
            shouldDisplayThreadsView -> ContentDisplayMode.Threads
            !isNetworkConnected -> ContentDisplayMode.NoNetwork
            else -> ContentDisplayMode.EmptyFolder
        }
    }

    private fun observeContentDisplayMode() {

        fun folderEmptyState() = when {
            isCurrentFolderRole(FolderRole.INBOX) -> EmptyState.INBOX
            isCurrentFolderRole(FolderRole.TRASH) -> EmptyState.TRASH
            else -> EmptyState.FOLDER
        }

        threadListViewModel.contentDisplayMode
            .observe(viewLifecycleOwner) {
                when (it) {
                    ContentDisplayMode.Threads, null -> displayThreadsView()
                    ContentDisplayMode.NoNetwork -> setEmptyState(EmptyState.NETWORK)
                    ContentDisplayMode.EmptyFolder -> setEmptyState(folderEmptyState())
                }
            }
    }

    private fun displayThreadsView() = with(binding) {
        emptyStateView.isGone = true
        threadsList.isVisible = true
    }

    private fun setEmptyState(emptyState: EmptyState): Unit = with(binding) {
        threadsList.isGone = true
        emptyStateView.apply {
            illustration = getDrawable(context, emptyState.drawableId)
            title = getString(emptyState.titleId)
            description = getString(emptyState.descriptionId)
            isVisible = true
        }
    }

    private fun hasSwitchedToAnotherFolder(): Boolean {
        val newCustomFolderId = "${AccountUtils.currentMailboxId}_${mainViewModel.currentFolderId}"
        return (newCustomFolderId != previousCustomFolderId).also {
            previousCustomFolderId = newCustomFolderId
        }
    }

    private fun scrollToTop() {
        _binding?.threadsList?.layoutManager?.scrollToPosition(0)
    }

    private fun isCurrentFolderRole(role: FolderRole) = mainViewModel.currentFolder.value?.role == role

    private fun showRefreshLayout() {
        binding.swipeRefreshLayout.isRefreshing = true
    }

    private fun showErrorShareUrl() {
        showSnackbar(title = if (mainViewModel.hasNetwork) RCore.string.anErrorHasOccurred else RCore.string.noConnection)
    }

    private enum class EmptyState(
        @DrawableRes val drawableId: Int,
        @StringRes val titleId: Int,
        @StringRes val descriptionId: Int,
    ) {
        NETWORK(R.drawable.ic_empty_state_network, R.string.emptyStateNetworkTitle, R.string.emptyStateNetworkDescription),
        INBOX(R.drawable.ic_empty_state_inbox, R.string.emptyStateInboxTitle, R.string.emptyStateInboxDescription),
        TRASH(R.drawable.ic_empty_state_trash, R.string.emptyStateTrashTitle, R.string.emptyStateTrashDescription),
        FOLDER(R.drawable.ic_empty_state_folder, R.string.emptyStateFolderTitle, R.string.emptyStateFolderDescription),
    }

    companion object {
        private const val WEBVIEW_PACKAGE_NAME = "com.google.android.webview"
    }
}
