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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.infomaniak.lib.core.utils.Utils
import com.infomaniak.lib.core.utils.loadAvatar
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.utils.setPagination
import com.infomaniak.mail.R
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.api.ApiRepository.OFFSET_FIRST_PAGE
import com.infomaniak.mail.data.api.ApiRepository.PER_PAGE
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.FragmentThreadListBinding
import com.infomaniak.mail.ui.main.MainViewModel
import com.infomaniak.mail.ui.main.menu.MenuDrawerFragment
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class ThreadListFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {

    private val mainViewModel: MainViewModel by activityViewModels()
    private val threadListViewModel: ThreadListViewModel by viewModels()

    private lateinit var binding: FragmentThreadListBinding

    private var threadListAdapter = ThreadListAdapter()

    private var folderNameJob: Job? = null
    private var threadsJob: Job? = null

    private var menuDrawerFragment: MenuDrawerFragment? = null

    private val showLoadingTimer: CountDownTimer by lazy {
        Utils.createRefreshTimer { binding.swipeRefreshLayout.isRefreshing = true }
    }

    private var currentOffset = OFFSET_FIRST_PAGE
    private var isDownloadingChanges = false

    private val drawerListener = object : DrawerLayout.DrawerListener {
        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            // No-op
        }

        override fun onDrawerOpened(drawerView: View) {
            // No-op
        }

        override fun onDrawerClosed(drawerView: View) {
            menuDrawerFragment?.closeDropdowns()
        }

        override fun onDrawerStateChanged(newState: Int) {
            // No-op
        }
    }

    private companion object {
        const val OFFSET_TRIGGER = 1
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentThreadListBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupOnRefresh()
        setupAdapter()
        setupMenuDrawer()
        binding.setupListeners()
        setupUserAvatar()

        threadListViewModel.setup()
    }

    override fun onDestroyView() {
        binding.drawerLayout.removeDrawerListener(drawerListener)

        super.onDestroyView()
    }

    private fun setupOnRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener(this)
    }

    override fun onRefresh() {
        currentOffset = OFFSET_FIRST_PAGE
        threadListViewModel.refreshThreads()
    }

    private fun setupMenuDrawer() {
        binding.drawerLayout.addDrawerListener(drawerListener)

        val fragment = MenuDrawerFragment(
            closeDrawer = { closeDrawer() },
            isDrawerOpen = { binding.drawerLayout.isOpen },
        ).also { menuDrawerFragment = it }

        activity?.supportFragmentManager
            ?.beginTransaction()
            ?.add(R.id.menuDrawerFragment, fragment)
            ?.commit()
    }

    private fun setupAdapter() {
        binding.threadsList.adapter = threadListAdapter

        mainViewModel.isInternetAvailable.observe(viewLifecycleOwner) { isInternetAvailable ->
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
                        threadIsFavorite = it.flagged
                    )
                )
            }
        }
    }

    private fun FragmentThreadListBinding.setupListeners() {
        // TODO: Multiselect
        // openMultiselectButton.setOnClickListener {}

        toolbar.setNavigationOnClickListener { drawerLayout.open() }

        searchViewCard.apply {
            // TODO: FilterButton doesn't propagate the event to root, must display it?
            searchView.isGone = true
            searchViewText.isVisible = true
            filterButton.isEnabled = false
            root.setOnClickListener {
                safeNavigate(ThreadListFragmentDirections.actionThreadListFragmentToSearchFragment())
            }
        }

        userAvatar.setOnClickListener {
            safeNavigate(ThreadListFragmentDirections.actionThreadListFragmentToSwitchUserFragment())
        }

        newMessageFab.setOnClickListener {
            safeNavigate(ThreadListFragmentDirections.actionHomeFragmentToNewMessageActivity())
        }

        threadsList.setPagination(
            whenLoadMoreIsPossible = { if (!isDownloadingChanges) downloadThreads() },
            triggerOffset = OFFSET_TRIGGER,
        )

        threadsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                if (layoutManager.findFirstCompletelyVisibleItemPosition() == 0 || dy < 0) {
                    newMessageFab.extend()
                } else {
                    newMessageFab.shrink()
                }
            }
        })
    }

    private fun setupUserAvatar() {
        AccountUtils.currentUser?.let(binding.userAvatarImage::loadAvatar)
    }

    override fun onResume() {
        super.onResume()

        binding.newMessageFab.shrink()

        listenToFolderName()
        listenToThreads()

        currentOffset = OFFSET_FIRST_PAGE
        threadListViewModel.loadMailData()
    }

    override fun onPause() {
        folderNameJob?.cancel()
        threadsJob?.cancel()
        super.onPause()
    }

    private fun listenToFolderName() {
        with(threadListViewModel) {
            folderNameJob?.cancel()
            folderNameJob = viewModelScope.launch(Dispatchers.Main) {
                MailData.currentFolderFlow.filterNotNull().collect(::displayFolderName)
            }
        }
    }

    private fun displayFolderName(folder: Folder) = with(binding) {
        val folderName = folder.getLocalizedName(context)
        Log.i("UI", "Received folder name (${folderName})")
        mailboxName.text = folderName
    }

    private fun listenToThreads() {
        with(threadListViewModel) {
            threadsJob?.cancel()
            threadsJob = viewModelScope.launch(Dispatchers.Main) {
                uiThreadsFlow.filterNotNull().collect(::displayThreads)
            }
        }
    }

    private fun displayThreads(threads: List<Thread>) = with(binding) {
        Log.i("UI", "Received threads (${threads.size})")
        isDownloadingChanges = false
        swipeRefreshLayout.isRefreshing = false

        if (threads.isEmpty()) displayNoEmailView() else displayThreadList()

        with(threadListAdapter) {
            notifyAdapter(formatList(threads, context))
        }
    }

    private fun displayNoEmailView() = with(binding) {
        threadsList.isGone = true
        noMailLayoutGroup.isVisible = true
    }

    private fun displayThreadList() = with(binding) {
        threadsList.isVisible = true
        noMailLayoutGroup.isGone = true
    }

    private fun closeDrawer() = with(binding) {
        drawerLayout.closeDrawer(menuDrawerNavigation)
    }

    private fun downloadThreads() {

        val folder = MailData.currentFolderFlow.value ?: return
        val mailbox = MailData.currentMailboxFlow.value ?: return

        if (folder.totalCount > currentOffset + PER_PAGE) {
            isDownloadingChanges = true
            currentOffset += PER_PAGE
            showLoadingTimer.start()
            threadListViewModel.loadThreads(folder, mailbox, currentOffset)
        }
    }
}
