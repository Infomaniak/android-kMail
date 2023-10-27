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
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.InputAlertDialog
import com.infomaniak.mail.utils.bindAlertToViewLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
abstract class MenuFoldersFragment : Fragment() {

    @Inject
    lateinit var defaultFolderAdapter: FolderAdapter

    @Inject
    lateinit var customFolderAdapter: FolderAdapter

    @Inject
    lateinit var folderController: FolderController

    @Inject
    lateinit var inputDialog: InputAlertDialog

    protected val mainViewModel: MainViewModel by activityViewModels()

    protected abstract val defaultFoldersList: RecyclerView
    protected abstract val customFoldersList: RecyclerView

    protected abstract val isInMenuDrawer: Boolean

    protected val defaultFoldersAdapter: FolderAdapter by lazy {
        defaultFolderAdapter(isInMenuDrawer, onFolderClicked = ::onFolderSelected)
    }

    protected val customFoldersAdapter: FolderAdapter by lazy {
        customFolderAdapter(isInMenuDrawer, onFolderClicked = ::onFolderSelected, onCollapseClicked = ::onFolderCollapse)
    }

    protected abstract fun onFolderSelected(folderId: String)

    protected abstract fun onFolderCollapse(folderId: String, shouldCollapse: Boolean)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        bindAlertToViewLifecycle(inputDialog)
    }

    open fun setupAdapters() {
        defaultFoldersList.adapter = defaultFoldersAdapter
        customFoldersList.adapter = customFoldersAdapter
    }

    /**
     * Asynchronously validate folder name locally
     * @return error string, otherwise null
     */
    protected fun checkForFolderCreationErrors(folderName: CharSequence): String? = when {
        folderName.length > 255 -> getString(R.string.errorNewFolderNameTooLong)
        folderName.contains(Regex(INVALID_CHARACTERS_PATTERN)) -> getString(R.string.errorNewFolderInvalidCharacter)
        folderController.getRootFolder(folderName.toString()) != null -> context?.getString(R.string.errorNewFolderAlreadyExists)
        else -> null
    }

    private companion object {
        const val INVALID_CHARACTERS_PATTERN = "[/'\"]"
    }
}
