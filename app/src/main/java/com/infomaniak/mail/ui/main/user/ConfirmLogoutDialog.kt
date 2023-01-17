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
package com.infomaniak.mail.ui.main.user

import android.app.Application
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.DialogConfirmLogoutBinding
import com.infomaniak.mail.utils.AccountUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ConfirmLogoutDialog : DialogFragment() {

    private val binding by lazy { DialogConfirmLogoutBinding.inflate(layoutInflater) }
    private val navigationArgs: ConfirmLogoutDialogArgs by navArgs()
    private val confirmLogoutViewModel: ConfirmLogoutViewModel by viewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setPositiveButton(R.string.buttonLogout) { _, _ ->
                confirmLogoutViewModel.removeCurrentUser()
            }
            .setNegativeButton(R.string.buttonCancel, null)
            .create().also {
                binding.confirmLogoutDialogTitle.text = getString(R.string.confirmLogoutTitle, navigationArgs.emailAddresse)
            }
    }

    class ConfirmLogoutViewModel(application: Application) : AndroidViewModel(application) {
        fun removeCurrentUser() = viewModelScope.launch(Dispatchers.IO) {
            AccountUtils.removeUser(getApplication(), AccountUtils.currentUser!!)
        }
    }
}
