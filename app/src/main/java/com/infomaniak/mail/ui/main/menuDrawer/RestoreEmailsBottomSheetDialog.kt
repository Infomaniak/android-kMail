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
package com.infomaniak.mail.ui.main.menuDrawer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.mail.MatomoMail.trackRestoreMailsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.BottomSheetRestoreEmailsBinding
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Locale

@AndroidEntryPoint
class RestoreEmailsBottomSheetDialog : BottomSheetDialogFragment() {

    private var binding: BottomSheetRestoreEmailsBinding by safeBinding()
    private val restoreEmailViewModel: RestoreEmailsViewModel by viewModels()

    private val autoCompleteTextView by lazy { (binding.datePicker.editText as? MaterialAutoCompleteTextView)!! }
    private val restoreEmailsButtonProgressTimer by lazy { Utils.createRefreshTimer(onTimerFinish = ::startProgress) }

    private var formattedDates: Map<String, String>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetRestoreEmailsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        toggleLoadedState(false)

        observeBackups()

        datePickerText.setOnItemClickListener { _, _, _, _ ->
            trackRestoreMailsEvent("selectDate", TrackerAction.INPUT)
            restoreMailsButton.isEnabled = true
        }

        restoreMailsButton.apply {
            initProgress(viewLifecycleOwner)
            setOnClickListener {
                restoreEmailsButtonProgressTimer.start()
                restoreEmails()
            }
        }
    }

    override fun onDestroyView() {
        restoreEmailsButtonProgressTimer.cancel()
        super.onDestroyView()
    }

    private fun toggleLoadedState(hasLoaded: Boolean) {
        binding.datePicker.isEnabled = hasLoaded

        autoCompleteTextView.apply {
            isEnabled = hasLoaded
            isClickable = hasLoaded
            if (hasLoaded) text = null else setText(R.string.loadingText)
        }
    }

    private fun observeBackups() {
        restoreEmailViewModel.getBackups().observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                toggleLoadedState(true)

                val backups = apiResponse.data!!.backups
                if (backups.isEmpty()) {
                    autoCompleteTextView.setText(R.string.restoreEmailsNoBackup)
                } else {
                    autoCompleteTextView.apply {
                        setText(R.string.pickerNoSelection)
                        formattedDates = backups.associateBy { it.getUserFriendlyDate() }
                        setSimpleItems(formattedDates!!.keys.toTypedArray())
                    }
                }
            } else {
                showSnackbar(apiResponse.translatedError)
                findNavController().popBackStack()
            }
        }
    }

    private fun restoreEmails() {
        trackRestoreMailsEvent("restore", TrackerAction.CLICK)

        val date = autoCompleteTextView.text.toString()
        val formattedDate = formattedDates?.get(date) ?: date
        restoreEmailViewModel.restoreEmails(formattedDate).observe(viewLifecycleOwner) { apiResponse ->
            restoreEmailsButtonProgressTimer.cancel()
            binding.restoreMailsButton.hideProgressCatching(R.string.buttonConfirmRestoreEmails)
            showSnackbar(if (apiResponse.isSuccess()) R.string.snackbarRestorationLaunched else apiResponse.translatedError)
            findNavController().popBackStack()
        }
    }

    private fun String.getUserFriendlyDate(): String {
        val backupDateFormat = FORMAT_DATE_WITH_TIMEZONE.dropLast(1)
        return SimpleDateFormat(backupDateFormat, Locale.getDefault()).parse(this)?.format(FORMAT_EVENT_DATE) ?: this
    }

    private fun startProgress() {
        binding.restoreMailsButton.showProgressCatching()
    }
}
