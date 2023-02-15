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
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.databinding.FragmentSearchBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.folder.ThreadListAdapter

class SearchFragment : Fragment() {

    private lateinit var binding: FragmentSearchBinding
    private val mainViewModel by activityViewModels<MainViewModel>()
    private val searchViewModel by viewModels<SearchViewModel>()
    private val navigationArgs by navArgs<SearchFragmentArgs>()
    private val localSettings by lazy { LocalSettings.getInstance(requireContext()) }

    private val threadAdapter by lazy {
        ThreadListAdapter(
            requireContext(),
            localSettings.threadDensity,
            null,
            mainViewModel.mergedContacts.value ?: emptyMap(),
            {}
        )
    }
    private val folders = mutableListOf<Folder?>()

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

    override fun onStop() {
        // TODO: localSettings.recentSearches = adapter.list
        super.onStop()
    }

    private fun updateUi(mode: VisibilityMode) = with(binding) {

        fun displayRecentSearches() {
            recentSearchesLayout.isVisible = true
            mailRecyclerView.isGone = true
            noResultsEmptyState.isGone = true
        }

        fun displayLoadingView() {
            // TODO
        }

        fun displaySearchResult(mode: VisibilityMode) {
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

        val popupMenu = createPopupMenu()

        folderDropDown.setOnClickListener {
            folderDropDown.isChecked =
                !folderDropDown.isChecked // Cancels the auto check // TODO : any better solution ? -> button ?
            popupMenu.show()
        }

        popupMenu.setOnMenuItemClickListener {
            folderDropDown.apply {
                text = it.title
                isChecked = it.itemId != 0
            }
            searchViewModel.selectFolder(folders[it.itemId])
            true
        }

        attachments.setOnCheckedChangeListener { _, isChecked ->
            searchViewModel.toggleFilter(ThreadFilter.ATTACHMENTS)
        }

        mutuallyExclusiveChipGroup.setOnCheckedStateChangeListener { chipGroup, _ ->
            when (chipGroup.checkedChipId) {
                R.id.read -> searchViewModel.toggleFilter(ThreadFilter.SEEN)
                R.id.unread -> searchViewModel.toggleFilter(ThreadFilter.UNSEEN)
                R.id.favorites -> searchViewModel.toggleFilter(ThreadFilter.STARRED)
                else -> searchViewModel.unSelectMutuallyExclusiveFilters()
            }
        }

        searchBar.searchTextInput.doOnTextChanged { text, _, _, _ ->
            searchViewModel.searchQuery(text.toString())
        }

        mailRecyclerView.adapter = threadAdapter
    }

    private fun createPopupMenu(): PopupMenu = with(binding) {
        val popupMenu = PopupMenu(context, folderDropDown)
        searchViewModel.folders.observe(viewLifecycleOwner) { realmFolders ->
            folders.clear()

            folders.add(null)
            popupMenu.menu.add(Menu.NONE, folders.lastIndex, Menu.NONE, getString(R.string.searchFilterFolder))

            realmFolders.forEach { folder ->
                folders.add(folder)
                popupMenu.menu.add(Menu.NONE, folders.lastIndex, Menu.NONE, folder.getLocalizedName(requireContext()))
            }

            // TODO : Cleanly separate elements and order them
            // TODO : popupMenu.menu.getItem(0).icon
        }
        return popupMenu
    }

    private fun observeFolders() {
        searchViewModel.folders.observe(viewLifecycleOwner) {
            // TODO: handle folders ui
        }
    }

    private fun observeSearchResults() {
        searchViewModel.searchResults.observe(viewLifecycleOwner) {
            // TODO: - handle visibility mode

            threadAdapter.dataSet = it
            threadAdapter.notifyDataSetChanged()
        }
    }

    enum class VisibilityMode {
        RECENT_SEARCHES, LOADING, NO_RESULTS, RESULTS
    }
}
