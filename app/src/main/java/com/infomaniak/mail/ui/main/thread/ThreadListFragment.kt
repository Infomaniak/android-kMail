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
package com.infomaniak.mail.ui.main.thread

import android.os.Bundle
import android.os.CountDownTimer
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
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
import com.infomaniak.mail.ui.main.MainActivity
import com.infomaniak.mail.ui.main.MainViewModel
import com.infomaniak.mail.utils.*
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.*
import java.util.*

class ThreadListFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {

    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var binding: FragmentThreadListBinding

    private var folderJob: Job? = null
    private var threadsJob: Job? = null
    private var updatedAtRefreshJob: Job? = null

    private var threadListAdapter = ThreadListAdapter()

    private val showLoadingTimer: CountDownTimer by lazy {
        Utils.createRefreshTimer(
            // TODO: Remove & fix this. It's been put there because currently, it seems that when the refresh API call is shorter
            // TODO: than 600ms, the spinning thingy never disappears (because it appears AFTER that everything is finished).
            milliseconds = 0L,
        ) { binding.swipeRefreshLayout.isRefreshing = true }
    }

    var filter: ThreadFilter = ThreadFilter.ALL // TODO: Do we need this? Here?
    var lastUnreadCount = 0 // TODO: Do we need this?
    var mailboxUuid: String? = null

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
                lifecycleScope.launch(Dispatchers.IO) {
                    if (MainViewModel.currentFolderId.value?.let(FolderController::getFolderSync)?.isDraftFolder == true) {
                        if (it.messages.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                openMessageEdition(R.id.action_threadListFragment_to_newMessageActivity, it.messages.first())
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            safeNavigate(
                                ThreadListFragmentDirections.actionThreadListFragmentToThreadFragment(
                                    threadUid = it.uid,
                                    threadSubject = it.subject,
                                    threadIsFavorite = it.isFavorite,
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setupListeners() = with(binding) {

        toolbar.setNavigationOnClickListener { (activity as? MainActivity)?.binding?.drawerLayout?.open() }

        searchButton.setOnClickListener {
            notYetImplemented()
            // safeNavigate(ThreadListFragmentDirections.actionThreadListFragmentToSearchFragment()) // TODO
        }

        userAvatar.setOnClickListener {
            safeNavigate(ThreadListFragmentDirections.actionThreadListFragmentToSwitchUserFragment())
        }

        newMessageFab.setOnClickListener {
            safeNavigate(ThreadListFragmentDirections.actionThreadListFragmentToNewMessageActivity())
        }

        threadsList.setPagination(
            whenLoadMoreIsPossible = { if (!mainViewModel.isDownloadingChanges) downloadThreads() },
            triggerOffset = OFFSET_TRIGGER,
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
                swipeRefreshLayout.isRefreshing = true
                mainViewModel.forceRefreshThreads(filter)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.unreadCountChip.apply { isCloseIconVisible = isChecked } // TODO: Do we need this? If yes, do we need it HERE?
    }

    private fun listenToCurrentMailbox() {
        MainViewModel.currentMailboxObjectId.observeNotNull(this) { mailboxObjectId ->
            lifecycleScope.launch(Dispatchers.IO) {
                mailboxUuid = MailboxController.getMailboxSync(mailboxObjectId)?.uuid
            }
        }
    }

    private fun listenToCurrentFolder() {
        MainViewModel.currentFolderId.observeNotNull(this) { folderId ->

            startPeriodicRefreshJob(folderId)

            folderJob?.cancel()
            folderJob = lifecycleScope.launch(Dispatchers.IO) {

                FolderController.getFolderSync(folderId)?.let { folder ->
                    withContext(Dispatchers.Main) {
                        displayFolderName(folder)
                        listenToThreads(folder)
                    }
                }

                FolderController.getFolderAsync(folderId).collect {
                    withContext(Dispatchers.Main) { it.obj?.let(::updateHeader) }
                }
            }
        }
    }

    override fun onDestroyView() {
        MainViewModel.currentFolderId.removeObservers(this)
        MainViewModel.currentMailboxObjectId.removeObservers(this)
        super.onDestroyView()
    }

    private fun startPeriodicRefreshJob(folderId: String) {
        updatedAtRefreshJob?.cancel()
        updatedAtRefreshJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                val lastUpdatedAt = FolderController.getFolderSync(folderId)?.lastUpdatedAt
                withContext(Dispatchers.Main) { updateUpdatedAt(lastUpdatedAt) }
                delay(DateUtils.MINUTE_IN_MILLIS + 1_000L)
            }
        }
    }

    private fun displayFolderName(folder: Folder) {
        val folderName = folder.getLocalizedName(binding.context)
        Log.i("UI", "Received folder name (${folderName})")
        binding.toolbar.title = folderName
    }

    private fun updateHeader(folder: Folder) {
        resetForFurtherThreadsLoading()
        updateUpdatedAt(folder.lastUpdatedAt)
        updateUnreadCount(folder.unreadCount)
    }

    private fun updateUpdatedAt(lastUpdatedAt: RealmInstant?) {
        val lastUpdatedDate = lastUpdatedAt?.toDate()
        val ago = when {
            lastUpdatedDate == null -> ""
            Date().time - lastUpdatedDate.time < DateUtils.MINUTE_IN_MILLIS -> getString(R.string.threadListHeaderLastUpdateNow)
            else -> DateUtils.getRelativeTimeSpanString(lastUpdatedDate.time).toString()
                .replaceFirstChar { it.lowercaseChar() }
        }

        binding.updatedAt.text = if (ago.isEmpty()) {
            getString(R.string.noNetworkDescription)
        } else {
            getString(R.string.threadListHeaderLastUpdate, ago)
        }
    }

    private fun updateUnreadCount(unreadCount: Int) = with(binding) {
        if (unreadCount == 0 && lastUnreadCount > 0 && filter != ThreadFilter.ALL) {
            swipeRefreshLayout.isRefreshing = true
            clearFilter()
            mainViewModel.forceRefreshThreads(filter)
        }

        lastUnreadCount = unreadCount

        unreadCountChip.apply {
            text = resources.getQuantityString(R.plurals.threadListHeaderUnreadCount, unreadCount, unreadCount)
            isVisible = unreadCount > 0
        }
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
        resetForFurtherThreadsLoading()
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

        if (canContinueToPaginate) {
            currentOffset += PER_PAGE
            showLoadingTimer.start()
            loadMoreThreads(uuid, currentOffset, filter)
        }
    }

    private fun clearFilter() = with(binding.unreadCountChip) {
        filter = ThreadFilter.ALL
        isChecked = false
        isCloseIconVisible = false
    }

    private fun scrollToTop() = binding.threadsList.layoutManager?.scrollToPosition(0)

    private fun resetForFurtherThreadsLoading() {
        mainViewModel.isDownloadingChanges = false
        binding.swipeRefreshLayout.isRefreshing = false
    }

    private companion object {
        const val OFFSET_TRIGGER = 1
    }
}
