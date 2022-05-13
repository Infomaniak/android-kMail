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

class ThreadListFragment : Fragment() {

    private val mainViewModel: MainViewModel by viewModels()
    private val threadListViewModel: ThreadListViewModel by viewModels()

    private lateinit var binding: FragmentThreadListBinding
    private lateinit var threadListAdapter: ThreadListAdapter

    private var jobFolderName: Job? = null
    private var jobThreadsFromApi: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentThreadListBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapter()
        setupListeners()

        val threads = getData()
        displayThreadsFromRealm(threads)
        displayFolderName()
        displayThreadsFromApi()
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
                safeNavigate(ThreadListFragmentDirections.actionThreadListFragmentToThreadFragment(it.uid, it.subject))
            }
        }
    }

    private fun setupListeners() {
        with(binding) {
            openMultiselectButton.setOnClickListener {
                // TODO multiselection
            }

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
    }

    private fun getData(): List<Thread> {
        return with(threadListViewModel) {
            threadsFromApi.value = null
            getDataFromRealmThenFetchFromApi(mainViewModel.isInternetAvailable.value ?: false)
        }
    }

    private fun displayThreadsFromRealm(threads: List<Thread>) {
        displayThreads(threads)
    }

    private fun displayFolderName() {
        if (jobFolderName != null) jobFolderName?.cancel()

        jobFolderName = with(threadListViewModel) {
            viewModelScope.launch(Dispatchers.Main) {
                MailRealm.currentFolderIdFlow.filterNotNull().collect {
                    MailboxContentController.getFolder(it)?.let { folder ->
                        binding.mailboxName.text = folder.getLocalizedName(requireContext())
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

        if (threads.isEmpty()) {
            displayNoEmailView()
        } else {
            displayThreadList()
        }

        with(threadListAdapter) {
            val newList = formatList(threads, requireContext())
            notifyAdapter(newList)
        }
    }

    private fun displayNoEmailView() {
        with(binding) {
            threadsList.isGone = true
            noMailLayout.root.isVisible = true
        }
    }

    private fun displayThreadList() {
        with(binding) {
            threadsList.isVisible = true
            noMailLayout.root.isGone = true
        }
    }
}
