/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.DialogNewFolderBinding
import com.infomaniak.mail.ui.MainViewModel

class NewFolderDialog : DialogFragment() {

    private val binding by lazy { DialogNewFolderBinding.inflate(layoutInflater) }

    private val mainViewModel: MainViewModel by activityViewModels()
    private val navigationArgs: NewFolderDialogArgs by navArgs()

    override fun onCreateDialog(savedInstanceState: Bundle?) = with(navigationArgs) {
        MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setPositiveButton(R.string.newFolderDialogPositiveButton) { _, _ ->
                val folderName = binding.folderName.text.toString()
                if (threadUid == null) {
                    mainViewModel.createNewFolder(folderName)
                } else {
                    mainViewModel.moveToNewFolder(folderName, threadUid, messageUid)
                    findNavController().popBackStack(R.id.moveFragment, true)
                }
            }
            .setNegativeButton(R.string.buttonCancel, null)
            .create()
    }
}
