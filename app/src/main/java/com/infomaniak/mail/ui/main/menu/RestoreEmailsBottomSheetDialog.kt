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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import com.infomaniak.lib.core.utils.FORMAT_DATE_WITH_TIMEZONE
import com.infomaniak.lib.core.utils.FORMAT_EVENT_DATE
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.format
import com.infomaniak.mail.MatomoMail.trackRestoreMailsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.BottomSheetRestoreEmailsBinding
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Locale

@AndroidEntryPoint
class RestoreEmailsBottomSheetDialog : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetRestoreEmailsBinding
    private val restoreEmailViewModel: RestoreEmailsViewModel by viewModels()

    private val autoCompleteTextView by lazy { (binding.datePicker.editText as? MaterialAutoCompleteTextView)!! }
    private lateinit var formattedDates: Map<String, String>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetRestoreEmailsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        autoCompleteTextView.hasLoaded(false)

        observeBackups()

        datePickerText.doOnTextChanged { text, _, _, _ ->
            trackRestoreMailsEvent("selectDate", TrackerAction.INPUT)
            restoreMailsButton.isEnabled = text?.isNotBlank() == true
        }

        restoreMailsButton.setOnClickListener { restoreEmails() }
    }

    private fun MaterialAutoCompleteTextView.hasLoaded(hasLoaded: Boolean) {
        isEnabled = hasLoaded
        isClickable = hasLoaded
        if (hasLoaded) text = null else setText(R.string.loadingText)
    }

    private fun observeBackups() {
        restoreEmailViewModel.getBackups().observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                formattedDates = apiResponse.data!!.backups.associateBy { it.getUserFriendlyDate() }
                autoCompleteTextView.apply {
                    setSimpleItems(formattedDates.keys.toTypedArray())
                    hasLoaded(true)
                }
            } else {
                showSnackbar(title = apiResponse.translatedError)
                findNavController().popBackStack()
            }
        }
    }

    private fun restoreEmails() {
        trackRestoreMailsEvent("restore", TrackerAction.CLICK)
        val date = autoCompleteTextView.text.toString()

        restoreEmailViewModel.restoreEmails(formattedDates[date] ?: date).observe(viewLifecycleOwner) { apiResponse ->
            showSnackbar(if (apiResponse.isSuccess()) R.string.snackbarRestorationLaunched else apiResponse.translatedError)
            findNavController().popBackStack()
        }
    }

    private fun String.getUserFriendlyDate(): String {
        val backupDateFormat = FORMAT_DATE_WITH_TIMEZONE.dropLast(1)
        return SimpleDateFormat(backupDateFormat, Locale.getDefault()).parse(this)?.format(FORMAT_EVENT_DATE) ?: this
    }
}
