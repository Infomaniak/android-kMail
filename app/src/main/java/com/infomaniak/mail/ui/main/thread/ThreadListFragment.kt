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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.data.cache.MailRealm
import com.infomaniak.mail.data.cache.MailboxContentController
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.FragmentThreadListBinding
import com.infomaniak.mail.ui.main.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class ThreadListFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {

    private val mainViewModel: MainViewModel by viewModels()
    private val threadListViewModel: ThreadListViewModel by viewModels()

    private lateinit var binding: FragmentThreadListBinding
    private lateinit var threadListAdapter: ThreadListAdapter

    private var jobFolderName: Job? = null
    private var jobThreadsFromApi: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentThreadListBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupOnRefresh()
        setupAdapter()
        setupListeners()
    }

    private fun setupOnRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener(this)
    }

    override fun onRefresh() {
        getData()
    }

    private fun setupAdapter() {
        binding.threadsList.adapter = ThreadListAdapter().also { threadListAdapter = it }

        mainViewModel.isInternetAvailable.observe(viewLifecycleOwner) { isInternetAvailable ->
            // TODO: Manage no Internet screen
            // threadAdapter.toggleOfflineMode(requireContext(), !isInternetAvailable)
            // binding.noNetwork.isGone = isInternetAvailable
        }

        threadListAdapter.apply {
            // stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

            // onEmptyList = { checkIfNoFiles() }

            onThreadClicked = {
                safeNavigate(
                    ThreadListFragmentDirections.actionThreadListFragmentToThreadFragment(
                        threadUid = it.uid,
                        threadSubject = it.subject,
                        // TODO see if favorite Icon must be displayed
                        // threadIsFavorite = it.flagged
                    )
                )
            }
        }
    }

    private fun setupListeners() = with(binding) {
        // TODO multiselection
        // openMultiselectButton.setOnClickListener {}

        header.searchViewCard.apply {
            // TODO filterButton doesn't propagate the event to root, must display it ?
            searchView.isGone = true
            searchViewText.isVisible = true
            filterButton.isEnabled = false
            root.setOnClickListener {
                safeNavigate(ThreadListFragmentDirections.actionThreadListFragmentToSearchFragment())
            }
        }

        header.userAvatar.setOnClickListener {
            safeNavigate(ThreadListFragmentDirections.actionThreadListFragmentToSwitchUserFragment())
        }

        newMessageFab.setOnClickListener {
            safeNavigate(ThreadListFragmentDirections.actionHomeFragmentToNewMessageActivity())
        }

        threadsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                if (layoutManager.findFirstCompletelyVisibleItemPosition() == 0 ||
                    layoutManager.findLastCompletelyVisibleItemPosition() == threadListAdapter.itemCount - 1
                ) {
                    newMessageFab.extend()
                } else {
                    newMessageFab.shrink()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()

        val threads = getData()
        displayThreadsFromRealm(threads)
        displayFolderName()
        displayThreadsFromApi()
    }

    override fun onPause() {
        jobFolderName?.cancel()
        jobThreadsFromApi?.cancel()
        super.onPause()
    }

    private fun getData(): List<Thread> = with(threadListViewModel) {
        threadsFromApi.value = null
        getDataFromRealmThenFetchFromApi()
    }

    private fun displayThreadsFromRealm(threads: List<Thread>) {
        displayThreads(threads)
    }

    private fun displayFolderName() {
        if (jobFolderName != null) jobFolderName?.cancel()

        jobFolderName = with(threadListViewModel) {
            viewModelScope.launch(Dispatchers.Main) {
                MailRealm.currentFolderIdFlow.filterNotNull().collect { folderId ->
                    context?.let {
                        MailboxContentController.getFolder(folderId)?.let { folder ->
                            binding.mailboxName.text = folder.getLocalizedName(it)
                        }
                    }
                }
            }
        }
    }

    private fun displayThreadsFromApi() {
        if (jobThreadsFromApi != null) jobThreadsFromApi?.cancel()

        jobThreadsFromApi = with(threadListViewModel) {
            viewModelScope.launch(Dispatchers.Main) {
                threadsFromApi.filterNotNull().collect(::displayThreads)
            }
        }
    }

    private fun displayThreads(threads: List<Thread>) {
        Log.i("UI", "Received threads (${threads.size})")
        // threads.forEach { Log.v("UI", "Subject: ${it.subject}") }

        binding.swipeRefreshLayout.isRefreshing = false

        if (threads.isEmpty()) {
            displayNoEmailView()
        } else {
            displayThreadList()
        }

        context?.let {
            with(threadListAdapter) {
                val newList = formatList(threads, it)
                notifyAdapter(newList)
            }
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
}
