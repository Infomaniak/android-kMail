/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import com.infomaniak.core.legacy.utils.Utils
import com.infomaniak.core.legacy.utils.hideKeyboard
import com.infomaniak.core.legacy.utils.safeNavigate
import com.infomaniak.core.legacy.utils.setMargins
import com.infomaniak.core.legacy.utils.showKeyboard
import com.infomaniak.dragdropswiperecyclerview.DragDropSwipeRecyclerView
import com.infomaniak.dragdropswiperecyclerview.DragDropSwipeRecyclerView.ListOrientation.DirectionFlag
import com.infomaniak.dragdropswiperecyclerview.listener.OnListScrollListener
import com.infomaniak.dragdropswiperecyclerview.listener.OnListScrollListener.ScrollDirection
import com.infomaniak.dragdropswiperecyclerview.listener.OnListScrollListener.ScrollState
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackSearchEvent
import com.infomaniak.mail.MatomoMail.trackThreadListEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.SwipeAction
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.databinding.FragmentSearchBinding
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.main.folder.MultiSelectionBinding
import com.infomaniak.mail.ui.main.folder.MultiSelectionHost
import com.infomaniak.mail.ui.main.folder.MultiSelectionListener
import com.infomaniak.mail.ui.main.folder.ThreadListAdapterCallbacks
import com.infomaniak.mail.ui.main.folder.ThreadListMultiSelection
import com.infomaniak.mail.ui.main.folder.TwoPaneFragment
import com.infomaniak.mail.ui.main.folderPicker.FolderPickerAction
import com.infomaniak.mail.ui.main.thread.ThreadFragment
import com.infomaniak.mail.utils.FolderRoleUtils
import com.infomaniak.mail.utils.RealmChangesBinding.Companion.bindResultsChangeToAdapter
import com.infomaniak.mail.utils.Utils.Shortcuts
import com.infomaniak.mail.utils.extensions.addStickyDateDecoration
import com.infomaniak.mail.utils.extensions.animatedNavigation
import com.infomaniak.mail.utils.extensions.applySideAndBottomSystemInsets
import com.infomaniak.mail.utils.extensions.applyWindowInsetsListener
import com.infomaniak.mail.utils.extensions.getLocalizedNameOrAllFolders
import com.infomaniak.mail.utils.extensions.handleEditorSearchAction
import com.infomaniak.mail.utils.extensions.safeArea
import com.infomaniak.mail.utils.extensions.setOnClearTextClickListener
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SearchFragment : TwoPaneFragment(), MultiSelectionHost {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView

    @Inject
    override lateinit var folderRoleUtils: FolderRoleUtils
    @Inject
    override lateinit var descriptionDialog: DescriptionAlertDialog

    private val searchViewModel: SearchViewModel by activityViewModels()

    override val substituteClassName: String = javaClass.name

    private val showLoadingTimer: CountDownTimer by lazy { Utils.createRefreshTimer(onTimerFinish = ::showRefreshLayout) }
    private val threadListMultiSelection by lazy { ThreadListMultiSelection() }
    private val recentSearchAdapter by lazy {
        RecentSearchAdapter(
            searchQueries = localSettings.recentSearches.toMutableList(),
            onSearchQueryClicked = {
                trackSearchEvent(MatomoName.FromHistory)
                with(binding.searchBar.searchTextInput) {
                    setText(it)
                    setSelection(it.count())
                    hideKeyboard()
                }
            },
            onSearchQueryDeleted = { history ->
                trackSearchEvent(MatomoName.DeleteFromHistory)
                val isThereHistory = history.isNotEmpty()
                localSettings.recentSearches = history
                updateHistoryEmptyStateVisibility(isThereHistory)
            },
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSearchBinding.inflate(inflater, container, false).also { _binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectCurrentFolder()

        handleEdgeToEdge()

        ShortcutManagerCompat.reportShortcutUsed(requireContext(), Shortcuts.SEARCH.id)

        searchViewModel.executePendingSearch()

        setupAdapter()
        setupListeners()

        threadListMultiSelection.initMultiSelection(
            mainViewModel = mainViewModel,
            host = this,
            activity = (requireActivity() as MainActivity),
            unlockSwipeActionsIfSet = ::unlockSwipeActionsIfSet,
            localSettings = localSettings,
        )

        setAllFoldersButtonListener()
        setAttachmentsUi()
        setMutuallyExclusiveChipGroupUi()
        setSearchBarUi()
        setMessagesUi()
        setRecentSearchesUi()

        observeVisibilityModeUpdates()
        observeSearchResults()
        observeHistory()
    }

    private fun handleEdgeToEdge(): Unit = with(binding) {
        applyWindowInsetsListener(shouldConsume = false) { _, insets ->
            toolbar.apply {
                applySideAndBottomSystemInsets(insets, withBottom = false)
                // Apply a margin instead of a padding so the icon and the search bar are aligned
                setMargins(top = insets.safeArea().top)
            }
            chipsList.applySideAndBottomSystemInsets(insets, withBottom = false)
            swipeRefreshLayout.applySideAndBottomSystemInsets(insets, withBottom = false)

            with(insets.safeArea()) {
                recentSearchesRecyclerView.updatePaddingRelative(bottom = bottom)
                mailRecyclerView.updatePaddingRelative(bottom = bottom)
            }
        }
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

    override fun getLeftPane(): View? = _binding?.threadsCoordinatorLayout

    override fun getRightPane(): FragmentContainerView? = _binding?.threadHostFragment

    override fun getAnchor(): View? {
        return if (isOnlyLeftShown()) {
            null
        } else {
            _binding?.threadHostFragment?.getFragment<ThreadFragment?>()?.getAnchor()
        }
    }

    override val multiSelectionBinding: MultiSelectionBinding
        get() = object : MultiSelectionBinding {
            override val quickActionBar get() = binding.quickActionBar
            override val multiselectToolbar get() = binding.multiselectToolbar
            override val toolbarLayout get() = binding.multiselectToolbar.toolbar
            override val toolbar get() = binding.toolbar
            override val threadsList get() = binding.mailRecyclerView
            override val newMessageFab get() = null
            override val unreadCountChip get() = null
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
                    trackThreadListEvent(MatomoName.LoadMore)
                    mainViewModel.getOnePageOfOldMessages()
                }

                override var onPositionClickedChanged: (Int, Int) -> Unit = ::updateAutoAdvanceNaturalThread

                override var deleteThreadInRealm: (String) -> Unit = { threadUid -> mainViewModel.deleteThreadInRealm(threadUid) }

                override val getFeatureFlags: () -> Mailbox.FeatureFlagSet? = { mainViewModel.featureFlagsLive.value }
            },
            multiSelection = object : MultiSelectionListener<Thread> {
                override var isEnabled by mainViewModel::isMultiSelectOn
                override val selectedItems by mainViewModel::selectedThreads
                override val publishSelectedItems = mainViewModel::publishSelectedItems
            },
        )

        threadListAdapter.stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    private fun setupListeners() = with(binding) {
        toolbar.setNavigationOnClickListener {
            searchViewModel.resetFolderFilter()
            findNavController().popBackStack()
        }
        swipeRefreshLayout.setOnRefreshListener { searchViewModel.refreshSearch() }
    }

    private fun setAllFoldersButtonListener() {
        binding.allFoldersButton.setOnClickListener {
            val navController = findNavController()
            navController.animatedNavigation(
                directions = SearchFragmentDirections.actionSearchFragmentToFolderPickerFragment(
                    threadsUids = emptyArray(),
                    action = FolderPickerAction.SEARCH,
                    sourceFolderId = searchViewModel.filterFolder?.id
                ),
                currentClassName = javaClass.name,
            )
        }
    }

    private fun selectCurrentFolder() {
        updateAllFoldersButtonUi()

        if (searchViewModel.filterFolder == null) {
            val currentFolder = mainViewModel.currentFolder.value
            if (currentFolder?.role != Folder.FolderRole.INBOX) {
                searchViewModel.selectFolder(currentFolder)
            }
        }
        trackSearchEvent(ThreadFilter.FOLDER.matomoName, true)
    }

    private fun updateAllFoldersButtonUi() = with(binding) {
        val folder = searchViewModel.filterFolder
        val title = requireContext().getLocalizedNameOrAllFolders(folder)
        val drawable = if (folder != null) R.drawable.ic_check_sharp else 0

        allFoldersButton.apply {
            isChecked = folder != null
            setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, 0, R.drawable.ic_chevron_down, 0)
            text = title
        }
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
        searchInputLayout.setOnClearTextClickListener { trackSearchEvent(MatomoName.DeleteSearch) }

        searchTextInput.apply {
            showKeyboard()

            doOnTextChanged { text, _, _, _ ->
                val newQuery = text.toString()
                if (searchViewModel.currentSearchQuery != newQuery) searchViewModel.searchQuery(newQuery)
            }

            handleEditorSearchAction { query ->
                searchViewModel.searchQuery(query, saveInHistory = true)
                trackSearchEvent(MatomoName.ValidateSearch)
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
            updateAllFoldersButtonUi()
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

    override fun safeNavigation(directions: NavDirections) {
        safeNavigate(directions)
    }

    override fun disableSwipeDirection(direction: DirectionFlag) {
        binding.mailRecyclerView.disableSwipeDirection(direction)
    }

    override fun unlockSwipeActionsIfSet() = with(binding.mailRecyclerView) {
        val isMultiSelectClosed = mainViewModel.isMultiSelectOn.not()

        val isLeftSet = localSettings.swipeLeft != SwipeAction.NONE
        val isLeftEnabled = isLeftSet && isMultiSelectClosed
        if (isLeftEnabled) enableSwipeDirection(DirectionFlag.LEFT) else disableSwipeDirection(DirectionFlag.LEFT)

        val isRightSet = localSettings.swipeRight != SwipeAction.NONE
        val isRightEnabled = isRightSet && isMultiSelectClosed
        if (isRightEnabled) enableSwipeDirection(DirectionFlag.RIGHT) else disableSwipeDirection(DirectionFlag.RIGHT)
    }

    override fun directionToThreadActionsBottomSheetDialog(
        threadUid: String,
        shouldLoadDistantResources: Boolean,
        shouldCloseMultiSelection: Boolean
    ): NavDirections {
        return SearchFragmentDirections.actionThreadListFragmentToThreadActionsBottomSheetDialog(
            threadUid,
            shouldLoadDistantResources,
            shouldCloseMultiSelection
        )
    }

    override fun directionsToMultiSelectBottomSheetDialog(): NavDirections {
        return SearchFragmentDirections.actionThreadListFragmentToMultiSelectBottomSheetDialog()
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
