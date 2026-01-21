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
package com.infomaniak.mail.ui.main.folderPicker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
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
import com.infomaniak.mail.databinding.FragmentFolderPickerBinding
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
class FolderPickerFragment : Fragment() {

    private var binding: FragmentFolderPickerBinding by safeBinding()
    private val navigationArgs: FolderPickerFragmentArgs by navArgs()
    private val folderPickerViewModel: FolderPickerViewModel by viewModels()
    private val searchViewModel: SearchViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    @Inject
    lateinit var createFolderDialog: CreateFolderDialog

    @Inject
    lateinit var folderPickerAdapter: FolderPickerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            folderPickerViewModel.initFolders(mainViewModel.displayedFoldersFlow.first(), navigationArgs.action)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentFolderPickerBinding.inflate(inflater, container, false).also { binding = it }.root
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
        setupToolbar()
        setupSearchBar()
        observeSearchResults()
        observeFolderCreation()
    }

    private fun setupRecyclerView() = with(binding.foldersRecyclerView) {
        adapter = folderPickerAdapter(onFolderClicked = ::onFolderSelected)
    }

    private fun setupListeners() = with(binding) {
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        iconAddFolder.setOnClickListener {
            trackCreateFolderEvent(MatomoName.FromMove)
            createFolderDialog.show(confirmButtonText = R.string.newFolderDialogMovePositiveButton)
        }
    }

    private fun setupToolbar() = with(navigationArgs) {
        when (action) {
            FolderPickerAction.SEARCH -> {
                binding.iconAddFolder.isInvisible = true
                binding.toolbarSubject.text = getString(R.string.searchFolderName)
            }
            FolderPickerAction.MOVE -> {
                binding.iconAddFolder.isVisible = true
                binding.toolbarSubject.text = getString(R.string.actionMove)
                setupCreateFolderDialog()
            }
        }
    }

    private fun setupCreateFolderDialog() = with(navigationArgs) {
        createFolderDialog.setCallbacks(
            onPositiveButtonClicked = { folderName ->
                mainViewModel.moveToNewFolder(folderName, threadsUids.toList(), messageUid)
            },
        )
    }

    private fun observeSearchResults() = with(folderPickerViewModel) {
        Utils.waitInitMediator(sourceFolderIdLiveData, filterResults).observe(viewLifecycleOwner) { (sourceFolderId, results) ->
            val (folders, shouldDisplayIndent) = results
            folderPickerAdapter.setFolders(sourceFolderId, shouldDisplayIndent, folders)
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

    private fun onFolderSelected(folder: Folder?): Unit = with(navigationArgs) {
        when (action) {
            FolderPickerAction.MOVE -> folder?.id?.let {
                mainViewModel.moveThreadsOrMessageTo(
                    it,
                    threadsUids.toList(),
                    messageUid
                )
            }
            FolderPickerAction.SEARCH -> searchViewModel.selectFolder(folder)
        }
        findNavController().popBackStack()
    }

    private fun setupSearchBar() = with(binding) {

        searchInputLayout.setOnClearTextClickListener { trackMoveSearchEvent(MatomoName.DeleteSearch) }

        searchTextInput.apply {
            doOnTextChanged { newQuery, _, _, _ ->
                folderPickerViewModel.filterFolders(newQuery, shouldDebounce = true)
                if (!folderPickerViewModel.hasAlreadyTrackedSearch) {
                    trackMoveSearchEvent(MatomoName.ExecuteSearch)
                    folderPickerViewModel.hasAlreadyTrackedSearch = true
                }
            }

            handleEditorSearchAction { query ->
                folderPickerViewModel.filterFolders(query, shouldDebounce = false)
                trackMoveSearchEvent(MatomoName.ValidateSearch)
            }
        }
    }

    override fun onStop() {
        binding.searchTextInput.hideKeyboard()
        folderPickerViewModel.cancelSearch()
        super.onStop()
    }

}

enum class FolderPickerAction {
    MOVE,
    SEARCH
}
