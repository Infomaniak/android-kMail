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
import com.infomaniak.mail.databinding.FragmentMoveBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.CreateFolderDialog
import com.infomaniak.mail.utils.UiUtils
import com.infomaniak.mail.utils.Utils
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
    lateinit var createFolderDialog: CreateFolderDialog

    @Inject
    lateinit var folderController: FolderController

    @Inject
    lateinit var moveAdapter: MoveAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMoveBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setSystemBarsColors()

        bindAlertToViewLifecycle(createFolderDialog)
        setupRecyclerView()
        setupListeners()
        setupCreateFolderDialog()
        setupSearchBar()
        observeSearchResults()
        observeFolderCreation()
    }

    private fun setupRecyclerView() = with(binding.foldersRecyclerView) {

        adapter = moveAdapter(onFolderClicked = ::onFolderSelected)

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
            createFolderDialog.show(confirmButtonText = R.string.newFolderDialogMovePositiveButton)
        }
    }

    private fun setupCreateFolderDialog() = with(navigationArgs) {
        createFolderDialog.setCallbacks(
            onPositiveButtonClicked = { folderName ->
                mainViewModel.moveToNewFolder(folderName, threadsUids.toList(), messageUid)
            },
        )
    }

    private fun observeSearchResults() = with(moveViewModel) {
        Utils.waitInitMediator(sourceFolderIdLiveData, filterResults).observe(viewLifecycleOwner) { (sourceFolderId, folders) ->
            moveAdapter.setFolders(sourceFolderId, folders)
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

    private fun onFolderSelected(folderId: String): Unit = with(navigationArgs) {
        mainViewModel.moveThreadsOrMessageTo(folderId, threadsUids.toList(), messageUid)
        findNavController().popBackStack()
    }

    private fun setupSearchBar() = with(binding) {

        searchInputLayout.setOnClearTextClickListener { trackMoveSearchEvent(SEARCH_DELETE_NAME) }

        searchTextInput.apply {
            doOnTextChanged { newQuery, _, _, _ ->
                moveViewModel.filterFolders(newQuery, shouldDebounce = true)
                if (!moveViewModel.hasAlreadyTrackedSearch) {
                    trackMoveSearchEvent("executeSearch")
                    moveViewModel.hasAlreadyTrackedSearch = true
                }
            }

            handleEditorSearchAction { query ->
                moveViewModel.filterFolders(query, shouldDebounce = false)
                trackMoveSearchEvent(SEARCH_VALIDATE_NAME)
            }
        }
    }

    override fun onStop() {
        binding.searchTextInput.hideKeyboard()
        moveViewModel.cancelSearch()
        super.onStop()
    }
}
