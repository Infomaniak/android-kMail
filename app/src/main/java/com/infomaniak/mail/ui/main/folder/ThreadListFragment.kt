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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnItemSwipeListener
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnItemSwipeListener.SwipeDirection
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener.ScrollDirection
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener.ScrollState
import com.infomaniak.lib.core.utils.Utils
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository.PER_PAGE
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.databinding.FragmentThreadListBinding
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.RealmChangesBinding.Companion.bindListChangeToAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.max

class ThreadListFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {

    private val mainViewModel: MainViewModel by activityViewModels()
    private val threadListViewModel: ThreadListViewModel by viewModels()

    private lateinit var binding: FragmentThreadListBinding

    private var folderJob: Job? = null

    private lateinit var threadListAdapter: ThreadListAdapter
    private var lastUpdatedDate: Date? = null
    private var previousFirstMessageUid: String? = null

    private val showLoadingTimer: CountDownTimer by lazy {
        Utils.createRefreshTimer { binding.swipeRefreshLayout.isRefreshing = true }
    }

    var filter: ThreadFilter = ThreadFilter.ALL
    private var lastUnreadCount = 0
    private var mailboxUuid: String? = null
    // We want to trigger the next page loading as soon as possible so there is as little wait time as possible;
    // but not before we do any scrolling, so we don't immediately load the 2nd page when opening the Folder.
    private val offsetTrigger = max(1, PER_PAGE - ESTIMATED_PAGE_SIZE)

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

        observeCurrentFolderThreads()
        observeCurrentMailbox()
        listenToDownloadState()
        listenToCurrentFolder()
        listenToUpdatedAtTriggers()
    }

    private fun setupOnRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener(this)
    }

    override fun onRefresh() {
        mainViewModel.forceRefreshThreads(filter)
    }

    private fun setupAdapter() {
        threadListAdapter = ThreadListAdapter(
            threadDensity = ThreadDensity.LARGE, // TODO : Take this value from the settings when available
            onSwipeFinished = { threadListViewModel.isRecoveringFinished.value = true },
        )
        binding.threadsList.apply {
            adapter = threadListAdapter
            layoutManager = LinearLayoutManager(context)
            orientation = DragDropSwipeRecyclerView.ListOrientation.VERTICAL_LIST_WITH_VERTICAL_DRAGGING
            disableDragDirection(DragDropSwipeRecyclerView.ListOrientation.DirectionFlag.UP)
            disableDragDirection(DragDropSwipeRecyclerView.ListOrientation.DirectionFlag.DOWN)
            disableDragDirection(DragDropSwipeRecyclerView.ListOrientation.DirectionFlag.RIGHT)
            disableDragDirection(DragDropSwipeRecyclerView.ListOrientation.DirectionFlag.LEFT)
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

            onThreadClicked = {
                safeNavigate(
                    ThreadListFragmentDirections.actionThreadListFragmentToThreadFragment(
                        threadUid = it.uid,
                        threadSubject = it.subject,
                        threadIsFavorite = it.isFavorite,
                        unseenMessagesCount = it.unseenMessagesCount,
                    )
                )
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
            safeNavigate(ThreadListFragmentDirections.actionHomeFragmentToNewMessageActivity())
        }

        threadsList.scrollListener = object : OnListScrollListener {
            override fun onListScrollStateChanged(scrollState: ScrollState) = Unit

            override fun onListScrolled(scrollDirection: ScrollDirection, distance: Int) {
                val layoutManager = threadsList.layoutManager as LinearLayoutManager
                layoutManager.handlePagination(scrollDirection)
                extendCollapseFab(layoutManager, scrollDirection)
            }
        }

        threadsList.swipeListener = object : OnItemSwipeListener<Any> {
            override fun onItemSwiped(position: Int, direction: SwipeDirection, item: Any): Boolean {
                when (direction) {
                    SwipeDirection.LEFT_TO_RIGHT -> ThreadController.toggleSeenStatus(
                        thread = item as Thread,
                        folderId = MainViewModel.currentFolderId.value!!,
                    )
                    SwipeDirection.RIGHT_TO_LEFT -> notYetImplemented() // TODO: Delete thread
                    else -> throw IllegalStateException("Only SwipeDirection.LEFT_TO_RIGHT and SwipeDirection.RIGHT_TO_LEFT can be triggered")
                }

                threadListAdapter.apply {
                    blockOtherSwipes()
                    notifyItemChanged(position) // Animate the swiped element back to its original position
                }
                threadListViewModel.isRecoveringFinished.value = false

                return true
            }
        }
    }

    fun LinearLayoutManager.handlePagination(scrollDirection: ScrollDirection) {
        if (scrollDirection == ScrollDirection.DOWN) {
            val pastVisibleItems = findFirstVisibleItemPosition() + offsetTrigger
            val isLastElement = (childCount + pastVisibleItems) >= itemCount
            if (isLastElement && mainViewModel.isDownloadingChanges.value == false) downloadThreads()
        }
    }

    private fun FragmentThreadListBinding.extendCollapseFab(
        layoutManager: LinearLayoutManager,
        scrollDirection: ScrollDirection,
    ) {
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
                filter = if (isChecked) ThreadFilter.UNSEEN else ThreadFilter.ALL
                mainViewModel.forceRefreshThreads(filter)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.unreadCountChip.apply { isCloseIconVisible = isChecked } // TODO: Do we need this? If yes, do we need it HERE?
    }

    private fun observeCurrentFolderThreads() {
        threadListViewModel.currentFolderThreads.bindListChangeToAdapter(viewLifecycleOwner, threadListAdapter).apply {
            waitingBeforeNotifyAdapter = threadListViewModel.isRecoveringFinished
            beforeUpdateAdapter = ::onThreadsUpdate
            afterUpdateAdapter = { threads -> if (firstMessageHasChanged(threads)) scrollToTop() }
        }
    }

    private fun observeCurrentMailbox() {
        MainViewModel.currentMailboxObjectId.observeNotNull(this) { objectId ->
            mainViewModel.getMailbox(objectId).observe(viewLifecycleOwner) { mailbox ->
                mailboxUuid = mailbox?.uuid
            }
        }
    }

    private fun listenToDownloadState() {
        mainViewModel.isDownloadingChanges.observeNotNull(this) { isDownloading ->
            if (isDownloading) {
                showLoadingTimer.start()
            } else {
                showLoadingTimer.cancel()
                if (binding.swipeRefreshLayout.isRefreshing) binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun listenToCurrentFolder() {
        MainViewModel.currentFolderId.observeNotNull(this) { folderId ->

            lastUpdatedDate = null
            updateUpdatedAt()

            folderJob?.cancel()
            folderJob = lifecycleScope.launch(Dispatchers.Main) {
                getFolder(folderId)
                listenToFolder(folderId)
            }
        }
    }

    private fun getFolder(folderId: String) {
        mainViewModel.getFolder(folderId).observeNotNull(viewLifecycleOwner) { folder ->
            threadListViewModel.currentFolder.value = folder
            displayFolderName(folder)
        }
    }

    private fun listenToFolder(folderId: String) {
        threadListViewModel.listenToFolder(folderId).observe(viewLifecycleOwner) { folder ->
            updateUpdatedAt(folder.lastUpdatedAt?.toDate())
            updateUnreadCount(folder.unreadCount)
            threadListViewModel.startUpdatedAtJob()
        }
    }

    private fun listenToUpdatedAtTriggers() {
        threadListViewModel.updatedAtTrigger.observe(viewLifecycleOwner) { updateUpdatedAt() }
    }

    private fun updateUpdatedAt(newLastUpdatedDate: Date? = null) {

        newLastUpdatedDate?.let { lastUpdatedDate = it }
        val lastUpdatedAt = lastUpdatedDate

        val ago = when {
            lastUpdatedAt == null -> {
                ""
            }
            Date().time - lastUpdatedAt.time < DateUtils.MINUTE_IN_MILLIS -> {
                getString(R.string.threadListHeaderLastUpdateNow)
            }
            else -> {
                DateUtils.getRelativeTimeSpanString(lastUpdatedAt.time).toString().replaceFirstChar { it.lowercaseChar() }
            }
        }

        binding.updatedAt.apply {
            isInvisible = ago.isEmpty()
            if (ago.isNotEmpty()) text = getString(R.string.threadListHeaderLastUpdate, ago)
        }
    }

    private fun updateUnreadCount(unreadCount: Int) = with(binding) {
        if (unreadCount == 0 && lastUnreadCount > 0 && filter != ThreadFilter.ALL) {
            clearFilter()
            mainViewModel.forceRefreshThreads(filter)
        }

        lastUnreadCount = unreadCount

        unreadCountChip.apply {
            text = resources.getQuantityString(R.plurals.threadListHeaderUnreadCount, unreadCount, unreadCount)
            isVisible = unreadCount > 0
        }
    }

    private fun displayFolderName(folder: Folder) {
        val folderName = folder.getLocalizedName(binding.context)
        Log.i("UI", "Received folder name (${folderName})")
        binding.toolbar.title = folderName
    }

    private fun onThreadsUpdate(threads: List<Thread>) {
        Log.i("UI", "Received threads (${threads.size})")
        if (threads.size < PER_PAGE) mainViewModel.canContinueToPaginate = false
        if (threads.isEmpty()) displayNoEmailView() else displayThreadList()
    }

    private fun displayNoEmailView() = with(binding) {
        threadsList.isGone = true
        noMailLayoutGroup.isVisible = true
    }

    private fun displayThreadList() = with(binding) {
        threadsList.isVisible = true
        noMailLayoutGroup.isGone = true
    }

    private fun firstMessageHasChanged(threads: List<Thread>): Boolean {
        val firstMessageCustomUid = "${threads.firstOrNull()?.uid}_${AccountUtils.currentMailboxId}"
        return (firstMessageCustomUid != previousFirstMessageUid).also {
            previousFirstMessageUid = firstMessageCustomUid
        }
    }

    private fun downloadThreads() = with(mainViewModel) {

        val uuid = mailboxUuid ?: return
        val folderId = MainViewModel.currentFolderId.value ?: return

        if (canContinueToPaginate) {
            currentOffset += PER_PAGE
            loadMoreThreads(uuid, folderId, currentOffset, filter)
        }
    }

    private fun clearFilter() = with(binding.unreadCountChip) {
        filter = ThreadFilter.ALL
        isChecked = false
        isCloseIconVisible = false
    }

    private fun scrollToTop() = binding.threadsList.layoutManager?.scrollToPosition(0)

    enum class ThreadDensity {
        COMPACT,
        NORMAL,
        LARGE,
    }

    private companion object {
        // This is approximately a little more than the number of Threads displayed at the same time on the screen.
        const val ESTIMATED_PAGE_SIZE = 13
    }
}
