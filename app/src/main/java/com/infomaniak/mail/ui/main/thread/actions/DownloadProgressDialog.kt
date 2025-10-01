/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread.actions

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.core.legacy.R
import com.infomaniak.core.legacy.utils.SnackbarUtils.showSnackbar
import com.infomaniak.mail.databinding.DialogDownloadProgressBinding
import com.infomaniak.mail.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
abstract class DownloadProgressDialog : DialogFragment() {

    protected val binding by lazy { DialogDownloadProgressBinding.inflate(layoutInflater) }
    protected val mainViewModel: MainViewModel by activityViewModels()

    abstract val dialogTitle: String

    protected abstract fun download()

    override fun onStart() {
        download()
        super.onStart()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(dialogTitle)
            .setView(binding.root)
            .create()
    }

    protected fun popBackStackWithError() {
        showSnackbar(title = if (mainViewModel.hasNetwork) R.string.anErrorHasOccurred else R.string.noConnection)
        findNavController().popBackStack()
    }
}
