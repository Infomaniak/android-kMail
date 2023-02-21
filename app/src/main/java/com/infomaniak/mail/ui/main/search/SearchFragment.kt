/*
 * Infomaniak kMail - Android
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
package com.infomaniak.mail.ui.main.search

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListPopupWindow
import android.widget.PopupWindow
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.utils.Utils
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.databinding.FragmentSearchBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.folder.ThreadListAdapter
import com.infomaniak.mail.utils.addStickyDateDecoration
import com.infomaniak.mail.utils.getMenuFolders

class SearchFragment : Fragment() {

    private lateinit var binding: FragmentSearchBinding
    private val mainViewModel by activityViewModels<MainViewModel>()
    private val searchViewModel by viewModels<SearchViewModel>()
    private val navigationArgs by navArgs<SearchFragmentArgs>()
    private val localSettings by lazy { LocalSettings.getInstance(requireContext()) }

    private val showLoadingTimer: CountDownTimer by lazy {
        Utils.createRefreshTimer { binding.swipeRefreshLayout.isRefreshing = true }
    }

    private val threadListAdapter by lazy {
        ThreadListAdapter(
            requireContext(),
            localSettings.threadDensity,
            null,
            mainViewModel.mergedContacts.value ?: emptyMap(),
            {}
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSearchBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        searchViewModel.init(navigationArgs.dummyFolderId)
        setUi()
        observeVisibilityModeUpdates()
        // observeFolders()
        observeSearchResults()
    }

    override fun onStop() = with(binding) {
        // TODO: localSettings.recentSearches = adapter.list
        searchViewModel.apply {
            previousSearch = searchBar.searchTextInput.text.toString()
            previousAttachments = attachments.isChecked
            previousMutuallyExclusiveChips = mutuallyExclusiveChipGroup.checkedChipId
        }
        super.onStop()
    }

    private fun updateUi(mode: VisibilityMode) = with(binding) {

        fun displayRecentSearches() {
            showLoadingTimer.cancel()
            swipeRefreshLayout.isRefreshing = false

            recentSearchesLayout.isVisible = true
            mailRecyclerView.isGone = true
            noResultsEmptyState.isGone = true
        }

        fun displayLoadingView() {
            showLoadingTimer.start()
        }

        fun displaySearchResult(mode: VisibilityMode) {
            showLoadingTimer.cancel()
            swipeRefreshLayout.isRefreshing = false

            recentSearchesLayout.isGone = true
            mailRecyclerView.isVisible = mode == VisibilityMode.RESULTS
            noResultsEmptyState.isGone = mode == VisibilityMode.RESULTS
        }


        when (mode) {
            VisibilityMode.RECENT_SEARCHES -> displayRecentSearches()
            VisibilityMode.LOADING -> displayLoadingView()
            VisibilityMode.NO_RESULTS, VisibilityMode.RESULTS -> displaySearchResult(mode)
        }
    }

    private fun observeVisibilityModeUpdates() {
        searchViewModel.visibilityMode.observe(viewLifecycleOwner) { updateUi(it) }
    }

    private fun setUi() = with(binding) {
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        swipeRefreshLayout.isEnabled = false

        val popupMenu = createPopupMenu()

        folderDropDown.setOnClickListener {
            // TODO : any better solution ? -> button ?
            folderDropDown.isChecked = !folderDropDown.isChecked // Cancels the auto check
            popupMenu.show()
        }

        attachments.setOnCheckedChangeListener { _, _ ->
            if (searchViewModel.previousAttachments != null) {
                searchViewModel.previousAttachments = null
                return@setOnCheckedChangeListener
            }

            searchViewModel.toggleFilter(ThreadFilter.ATTACHMENTS)
        }

        mutuallyExclusiveChipGroup.setOnCheckedStateChangeListener { chipGroup, _ ->
            if (searchViewModel.previousMutuallyExclusiveChips != null) {
                searchViewModel.previousMutuallyExclusiveChips = null
                return@setOnCheckedStateChangeListener
            }

            when (chipGroup.checkedChipId) {
                R.id.read -> searchViewModel.toggleFilter(ThreadFilter.SEEN)
                R.id.unread -> searchViewModel.toggleFilter(ThreadFilter.UNSEEN)
                R.id.favorites -> searchViewModel.toggleFilter(ThreadFilter.STARRED)
                else -> searchViewModel.unSelectMutuallyExclusiveFilters()
            }
        }

        searchBar.searchTextInput.doOnTextChanged { text, _, _, _ ->
            if (searchViewModel.previousSearch != null) {
                searchViewModel.previousSearch = null
                return@doOnTextChanged
            }
            searchViewModel.searchQuery(text.toString())
        }

        mailRecyclerView.apply {
            adapter = threadListAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
                onThreadClicked = { thread -> navigateToThread(thread, mainViewModel) }
            }
            addStickyDateDecoration(threadListAdapter)
        }
    }

    private fun createPopupMenu(): ListPopupWindow = with(binding) {
        val popupMenu = ListPopupWindow(requireContext()).apply {
            isModal = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            anchorView = folderDropDown
            width = resources.getDimensionPixelSize(R.dimen.maxSearchChipWidth)
        }
        searchViewModel.folders.observe(viewLifecycleOwner) { realmFolders ->
            val folders = mutableListOf<Folder?>(null)

            val (common, custom) = realmFolders.getMenuFolders()
            folders.addAll(common)
            folders.addAll(custom)

            popupMenu.setAdapter(
                SearchFolderAdapter(folders) { folder, title ->
                    onFolderSelected(folder, title)
                    popupMenu.dismiss()
                }
            )

            // TODO : Cleanly separate elements ?
        }
        return popupMenu
    }

    private fun onFolderSelected(folder: Folder?, title: String) = with(binding) {
        folderDropDown.apply {
            text = title
            isChecked = folder != null
        }
        searchViewModel.selectFolder(folder)
    }

    private fun observeFolders() {
        searchViewModel.folders.observe(viewLifecycleOwner) {
            // TODO: handle folders ui
        }
    }

    private fun observeSearchResults() {
        searchViewModel.searchResults.observe(viewLifecycleOwner) {
            // TODO: - handle visibility mode

            threadListAdapter.updateList(it)
            threadListAdapter.notifyDataSetChanged()
        }
    }

    enum class VisibilityMode {
        RECENT_SEARCHES, LOADING, NO_RESULTS, RESULTS
    }
}
