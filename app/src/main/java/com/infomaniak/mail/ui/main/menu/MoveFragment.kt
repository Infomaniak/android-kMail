/*
 * Infomaniak kMail - Android
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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.mail.databinding.FragmentMoveBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.refreshObserve

class MoveFragment : Fragment() {

    private lateinit var binding: FragmentMoveBinding
    private val mainViewModel: MainViewModel by activityViewModels()
    private val navigationArgs: MoveFragmentArgs by navArgs()

    private var inboxFolderId: String? = null

    private val defaultFoldersAdapter = FolderAdapter(
        onClick = { folderId -> moveToFolder(folderId) },
        isInMenuDrawer = false,
    )
    private val customFoldersAdapter = FolderAdapter(
        onClick = { folderId -> moveToFolder(folderId) },
        isInMenuDrawer = false,
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMoveBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        setupAdapters()
        setupListeners()
        observeFoldersLive()
    }

    private fun setupAdapters() = with(binding) {
        defaultFoldersList.adapter = defaultFoldersAdapter
        customFoldersList.adapter = customFoldersAdapter
    }

    private fun setupListeners() {
        binding.inboxFolder.setOnClickListener { inboxFolderId?.let(::moveToFolder) }
    }

    private fun observeFoldersLive() {
        mainViewModel.currentFoldersLiveToObserve.refreshObserve(viewLifecycleOwner) { (inbox, defaultFolders, customFolders) ->

            inboxFolderId = inbox?.id

            val currentFolder = mainViewModel.currentFolder.value ?: return@refreshObserve
            defaultFoldersAdapter.setFolders(defaultFolders, currentFolder.id)
            customFoldersAdapter.setFolders(customFolders, currentFolder.id)
        }
    }

    private fun moveToFolder(folderId: String) = with(navigationArgs) {
        mainViewModel.moveTo(folderId, threadUid, messageUid)
        findNavController().popBackStack()
    }
}
