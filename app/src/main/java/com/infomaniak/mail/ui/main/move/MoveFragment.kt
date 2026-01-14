/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.move

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.legacy.utils.hideKeyboard
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackCreateFolderEvent
import com.infomaniak.mail.MatomoMail.trackMoveSearchEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.databinding.FragmentMoveBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.CreateFolderDialog
import com.infomaniak.mail.ui.main.search.SearchViewModel
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.extensions.applySideAndBottomSystemInsets
import com.infomaniak.mail.utils.extensions.applyStatusBarInsets
import com.infomaniak.mail.utils.extensions.applyWindowInsetsListener
import com.infomaniak.mail.utils.extensions.bindAlertToViewLifecycle
import com.infomaniak.mail.utils.extensions.handleEditorSearchAction
import com.infomaniak.mail.utils.extensions.setOnClearTextClickListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MoveFragment : Fragment() {

    private var binding: FragmentMoveBinding by safeBinding()
    private val navigationArgs: MoveFragmentArgs by navArgs()
    private val moveViewModel: MoveViewModel by viewModels()
    private val searchViewModel: SearchViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    @Inject
    lateinit var createFolderDialog: CreateFolderDialog

    @Inject
    lateinit var moveAdapter: MoveAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { moveViewModel.initFolders(mainViewModel.displayedFoldersFlow.first()) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMoveBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.applyWindowInsetsListener { _, insets ->
            binding.appBarLayout.applyStatusBarInsets(insets)
            binding.foldersRecyclerView.applySideAndBottomSystemInsets(insets)
        }

        bindAlertToViewLifecycle(createFolderDialog)
        setupRecyclerView()
        setupListeners()
        setupCreateFolder()
        setupSearchBar()
        observeSearchResults()
        observeFolderCreation()
    }

    private fun setupRecyclerView() = with(binding.foldersRecyclerView) {
        adapter = moveAdapter(onFolderClicked = ::onFolderSelected)
    }

    private fun setupListeners() = with(binding) {
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        iconAddFolder.setOnClickListener {
            trackCreateFolderEvent(MatomoName.FromMove)
            createFolderDialog.show(confirmButtonText = R.string.newFolderDialogMovePositiveButton)
        }
    }

    private fun setupCreateFolder() = with(navigationArgs) {
        binding.iconAddFolder.isGone = action == SEARCH
        createFolderDialog.setCallbacks(
            onPositiveButtonClicked = { folderName ->
                mainViewModel.moveToNewFolder(folderName, threadsUids.toList(), messageUid)
            },
        )
    }

    private fun observeSearchResults() = with(moveViewModel) {
        Utils.waitInitMediator(sourceFolderIdLiveData, filterResults).observe(viewLifecycleOwner) { (sourceFolderId, results) ->
            val (folders, shouldDisplayIndent) = results
            moveAdapter.setFolders(sourceFolderId, shouldDisplayIndent, folders)
        }
    }

    private fun observeFolderCreation() = with(mainViewModel) {

        newFolderResultTrigger.observe(viewLifecycleOwner) {
            createFolderDialog.resetLoadingAndDismiss()
        }

        isMovedToNewFolder.observe(viewLifecycleOwner) { isFolderCreated ->
            if (isFolderCreated) findNavController().popBackStack()
        }
    }

    private fun onFolderSelected(folder: Folder): Unit = with(navigationArgs) {
        when (action) {
            MOVE -> mainViewModel.moveThreadsOrMessageTo(folder.id, threadsUids.toList(), messageUid)
            SEARCH -> searchViewModel.selectFolder(folder)
        }
        findNavController().popBackStack()
    }

    private fun setupSearchBar() = with(binding) {

        searchInputLayout.setOnClearTextClickListener { trackMoveSearchEvent(MatomoName.DeleteSearch) }

        searchTextInput.apply {
            doOnTextChanged { newQuery, _, _, _ ->
                moveViewModel.filterFolders(newQuery, shouldDebounce = true)
                if (!moveViewModel.hasAlreadyTrackedSearch) {
                    trackMoveSearchEvent(MatomoName.ExecuteSearch)
                    moveViewModel.hasAlreadyTrackedSearch = true
                }
            }

            handleEditorSearchAction { query ->
                moveViewModel.filterFolders(query, shouldDebounce = false)
                trackMoveSearchEvent(MatomoName.ValidateSearch)
            }
        }
    }

    override fun onStop() {
        binding.searchTextInput.hideKeyboard()
        moveViewModel.cancelSearch()
        super.onStop()
    }

    companion object {
        // Actions
        const val MOVE = "move"
        const val SEARCH = "search"
    }
}
