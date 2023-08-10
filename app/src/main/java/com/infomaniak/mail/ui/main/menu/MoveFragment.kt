/*
 * Infomaniak ikMail - Android
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
import com.infomaniak.mail.MatomoMail.SEARCH_DELETE_NAME
import com.infomaniak.mail.MatomoMail.SEARCH_VALIDATE_NAME
import com.infomaniak.mail.MatomoMail.trackCreateFolderEvent
import com.infomaniak.mail.MatomoMail.trackMoveSearchEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.databinding.FragmentMoveBinding
import com.infomaniak.mail.utils.createInputDialog
import com.infomaniak.mail.utils.handleEditorSearchAction
import com.infomaniak.mail.utils.setOnClearTextClickListener
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MoveFragment : MenuFoldersFragment() {

    private lateinit var binding: FragmentMoveBinding
    private val navigationArgs: MoveFragmentArgs by navArgs()
    private val moveViewModel: MoveViewModel by viewModels()

    private val createFolderDialog by lazy { initNewFolderDialog() }

    private lateinit var searchResultsAdpater: FolderAdapter

    private var hasAlreadyTrackedFolderSearch = false

    override val defaultFoldersList: RecyclerView by lazy { binding.defaultFoldersList }
    override val customFoldersList: RecyclerView by lazy { binding.customFoldersList }

    override val isInMenuDrawer = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMoveBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeFolderId()
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
            createFolderDialog.show()
        }
    }

    private fun observeFolderId() = with(navigationArgs) {
        messageUid?.let {
            moveViewModel.getFolderIdByMessage(messageUid).observe(viewLifecycleOwner, ::setAdaptersFolders)
        } ?: run {
            moveViewModel.getFolderIdByThread(threadsUids.first()).observe(viewLifecycleOwner, ::setAdaptersFolders)
        }
    }

    private fun setAdaptersFolders(folderId: String) {
        val (defaultFoldersWithoutDraft, customFolders) = mainViewModel.currentFoldersLive.value!!.let { foldersLists ->
            foldersLists.first.filterNot { it.role == FolderRole.DRAFT } to foldersLists.second
        }

        defaultFoldersAdapter.setFolders(defaultFoldersWithoutDraft, folderId)
        customFoldersAdapter.setFolders(customFolders, folderId)
        setSearchBarUi(allFolders = defaultFoldersWithoutDraft + customFolders, currentFolderId = folderId)
    }

    private fun observeNewFolderCreation() {
        mainViewModel.isNewFolderCreated.observe(viewLifecycleOwner) { isFolderCreated ->
            if (isFolderCreated) findNavController().popBackStack()
        }
    }

    override fun onFolderSelected(folderId: String): Unit = with(navigationArgs) {
        mainViewModel.moveThreadsOrMessageTo(folderId, threadsUids, messageUid)
        findNavController().popBackStack()
    }

    override fun onFolderCollapse(folderId: String, shouldCollapse: Boolean) = Unit

    private fun initNewFolderDialog() = with(navigationArgs) {
        createInputDialog(
            title = R.string.newFolderDialogTitle,
            hint = R.string.newFolderDialogHint,
            confirmButtonText = R.string.newFolderDialogMovePositiveButton,
            onErrorCheck = { folderName -> checkForFolderCreationErrors(folderName) },
            onPositiveButtonClicked = { folderName ->
                trackCreateFolderEvent("confirm")
                mainViewModel.moveToNewFolder(folderName!!.toString(), threadsUids, messageUid)
            },
        )
    }

    private fun setSearchBarUi(allFolders: List<Folder>, currentFolderId: String) = with(binding) {
        searchResultsAdpater = FolderAdapter(
            isInMenuDrawer,
            currentFolderId,
            shouldIndent = false,
            onFolderClicked = ::onFolderSelected,
        )
        searchResultsList.adapter = searchResultsAdpater

        searchInputLayout.setOnClearTextClickListener { trackMoveSearchEvent(SEARCH_DELETE_NAME) }
        searchTextInput.apply {
            doOnTextChanged { text, _, _, _ ->
                toggleFolderListsVisibility(!text.isNullOrBlank())
                if (text?.isNotBlank() == true) moveViewModel.filterFolders(text.toString(), allFolders, shouldDebounce = true)
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
        moveViewModel.filterResults.observe(viewLifecycleOwner) { folders -> searchResultsAdpater.setFolders(folders) }
    }
}
