/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import androidx.annotation.ColorRes
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView.ListOrientation.DirectionFlag
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener.ScrollDirection
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener.ScrollState
import com.infomaniak.lib.core.utils.Utils
import com.infomaniak.lib.core.utils.hideKeyboard
import com.infomaniak.lib.core.utils.showKeyboard
import com.infomaniak.mail.MatomoMail.SEARCH_DELETE_NAME
import com.infomaniak.mail.MatomoMail.SEARCH_VALIDATE_NAME
import com.infomaniak.mail.MatomoMail.trackSearchEvent
import com.infomaniak.mail.MatomoMail.trackThreadListEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.databinding.FragmentSearchBinding
import com.infomaniak.mail.ui.main.folder.ThreadListAdapterCallbacks
import com.infomaniak.mail.ui.main.folder.TwoPaneFragment
import com.infomaniak.mail.ui.main.search.SearchFolderAdapter.SearchFolderElement
import com.infomaniak.mail.ui.main.thread.ThreadFragment
import com.infomaniak.mail.utils.RealmChangesBinding.Companion.bindResultsChangeToAdapter
import com.infomaniak.mail.utils.Utils.Shortcuts
import com.infomaniak.mail.utils.extensions.addStickyDateDecoration
import com.infomaniak.mail.utils.extensions.getLocalizedNameOrAllFolders
import com.infomaniak.mail.utils.extensions.handleEditorSearchAction
import com.infomaniak.mail.utils.extensions.setOnClearTextClickListener
import com.infomaniak.mail.utils.extensions.setSystemBarsColors
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchFragment : TwoPaneFragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView

    private val searchViewModel: SearchViewModel by viewModels()

    private val showLoadingTimer: CountDownTimer by lazy { Utils.createRefreshTimer(onTimerFinish = ::showRefreshLayout) }

    private val recentSearchAdapter by lazy {
        RecentSearchAdapter(
            searchQueries = localSettings.recentSearches.toMutableList(),
            onSearchQueryClicked = {
                trackSearchEvent("fromHistory")
                with(binding.searchBar.searchTextInput) {
                    setText(it)
                    setSelection(it.count())
                    hideKeyboard()
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

    private lateinit var searchAdapter: SearchFolderAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSearchBinding.inflate(inflater, container, false).also { _binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setSystemBarsColors(statusBarColor = R.color.backgroundColor)

        ShortcutManagerCompat.reportShortcutUsed(requireContext(), Shortcuts.SEARCH.id)

        searchViewModel.executePendingSearch()

        setupAdapter()
        setupListeners()

        setFoldersDropdownUi()
        setAttachmentsUi()
        setMutuallyExclusiveChipGroupUi()
        setSearchBarUi()
        setMessagesUi()
        setRecentSearchesUi()

        observeVisibilityModeUpdates()
        observeSearchResults()
        observeHistory()
    }

    override fun onStop() {
        searchViewModel.cancelSearch()
        super.onStop()
    }

    override fun onDestroyView() {
        showLoadingTimer.cancel()
        super.onDestroyView()
        _binding = null
    }

    @ColorRes
    override fun getStatusBarColor(): Int = R.color.backgroundColor

    override fun getLeftPane(): View? = _binding?.threadsCoordinatorLayout

    override fun getRightPane(): FragmentContainerView? = _binding?.threadHostFragment

    override fun getAnchor(): View? {
        return if (isOnlyLeftShown()) {
            null
        } else {
            _binding?.threadHostFragment?.getFragment<ThreadFragment?>()?.getAnchor()
        }
    }

    private fun setupAdapter() {
        threadListAdapter(
            folderRole = null,
            isFolderNameVisible = true,
            callbacks = object : ThreadListAdapterCallbacks {

                override var onSwipeFinished: (() -> Unit)? = null

                override var onThreadClicked: (Thread) -> Unit = { thread ->
                    with(searchViewModel) {
                        if (!isLengthTooShort(currentSearchQuery)) history.value = currentSearchQuery
                        binding.searchBar.searchTextInput.apply {
                            hideKeyboard()
                            clearFocus()
                        }
                        navigateToThread(thread)
                    }
                }

                override var onFlushClicked: ((String) -> Unit)? = null

                override var onLoadMoreClicked: () -> Unit = {
                    trackThreadListEvent("loadMore")
                    mainViewModel.getOnePageOfOldMessages()
                }

                override var onPositionClickedChanged: (Int, Int) -> Unit = ::updateAutoAdvanceNaturalThread

                override var deleteThreadInRealm: (String) -> Unit = { threadUid -> mainViewModel.deleteThreadInRealm(threadUid) }
            },
        )

        threadListAdapter.stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    private fun setupListeners() = with(binding) {
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        swipeRefreshLayout.setOnRefreshListener { searchViewModel.refreshSearch() }
    }

    private fun setFoldersDropdownUi() {
        val popupMenu = createPopupMenu()
        binding.folderDropDown.setOnClickListener { popupMenu.show() }
    }

    private fun createPopupMenu(): ListPopupWindow {
        val popupMenu = ListPopupWindow(requireContext()).apply {
            isModal = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            anchorView = binding.folderDropDown
            width = resources.getDimensionPixelSize(R.dimen.maxSearchChipWidth)
        }

        searchViewModel.foldersLive.observe(viewLifecycleOwner) { allFolders ->

            val folders = allFolders
                .addDividerBeforeFirstCustomFolder(dividerType = SearchFolderElement.DIVIDER)
                .toMutableList()
                .apply { add(0, SearchFolderElement.ALL_FOLDERS) }
                .toList()

            searchAdapter = SearchFolderAdapter(folders) { folder, title ->
                onFolderSelected(folder, title)
                popupMenu.dismiss()
            }

            popupMenu.setAdapter(searchAdapter)

            updateFolderDropDownUi(
                folder = searchViewModel.filterFolder,
                title = requireContext().getLocalizedNameOrAllFolders(searchViewModel.filterFolder),
            )
        }

        return popupMenu
    }

    private fun onFolderSelected(folder: Folder?, title: String) {
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

        searchAdapter.updateVisuallySelectedFolder(folder)
    }

    private fun setAttachmentsUi() = with(searchViewModel) {
        binding.attachments.setOnCheckedChangeListener { _, isChecked ->

            setFilter(ThreadFilter.ATTACHMENTS, isChecked)
        }
    }

    private fun setMutuallyExclusiveChipGroupUi() = with(searchViewModel) {
        binding.mutuallyExclusiveChipGroup.setOnCheckedStateChangeListener { chipGroup, _ ->

            when (chipGroup.checkedChipId) {
                R.id.read -> setFilter(ThreadFilter.SEEN)
                R.id.unread -> setFilter(ThreadFilter.UNSEEN)
                R.id.favorites -> setFilter(ThreadFilter.STARRED)
                else -> unselectMutuallyExclusiveFilters()
            }
        }
    }

    private fun setSearchBarUi() = with(binding.searchBar) {
        searchInputLayout.setOnClearTextClickListener { trackSearchEvent(SEARCH_DELETE_NAME) }

        searchTextInput.apply {
            showKeyboard()

            doOnTextChanged { text, _, _, _ ->
                val newQuery = text.toString()
                if (searchViewModel.currentSearchQuery != newQuery) searchViewModel.searchQuery(newQuery)
            }

            handleEditorSearchAction { query ->
                searchViewModel.searchQuery(query, saveInHistory = true)
                context.trackSearchEvent(SEARCH_VALIDATE_NAME)
            }
        }
    }

    private fun setMessagesUi() {
        binding.mailRecyclerView.apply {
            adapter = threadListAdapter

            disableDragDirection(DirectionFlag.UP)
            disableDragDirection(DirectionFlag.DOWN)
            disableDragDirection(DirectionFlag.LEFT)
            disableDragDirection(DirectionFlag.RIGHT)

            disableSwipeDirection(DirectionFlag.LEFT)
            disableSwipeDirection(DirectionFlag.RIGHT)

            addStickyDateDecoration(threadListAdapter, localSettings.threadDensity)
            setPagination()
        }
    }

    private fun DragDropSwipeRecyclerView.setPagination() = with(binding) {
        scrollListener = object : OnListScrollListener {

            override fun onListScrollStateChanged(scrollState: ScrollState) = Unit

            override fun onListScrolled(
                scrollDirection: ScrollDirection,
                distance: Int,
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

    private fun setRecentSearchesUi() {
        binding.recentSearchesRecyclerView.adapter = recentSearchAdapter
        updateHistoryEmptyStateVisibility(recentSearchAdapter.getSearchQueries().isNotEmpty())
    }

    private fun observeVisibilityModeUpdates() {
        searchViewModel.visibilityMode.observe(viewLifecycleOwner, ::updateUi)
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

    private fun observeSearchResults() {
        searchViewModel.searchResults.bindResultsChangeToAdapter(viewLifecycleOwner, threadListAdapter)
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

    private fun showRefreshLayout() {
        binding.swipeRefreshLayout.isRefreshing = true
    }

    enum class VisibilityMode {
        RECENT_SEARCHES,
        LOADING,
        NO_RESULTS,
        RESULTS,
    }

    companion object {
        private const val PAGINATION_TRIGGER_OFFSET = 15
    }
}
