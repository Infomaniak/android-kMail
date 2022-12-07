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
package com.infomaniak.mail.ui.main.folder

import android.os.Bundle
import android.os.CountDownTimer
import android.text.format.DateUtils
import android.transition.TransitionManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.Operation
import androidx.work.WorkManager
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView.ListOrientation.DirectionFlag
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView.ListOrientation.VERTICAL_LIST_WITH_VERTICAL_DRAGGING
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnItemSwipeListener
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnItemSwipeListener.SwipeDirection
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener.ScrollDirection
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener.ScrollState
import com.infomaniak.lib.core.utils.Utils
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.Companion.DEFAULT_SWIPE_ACTION_LEFT
import com.infomaniak.mail.data.LocalSettings.Companion.DEFAULT_SWIPE_ACTION_RIGHT
import com.infomaniak.mail.data.LocalSettings.SwipeAction
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.databinding.FragmentThreadListBinding
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.RealmChangesBinding.Companion.bindResultsChangeToAdapter
import com.infomaniak.mail.workers.DraftsActionsWorker
import java.util.*

class ThreadListFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {

    private lateinit var binding: FragmentThreadListBinding
    private val mainViewModel: MainViewModel by activityViewModels()
    private val threadListViewModel: ThreadListViewModel by viewModels()

    private val localSettings by lazy { LocalSettings.getInstance(requireContext()) }

    private lateinit var threadListAdapter: ThreadListAdapter
    private var lastUpdatedDate: Date? = null
    private var previousFirstMessageUid: String? = null

    private val showLoadingTimer: CountDownTimer by lazy {
        Utils.createRefreshTimer { binding.swipeRefreshLayout.isRefreshing = true }
    }

    private var canRefreshThreads = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentThreadListBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupOnRefresh()
        setupAdapter()
        setupListeners()
        setupUserAvatar()
        setupUnreadCountChip()

        observeCurrentThreads()
        observeDownloadState()
        observeCurrentFolder()
        observeCurrentFolderLive()
        observeUpdatedAtTriggers()
        observeContacts()
        observerDraftsActionsCompletedWorks()
    }

    override fun onResume(): Unit = with(binding) {
        super.onResume()

        unreadCountChip.apply { isCloseIconVisible = isChecked } // TODO: Do we need this? If yes, do we need it HERE?

        if (canRefreshThreads) mainViewModel.forceRefreshThreads()
        canRefreshThreads = true

        updateSwipeActionsAccordingToSettings()
    }

    private fun updateSwipeActionsAccordingToSettings() {
        binding.threadsList.apply {
            behindSwipedItemBackgroundColor = localSettings.swipeLeft.getBackgroundColor(requireContext())
            behindSwipedItemBackgroundSecondaryColor = localSettings.swipeRight.getBackgroundColor(requireContext())

            behindSwipedItemIconDrawableId = localSettings.swipeLeft.iconRes
            behindSwipedItemIconSecondaryDrawableId = localSettings.swipeRight.iconRes

            val leftIsSet = localSettings.swipeLeft != SwipeAction.NONE
            if (leftIsSet) enableSwipeDirection(DirectionFlag.LEFT) else disableSwipeDirection(DirectionFlag.LEFT)
            val rightIsSet = localSettings.swipeRight != SwipeAction.NONE
            if (rightIsSet) enableSwipeDirection(DirectionFlag.RIGHT) else disableSwipeDirection(DirectionFlag.RIGHT)
        }
    }

    override fun onRefresh() {
        mainViewModel.forceRefreshThreads()
    }

    private fun setupOnRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener(this)
    }

    private fun setupAdapter() {
        threadListAdapter = ThreadListAdapter(
            context = requireContext(),
            threadDensity = LocalSettings.getInstance(requireContext()).threadDensity,
            folderRole = FolderRole.INBOX,
            contacts = mainViewModel.mergedContacts.value ?: emptyMap(),
            onSwipeFinished = { threadListViewModel.isRecoveringFinished.value = true },
        )
        binding.threadsList.apply {
            adapter = threadListAdapter
            layoutManager = LinearLayoutManager(context)
            orientation = VERTICAL_LIST_WITH_VERTICAL_DRAGGING
            disableDragDirection(DirectionFlag.UP)
            disableDragDirection(DirectionFlag.DOWN)
            disableDragDirection(DirectionFlag.RIGHT)
            disableDragDirection(DirectionFlag.LEFT)
            addItemDecoration(HeaderItemDecoration(this, false) { position ->
                return@HeaderItemDecoration position >= 0 && threadListAdapter.dataSet[position] is String
            })
            addItemDecoration(DateSeparatorItemDecoration())
        }

        mainViewModel.isInternetAvailable.observe(viewLifecycleOwner) {
            // TODO: Manage no Internet screen
            // threadAdapter.toggleOfflineMode(requireContext(), !isInternetAvailable)
            TransitionManager.beginDelayedTransition(binding.root)
            binding.noNetwork.isGone = it
        }

        threadListAdapter.apply {
            stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY

            onThreadClicked = { thread ->
                if (thread.isOnlyOneDraft()) { // Directly go to NewMessage screen
                    threadListViewModel.navigateToSelectedDraft(thread.messages.first()).observe(viewLifecycleOwner) {
                        val mailboxObjectId = mainViewModel.currentMailbox.value?.objectId ?: return@observe
                        safeNavigate(
                            ThreadListFragmentDirections.actionThreadListFragmentToNewMessageActivity(
                                currentMailboxObjectId = mailboxObjectId,
                                draftExists = true,
                                draftLocalUuid = it.draftLocalUuid,
                                draftResource = it.draftResource,
                                messageUid = it.messageUid,
                            )
                        )
                    }
                } else {
                    mainViewModel.selectThread(thread.uid)
                    safeNavigate(ThreadListFragmentDirections.actionThreadListFragmentToThreadFragment(thread.uid))
                }
            }
        }
    }

    private fun setupListeners() = with(binding) {

        toolbar.setNavigationOnClickListener { (activity as? MainActivity)?.binding?.drawerLayout?.open() }

        searchButton.setOnClickListener {
            safeNavigate(ThreadListFragmentDirections.actionThreadListFragmentToSearchFragment())
        }

        userAvatar.setOnClickListener {
            safeNavigate(ThreadListFragmentDirections.actionThreadListFragmentToSwitchUserFragment())
        }

        newMessageFab.setOnClickListener {
            val mailboxObjectId = mainViewModel.currentMailbox.value?.objectId ?: return@setOnClickListener
            safeNavigate(ThreadListFragmentDirections.actionThreadListFragmentToNewMessageActivity(mailboxObjectId))
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

                val shouldKeepItem = performSwipeActionOnThread(swipeAction, item)

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

    private fun performSwipeActionOnThread(swipeAction: SwipeAction, thread: Thread): Boolean {
        return when (swipeAction) {
            SwipeAction.TUTORIAL -> {
                setDefaultSwipeActions()
                safeNavigate(ThreadListFragmentDirections.actionThreadListFragmentToSettingsFragment())
                findNavController().navigate(R.id.swipeActionsSettingsFragment, null, getAnimatedNavOptions())
                true
            }
            SwipeAction.DELETE -> {
                mainViewModel.deleteThread(thread)
                false
            }
            SwipeAction.READ_UNREAD -> {
                mainViewModel.currentMailbox.value?.let { mailbox ->
                    threadListViewModel.toggleSeenStatus(thread, mailbox)
                }
                true
            }
            SwipeAction.NONE -> throw IllegalStateException("Cannot swipe on an action which is not set")
            else -> {
                notYetImplemented()
                true
            }
        }
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
                isCloseIconVisible = isChecked
                mainViewModel.currentFilter.value = if (isChecked) ThreadFilter.UNSEEN else ThreadFilter.ALL
            }
        }
    }

    private fun observeCurrentThreads() {
        mainViewModel.currentThreadsLive.bindResultsChangeToAdapter(viewLifecycleOwner, threadListAdapter).apply {
            recyclerView = binding.threadsList
            waitingBeforeNotifyAdapter = threadListViewModel.isRecoveringFinished
            beforeUpdateAdapter = ::onThreadsUpdate
            afterUpdateAdapter = { threads -> if (firstMessageHasChanged(threads)) scrollToTop() }
        }
    }

    private fun observeDownloadState() {
        mainViewModel.isDownloadingChanges.observe(viewLifecycleOwner) { isDownloading ->
            if (isDownloading) {
                showLoadingTimer.start()
            } else {
                showLoadingTimer.cancel()
                if (binding.swipeRefreshLayout.isRefreshing) binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun observeCurrentFolder() {
        mainViewModel.currentFolder.observe(viewLifecycleOwner) { folder ->
            lastUpdatedDate = null
            updateUpdatedAt()
            clearFilter()
            displayFolderName(folder)
            threadListAdapter.updateFolderRole(folder.role)
        }
    }

    private fun observeCurrentFolderLive() {
        mainViewModel.currentFolderLive.observe(viewLifecycleOwner) { folder ->
            updateUpdatedAt(folder.lastUpdatedAt?.toDate())
            updateUnreadCount(folder.unreadCount)
            threadListViewModel.startUpdatedAtJob()
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
            DraftsActionsWorker.getCompletedWorkInfosLiveData(requireContext()).observe(viewLifecycleOwner) {
                if (mainViewModel.currentFolder.value?.role == FolderRole.DRAFT) {
                    mainViewModel.forceRefreshThreads()
                }
            }
        }

        WorkManager.getInstance(requireContext()).pruneWork().state.observe(viewLifecycleOwner) {
            if (it is Operation.State.FAILURE || it is Operation.State.SUCCESS) observeDraftsActions()
        }
    }

    private fun updateUpdatedAt(newLastUpdatedDate: Date? = null) {

        newLastUpdatedDate?.let { lastUpdatedDate = it }
        val lastUpdatedAt = lastUpdatedDate

        val ago = when {
            lastUpdatedAt == null -> ""
            Date().time - lastUpdatedAt.time < DateUtils.MINUTE_IN_MILLIS -> getString(R.string.threadListHeaderLastUpdateNow)
            else -> DateUtils.getRelativeTimeSpanString(lastUpdatedAt.time).toString().replaceFirstChar { it.lowercaseChar() }
        }

        binding.updatedAt.apply {
            isInvisible = ago.isEmpty()
            if (ago.isNotEmpty()) text = getString(R.string.threadListHeaderLastUpdate, ago)
        }
    }

    private fun updateUnreadCount(unreadCount: Int) {

        if (mainViewModel.currentFilter.value == ThreadFilter.UNSEEN && unreadCount == 0) clearFilter()

        binding.unreadCountChip.apply {
            text = resources.getQuantityString(R.plurals.threadListHeaderUnreadCount, unreadCount, unreadCount)
            isVisible = unreadCount > 0
        }
    }

    private fun displayFolderName(folder: Folder) {
        val folderName = folder.getLocalizedName(binding.context)
        Log.d("UI", "Received folder name (${folderName})")
        binding.toolbar.title = folderName
    }

    private fun onThreadsUpdate(threads: List<Thread>) {
        Log.d("UI", "Received threads (${threads.size})")
        if (threads.isEmpty()) displayNoEmailView() else displayThreadList()
    }

    private fun displayNoEmailView() = with(binding) {
        threadsList.isGone = true
        emptyState.isVisible = true
    }

    private fun displayThreadList() = with(binding) {
        emptyState.isGone = true
        threadsList.isVisible = true
    }

    private fun firstMessageHasChanged(threads: List<Thread>): Boolean {
        val firstMessageCustomUid = "${threads.firstOrNull()?.uid}_${AccountUtils.currentMailboxId}"
        return (firstMessageCustomUid != previousFirstMessageUid).also {
            previousFirstMessageUid = firstMessageCustomUid
        }
    }

    private fun clearFilter() {
        with(mainViewModel.currentFilter) {
            if (value != ThreadFilter.ALL) value = ThreadFilter.ALL
        }

        with(binding.unreadCountChip) {
            isChecked = false
            isCloseIconVisible = false
        }
    }

    private fun scrollToTop() = binding.threadsList.layoutManager?.scrollToPosition(0)
}
