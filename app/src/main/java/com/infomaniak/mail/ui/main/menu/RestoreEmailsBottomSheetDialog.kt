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
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.mail.databinding.BottomSheetRestoreEmailsBinding
import com.infomaniak.lib.core.R as RCore

class RestoreEmailsBottomSheetDialog : BottomSheetDialogFragment() {
    private lateinit var binding: BottomSheetRestoreEmailsBinding
    private val restoreEmailViewModel: RestoreEmailsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetRestoreEmailsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        val autoCompleteTextView = (datePicker.editText as? MaterialAutoCompleteTextView) ?: return
        autoCompleteTextView.setSimpleItems(
            arrayOf(
                "20 avril 2023 à 4:30 AM",
                "19 avril 2023 à 4:30 AM",
                "12 avril 2023 à 4:30 AM",
                "10 avril 2023 à 4:30 AM",
            )
        )

        restoreEmailViewModel.getBackups().observe(viewLifecycleOwner) { apiResponse ->
            apiResponse?.data?.backups?.let(autoCompleteTextView::setSimpleItems)
            // TODO better management of api Error
            if (!apiResponse.isSuccess()) {
                showSnackbar(RCore.string.anErrorHasOccurred)
                // findNavController().popBackStack()
            }
        }

        datePickerText.doOnTextChanged { text, _, _, _ ->
            restoreMailsButton.isEnabled = text?.isNotBlank() == true
        }

        restoreMailsButton.setOnClickListener {
            restoreEmailViewModel.restoreEmails(autoCompleteTextView.text.toString()).observe(viewLifecycleOwner) { apiResponse ->
                // TODO better management of api Error
                if (apiResponse.isSuccess()) {
                    findNavController().popBackStack()
                } else {
                    showSnackbar(RCore.string.anErrorHasOccurred)
                }
            }
        }
    }
}
