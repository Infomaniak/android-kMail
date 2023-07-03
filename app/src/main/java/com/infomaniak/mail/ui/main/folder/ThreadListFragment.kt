/*
 * Infomaniak ikMail - Android
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
package com.infomaniak.mail.ui.main.folder

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.format.DateUtils
import android.transition.TransitionManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.distinctUntilChanged
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView.ListOrientation.DirectionFlag
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView.ListOrientation.VERTICAL_LIST_WITH_VERTICAL_DRAGGING
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnItemSwipeListener
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnItemSwipeListener.SwipeDirection
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener.ScrollDirection
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener.ScrollState
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.Utils
import com.infomaniak.mail.GplayUtils.areGooglePlayServicesNotAvailable
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.MatomoMail.trackMultiSelectionEvent
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.MatomoMail.trackThreadListEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.Companion.DEFAULT_SWIPE_ACTION_LEFT
import com.infomaniak.mail.data.LocalSettings.Companion.DEFAULT_SWIPE_ACTION_RIGHT
import com.infomaniak.mail.data.LocalSettings.SwipeAction
import com.infomaniak.mail.data.LocalSettings.ThreadDensity.COMPACT
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.SelectedThread
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.databinding.FragmentThreadListBinding
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.login.NoMailboxActivity
import com.infomaniak.mail.ui.login.NoValidMailboxesActivity
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.RealmChangesBinding.Companion.bindResultsChangeToAdapter
import com.infomaniak.mail.utils.UiUtils.formatUnreadCount
import com.infomaniak.mail.workers.DraftsActionsWorker
import dagger.hilt.android.AndroidEntryPoint
import io.realm.kotlin.ext.isValid
import java.util.Date
import javax.inject.Inject
import com.infomaniak.lib.core.R as RCore

@AndroidEntryPoint
class ThreadListFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {

    private lateinit var binding: FragmentThreadListBinding
    private val mainViewModel: MainViewModel by activityViewModels()
    private val threadListViewModel: ThreadListViewModel by viewModels()

    private val threadListMultiSelection by lazy { ThreadListMultiSelection() }

    private val localSettings by lazy { LocalSettings.getInstance(requireContext()) }

    private lateinit var threadListAdapter: ThreadListAdapter
    private var lastUpdatedDate: Date? = null
    private var previousCustomFolderId: String? = null

    @Inject
    lateinit var draftsActionsWorkerScheduler: DraftsActionsWorker.Scheduler
    @Inject
    lateinit var notificationManagerCompat: NotificationManagerCompat

    private val showLoadingTimer: CountDownTimer by lazy {
        Utils.createRefreshTimer { binding.swipeRefreshLayout.isRefreshing = true }
    }

    private var canRefreshThreads = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentThreadListBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeNoMailboxActivityTriggers()
        observeNoValidMailboxesActivityTriggers()
        setupDensityDependentUi()
        setupOnRefresh()
        setupAdapter()
        setupListeners()
        setupUserAvatar()
        setupUnreadCountChip()

        threadListMultiSelection.initMultiSelection(
            binding = binding,
            mainViewModel = mainViewModel,
            threadListFragment = this,
            threadListAdapter = threadListAdapter,
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
        observeContacts()
        observerDraftsActionsCompletedWorks()
    }

    override fun onStart() {
        super.onStart()
        binding.unreadCountChip.apply { isCloseIconVisible = isChecked }
    }

    override fun onResume() {
        super.onResume()
        refreshThreadsIfNotificationsAreDisabled()
        updateSwipeActionsAccordingToSettings()
        canRefreshThreads = true
    }

    private fun refreshThreadsIfNotificationsAreDisabled() = with(mainViewModel) {
        val areGoogleServicesDisabled = requireContext().areGooglePlayServicesNotAvailable()
        val areAppNotifsDisabled = !notificationManagerCompat.areNotificationsEnabled()
        val areMailboxNotifsDisabled = currentMailbox.value?.notificationsIsDisabled(notificationManagerCompat) == true
        val shouldRefreshThreads = areGoogleServicesDisabled || areAppNotifsDisabled || areMailboxNotifsDisabled

        if (shouldRefreshThreads && canRefreshThreads) forceRefreshThreads()
    }

    private fun updateSwipeActionsAccordingToSettings() = with(binding.threadsList) {
        behindSwipedItemBackgroundColor = localSettings.swipeLeft.getBackgroundColor(requireContext())
        behindSwipedItemBackgroundSecondaryColor = localSettings.swipeRight.getBackgroundColor(requireContext())

        behindSwipedItemIconDrawableId = localSettings.swipeLeft.iconRes
        behindSwipedItemIconSecondaryDrawableId = localSettings.swipeRight.iconRes

        unlockSwipeActionsIfSet()
    }

    private fun unlockSwipeActionsIfSet() = with(binding.threadsList) {
        val leftIsSet = localSettings.swipeLeft != SwipeAction.NONE
        if (leftIsSet) enableSwipeDirection(DirectionFlag.LEFT) else disableSwipeDirection(DirectionFlag.LEFT)

        val rightIsSet = localSettings.swipeRight != SwipeAction.NONE
        if (rightIsSet) enableSwipeDirection(DirectionFlag.RIGHT) else disableSwipeDirection(DirectionFlag.RIGHT)
    }

    override fun onRefresh() {
        if (mainViewModel.isDownloadingChanges.value?.first == true) return
        mainViewModel.forceRefreshThreads()
    }

    private fun observeNoMailboxActivityTriggers() {
        mainViewModel.shouldStartNoMailboxActivity.observe(viewLifecycleOwner) {
            startActivity(Intent(requireContext(), NoMailboxActivity::class.java).clearStack())
        }
    }

    private fun observeNoValidMailboxesActivityTriggers() {
        mainViewModel.shouldStartNoValidMailboxesActivity.observe(viewLifecycleOwner) {
            startActivity(Intent(requireContext(), NoValidMailboxesActivity::class.java).clearStack())
        }
    }

    private fun setupDensityDependentUi() = with(binding) {
        val paddingTop = resources.getDimension(RCore.dimen.marginStandardMedium).toInt()
        threadsList.setPaddingRelative(top = if (localSettings.threadDensity == COMPACT) paddingTop else 0)
    }

    private fun setupOnRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener(this)
    }

    private fun setupAdapter() = with(threadListViewModel) {

        threadListAdapter = ThreadListAdapter(
            context = requireContext(),
            threadDensity = localSettings.threadDensity,
            folderRole = mainViewModel.currentFolder.value?.role,
            contacts = mainViewModel.mergedContacts.value ?: emptyMap(),
            onSwipeFinished = { isRecoveringFinished.value = true },
            multiSelection = object : MultiSelectionListener<SelectedThread> {
                override var isEnabled by mainViewModel::isMultiSelectOn
                override val selectedItems by mainViewModel::selectedThreads
                override val publishSelectedItems = mainViewModel::publishSelectedItems
            }
        )

        binding.threadsList.apply {
            adapter = threadListAdapter
            layoutManager = LinearLayoutManager(context)
            orientation = VERTICAL_LIST_WITH_VERTICAL_DRAGGING
            disableDragDirection(DirectionFlag.UP)
            disableDragDirection(DirectionFlag.DOWN)
            disableDragDirection(DirectionFlag.RIGHT)
            disableDragDirection(DirectionFlag.LEFT)
            addStickyDateDecoration(threadListAdapter, localSettings.threadDensity)
        }

        threadListAdapter.apply {

            stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY

            onThreadClicked = { thread -> navigateToThread(thread, mainViewModel) }

            onFlushClicked = { dialogTitle ->

                val trackerName = when {
                    mainViewModel.isCurrentFolderRole(FolderRole.TRASH) -> "emptyTrash"
                    mainViewModel.isCurrentFolderRole(FolderRole.DRAFT) -> "emptyDraft"
                    mainViewModel.isCurrentFolderRole(FolderRole.SPAM) -> "emptySpam"
                    else -> null
                }

                trackerName?.let { trackThreadListEvent(it) }

                createDescriptionDialog(
                    title = dialogTitle,
                    description = getString(R.string.threadListEmptyFolderAlertDescription),
                    onPositiveButtonClicked = {
                        trackThreadListEvent("${trackerName}Confirm")
                        mainViewModel.flushFolder()
                    },
                ).show()
            }

            onLoadMoreClicked = { mainViewModel.getOnePageOfOldMessages() }
        }
    }

    private fun setupListeners() = with(binding) {

        toolbar.setNavigationOnClickListener {
            trackMenuDrawerEvent("openByButton")
            (activity as? MainActivity)?.binding?.drawerLayout?.open()
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
                ThreadListFragmentDirections.actionThreadListFragmentToSearchFragment(
                    dummyFolderId = mainViewModel.currentFolderId!!,
                )
            )
        }

        userAvatar.setOnClickListener {
            safeNavigate(ThreadListFragmentDirections.actionThreadListFragmentToAccountFragment())
        }

        newMessageFab.setOnClickListener {
            trackNewMessageEvent("openFromFab")
            safeNavigate(ThreadListFragmentDirections.actionThreadListFragmentToNewMessageActivity())
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
                    else -> throw IllegalStateException("Only SwipeDirection.LEFT_TO_RIGHT and SwipeDirection.RIGHT_TO_LEFT can be triggered")
                }

                val singleMessage = if (item.isOnlyOneMessage) item.messages.single() else null
                val shouldKeepItem = performSwipeActionOnThread(swipeAction, item.uid, singleMessage)

                threadListAdapter.apply {
                    blockOtherSwipes()
                    notifyItemChanged(position) // Animate the swiped element back to its original position
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
        threadUid: String,
        singleMessage: Message?,
    ): Boolean = with(mainViewModel) {

        trackEvent("swipeActions", swipeAction.matomoValue, TrackerAction.DRAG)

        val shouldKeepItem = when (swipeAction) {
            SwipeAction.TUTORIAL -> {
                setDefaultSwipeActions()
                safeNavigate(ThreadListFragmentDirections.actionThreadListFragmentToSettingsFragment())
                findNavController().navigate(R.id.swipeActionsSettingsFragment, null, getAnimatedNavOptions())
                true
            }
            SwipeAction.ARCHIVE -> {
                archiveThread(threadUid)
                isCurrentFolderRole(FolderRole.ARCHIVE)
            }
            SwipeAction.DELETE -> {
                deleteThread(threadUid)
                false
            }
            SwipeAction.FAVORITE -> {
                toggleThreadFavoriteStatus(threadUid)
                true
            }
            SwipeAction.MOVE -> {
                animatedNavigation(ThreadListFragmentDirections.actionThreadListFragmentToMoveFragment(arrayOf(threadUid)))
                false
            }
            SwipeAction.QUICKACTIONS_MENU -> {
                singleMessage?.let { message ->
                    safeNavigate(
                        ThreadListFragmentDirections.actionThreadListFragmentToMessageActionsBottomSheetDialog(
                            messageUid = message.uid,
                            threadUid = threadUid,
                            isFavorite = message.isFavorite,
                            isSeen = message.isSeen,
                            isThemeTheSame = true,
                            shouldLoadDistantResources = false,
                        )
                    )
                } ?: safeNavigate(
                    ThreadListFragmentDirections.actionThreadListFragmentToThreadActionsBottomSheetDialog(
                        threadUid = threadUid,
                        shouldLoadDistantResources = false,
                    )
                )
                true
            }
            SwipeAction.READ_UNREAD -> {
                toggleThreadSeenStatus(threadUid)
                currentFilter.value != ThreadFilter.UNSEEN
            }
            SwipeAction.SPAM -> {
                toggleThreadSpamStatus(threadUid)
                false
            }
            SwipeAction.POSTPONE -> {
                notYetImplemented()
                true
            }
            SwipeAction.NONE -> throw IllegalStateException("Cannot swipe on an action which is not set")
        }

        return shouldKeepItem
    }

    private fun setDefaultSwipeActions() = with(localSettings) {
        if (swipeRight == SwipeAction.TUTORIAL) swipeRight = DEFAULT_SWIPE_ACTION_RIGHT
        if (swipeLeft == SwipeAction.TUTORIAL) swipeLeft = DEFAULT_SWIPE_ACTION_LEFT
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
                threadListAdapter.updateLoadMore(shouldDisplayLoadMore = !isChecked)
            }
        }
    }

    private fun observeNetworkStatus() {
        mainViewModel.isInternetAvailable.observe(viewLifecycleOwner) { isAvailable ->
            TransitionManager.beginDelayedTransition(binding.root)
            binding.noNetwork.isGone = isAvailable
            if (!isAvailable) updateThreadsVisibility()
        }
    }

    private fun observeCurrentThreads() = with(threadListViewModel) {
        mainViewModel.currentThreadsLive.bindResultsChangeToAdapter(viewLifecycleOwner, threadListAdapter).apply {
            recyclerView = binding.threadsList
            beforeUpdateAdapter = { threads ->
                currentThreadsCount = threads.count()
                Log.i("UI", "Received threads: $currentThreadsCount | (${mainViewModel.currentFolder.value?.name})")
                updateThreadsVisibility()
            }
            waitingBeforeNotifyAdapter = isRecoveringFinished
            afterUpdateAdapter = { threads ->
                if (hasSwitchedToAnotherFolder()) scrollToTop()

                if (mainViewModel.currentFilter.value == ThreadFilter.UNSEEN && threads.isEmpty()) {
                    mainViewModel.currentFilter.value = ThreadFilter.ALL
                }
            }
        }
    }

    private fun observeDownloadState() {
        mainViewModel.isDownloadingChanges
            .distinctUntilChanged()
            .observe(viewLifecycleOwner) { (isDownloading, shouldDisplayLoadMore) ->
                if (isDownloading) {
                    showLoadingTimer.start()
                } else {
                    showLoadingTimer.cancel()
                    binding.swipeRefreshLayout.isRefreshing = false
                    if (mainViewModel.currentFilter.value == ThreadFilter.ALL) {
                        shouldDisplayLoadMore?.let(threadListAdapter::updateLoadMore)
                    }
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

            threadListAdapter.apply {
                updateFolderRole(folder.role)
                updateLoadMore(shouldDisplayLoadMore = false)
            }

            binding.newMessageFab.extend()
        }
    }

    private fun observeCurrentFolderLive() = with(threadListViewModel) {
        mainViewModel.currentFolderLive.observe(viewLifecycleOwner) { folder ->
            currentFolderCursor = folder.cursor
            Log.i("UI", "Received cursor: $currentFolderCursor | (${folder.name})")
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

    private fun observeContacts() {
        mainViewModel.mergedContacts.observeNotNull(viewLifecycleOwner, threadListAdapter::updateContacts)
    }

    private fun observerDraftsActionsCompletedWorks() {

        fun observeDraftsActions() {
            draftsActionsWorkerScheduler.getCompletedWorkInfoLiveData().observe(viewLifecycleOwner) {
                mainViewModel.currentFolder.value?.let { folder ->
                    if (folder.isValid() && folder.role == FolderRole.DRAFT) mainViewModel.forceRefreshThreads()
                }
            }
        }

        WorkerUtils.flushWorkersBefore(requireContext(), viewLifecycleOwner, ::observeDraftsActions)
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
        Log.i("UI", "Received folder name: $folderName")
        binding.toolbar.title = folderName
    }

    private fun updateThreadsVisibility() = with(mainViewModel) {

        val thereAreThreads = (threadListViewModel.currentThreadsCount ?: 0) > 0
        val filterIsEnabled = currentFilter.value != ThreadFilter.ALL
        val cursorIsNull = threadListViewModel.currentFolderCursor == null
        val isNetworkConnected = isInternetAvailable.value == true
        val isBooting = threadListViewModel.currentThreadsCount == null && !cursorIsNull && isNetworkConnected
        val shouldDisplayThreadsView = isBooting || thereAreThreads || filterIsEnabled || (cursorIsNull && isNetworkConnected)

        when {
            shouldDisplayThreadsView -> binding.emptyStateView.isGone = true
            cursorIsNull -> setEmptyState(EmptyState.NETWORK)
            isCurrentFolderRole(FolderRole.INBOX) -> setEmptyState(EmptyState.INBOX)
            isCurrentFolderRole(FolderRole.TRASH) -> setEmptyState(EmptyState.TRASH)
            else -> setEmptyState(EmptyState.FOLDER)
        }
    }

    private fun setEmptyState(emptyState: EmptyState) {
        binding.emptyStateView.apply {
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

    private fun scrollToTop() = binding.threadsList.layoutManager?.scrollToPosition(0)

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
}
