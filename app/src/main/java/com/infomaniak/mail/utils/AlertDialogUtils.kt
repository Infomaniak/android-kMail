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
package com.infomaniak.mail.utils

import android.app.Activity
import android.content.DialogInterface
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.Utils
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.DialogDescriptionBinding
import com.infomaniak.mail.databinding.DialogInputBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch

object AlertDialogUtils {

    fun Fragment.createInformationDialog(
        title: String,
        description: CharSequence = "",
        @StringRes confirmButtonText: Int = R.string.buttonConfirm,
    ): AlertDialog = requireActivity().createInformationDialog(title, description, confirmButtonText)

    fun Activity.createInformationDialog(
        title: String,
        description: CharSequence,
        @StringRes confirmButtonText: Int,
    ): AlertDialog = createDescriptionDialog(
        title = title,
        description = description,
        confirmButtonText = confirmButtonText,
        displayCancelButton = false,
        displayLoader = false,
        onPositiveButtonClicked = {},
    )

    fun Fragment.createDescriptionDialog(
        title: String,
        description: CharSequence,
        @StringRes confirmButtonText: Int = R.string.buttonConfirm,
        displayCancelButton: Boolean = true,
        displayLoader: Boolean = true,
        onPositiveButtonClicked: () -> Unit,
        onDismissed: (() -> Unit)? = null,
    ): AlertDialog = requireActivity().createDescriptionDialog(
        title,
        description,
        confirmButtonText,
        displayCancelButton,
        displayLoader,
        onPositiveButtonClicked,
        onDismissed,
    )

    fun Activity.createDescriptionDialog(
        title: String,
        description: CharSequence,
        @StringRes confirmButtonText: Int = R.string.buttonConfirm,
        displayCancelButton: Boolean = true,
        displayLoader: Boolean = true,
        onPositiveButtonClicked: () -> Unit,
        onDismissed: (() -> Unit)? = null,
    ) = with(DialogDescriptionBinding.inflate(layoutInflater)) {

        fun AlertDialog.setupOnShowListener() = apply {
            setOnShowListener {
                // We are forced to override the ClickListener to prevent the default one to dismiss automatically the Alert
                positiveButton.setOnClickListener {
                    onPositiveButtonClicked()
                    startLoading()
                }
            }
        }

        dialogTitle.text = title
        dialogDescription.text = description

        MaterialAlertDialogBuilder(context)
            .setView(root)
            .setPositiveButton(confirmButtonText) { _, _ -> onPositiveButtonClicked() }
            .apply { if (displayCancelButton) setNegativeButton(com.infomaniak.lib.core.R.string.buttonCancel, null) }
            .setOnDismissListener { onDismissed?.invoke() }
            .create()
            .apply { if (displayLoader) setupOnShowListener() }
    }

    fun Fragment.createInputDialog(
        @StringRes title: Int,
        @StringRes hint: Int,
        @StringRes confirmButtonText: Int,
        onErrorCheck: suspend (CharSequence) -> String?,
        onPositiveButtonClicked: (String) -> Unit,
    ): AlertDialog = with(DialogInputBinding.inflate(layoutInflater)) {

        var errorJob: Job? = null

        fun Button.checkValidation(text: CharSequence) {
            errorJob?.cancel()
            errorJob = lifecycleScope.launch(Dispatchers.IO) {
                val error = onErrorCheck(text.trim())
                if (errorJob?.isActive == true) Dispatchers.Main {
                    textInputLayout.error = error
                    isEnabled = error == null
                }
            }
        }

        fun AlertDialog.setupOnShowListener() = apply {
            setOnShowListener {
                showKeyboard()
                positiveButton.apply {
                    // We are forced to override the ClickListener to prevent the default one to dismiss automatically the Alert
                    setOnClickListener {
                        onPositiveButtonClicked(textInput.trimmedText)
                        hideKeyboard()
                        startLoading()
                    }
                    setText(confirmButtonText)
                    isEnabled = false
                    textInput.doAfterTextChanged {
                        if (it.isNullOrBlank()) {
                            errorJob?.cancel()
                            isEnabled = false
                            textInputLayout.error = null
                        } else {
                            checkValidation(it.trim())
                        }
                    }
                }
            }
        }

        dialogTitle.setText(title)
        textInputLayout.setHint(hint)

        return@with MaterialAlertDialogBuilder(context)
            .setView(root)
            .setPositiveButton(confirmButtonText, null)
            .setNegativeButton(com.infomaniak.lib.core.R.string.buttonCancel, null)
            .setOnDismissListener {
                errorJob?.cancel()
                textInput.text?.clear()
            }
            .create()
            .setupOnShowListener()
    }

    fun AlertDialog.startLoading() {
        setCancelable(false)
        negativeButton.isEnabled = false
        Utils.createRefreshTimer(onTimerFinish = positiveButton::showProgress).start()
    }

    fun AlertDialog.resetLoadingAndDismiss() {
        if (isShowing) {
            dismiss()
            setCancelable(true)
            positiveButton.hideProgress(R.string.buttonCreate)
            negativeButton.isEnabled = true
        }
    }

    fun AlertDialog.showWithDescription(description: CharSequence) {
        show()
        findViewById<TextView>(R.id.dialogDescription)?.text = description
    }

    private inline val AlertDialog.positiveButton get() = (getButton(DialogInterface.BUTTON_POSITIVE) as MaterialButton)
    private inline val AlertDialog.negativeButton get() = (getButton(DialogInterface.BUTTON_NEGATIVE) as MaterialButton)
}
