/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.menu

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.utils.hideKeyboard
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.MatomoMail.SEARCH_DELETE_NAME
import com.infomaniak.mail.MatomoMail.SEARCH_VALIDATE_NAME
import com.infomaniak.mail.MatomoMail.trackCreateFolderEvent
import com.infomaniak.mail.MatomoMail.trackMoveSearchEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.databinding.FragmentMoveBinding
import com.infomaniak.mail.utils.handleEditorSearchAction
import com.infomaniak.mail.utils.setOnClearTextClickListener
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MoveFragment : MenuFoldersFragment() {

    private var binding: FragmentMoveBinding by safeBinding()
    private val navigationArgs: MoveFragmentArgs by navArgs()
    private val moveViewModel: MoveViewModel by viewModels()

    @Inject
    lateinit var searchFolderAdapter: FolderAdapter

    private val searchResultsAdapter by lazy {
        searchFolderAdapter(isInMenuDrawer, shouldIndent = false, onFolderClicked = ::onFolderSelected)
    }

    private var hasAlreadyTrackedFolderSearch = false

    private var currentFolderId: String? = null

    override val defaultFoldersList: RecyclerView by lazy { binding.defaultFoldersList }
    override val customFoldersList: RecyclerView by lazy { binding.customFoldersList }

    override val isInMenuDrawer = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMoveBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        setupFolderAdapters()
        setupCreateFolderDialog()
        observeNewFolderCreation()
        observeSearchResults()
    }

    override fun onStop() {
        binding.searchTextInput.hideKeyboard()
        moveViewModel.cancelSearch()
        super.onStop()
    }

    private fun setupListeners() = with(binding) {
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        iconAddFolder.setOnClickListener {
            trackCreateFolderEvent("fromMove")
            inputDialog.show(
                title = R.string.newFolderDialogTitle,
                hint = R.string.newFolderDialogHint,
                confirmButtonText = R.string.newFolderDialogMovePositiveButton,
            )
        }
    }

    private fun setupCreateFolderDialog() {
        inputDialog.setCallbacks(
            onPositiveButtonClicked = { folderName ->
                trackCreateFolderEvent("confirm")
                mainViewModel.moveToNewFolder(folderName, navigationArgs.threadsUids, navigationArgs.messageUid)
            },
            onErrorCheck = ::checkForFolderCreationErrors,
        )
    }

    private fun setupFolderAdapters() {
        moveViewModel.getFolderIdAndCustomFolders().observe(viewLifecycleOwner) { (folderId, customFolders) ->

            currentFolderId = folderId

            val defaultFoldersWithoutDraft = mainViewModel.currentDefaultFoldersLive.value!!.let { folders ->
                folders.filterNot { it.role == FolderRole.DRAFT }
            }

            defaultFoldersAdapter.setFolders(defaultFoldersWithoutDraft, folderId)
            customFoldersAdapter.setFolders(customFolders, folderId)
            setSearchBarUi(allFolders = defaultFoldersWithoutDraft + customFolders)
        }
    }

    private fun observeNewFolderCreation() = with(mainViewModel) {
        newFolderResultTrigger.observe(viewLifecycleOwner) { inputDialog.resetLoadingAndDismiss() }
        isMovedToNewFolder.observe(viewLifecycleOwner) { isFolderCreated ->
            if (isFolderCreated) findNavController().popBackStack()
        }
    }

    override fun onFolderSelected(folderId: String): Unit = with(navigationArgs) {
        mainViewModel.moveThreadsOrMessageTo(folderId, threadsUids, messageUid)
        findNavController().popBackStack()
    }

    override fun onFolderCollapse(folderId: String, shouldCollapse: Boolean) = Unit
    override fun onCollapseTransition() = Unit

    private fun setSearchBarUi(allFolders: List<Folder>) = with(binding) {
        searchResultsList.adapter = searchResultsAdapter

        searchInputLayout.setOnClearTextClickListener { trackMoveSearchEvent(SEARCH_DELETE_NAME) }

        searchTextInput.apply {
            toggleFolderListsVisibility(!text.isNullOrBlank())
            doOnTextChanged { newQuery, _, _, _ ->
                toggleFolderListsVisibility(!newQuery.isNullOrBlank())
                if (newQuery?.isNotBlank() == true) {
                    moveViewModel.filterFolders(newQuery.toString(), allFolders, shouldDebounce = true)
                }

                if (!hasAlreadyTrackedFolderSearch) {
                    trackMoveSearchEvent("executeSearch")
                    hasAlreadyTrackedFolderSearch = true
                }
            }

            handleEditorSearchAction { query ->
                moveViewModel.filterFolders(query, allFolders, shouldDebounce = false)
                trackMoveSearchEvent(SEARCH_VALIDATE_NAME)
            }
        }
    }

    private fun toggleFolderListsVisibility(isSearching: Boolean) = with(binding) {
        searchResultsList.isVisible = isSearching
        moveFoldersLists.isGone = isSearching
    }

    private fun observeSearchResults() {
        moveViewModel.filterResults.observe(viewLifecycleOwner) { folders ->
            searchResultsAdapter.setFolders(folders, currentFolderId)
        }
    }
}
