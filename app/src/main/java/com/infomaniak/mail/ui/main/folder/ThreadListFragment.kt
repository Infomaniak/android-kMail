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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil.imageLoader
import com.infomaniak.lib.core.utils.Utils
import com.infomaniak.lib.core.utils.loadAvatar
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.utils.setPagination
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository.OFFSET_FIRST_PAGE
import com.infomaniak.mail.data.api.ApiRepository.PER_PAGE
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxInfos.MailboxController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.databinding.FragmentThreadListBinding
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.*
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.max

class ThreadListFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {

    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var binding: FragmentThreadListBinding

    private var folderJob: Job? = null
    private var threadsJob: Job? = null
    private var updatedAtRefreshJob: Job? = null

    private var threadListAdapter = ThreadListAdapter()
    var lastUpdatedDate: Date? = null

    private val showLoadingTimer: CountDownTimer by lazy {
        Utils.createRefreshTimer(milliseconds = 600L) { binding.swipeRefreshLayout.isRefreshing = true }
    }

    var filter: ThreadFilter = ThreadFilter.ALL
    var lastUnreadCount = 0
    private var mailboxUuid: String? = null
    private val offsetTrigger = max(1, PER_PAGE - 13)

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

        listenToCurrentMailbox()
        listenToCurrentFolder()
        listenToDownloadState()
    }

    private fun setupOnRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener(this)
    }

    override fun onRefresh() {
        mainViewModel.forceRefreshThreads(filter)
    }

    private fun setupAdapter() {
        binding.threadsList.adapter = threadListAdapter

        mainViewModel.isInternetAvailable.observe(viewLifecycleOwner) {
            // TODO: Manage no Internet screen
            // threadAdapter.toggleOfflineMode(requireContext(), !isInternetAvailable)
            // binding.noNetwork.isGone = isInternetAvailable
        }

        threadListAdapter.apply {
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

            // onEmptyList = { checkIfNoFiles() }

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

        threadsList.setPagination(
            whenLoadMoreIsPossible = { if (mainViewModel.isDownloadingChanges.value == false) downloadThreads() },
            triggerOffset = offsetTrigger,
        )

        threadsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                if (layoutManager.findFirstCompletelyVisibleItemPosition() == 0 || dy <= 0) {
                    newMessageFab.extend()
                } else {
                    newMessageFab.shrink()
                }

                super.onScrolled(recyclerView, dx, dy)
            }
        })
    }

    private fun setupUserAvatar() {
        AccountUtils.currentUser?.let { binding.userAvatarImage.loadAvatar(it, requireContext().imageLoader) }
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

    override fun onDestroyView() {
        mainViewModel.isDownloadingChanges.removeObservers(this)
        MainViewModel.currentFolderId.removeObservers(this)
        MainViewModel.currentMailboxObjectId.removeObservers(this)
        super.onDestroyView()
    }

    private fun listenToCurrentMailbox() {
        MainViewModel.currentMailboxObjectId.observeNotNull(this) { mailboxObjectId ->
            lifecycleScope.launch(Dispatchers.IO) {
                mailboxUuid = MailboxController.getMailboxSync(mailboxObjectId)?.uuid
            }
        }
    }

    private fun listenToDownloadState() {
        mainViewModel.isDownloadingChanges.observeNotNull(this) { isDownloading ->
            if (isDownloading) {
                showLoadingTimer.start()
            } else {
                showLoadingTimer.cancel()
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun listenToCurrentFolder() {
        MainViewModel.currentFolderId.observeNotNull(this) { folderId ->

            lastUpdatedDate = null
            updateUpdatedAt()

            folderJob?.cancel()
            folderJob = lifecycleScope.launch(Dispatchers.IO) {

                FolderController.getFolderSync(folderId)?.let { folder ->
                    withContext(Dispatchers.Main) {
                        displayFolderName(folder)
                        listenToThreads(folder)
                        updateUpdatedAt(folder.lastUpdatedAt?.toDate())
                    }
                }

                FolderController.getFolderAsync(folderId).collect {
                    startPeriodicUpdatedAtRefreshJob()
                    withContext(Dispatchers.Main) {
                        updateUpdatedAt(it.obj?.lastUpdatedAt?.toDate())
                        it.obj?.unreadCount?.let(::updateUnreadCount)
                    }
                }
            }
        }
    }

    private fun startPeriodicUpdatedAtRefreshJob() {
        updatedAtRefreshJob?.cancel()
        updatedAtRefreshJob = lifecycleScope.launch(Dispatchers.IO) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(DateUtils.MINUTE_IN_MILLIS)
                    withContext(Dispatchers.Main) { updateUpdatedAt() }
                }
            }
        }
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

    private fun listenToThreads(folder: Folder) {
        threadsJob?.cancel()
        threadsJob = lifecycleScope.launch(Dispatchers.IO) {
            folder.threads.asFlow().toSharedFlow().collect {
                if (isResumed) withContext(Dispatchers.Main) { displayThreads(it.list) }
            }
        }
    }

    private fun displayThreads(threads: List<Thread>) = with(mainViewModel) {
        Log.i("UI", "Received threads (${threads.size})")
        if (threads.size < PER_PAGE) canContinueToPaginate = false

        if (threads.isEmpty()) displayNoEmailView() else displayThreadList()

        threadListAdapter.notifyAdapter(threadListAdapter.formatList(threads, binding.context))

        if (currentOffset == OFFSET_FIRST_PAGE) scrollToTop()
    }

    private fun displayNoEmailView() = with(binding) {
        threadsList.isGone = true
        noMailLayoutGroup.isVisible = true
    }

    private fun displayThreadList() = with(binding) {
        threadsList.isVisible = true
        noMailLayoutGroup.isGone = true
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
}
