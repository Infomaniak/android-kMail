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
import android.view.inputmethod.EditorInfo
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView.ListOrientation.DirectionFlag
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener.ScrollDirection
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener.ScrollState
import com.infomaniak.lib.core.utils.Utils
import com.infomaniak.lib.core.utils.showKeyboard
import com.infomaniak.mail.MatomoMail.trackSearchEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.databinding.FragmentSearchBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.folder.ThreadListAdapter
import com.infomaniak.mail.ui.main.search.SearchFolderAdapter.SearchFolderElement
import com.infomaniak.mail.utils.addStickyDateDecoration
import com.infomaniak.mail.utils.getLocalizedNameOrAllFolders
import com.infomaniak.mail.utils.navigateToThread

class SearchFragment : Fragment() {

    private lateinit var binding: FragmentSearchBinding
    private val navigationArgs by navArgs<SearchFragmentArgs>()
    private val mainViewModel by activityViewModels<MainViewModel>()
    private val searchViewModel by viewModels<SearchViewModel>()

    private val localSettings by lazy { LocalSettings.getInstance(requireContext()) }

    private val showLoadingTimer: CountDownTimer by lazy {
        Utils.createRefreshTimer { binding.swipeRefreshLayout.isRefreshing = true }
    }

    private val threadListAdapter by lazy {
        ThreadListAdapter(
            context = requireContext(),
            threadDensity = localSettings.threadDensity,
            folderRole = null,
            contacts = mainViewModel.mergedContacts.value ?: emptyMap(),
            onSwipeFinished = {},
        )
    }

    private val recentSearchAdapter by lazy {
        RecentSearchAdapter(
            searchQueries = localSettings.recentSearches.toMutableList(),
            onSearchQueryClicked = {
                trackSearchEvent("fromHistory")
                with(binding.searchBar.searchTextInput) {
                    setText(it)
                    requestFocus()
                    setSelection(it.count())
                }
            },
            onSearchQueryDeleted = { history ->
                trackSearchEvent("deleteFromHistory")
                val isThereHistory = history.isNotEmpty()
                localSettings.recentSearches = history
                updateHistoryEmptyStateVisibility(isThereHistory)
            },
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
        observeSearchResults()
        observeHistory()
    }

    override fun onStop() = with(binding) {
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
            swipeRefreshLayout.apply {
                isRefreshing = false
                isEnabled = false
            }

            recentSearchesLayout.isVisible = true
            recentSearchesRecyclerView.scrollToPosition(0)
            mailRecyclerView.isGone = true
            noResultsEmptyState.isGone = true
        }

        fun displayLoadingView() {
            showLoadingTimer.start()
        }

        fun displaySearchResult(mode: VisibilityMode) {
            showLoadingTimer.cancel()
            swipeRefreshLayout.apply {
                isRefreshing = false
                isEnabled = true
            }

            recentSearchesLayout.isGone = true
            val thereAreResults = mode == VisibilityMode.RESULTS
            mailRecyclerView.isVisible = thereAreResults
            noResultsEmptyState.isGone = thereAreResults
        }

        when (mode) {
            VisibilityMode.RECENT_SEARCHES -> displayRecentSearches()
            VisibilityMode.LOADING -> displayLoadingView()
            VisibilityMode.NO_RESULTS, VisibilityMode.RESULTS -> displaySearchResult(mode)
        }
    }

    private fun observeVisibilityModeUpdates() {
        searchViewModel.visibilityMode.observe(viewLifecycleOwner, ::updateUi)
    }

    private fun setUi() = with(binding) {
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        swipeRefreshLayout.setOnRefreshListener { searchViewModel.refreshSearch() }

        val popupMenu = createPopupMenu()

        folderDropDown.setOnClickListener { popupMenu.show() }
        updateFolderDropDownUi(
            folder = searchViewModel.selectedFolder,
            title = requireContext().getLocalizedNameOrAllFolders(searchViewModel.selectedFolder),
        )

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
                else -> searchViewModel.unselectMutuallyExclusiveFilters()
            }
        }

        with(searchBar) {
            searchInputLayout.setEndIconOnClickListener {
                searchTextInput.text?.clear()
                trackSearchEvent("deleteSearch")
            }
            searchTextInput.apply {
                showKeyboard()

                doOnTextChanged { text, _, _, _ ->
                    if (searchViewModel.previousSearch != null) {
                        searchViewModel.previousSearch = null
                        return@doOnTextChanged
                    }
                    searchViewModel.searchQuery(text.toString())
                }

                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_SEARCH && !text.isNullOrBlank()) {
                        trackSearchEvent("validateSearch")
                        searchViewModel.searchQuery(text.toString(), saveInHistory = true)
                    }
                    true // Keep keyboard open
                }
            }
        }

        mailRecyclerView.apply {
            adapter = threadListAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
                onThreadClicked = { thread ->
                    searchViewModel.history.value = searchViewModel.searchQuery
                    navigateToThread(thread, mainViewModel)
                }
            }
            disableDragDirection(DirectionFlag.DOWN)
            disableDragDirection(DirectionFlag.LEFT)
            disableDragDirection(DirectionFlag.RIGHT)
            disableDragDirection(DirectionFlag.UP)
            disableSwipeDirection(DirectionFlag.LEFT)
            disableSwipeDirection(DirectionFlag.RIGHT)
            addStickyDateDecoration(threadListAdapter, localSettings.threadDensity)
            setPagination()
        }

        recentSearchesRecyclerView.adapter = recentSearchAdapter
        updateHistoryEmptyStateVisibility(recentSearchAdapter.getSearchQueries().isNotEmpty())
    }

    private fun createPopupMenu(): ListPopupWindow = with(binding) {
        val popupMenu = ListPopupWindow(requireContext()).apply {
            isModal = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            anchorView = folderDropDown
            width = resources.getDimensionPixelSize(R.dimen.maxSearchChipWidth)
        }

        mainViewModel.currentFoldersLive.value?.let { (defaultFolders, customFolders) ->
            val folders = defaultFolders.toMutableList<Any>().apply {
                add(0, SearchFolderElement.ALL_FOLDERS)
                add(SearchFolderElement.SEPARATOR)
                addAll(customFolders)
            }.toList()

            popupMenu.setAdapter(
                SearchFolderAdapter(folders) { folder, title ->
                    onFolderSelected(folder, title)
                    popupMenu.dismiss()
                }
            )
        }

        popupMenu
    }

    private fun onFolderSelected(folder: Folder?, title: String) = with(binding) {
        updateFolderDropDownUi(folder, title)
        searchViewModel.selectFolder(folder)
        trackSearchEvent(ThreadFilter.FOLDER.matomoValue, folder != null)
    }

    private fun updateFolderDropDownUi(folder: Folder?, title: String) = with(binding) {
        val drawable = if (folder != null) R.drawable.ic_check_sharp else 0
        folderDropDown.apply {
            isChecked = folder != null
            setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, 0, R.drawable.ic_chevron_down, 0)
            text = title
        }
    }

    private fun DragDropSwipeRecyclerView.setPagination() = with(binding) {
        scrollListener = object : OnListScrollListener {
            override fun onListScrollStateChanged(scrollState: ScrollState) = Unit

            override fun onListScrolled(
                scrollDirection: ScrollDirection,
                distance: Int
            ) = with(mailRecyclerView.layoutManager!!) {
                if (scrollDirection == ScrollDirection.DOWN) {
                    val visibleItemCount = childCount
                    val totalItemCount = itemCount
                    val pastVisibleItems = (this as? LinearLayoutManager)
                        ?.findFirstVisibleItemPosition()
                        ?.plus(PAGINATION_TRIGGER_OFFSET)!!
                    val isLastElement = (visibleItemCount + pastVisibleItems) >= totalItemCount

                    if (isLastElement && searchViewModel.visibilityMode.value != VisibilityMode.LOADING) {
                        searchViewModel.nextPage()
                    }
                }
            }
        }
    }

    private fun observeSearchResults() {
        searchViewModel.searchResults.observe(viewLifecycleOwner) {
            threadListAdapter.updateList(it)
            threadListAdapter.notifyDataSetChanged()
        }
    }

    private fun observeHistory() {
        searchViewModel.history.observe(viewLifecycleOwner) {
            val hasInsertedSuccessfully = recentSearchAdapter.addSearchQuery(it)
            if (hasInsertedSuccessfully) localSettings.recentSearches = recentSearchAdapter.getSearchQueries()
            updateHistoryEmptyStateVisibility(true)
        }
    }

    private fun updateHistoryEmptyStateVisibility(isThereHistory: Boolean) = with(binding) {
        recentSearches.isVisible = isThereHistory
        noHistory.isGone = isThereHistory
    }

    enum class VisibilityMode {
        RECENT_SEARCHES, LOADING, NO_RESULTS, RESULTS
    }

    private companion object {
        const val PAGINATION_TRIGGER_OFFSET = 15
    }
}
