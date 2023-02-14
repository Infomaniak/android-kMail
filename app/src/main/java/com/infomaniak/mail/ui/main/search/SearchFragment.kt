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
package com.infomaniak.mail.ui.main.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings

class SearchFragment : Fragment() {

    private val searchViewModel by viewModels<SearchViewModel>()
    private val navigationArgs by navArgs<SearchFragmentArgs>()
    private val localSettings by lazy { LocalSettings.getInstance(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        searchViewModel.init(navigationArgs.currentFolderId)
        observeVisibilityModeUpdates()
        observeFolders()
        observeFilters()
        observeSearchResults()
    }

    override fun onStop() {
        // TODO: localSettings.recentSearches = adapter.list
        super.onStop()
    }

    private fun updateUi(mode: VisibilityMode) {

        fun displayRecentSearches() {
            // TODO
        }

        fun displayLoadingView() {
            // TODO
        }

        fun displaySearchResult(mode: VisibilityMode) {
            // TODO
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

    private fun observeFolders() {
        searchViewModel.folders.observe(viewLifecycleOwner) {
            // TODO: handle folders ui
        }
    }

    private fun observeFilters() {
        searchViewModel.selectedFilters.observe(viewLifecycleOwner) {
            // TODO: handle selected filters in ui
        }
    }

    private fun observeSearchResults() {
        searchViewModel.searchResults.observe(viewLifecycleOwner) {
            // TODO: - handle search results
            // TODO: - handle visibility mode
        }
    }

    enum class VisibilityMode {
        RECENT_SEARCHES, LOADING, NO_RESULTS, RESULTS
    }

}
