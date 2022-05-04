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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.databinding.FragmentThreadListBinding
import kotlinx.coroutines.launch

class ThreadListFragment : Fragment() {

    private val threadListViewModel: ThreadListViewModel by viewModels()

    private lateinit var binding: FragmentThreadListBinding
    private val threadAdapter = ThreadListAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentThreadListBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.threadList.adapter = threadAdapter
        listenToChanges()
        threadListViewModel.getThreads()

        setupListeners()
        setupThreadAdapter()
    }

    private fun listenToChanges() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                threadListViewModel.threads.collect { threads ->

                    try {
                        Log.e("Realm", "listenToChanges: Received threads (${threads?.size})")
                        threads?.forEach { Log.d("Realm", "listenToChanges: ${it.subject}") }
                    } catch (e: RuntimeException) {
                        Log.e("Realm", "listenToChanges: Received threads crashed")
                    }

                    if (threads?.isNotEmpty() == true) {
                        displayThreadList()
                        with(threadAdapter) {
                            val newList = formatList(threads, requireContext())
                            notifyAdapter(newList)
                        }
                    } else {
                        displayNoEmailView()
                    }
                }
            }
        }
    }

    private fun setupThreadAdapter() {
        threadListViewModel.isInternetAvailable.observe(viewLifecycleOwner) { isInternetAvailable ->
            // TODO manage no internet screen
//            threadAdapter.toggleOfflineMode(requireContext(), !isInternetAvailable)
//            binding.noNetwork.isGone = isInternetAvailable
        }

        threadAdapter.apply {
//            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

//            onEmptyList = { checkIfNoFiles() }

            onThreadClicked = { thread ->
//                if (thread.isUsable()) {
                safeNavigate(ThreadListFragmentDirections.actionThreadListFragmentToThreadFragment())
//                    }
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
        }
    }

    private fun displayNoEmailView() {
        with(binding) {
            threadList.isGone = true
            noMailLayout.root.isVisible = true
        }
    }

    private fun displayThreadList() {
        with(binding) {
            threadList.isVisible = true
            noMailLayout.root.isGone = true
        }
    }
}
