/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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

import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.lib.core.utils.hideKeyboard
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.views.DividerItemDecorator
import com.infomaniak.mail.MatomoMail.SEARCH_DELETE_NAME
import com.infomaniak.mail.MatomoMail.SEARCH_VALIDATE_NAME
import com.infomaniak.mail.MatomoMail.trackCreateFolderEvent
import com.infomaniak.mail.MatomoMail.trackMoveSearchEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.databinding.FragmentMoveBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.InputAlertDialog
import com.infomaniak.mail.utils.UiUtils
import com.infomaniak.mail.utils.extensions.bindAlertToViewLifecycle
import com.infomaniak.mail.utils.extensions.handleEditorSearchAction
import com.infomaniak.mail.utils.extensions.setOnClearTextClickListener
import com.infomaniak.mail.utils.extensions.setSystemBarsColors
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MoveFragment : Fragment() {

    private var binding: FragmentMoveBinding by safeBinding()
    private val navigationArgs: MoveFragmentArgs by navArgs()
    private val moveViewModel: MoveViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    @Inject
    lateinit var inputDialog: InputAlertDialog

    @Inject
    lateinit var folderController: FolderController

    @Inject
    lateinit var folderAdapter: FolderAdapter

    private var hasAlreadyTrackedSearch = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMoveBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setSystemBarsColors()

        bindAlertToViewLifecycle(inputDialog)
        setupRecyclerView()
        setupListeners()
        setupCreateFolderDialog()
        observeFolders()
        observeSearchResults()
        observeFolderCreation()
    }

    private fun setupRecyclerView() = with(binding.foldersRecyclerView) {

        adapter = folderAdapter(isInMenuDrawer = false, onFolderClicked = ::onFolderSelected)

        val margin = resources.getDimensionPixelSize(R.dimen.dividerHorizontalPadding)
        addItemDecoration(
            DividerItemDecorator(
                divider = InsetDrawable(UiUtils.dividerDrawable(requireContext()), margin, 0, margin, 0),
                shouldIgnoreView = { view -> view.tag == UiUtils.IGNORE_DIVIDER_TAG },
            ),
        )
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

    private fun setupCreateFolderDialog() = with(navigationArgs) {
        inputDialog.setCallbacks(
            onPositiveButtonClicked = { folderName ->
                trackCreateFolderEvent("confirm")
                mainViewModel.moveToNewFolder(folderName, threadsUids.toList(), messageUid)
            },
            onErrorCheck = ::checkForFolderCreationErrors,
        )
    }

    private fun observeFolders() {
        moveViewModel.getCurrentFolderAndAllFolders().observe(viewLifecycleOwner) { (allFolders, currentFolderId) ->
            folderAdapter.setFolders(allFolders, currentFolderId, isSearching = false)
            setupSearchBar(allFolders, currentFolderId)
        }
    }

    private fun observeSearchResults() {
        moveViewModel.filterResults.observe(viewLifecycleOwner) { (folders, currentFolderId) ->
            folderAdapter.setFolders(folders, currentFolderId, isSearching = true)
        }
    }

    private fun observeFolderCreation() = with(mainViewModel) {

        newFolderResultTrigger.observe(viewLifecycleOwner) {
            inputDialog.resetLoadingAndDismiss()
        }

        isMovedToNewFolder.observe(viewLifecycleOwner) { isFolderCreated ->
            if (isFolderCreated) findNavController().popBackStack()
        }
    }

    private fun onFolderSelected(folderId: String): Unit = with(navigationArgs) {
        mainViewModel.moveThreadsOrMessageTo(folderId, threadsUids.toList(), messageUid)
        findNavController().popBackStack()
    }

    /**
     * Asynchronously validate folder name locally
     * @return error string, otherwise null
     */
    private fun checkForFolderCreationErrors(folderName: CharSequence): String? = when {
        folderName.length > 255 -> getString(R.string.errorNewFolderNameTooLong)
        folderName.contains(Regex(INVALID_CHARACTERS_PATTERN)) -> getString(R.string.errorNewFolderInvalidCharacter)
        folderController.getRootFolder(folderName.toString()) != null -> context?.getString(R.string.errorNewFolderAlreadyExists)
        else -> null
    }

    private fun setupSearchBar(allFolders: List<Folder>, currentFolderId: String) = with(binding) {

        searchInputLayout.setOnClearTextClickListener { trackMoveSearchEvent(SEARCH_DELETE_NAME) }

        searchTextInput.apply {

            doOnTextChanged { newQuery, _, _, _ ->

                if (newQuery?.isNotBlank() == true) {
                    moveViewModel.filterFolders(newQuery.toString(), allFolders, currentFolderId, shouldDebounce = true)
                } else {
                    folderAdapter.setFolders(allFolders, currentFolderId, isSearching = false)
                }

                if (!hasAlreadyTrackedSearch) {
                    trackMoveSearchEvent("executeSearch")
                    hasAlreadyTrackedSearch = true
                }
            }

            handleEditorSearchAction { query ->
                moveViewModel.filterFolders(query, allFolders, currentFolderId, shouldDebounce = false)
                trackMoveSearchEvent(SEARCH_VALIDATE_NAME)
            }
        }
    }

    override fun onStop() {
        binding.searchTextInput.hideKeyboard()
        moveViewModel.cancelSearch()
        super.onStop()
    }

    companion object {
        private const val INVALID_CHARACTERS_PATTERN = "[/'\"]"
    }
}
